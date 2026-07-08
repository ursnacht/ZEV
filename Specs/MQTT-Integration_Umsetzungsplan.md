# MQTT-Integration – Umsetzungsplan

## Zusammenfassung

Das ZEV-Backend wird um einen **MQTT-Subscriber** erweitert, der die vom Pi-Gateway
publizierten **absoluten Zählerstände** (`zev/{orgId}/{messpunkt}/messwert`) empfängt,
validiert und als Rohdaten persistiert. Ein **Scheduled-Aggregations-Job** bildet daraus
je Register die Intervall-Differenz und schreibt vorzeichenbehaftete `messwerte`
(`total = ΔBezug − ΔEinspeisung`, `zev = 0`, `quelle = 'MQTT'`). Die Solarverteilung
ersetzt `zev = 0` durch `zev_calculated`. Der gesamte MQTT-Pfad ist über das
Spring-Profil **`mqtt`** zuschaltbar; ohne Profil bleibt das Backend unverändert
(nur CSV-Upload).

Grundlage: [`Specs/MQTT-Integration.md`](./MQTT-Integration.md); Publisher-Gegenstück:
[`Specs/Pi-Gateway-Software.md`](./Pi-Gateway-Software.md); Broker/Topologie:
[`docs/Netzwerk-Topologie-Hene.md`](../docs/Netzwerk-Topologie-Hene.md).

> **Reines Backend-Feature** (Hintergrund-Ingest): kein Frontend, kein Controller, keine
> Route, kein `@PreAuthorize`, keine i18n-Texte (NFR-2). Die Code-Vorlagen aus CLAUDE.md
> (Entity/Repository/Service) gelten für Entity/Repository/Service-Teile; Controller/Angular
> entfallen.

## Architektur-Hinweise (wichtig)

- **Kein Request-Scope im Ingest/Job:** MQTT-Nachrichten und der Scheduled-Job laufen ohne
  HTTP-Request/JWT. `OrganizationContextService` (`@RequestScope`) und
  `HibernateFilterService.enableOrgFilter()` (zieht `org_id` aus dem Kontext) sind hier
  **nicht** verwendbar. Mandantentrennung erfolgt **explizit**: Auflösung via
  `findByOrgIdAndMesspunkt(orgId, messpunkt)`, `org_id` beim Persistieren explizit gesetzt,
  Aggregation gruppiert explizit nach `(org_id, einheit)`.
- **`org_id` = internes `BIGINT`/`Long`** (Entscheidung Spec §8), konsistent mit
  `Messwerte.orgId` und `Einheit.orgId`.
- **`@Profile("mqtt")`** auf allen MQTT-Beans (Subscriber, Ingest-Handler, Aggregations-Job,
  Health-Indicator); FR-9 (Solarverteilung) ist profil-**unabhängig** (nur `zev=0`-Fallback).

## Betroffene Komponenten

**Neu:**
- `backend-service/src/main/resources/db/migration/V69__create_zaehler_rohdaten.sql` – Rohdaten-Tabelle
- `backend-service/src/main/resources/db/migration/V70__Add_messwerte_quelle.sql` – `quelle`-Spalte
- `backend-service/src/main/resources/application-mqtt.yml` – MQTT-Profil-Konfiguration (Broker via Env)
- `entity/ZaehlerRohdaten.java` – Entity (org_id, `@Filter orgFilter`)
- `entity/Quelle.java` – Enum `CSV`/`MQTT`/`API` (oder `String`-Konstante; siehe Annahmen)
- `repository/ZaehlerRohdatenRepository.java` – Repository (unverarbeitete Rohdaten, Upsert-Auflösung)
- `config/MqttConfig.java` – Spring-Integration-MQTT Inbound-Adapter + Connection (`@Profile("mqtt")`)
- `dto/ZaehlerMesswertPayload.java` – JSON-Payload (`timestamp`, `zaehlerstandBezug`, `zaehlerstandEinspeisung`)
- `service/MqttIngestService.java` – Topic-/Payload-Parsing, Validierung, Einheit-Auflösung, Persistenz (`@Profile("mqtt")`)
- `service/ZaehlerAggregationService.java` – `@Scheduled`-Delta-/Aggregations-Job (`@Profile("mqtt")`)
- `health/MqttHealthIndicator.java` – `/actuator/health/mqtt` (`@Profile("mqtt")`)

**Geändert:**
- `backend-service/pom.xml` – Dependencies `spring-integration-mqtt` (+ Paho mqttv3)
- `entity/Messwerte.java` – Feld `quelle`
- `repository/EinheitRepository.java` – `findByOrgIdAndMesspunkt(Long, String)`
- `service/SolarDistribution.java` – FR-9: `zev = zev_calculated`, wo `zev == 0`
- `service/MetricsService.java` – MQTT-/Aggregations-Metriken (FR-8)
- `service/MesswerteService.java` – `quelle = 'CSV'` beim CSV-Upload setzen (Konsistenz)

