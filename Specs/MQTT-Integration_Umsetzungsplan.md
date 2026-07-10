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
- **Zeitzone:** Der Pi sendet UTC; der Ingest konvertiert auf die **lokale Zone**
  (`ZoneId.systemDefault()`) und speichert `zaehler_rohdaten.zeit` als lokale Wanduhrzeit.
  Der Aggregations-Job nutzt `LocalDateTime.now()` (lokal). Voraussetzung: Container-TZ =
  lokale Zone (`TZ=Europe/Zurich` in `docker-compose*.yml`). So liegen MQTT- und CSV-Werte
  auf demselben lokalen 15-Minuten-Raster.
- **`@Profile("mqtt")`** auf allen MQTT-Beans (Subscriber, Ingest-Handler, Aggregations-Job,
  Health-Indicator); FR-9 (Solarverteilung) ist profil-**unabhängig** (nur `zev=0`-Fallback).

## Ablauf: zwei getrennte Auslöser

Das Feature hat **zwei unabhängige Auslöser** — der Ingest ist **ereignisgesteuert (Push)**,
die Aggregation **zeitgesteuert (`@Scheduled`)**. Für den Ingest gibt es bewusst **keinen**
Scheduled-/Polling-Task: der Broker stellt Nachrichten über den message-driven Inbound-Adapter zu.

**1) Ingest (Push, ereignisgesteuert):**

```
Broker (Mosquitto)
  │  liefert Nachricht auf  zev/+/+/messwert
  ▼
MqttPahoMessageDrivenChannelAdapter  (Bean "mqttInbound", MessageProducerSupport)
  │  dauerhafte Subscription, beim Start geöffnet; kein Polling
  ▼
mqttInputChannel  (DirectChannel)
  ▼
@ServiceActivator  mqttMessageHandler  (MessageHandler in MqttConfig)
  │  liest RECEIVED_TOPIC-Header + Payload
  ▼
MqttIngestService.handle(topic, payload)   ← hier wird handle() aufgerufen
  │  Topic/Payload parsen, validieren, Einheit (org_id, messpunkt) auflösen
  ▼
zev.zaehler_rohdaten  (Upsert, org_id explizit)
```

**2) Aggregation (Pull, zeitgesteuert):**

```
@Scheduled(cron "0 0,15,30,45 * * * *")
  ▼
ZaehlerAggregationService.aggregiere()
  │  je Einheit: ΔBezug/ΔEinspeisung über Intervallgrenze (Reset-Guard pro Register)
  ▼
zev.messwerte  (total = ΔBezug − ΔEinspeisung, zev = 0, quelle = MQTT)  +  Rohdaten verarbeitet=true
```

> `MqttIngestService.handle(...)` wird **ausschliesslich** vom `@ServiceActivator`-Handler in
> `MqttConfig` aufgerufen (kein direkter/geplanter Aufruf). Beide Auslöser sind nur mit aktivem
> `@Profile("mqtt")` scharf. Der Empfang lässt sich erst gegen einen echten Broker verifizieren.

## Betroffene Komponenten

**Neu:**
- `backend-service/src/main/resources/db/migration/V69__create_zaehler_rohdaten.sql` – Rohdaten-Tabelle
- `backend-service/src/main/resources/db/migration/V72__create_zaehler_rohdaten.sql` – Rohdaten-Tabelle
- `backend-service/src/main/resources/db/migration/V73__Add_messwerte_quelle.sql` – `quelle`-Spalte
- `backend-service/src/main/resources/application-mqtt.yml` – MQTT-Profil-Konfiguration (Broker via Env)
- `entity/ZaehlerRohdaten.java` – Entity (org_id, `@Filter orgFilter`)
- `entity/Quelle.java` – Enum `CSV`/`MQTT`/`API`
- `repository/ZaehlerRohdatenRepository.java` – Upsert-/Referenz-/Catch-up-Queries, `markVerarbeitet`
- `config/MqttConfig.java` – Spring-Integration-MQTT Inbound-Adapter + Connection (`@Profile("mqtt")`)
- `dto/ZaehlerMesswertPayloadDTO.java` – JSON-Payload (`timestamp`, `zaehlerstandBezug`, `zaehlerstandEinspeisung`)
- `service/MqttIngestService.java` – Topic-/Payload-Parsing, Validierung, Einheit-Auflösung, Persistenz (`@Profile("mqtt")`)
- `service/ZaehlerAggregationService.java` – `@Scheduled`-Delta-/Aggregations-Job (`@Profile("mqtt")`)
- `service/MqttMetrics.java` – Prometheus-Metriken via `MeterRegistry` (`@Profile("mqtt")`)
- `health/MqttHealthIndicator.java` – `/actuator/health/mqtt` (`@Profile("mqtt")`)

**Geändert:**
- `backend-service/pom.xml` – `spring-boot-starter-integration`, `spring-integration-mqtt`, Paho `1.2.5`
- `entity/Messwerte.java` – Feld `quelle` (Default `CSV`)
- `repository/EinheitRepository.java` – `findByOrgIdAndMesspunkt(Long, String)`
- `repository/MesswerteRepository.java` – `findByEinheitAndZeit` (Upsert)
- `service/MesswerteService.java` – FR-9: `zev = zev_calculated`, wo `zev == 0` (in der Verteil-Schleife)
- `.env.example` – MQTT-Broker-Variablen

