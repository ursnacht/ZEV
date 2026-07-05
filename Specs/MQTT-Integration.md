# MQTT-Integration

## 1. Ziel & Kontext - Warum wird das Feature benötigt?

* **Was soll erreicht werden:** Das ZEV-Backend abonniert einen MQTT-Broker und übernimmt automatisch die von einem Raspberry-Pi-Gateway gelieferten Zähler-Messwerte in die Datenbank — als Alternative/Ergänzung zum manuellen CSV-Upload für die angebundenen Zähler.
* **Warum machen wir das:**
  - Automatisierte, zeitnahe Datenerfassung statt manueller CSV-Uploads.
  - Weniger Fehlerquellen (kein manuelles Hochladen/Zuordnen).
  - Grundlage für spätere Live-Auswertungen.
* **Aktueller Stand:**
  - Messwerte kommen heute per **CSV-Upload** (`MesswerteService.processCsvUpload`, Spalten `zeit,total,zev` je Einheit, inkl. KI-gestütztem Einheiten-Matching). `zev_calculated` wird separat durch die **Solarverteilung** (`SolarDistribution`) berechnet.
  - Die Zähler werden physisch bereits über einen **Raspberry Pi** ausgelesen (3× Wago via Modbus TCP, 1× BKW via gPlug) und heute per **SFTP** bereitgestellt (siehe `docs/Netzwerk-Topologie-Hene.md`).
  - Ziel-Topologie: Der Pi ist MQTT-**Publisher**, der **Broker (Mosquitto) läuft auf dem NAS** (Variante B der Topologie-Doku), das ZEV-Backend ist **Subscriber**.
  - Es gibt **noch keinen** MQTT-Code / keine MQTT-Dependency im Backend.

> **Rollentrennung (siehe Topologie-Doku):** Ein MQTT-Broker verteilt nur Nachrichten und liest selbst keine Zähler. Das Auslesen (Modbus/gPlug) + Delta-Berechnung + Publish macht ein **separater Reader-/Publisher-Prozess auf dem Pi** — nicht Teil dieses Backend-Features (Out of Scope).

## 2. Funktionale Anforderungen (FR)

### FR-1: MQTT-Broker-Anbindung (Subscriber)
1. Das ZEV-Backend verbindet sich beim Start automatisch zum konfigurierten Broker (Mosquitto auf dem NAS).
2. Verbindungsparameter (URL, Username, Passwort, Client-ID, QoS) über `application.yml` / Environment — **keine Secrets im Code**.
3. Subscribe mit Wildcard auf alle Messwert-Topics (FR-2), QoS 1.
4. Bei Verbindungsabbruch automatischer Reconnect mit Exponential Backoff; persistente Session.

### FR-2: Topic-Struktur
```
zev/{orgId}/{messpunkt}/messwert
```
| Segment | Beschreibung | Beispiel |
|---------|--------------|----------|
| `zev` | Root-Namespace | `zev` |
| `{orgId}` | Mandanten-ID (`org_id`) der Einheit | `42` |
| `{messpunkt}` | Messpunkt-Bezeichnung der Einheit (`einheit.messpunkt`) | `ID742-Wohnung-1` |
| `messwert` | Message-Typ | `messwert` |

* Backend abonniert `zev/+/+/messwert`.
* Der Pi adressiert die Einheit über den **Messpunkt** (physisch bekannt), nicht über die interne `einheit.id`. Das Backend löst die Einheit über **(`org_id`, `messpunkt`)** auf.
* `{orgId}` folgt vorerst dem bestehenden `Messwerte`-Modell (internes `org_id` als `BIGINT`); Alternative (Keycloak-Alias/UUID) siehe Offene Fragen.

### FR-3: Payload-Format (JSON, Deltas)
Der Pi publiziert pro Messung bereits berechnete **Deltas** seit der letzten Messung:
```json
{
  "timestamp": "2026-06-19T14:30:00Z",
  "verbrauch": 1.25,
  "einspeisung": 0.0
}
```
| Feld | Typ | Pflicht | Beschreibung |
|------|-----|---------|--------------|
| `timestamp` | ISO 8601 (UTC) | Ja | Zeitpunkt der Messung |
| `verbrauch` | Decimal (kWh ≥ 0) | Ja | Verbrauch seit letzter Messung |
| `einspeisung` | Decimal (kWh ≥ 0) | Ja | Einspeisung seit letzter Messung |

> Die Payload trägt **physische Zählerwerte**. Die Abbildung auf die `messwerte`-Felder (`total`/`zev`) erfolgt bei der Aggregation (FR-6); `zev_calculated` bleibt Ergebnis der Solarverteilung. Die genaue Zuordnung ist eine **Offene Frage** (Abschnitt 8).