## Umsetzungsreihenfolge (Phasen)

| Status | Phase | Beschreibung |
|--------|-------|--------------|
| [ ] | 1. Dependencies & Profil-Gerüst | `spring-integration-mqtt` + Paho in `pom.xml` (Version aus Spring-Boot-BOM, keine Modul-Override). `application-mqtt.yml` mit `mqtt.broker.url/username/password`, `mqtt.topic`, `mqtt.qos`, `mqtt.client-id` (Secrets via Env). Kompiliert ohne aktives Profil unverändert. |
| [ ] | 2. DB-Migration Rohdaten | `V69__create_zaehler_rohdaten.sql`: Tabelle gemäss FR-5 (`org_id BIGINT`, `einheit_id FK`, `zeit`, `zaehlerstand_bezug/einspeisung DECIMAL(14,4)`, `empfangen_am`, `verarbeitet`, `verarbeitet_am`, Unique `(einheit_id, zeit)`, Teilindex unverarbeitet). Spalten-Kommentare. |
| [ ] | 3. DB-Migration `quelle` | `V70__Add_messwerte_quelle.sql`: `ALTER TABLE zev.messwerte ADD COLUMN IF NOT EXISTS quelle VARCHAR(20) DEFAULT 'CSV'` + Kommentar. **Kein** Eingriff an `zev` (bleibt `NOT NULL`). |
| [ ] | 4. Entity + Repository Rohdaten | `ZaehlerRohdaten` (Vorlage `Tarif.java`; `orgId` + `@Filter orgFilter`), `Quelle`-Enum, `ZaehlerRohdatenRepository` (`findFirstByEinheitIdAndZeit` für Upsert, `findByVerarbeitetFalseOrderByZeitAsc`). `Messwerte.quelle`-Feld ergänzen. |
| [ ] | 5. Einheit-Auflösung | `EinheitRepository.findByOrgIdAndMesspunkt(Long orgId, String messpunkt)` (explizite Mandantenprüfung, unabhängig vom orgFilter). |
| [ ] | 6. MQTT-Subscriber (Connection) | `MqttConfig` (`@Profile("mqtt")`): Paho-`MqttPahoClientFactory` (URL/Creds/TLS aus Config), Inbound-Channel-Adapter auf `mqtt.topic` (`zev/+/+/messwert`), QoS, automatischer Reconnect. Verbindung beim Start (FR-1). |
| [ ] | 7. Ingest-Handler (FR-4) | `MqttIngestService`: Topic→`orgId`+`messpunkt`, JSON→`ZaehlerMesswertPayload`, Validierung (Pflichtfelder/≥0), Einheit-Auflösung `(orgId, messpunkt)`, Cross-Tenant-Check, Rohdaten-Upsert (org_id **explizit**), Fehler → WARN + verwerfen (keine Exception nach aussen), non-blocking. |
| [ ] | 8. Aggregations-Job (FR-6) | `ZaehlerAggregationService` (`@Scheduled(cron "0 0,15,30,45 * * * *")`, `@Profile("mqtt")`): je `(org_id, einheit)` `ΔBezug`/`ΔEinspeisung` über Intervallgrenze; **Reset-Guard pro Register** (Δ<0 → 0, Referenz neu, WARN); `total = ΔBezug − ΔEinspeisung` (signed), `zev = 0`, `messwerte` Insert/Upsert mit `quelle='MQTT'` + org_id explizit; Rohdaten `verarbeitet=TRUE`. Leeres Intervall → kein Eintrag. |
| [ ] | 9. Solarverteilung FR-9 | `SolarDistribution`: nach Berechnung `zev = zev_calculated` setzen, sofern `zev == 0`; gemessene Werte (`zev ≠ 0`) unverändert. Verteilalgorithmus selbst unverändert. |
| [ ] | 10. Metriken (FR-8) | `MetricsService` erweitern: `zev_mqtt_messages_received/processed/failed_total` (Counter), `zev_mqtt_last_message_timestamp` (Gauge), `zev_aggregation_runs_total` (Counter), `zev_aggregation_last_run_timestamp` (Gauge). Unter `/actuator/prometheus`. |
| [ ] | 11. Health-Indicator (FR-8) | `MqttHealthIndicator implements HealthIndicator` (`@Profile("mqtt")`): Broker-Verbindungsstatus → `/actuator/health/mqtt`. Actuator-Exposure ggf. ergänzen. |
| [ ] | 12. CSV-Quelle & Doku | `MesswerteService`: `quelle='CSV'` beim Upload setzen. `.env.example` (Broker-Vars), kurzer CLAUDE.md-/README-Hinweis zum `mqtt`-Profil. |