## Umsetzungsreihenfolge (Phasen)

| Status | Phase | Beschreibung |
|--------|-------|--------------|
| [x] | 1. Dependencies & Profil-Gerüst | `spring-boot-starter-integration` + `spring-integration-mqtt` (via BOM) + **Paho `1.2.5` explizit** (in `spring-integration-mqtt` optional, nicht BOM-verwaltet) in `pom.xml`. `application-mqtt.yml` mit `mqtt.broker.url/username/password`, `mqtt.topic`, `mqtt.qos`, `mqtt.client-id` (Secrets via Env). |
| [x] | 2. DB-Migration Rohdaten | **`V72__create_zaehler_rohdaten.sql`** (V69/V70 waren im Plan reserviert, inzwischen aber V71 vergeben → V72/V73). Tabelle gemäss FR-5 (Sequence-Pattern, `org_id BIGINT`, `einheit_id FK`, `zeit`, `zaehlerstand_bezug/einspeisung DECIMAL(14,4)`, `empfangen_am`, `verarbeitet`, `verarbeitet_am`, Unique `(einheit_id, zeit)`, Teilindex). Spalten-Kommentare. |
| [x] | 3. DB-Migration `quelle` | **`V73__Add_messwerte_quelle.sql`**: `ADD COLUMN IF NOT EXISTS quelle VARCHAR(20) NOT NULL DEFAULT 'CSV'` + Kommentar. **Kein** Eingriff an `zev`. |
| [x] | 4. Entity + Repository Rohdaten | `ZaehlerRohdaten` (`orgId` + `@Filter orgFilter`, Sequence), `Quelle`-Enum, `ZaehlerRohdatenRepository` (Upsert, Referenz-/Catch-up-Queries, `markVerarbeitet`). `Messwerte.quelle` (Default `CSV`). |
| [x] | 5. Einheit-Auflösung | `EinheitRepository.findByOrgIdAndMesspunkt(Long, String)`. |
| [x] | 6. MQTT-Subscriber (Connection) | `MqttConfig` (`@Profile("mqtt")`): `DefaultMqttPahoClientFactory` (URL/Creds/TLS, `automaticReconnect`), `MqttPahoMessageDrivenChannelAdapter` auf `mqtt.topic`, QoS, `@ServiceActivator`-Handler. |
| [x] | 7. Ingest-Handler (FR-4) | `MqttIngestService` (`@Profile("mqtt")`, `@Transactional`): Topic→`orgId`+`messpunkt`, JSON→`ZaehlerMesswertPayloadDTO`, Validierung (Pflichtfelder/≥0), Auflösung `(orgId, messpunkt)`, Rohdaten-Upsert (org_id **explizit**), Fehler → WARN + verwerfen; Metriken. |
| [x] | 8. Aggregations-Job (FR-6) | `ZaehlerAggregationService` (`@Scheduled "0 0,15,30,45 * * * *"`, `@Profile("mqtt")`): Catch-up je Einheit über Quartals-Intervalle; **Reset-Guard pro Register**; `total = ΔBezug − ΔEinspeisung` (signed), `zev = 0`, Upsert `quelle='MQTT'` (org_id explizit); Rohdaten `verarbeitet=TRUE`. Leeres Intervall → kein Eintrag. |
| [x] | 9. Solarverteilung FR-9 | `MesswerteService` (enthält die Verteil-Logik): nach `setZevCalculated` zusätzlich `zev = zev_calculated`, sofern `zev == 0`. Verteilalgorithmus unverändert. |
| [x] | 10. Metriken (FR-8) | **Eigene `MqttMetrics`-Komponente** (`@Profile("mqtt")`, direkt via `MeterRegistry`) statt `MetricsService` — Letzterer nutzt den request-scoped Org-Kontext (im Ingest/Job nicht verfügbar). Counter/Gauges wie geplant. |
| [x] | 11. Health-Indicator (FR-8) | `MqttHealthIndicator` (`@Profile("mqtt")`, Boot-4-API `org.springframework.boot.health.contributor`): `/actuator/health/mqtt`, UP solange Inbound-Adapter läuft. |
| [x] | 12. CSV-Quelle & Doku | `Messwerte.quelle` Default `CSV` (CSV-Upload bleibt automatisch `CSV`). `.env.example` um MQTT-Broker-Vars ergänzt. |

> **Reihenfolge-Hinweis:** Phasen 2–5 (DB/Entity) sind Voraussetzung für 7/8. Phase 6 (Connection) und 7 (Handler) bilden den Empfang; 8 die Verarbeitung; 9 die Verrechnung. 10/11 sind Monitoring, unabhängig testbar. FR-9 (Phase 9) ist die einzige profil-unabhängige Änderung.

## Validierungen

### Ingest (MqttIngestService, FR-4)
- **Topic:** genau 4 Segmente `zev/{orgId}/{messpunkt}/messwert`; `orgId` numerisch parsebar — sonst WARN + verwerfen.
- **Payload:** `timestamp` (ISO 8601, lokale Zeit mit UTC-Offset) vorhanden/parsebar, verbatim als lokale Wanduhrzeit übernommen; `zaehlerstandBezug` und `zaehlerstandEinspeisung` vorhanden, numerisch, **≥ 0** — sonst WARN + verwerfen.
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