### FR-4: Backend MQTT-Subscriber-Verarbeitung
Pro eingehender Nachricht:
1. Topic parsen → `orgId` + `messpunkt`.
2. JSON validieren (Pflichtfelder, Typen, keine negativen Werte).
3. Einheit über **`messpunkt` + `orgId`** auflösen (Multi-Tenancy-Prüfung).
4. Bei Erfolg: Rohdatensatz in `zaehler_rohdaten` (FR-5).
5. Bei Validierungs-/Zuordnungsfehlern: Warnung loggen, Nachricht verwerfen (keine Exception nach aussen).
6. Verarbeitung asynchron / non-blocking.

> **Kein JWT im Ingest-Pfad:** MQTT-Nachrichten laufen ohne User-Request/JWT. Der Mandantenkontext (`org_id`) darf **nicht** aus `OrganizationContextService` (JWT) stammen, sondern wird aus dem Topic abgeleitet, gegen die Einheit geprüft und beim Persistieren explizit gesetzt (Hibernate-`orgFilter` entsprechend versorgen).

### FR-5: Rohdaten-Persistierung
Neue Flyway-Migration (`V[n]__create_zaehler_rohdaten.sql`):
```sql
CREATE TABLE zev.zaehler_rohdaten (
    id              BIGSERIAL PRIMARY KEY,
    org_id          BIGINT NOT NULL,
    einheit_id      BIGINT NOT NULL REFERENCES zev.einheit(id),
    zeit            TIMESTAMP NOT NULL,
    verbrauch       DECIMAL(12,4) NOT NULL,
    einspeisung     DECIMAL(12,4) NOT NULL,
    empfangen_am    TIMESTAMP DEFAULT NOW(),
    verarbeitet     BOOLEAN DEFAULT FALSE,
    verarbeitet_am  TIMESTAMP,
    CONSTRAINT uk_zaehler_rohdaten UNIQUE (einheit_id, zeit)
);
CREATE INDEX idx_zaehler_rohdaten_unverarbeitet
    ON zev.zaehler_rohdaten(verarbeitet, zeit) WHERE verarbeitet = FALSE;
```
* `org_id` als `BIGINT` analog zur `messwerte`-Tabelle (`Messwerte.orgId` = `Long`); Hibernate-`@Filter` `orgFilter` wie bei allen Entities.
* Entkoppelt Empfang von Verarbeitung und dient als Puffer.
* Duplikat (gleiche `einheit_id` + `zeit`): Upsert.

### FR-6: Scheduled Aggregation (15-Minuten-Job)
1. Scheduled Job (`0 0,15,30,45 * * * *`) ermittelt das abgeschlossene 15-Minuten-Intervall.
2. Pro Einheit mit unverarbeiteten Rohdaten im Intervall aggregieren (Summe `verbrauch`, Summe `einspeisung`; bei einer einzelnen Zeile 1:1).
3. Mapping auf `messwerte` (`zeit`, `total`, `zev`, `einheit`, `org_id`); `zev_calculated` wird **nicht** aus MQTT befüllt (Solarverteilung, unverändert). Genaues Feld-Mapping siehe Offene Fragen.
4. Speichern mit `quelle = 'MQTT'` (FR-7); Insert/Upsert.
5. Verarbeitete Rohdaten markieren: `verarbeitet = TRUE`, `verarbeitet_am = NOW()`.

### FR-7: Erweiterung der `messwerte`-Tabelle (Quelle)
```sql
ALTER TABLE zev.messwerte ADD COLUMN IF NOT EXISTS quelle VARCHAR(20) DEFAULT 'CSV';
-- Mögliche Werte: 'CSV', 'MQTT', 'API'
```
Bestehende Zeilen erhalten per Default `'CSV'` (rückwärtskompatibel).

### FR-8: Monitoring & Status
1. Prometheus-Metriken (bestehende Infrastruktur, siehe `Specs/Metriken.md` / `MetricsService`):
   - `zev_mqtt_messages_received_total`, `..._processed_total`, `..._failed_total` (Counter)
   - `zev_mqtt_last_message_timestamp` (Gauge)
   - `zev_aggregation_runs_total` (Counter), `zev_aggregation_last_run_timestamp` (Gauge)
2. Health-Indicator `/actuator/health/mqtt` zeigt den Broker-Verbindungsstatus.

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)