> **Reihenfolge-Hinweis:** Phasen 2–5 (DB/Entity) sind Voraussetzung für 7/8. Phase 6 (Connection) und 7 (Handler) bilden den Empfang; 8 die Verarbeitung; 9 die Verrechnung. 10/11 sind Monitoring, unabhängig testbar. FR-9 (Phase 9) ist die einzige profil-unabhängige Änderung.

## Validierungen

### Ingest (MqttIngestService, FR-4)
- **Topic:** genau 4 Segmente `zev/{orgId}/{messpunkt}/messwert`; `orgId` numerisch parsebar — sonst WARN + verwerfen.
- **Payload:** `timestamp` (ISO 8601 UTC) vorhanden/parsebar; `zaehlerstandBezug` und `zaehlerstandEinspeisung` vorhanden, numerisch, **≥ 0** — sonst WARN + verwerfen.
- **Einheit-Auflösung:** `(orgId, messpunkt)` muss existieren — unbekannt → WARN + verwerfen (kein Insert).
- **Mandanten-Isolation:** Auflösung ausschliesslich über `(orgId, messpunkt)`; kein Cross-Tenant-Write möglich (org_id explizit gesetzt).
- **Duplikat** `(einheit_id, zeit)`: Upsert (idempotent), kein Doppel-Insert.
- **Zeitstempel in der Zukunft:** WARN, aber speichern.
- **Robustheit:** kein Fehler propagiert an den MQTT-Adapter (Nachricht gilt als konsumiert); DB-Fehler → nicht als verarbeitet markieren.

### Aggregation (ZaehlerAggregationService, FR-6)
- Delta **pro Register** über die Intervallgrenze; `ΔBezug`/`ΔEinspeisung` < 0 → betroffenes Delta = 0, Referenz neu, WARN (nicht am `total`-Vorzeichen prüfen).
- `total = ΔBezug − ΔEinspeisung` (vorzeichenbehaftet); `zev = 0`; `quelle = 'MQTT'`.
- Leeres Intervall (keine neue Meldung) → **kein** `messwerte`-Eintrag.
- Bereits verarbeitete Rohdaten (`verarbeitet = TRUE`) werden nicht erneut aggregiert; Job-Neustart verarbeitet offene Rohdaten.

### Solarverteilung (FR-9)
- `zev = zev_calculated` **nur** wo `zev == 0`; `zev ≠ 0` (CSV) bleibt unverändert.

## Offene Punkte / Annahmen

- **Reset-Schwellwert:** Annahme = einfache Regel „Δ < 0 (pro Register) ⇒ Delta 0 + Referenz neu" ohne zusätzlichen Toleranz-Schwellwert (Spec §8 offen, Detail).
- **Prosumer im selben Intervall:** Annahme = Netto `total = ΔBezug − ΔEinspeisung` genügt (Spec §8 offen; fachliche Bestätigung ausstehend).
- **`quelle`-Repräsentation:** Annahme = `Quelle`-Enum (`CSV`/`MQTT`/`API`) als `@Enumerated(STRING)`; DB-Spalte `VARCHAR(20)` DEFAULT `'CSV'`.
- **`messpunkt` eindeutig pro `org_id`** (Spec §8 entschieden): Auflösung erwartet höchstens einen Treffer; Annahme = Eindeutigkeit fachlich sichergestellt (optionaler Unique-Index `(org_id, messpunkt)` auf `einheit` als spätere Härtung, nicht Teil dieses Plans).
- **FR-9-Kante:** gemessenes echtes `zev = 0` würde ebenfalls durch `zev_calculated` ersetzt (Effekt vernachlässigbar; Alternative über `quelle='MQTT'` möglich — Umsetzungsdetail, Spec dokumentiert).
- **Rohdaten-Retention/Cleanup:** Out of Scope (Spec: „folgt später").
- **Dependency-Versionen:** aus Spring-Boot-BOM (keine Modul-Property-Overrides, gemäss Projekt-Konvention); Paho = `mqttv3` (Default von `spring-integration-mqtt`).
- **Broker/ACL-Einrichtung** auf dem NAS ist Betrieb (nicht Code) — `docs/Netzwerk-Topologie-Hene.md`.
- **Tests** werden separat erstellt (`/3_backend-tests`): Ingest (Topic/Payload/Validierung/Auflösung/Mandant), Aggregation (Delta/Signed/Reset/Leerintervall), FR-9, Rohdaten-Repository.