**MQTT-Empfang:**
* [ ] Backend verbindet sich beim Start automatisch zum Broker und abonniert `zev/+/+/messwert`.
* [ ] Gültige Nachricht → Zeile in `zaehler_rohdaten` mit korrektem `org_id` + `einheit_id` (aufgelöst über `messpunkt`).
* [ ] Ungültiges JSON / fehlende Pflichtfelder → WARN, kein Insert.
* [ ] Unbekannter Messpunkt → abgelehnt (WARN, kein Insert).
* [ ] `orgId` passt nicht zur Einheit → verworfen (Security-Log) — Mandanten-Isolation gewährleistet.
* [ ] Negative `verbrauch`/`einspeisung` → verworfen.
* [ ] Duplikat (Einheit + `zeit`) → Upsert.
* [ ] Nach Broker-Abbruch automatischer Reconnect.

**15-Minuten-Aggregation:**
* [ ] Job läuft um :00, :15, :30, :45; Rohdaten korrekt summiert.
* [ ] Aggregierte Werte in `messwerte` mit `quelle = 'MQTT'`.
* [ ] Verarbeitete Rohdaten `verarbeitet = TRUE`; bereits verarbeitete werden nicht erneut aggregiert.
* [ ] Leeres Intervall → kein `messwerte`-Eintrag (kein Nullwert).
* [ ] `zev_calculated` wird durch den Ingest nicht verändert; Solarverteilung funktioniert unverändert.

**Monitoring & Kompatibilität:**
* [ ] MQTT-Metriken unter `/actuator/prometheus`; `/actuator/health/mqtt` zeigt Status.
* [ ] Bestehender CSV-Upload unverändert nutzbar; Werte beider Quellen gleichwertig.

## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Performance
* Verarbeitung einer Nachricht < 100 ms; asynchron/non-blocking; der Aggregations-Job blockiert die MQTT-Verarbeitung nicht.

### NFR-2: Sicherheit & Mandantenfähigkeit
* **Kein Endbenutzer-UI / nicht rollenbasiert:** Der Ingest ist ein Backend-Hintergrunddienst (keine `zev_user`/`zev_admin`-Route, kein `@PreAuthorize`). Ein späteres Status-/Konfig-UI wäre über das Permission-Modell (`Specs/Composite-Roles.md`) zu schützen (Out of Scope).
* **Broker-Auth:** Username/Passwort (nur via Env), **ACLs pro Mandant** (Pi darf nur in seine `org_id` publizieren); getrennte Broker-User für Publisher (Pi) und Backend-Subscriber.
* **TLS (Port 8883)** empfohlen, zusätzlich zum VPN-Tunnel (Defense-in-Depth).
* **Mandanten-Isolation:** `org_id` aus dem Topic wird gegen die Einheit geprüft; Rohdaten/Messwerte tragen `org_id` und unterliegen dem Hibernate-`orgFilter`. Da kein JWT vorliegt, wird der Filter explizit mit dem topic-abgeleiteten `org_id` versorgt.

### NFR-3: Zuverlässigkeit
* QoS 1 (at least once); Duplikate idempotent über Unique-Constraint (`einheit_id`, `zeit`).
* Persistente Session für Reconnect ohne Nachrichtenverlust.
* Bei DB-Fehler: Rohdaten nicht als verarbeitet markieren, erneuter Versuch.
* Puffer-Grenzen bewusst wählen (`persistence`, `max_queued_messages`) — MQTT puffert nur begrenzt, anders als das File/SFTP-Modell (siehe Topologie-Doku).

### NFR-4: Kompatibilität
* Bestehender CSV-Upload bleibt funktionsfähig; CSV- und MQTT-Werte werden fachlich gleich behandelt (gleiche `messwerte`-Tabelle), unterscheidbar über `quelle`.
* `quelle`-Default `'CSV'` → rückwärtskompatibel, keine Breaking Changes.
* Neue Tabelle/Spalte via **Flyway-Migration** (Namenskonvention `V[XX]__…`).

## 5. Edge Cases & Fehlerbehandlung
| Szenario | Verhalten |
|----------|-----------|
| Ungültiges/leeres JSON, fehlende Pflichtfelder | WARN, verwerfen |
| Unbekannter Messpunkt | WARN, verwerfen |
| `orgId` passt nicht zur Einheit | Security-WARN, verwerfen (kein Cross-Tenant-Write) |
| Negative Werte | WARN, verwerfen |
| Timestamp in der Zukunft | WARN, trotzdem speichern |
| Duplikat (Einheit + `zeit`) | Upsert |
| Broker nicht erreichbar | Reconnect mit Exponential Backoff |
| DB nicht erreichbar | Nachricht nicht markieren, Retry |
| Keine Rohdaten im Intervall | kein `messwerte`-Eintrag |
| Nur eine Rohdaten-Zeile im Intervall | Wert 1:1 übernehmen |
| Job-Ausfall/Neustart | nächster Lauf verarbeitet alle unverarbeiteten Rohdaten |
| Bereits aggregierter `messwerte`-Eintrag | Upsert statt Doppel-Insert |
| Leere Zähler-/Einheitenliste | kein Fehler; nichts zu verarbeiten |

## 6. Abhängigkeiten & betroffene Funktionalität
* **Voraussetzungen:**
  - Einheiten (`einheit`) inkl. `messpunkt` müssen existieren (keine automatische Geräteregistrierung).
  - Mosquitto-Broker auf dem NAS betriebsbereit (siehe `docs/Netzwerk-Topologie-Hene.md`, Abschnitt „MQTT-Broker auf dem NAS betreiben").
  - VPN-Verbindung zwischen Pi und NAS; **separater Reader-/Publisher-Prozess** auf dem Pi (Out of Scope).
* **Neue Dependencies (Backend):** `spring-integration-mqtt` + Eclipse Paho (`org.eclipse.paho.client.mqttv3`).
* **Backend-Konfiguration (`application.yml`):** `mqtt.broker.url/username/password`, `mqtt.topics.messwerte=zev/+/+/messwert`, `mqtt.qos=1` (Secrets via Env).
* **Neuer Code:** MQTT-Subscriber/Handler, Topic-/Payload-Parser, `ZaehlerRohdaten`-Entity + Repository, Aggregations-Service (Scheduled), Health-Indicator, Prometheus-Metriken.
* **Erweiterung:** `messwerte` um `quelle`.
* **Wiederverwendet:** `Einheit`/`Messwerte`, `SolarDistribution` (unverändert), `MetricsService`, Grafana/Prometheus-Stack.
* **Datenmigration:** Keine Bestandsmigration; nur neue Tabelle + `quelle`-Spalte.
* **i18n:** Kein UI-Text in diesem Feature (reiner Backend-Ingest). Falls später Status-UI: Texte via `TranslationService`.
* **Abhängige Dokumente:** `docs/Netzwerk-Topologie-Hene.md` (Topologie/Broker-Setup/Varianten A/B/C), `Specs/Metriken.md` (Prometheus-Infrastruktur).

## 7. Abgrenzung / Out of Scope
* **Pi-Gateway-Software** (Auslesen Wago/Modbus TCP + BKW/gPlug, Delta-Berechnung, MQTT-Publish) — separat auf dem Pi implementiert/konfiguriert.
* Frontend-Live-Anzeige / WebSocket-Bridge.
* Admin-UI für MQTT-Konfiguration / Broker-/ACL-Verwaltung.
* Automatische Einheiten-/Geräteregistrierung; bidirektionale Befehle an Zähler.
* TLS-Zertifikatsverwaltung (manuell, siehe Topologie-Doku).
* Aufbewahrungs-/Cleanup-Strategie für Rohdaten (eigene Spec).
* Beibehaltung des SFTP-Pfads als Fallback — nicht Gegenstand dieser Spec.
* Änderung der Solarverteilungs-Logik (`zev_calculated`) — bleibt wie bisher.

## 8. Offene Fragen
* [ ] **Mapping `verbrauch`/`einspeisung` → `messwerte`:** Wie werden die physischen Deltas auf `total`/`zev` abgebildet (z.B. `total = verbrauch`, Producer-Einspeisung → eigenes Feld/Vorzeichen)? Liefert der Zähler `zev` überhaupt, oder wird es abgeleitet? `zev_calculated` bleibt Ergebnis der Solarverteilung — korrekt?
* [ ] **`org_id`-Typ & Topic-Kennung:** `Messwerte.orgId` ist `BIGINT`/`Long`, ERD/`generell.md` nennen `UUID`. Welcher Typ gilt für `zaehler_rohdaten`/Topic — internes `org_id` (BIGINT, aktuell angenommen) oder Keycloak-Alias/UUID (mapping via `OrganisationService`)?
* [ ] **Messpunkt-Eindeutigkeit:** Ist `messpunkt` pro `org_id` eindeutig (nötig für die Auflösung)?
* [ ] **QoS/Persistenz Pi-Seite:** Welche QoS-Stufe und Pufferung liefert das Pi-Gateway (Store-and-Forward bei VPN-Ausfall — evtl. Bridge nach Variante C der Topologie-Doku)?
* [ ] **Rohdaten-Retention:** Cleanup-Job nötig, ab welchem Alter?
* [ ] **Mehrfach-Quelle pro Einheit:** Verhalten, wenn für dieselbe Einheit + Zeitpunkt sowohl CSV- als auch MQTT-Daten vorliegen?
* [ ] **Producer vs. Consumer** (`einheit.typ`): getrennte Payload-Felder/Topics für Einspeisung nötig?
