# MQTT-Anbindung

> **Verhältnis zu bestehenden Dokumenten:** Diese Spec ist eigenständig und
> beschreibt die konkrete MQTT-Anbindung gemäss der in
> `docs/Netzwerk-Topologie-Hene.md` dokumentierten Architektur (Raspberry Pi als
> Gateway, Mosquitto-Broker auf dem NAS, ZEV-Backend als Subscriber). Die ältere,
> konzeptionelle Spec `Specs/MQTT-SmartMeter.md` bleibt als Referenz bestehen;
> bei Widersprüchen gilt dieses Dokument.

## 1. Ziel & Kontext - Warum wird das Feature benötigt?

* **Was soll erreicht werden:** Das ZEV-Backend abonniert einen MQTT-Broker und
  übernimmt automatisch die von einem Raspberry-Pi-Gateway gelieferten
  Zähler-Messwerte (Verbrauch/Einspeisung pro Einheit) in die Datenbank. Damit
  entfällt der manuelle CSV-Upload für die angebundenen Zähler.
* **Warum machen wir das:**
  - Automatisierte, zeitnahe Datenerfassung statt manueller CSV-Uploads
  - Weniger Fehlerquellen (kein manuelles Hochladen/Zuordnen)
  - Grundlage für spätere Live-Auswertungen
* **Aktueller Stand:**
  - Messwerte werden manuell per CSV-Upload erfasst (`MesswerteUploadComponent` /
    `MesswerteController`), inkl. KI-gestütztem Einheiten-Matching.
  - Physisch werden die Zähler bereits über einen Raspberry Pi ausgelesen
    (3× Wago via Modbus TCP, 1× BKW via gPlug) und die Daten heute per SFTP
    bereitgestellt (siehe `docs/Netzwerk-Topologie-Hene.md`).
  - Der Pi soll künftig als MQTT-**Publisher** die berechneten Intervall-Deltas
    publizieren; der Broker (Mosquitto) läuft auf dem NAS (Variante B der
    Topologie-Doku), das ZEV-Backend ist **Subscriber**.

## 2. Funktionale Anforderungen (FR) - Was soll das System tun?

### FR-1: MQTT-Broker-Anbindung (Subscriber)

1. Das ZEV-Backend verbindet sich beim Start automatisch zum konfigurierten
   MQTT-Broker (Mosquitto auf dem NAS).
2. Verbindungsparameter (URL, Username, Passwort, Client-ID, QoS) werden über
   `application.yml` / Environment-Variablen konfiguriert – **keine Secrets im
   Code**.
3. Das Backend subscribed mit Wildcard auf alle Messwert-Topics (s. FR-2).
4. Bei Verbindungsabbruch erfolgt automatischer Reconnect mit Exponential
   Backoff.

### FR-2: Topic-Struktur

```
zev/{orgId}/{messpunkt}/messwert
```

| Segment       | Beschreibung                                   | Beispiel              |
|---------------|------------------------------------------------|-----------------------|
| `zev`         | Root-Namespace                                 | `zev`                 |
| `{orgId}`     | Mandanten-ID (org_id) der Einheit              | `42`                  |
| `{messpunkt}` | Messpunkt-Bezeichnung der Einheit (`Einheit.messpunkt`) | `ID742-Wohnung-1` |
| `messwert`    | Message-Typ                                    | `messwert`            |

* Das Backend abonniert `zev/+/+/messwert`.
* Der Pi-Gateway adressiert die Einheit über den **Messpunkt** (physisch
  bekannt), nicht über die interne DB-`einheitId`. Das Backend löst Messpunkt +
  org_id zur Einheit auf.

### FR-3: Payload-Format (JSON, Deltas)

Der Pi publiziert pro Messung bereits berechnete **Deltas** (Verbrauch/
Einspeisung seit der letzten Messung):

```json
{
  "timestamp": "2026-06-19T14:30:00Z",
  "verbrauch": 1.25,
  "einspeisung": 0.0
}
```

| Feld          | Typ                | Pflicht | Beschreibung                          |
|---------------|--------------------|---------|---------------------------------------|
| `timestamp`   | ISO 8601 DateTime  | Ja      | Zeitpunkt der Messung (UTC)           |
| `verbrauch`   | Decimal (kWh ≥ 0)  | Ja      | Verbrauch seit letzter Messung        |
| `einspeisung` | Decimal (kWh ≥ 0)  | Ja      | Einspeisung seit letzter Messung      |

### FR-4: Backend MQTT-Subscriber-Verarbeitung

Bei eingehender Nachricht:
1. Topic parsen → `orgId` und `messpunkt` extrahieren.
2. JSON-Payload validieren (Pflichtfelder, Typen, keine negativen Werte).
3. Einheit über `messpunkt` **und** `orgId` auflösen (Multi-Tenancy-Prüfung).
4. Bei Erfolg: Rohdatensatz in `zaehler_rohdaten` speichern (s. FR-5).
5. Bei Validierungs-/Zuordnungsfehlern: Warnung loggen, Nachricht verwerfen
   (keine Exception nach aussen).
6. Verarbeitung asynchron / non-blocking.

### FR-5: Rohdaten-Persistierung

MQTT-Nachrichten werden zunächst in einer separaten Rohdaten-Tabelle abgelegt
(neue Flyway-Migration `V[n]__create_zaehler_rohdaten.sql`):

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
    ON zev.zaehler_rohdaten(verarbeitet, zeit)
    WHERE verarbeitet = FALSE;
```

* `org_id` als `BIGINT` analog zur bestehenden `messwerte`-Tabelle (Entity
  `Messwerte.orgId` ist `Long`); Hibernate-`@Filter` `orgFilter` wie bei allen
  Entities.
* Duplikat (gleiche `einheit_id` + `zeit`): bestehenden Eintrag aktualisieren
  (Upsert).

### FR-6: Scheduled Aggregation (15-Minuten-Job)

Analog `MQTT-SmartMeter.md` FR-6: Ein Scheduled Job (`0 0,15,30,45 * * * *`)
überträgt unverarbeitete Rohdaten in die `messwerte`-Tabelle.

1. Abgeschlossenes 15-Minuten-Intervall ermitteln.
2. Pro Einheit mit unverarbeiteten Rohdaten im Intervall aggregieren
   (Summe `verbrauch`, Summe `einspeisung`).
3. Mapping auf `messwerte` (Felder `total`, `zev`; `zev_calculated` wird **nicht**
   aus MQTT befüllt, sondern später vom Verteilungsalgorithmus berechnet –
   siehe offene Fragen).
4. Speichern mit `quelle = 'MQTT'` (Spaltenerweiterung, s. FR-7).
5. Verarbeitete Rohdaten markieren: `verarbeitet = TRUE`, `verarbeitet_am = NOW()`.

### FR-7: Erweiterung der messwerte-Tabelle (Quelle)

```sql
ALTER TABLE zev.messwerte ADD COLUMN IF NOT EXISTS quelle VARCHAR(20) DEFAULT 'CSV';
-- Mögliche Werte: 'CSV', 'MQTT', 'API'
```

* Bestehende Zeilen erhalten per Default `'CSV'` (rückwärtskompatibel).

### FR-8: Monitoring & Status

1. Prometheus-Metriken (vorhandene Metriken-Infrastruktur nutzen, siehe
   `Specs/Metriken.md`):
   - `zev_mqtt_messages_received_total` (Counter)
   - `zev_mqtt_messages_processed_total` (Counter)
   - `zev_mqtt_messages_failed_total` (Counter)
   - `zev_mqtt_last_message_timestamp` (Gauge)
   - `zev_aggregation_runs_total` (Counter)
   - `zev_aggregation_last_run_timestamp` (Gauge)
2. Health-Indicator `/actuator/health/mqtt` zeigt den Broker-Verbindungsstatus.

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)

**MQTT-Empfang:**
* [ ] Backend verbindet sich beim Start automatisch zum Broker.
* [ ] Backend abonniert `zev/+/+/messwert`.
* [ ] Gültige Nachricht wird als Zeile in `zaehler_rohdaten` gespeichert.
* [ ] Ungültiges JSON wird geloggt (WARN) und nicht gespeichert.
* [ ] Nachricht mit unbekanntem Messpunkt wird abgelehnt (WARN, kein Insert).
* [ ] Stimmt `orgId` im Topic nicht zur Einheit, wird die Nachricht verworfen
      (Security-Log) – Mandanten-Isolation gewährleistet.
* [ ] Negative `verbrauch`/`einspeisung`-Werte werden verworfen.
* [ ] Duplikat (gleiche Einheit + `zeit`) aktualisiert den bestehenden Eintrag.
* [ ] Nach Broker-Abbruch verbindet sich das Backend automatisch neu.

**15-Minuten-Aggregation:**
* [ ] Job läuft um :00, :15, :30, :45.
* [ ] Rohdaten werden korrekt zu 15-Minuten-Werten summiert.
* [ ] Aggregierte Werte erscheinen in `messwerte` mit `quelle = 'MQTT'`.
* [ ] Verarbeitete Rohdaten sind als `verarbeitet = TRUE` markiert.
* [ ] Bereits verarbeitete Rohdaten werden nicht erneut aggregiert.
* [ ] Leeres Intervall erzeugt keinen `messwerte`-Eintrag (kein Nullwert).

**Monitoring:**
* [ ] MQTT-Metriken sind unter `/actuator/prometheus` verfügbar.
* [ ] `/actuator/health/mqtt` zeigt den Verbindungsstatus.

## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Performance
* Verarbeitung einer einzelnen Nachricht < 100 ms.
* Nachrichten werden asynchron / non-blocking verarbeitet.
* Der Aggregations-Job blockiert die MQTT-Verarbeitung nicht.

### NFR-2: Sicherheit
* **Kein Endbenutzer-UI** in dieser Spec – die Anbindung ist ein
  Backend-Hintergrunddienst (keine `zev`/`zev_admin`-Route). Ein späteres
  Status-/Konfig-UI wäre `zev_admin` (Out of Scope, s. Abschnitt 7).
* MQTT-Broker erfordert Authentifizierung (Username/Passwort); Zugangsdaten nur
  via Environment-Variablen, nie im Code/Repository.
* Broker-ACLs beschränken Topics pro Mandant (Pi darf nur in seine `org_id`
  publizieren).
* TLS (Port 8883) für den Broker empfohlen (siehe `docs/Netzwerk-Topologie-Hene.md`),
  zusätzlich zum VPN-Tunnel (Defense-in-Depth).
* Mandanten-Isolation: `org_id` aus dem Topic wird gegen die Einheit geprüft;
  Rohdaten/Messwerte tragen `org_id` und unterliegen dem Hibernate-`orgFilter`.

### NFR-3: Zuverlässigkeit
* QoS Level 1 (At least once); Duplikate werden über den Unique-Constraint
  (`einheit_id`, `zeit`) idempotent behandelt.
* Persistente Session für Reconnect ohne Nachrichtenverlust.
* Bei DB-Fehler: Nachricht nicht als verarbeitet markieren, erneuter Versuch.

### NFR-4: Kompatibilität
* Bestehender CSV-Upload bleibt unverändert funktionsfähig.
* Messwerte aus CSV und MQTT werden fachlich gleich behandelt
  (gleiche `messwerte`-Tabelle), unterscheidbar nur über `quelle`.
* `quelle`-Spalte mit Default `'CSV'` ist rückwärtskompatibel; keine Breaking
  Changes an bestehenden Spalten.

## 5. Edge Cases & Fehlerbehandlung

| Szenario                              | Verhalten                                                 |
|---------------------------------------|-----------------------------------------------------------|
| Ungültiges/leeres JSON                | WARN loggen, Nachricht verwerfen                          |
| Fehlende Pflichtfelder                | WARN loggen, Nachricht verwerfen                          |
| Unbekannter Messpunkt                 | WARN loggen, Nachricht verwerfen                          |
| `orgId` passt nicht zur Einheit       | Security-WARN loggen, Nachricht verwerfen                 |
| Negative Werte                        | WARN loggen, Nachricht verwerfen                          |
| Timestamp in der Zukunft              | WARN loggen, Nachricht trotzdem speichern                 |
| Duplikat (Einheit + zeit)             | Upsert (bestehenden Rohdatensatz aktualisieren)           |
| Broker nicht erreichbar               | Reconnect mit Exponential Backoff                         |
| DB nicht erreichbar                   | Nachricht nicht bestätigen/markieren, Retry              |
| Keine Rohdaten im Intervall           | Kein `messwerte`-Eintrag                                  |
| Nur eine Rohdaten-Zeile im Intervall  | Wert 1:1 übernehmen                                       |
| Job-Ausfall (Neustart)                | Nächster Lauf verarbeitet alle unverarbeiteten Rohdaten   |
| Bereits aggregierter messwerte-Eintrag| Upsert statt doppeltem Insert                             |

## 6. Abhängigkeiten & betroffene Funktionalität

* **Voraussetzungen:**
  - Einheiten (`einheit`) inkl. `messpunkt` müssen existieren (keine
    automatische Geräteregistrierung).
  - Mosquitto-Broker auf dem NAS betriebsbereit (siehe
    `docs/Netzwerk-Topologie-Hene.md`, Abschnitt „MQTT-Broker auf dem NAS betreiben").
  - VPN-Verbindung zwischen Pi und NAS.
* **Betroffener Code:**
  - Neu: MQTT-Subscriber-Service, Topic-/Payload-Parser, Rohdaten-Entity +
    Repository, Aggregations-Service (Scheduled), Health-Indicator.
  - Erweiterung: `Messwerte`-Entity / Tabelle um `quelle`.
  - Build: neue Maven-Dependencies (`spring-integration-mqtt`, Eclipse Paho).
* **Datenmigration:**
  - Flyway: Tabelle `zaehler_rohdaten` anlegen, `messwerte.quelle` ergänzen.
  - i18n: nur falls UI hinzukommt (in dieser Spec keine UI-Texte).
* **Abhängige Dokumente:** `docs/Netzwerk-Topologie-Hene.md` (Topologie, Broker-Setup,
  Varianten A/B/C), `Specs/MQTT-SmartMeter.md` (konzeptionelle Referenz),
  `Specs/Metriken.md` (Prometheus-Infrastruktur).

## 7. Abgrenzung / Out of Scope

* **Pi-Gateway-Software** (Auslesen von Wago/Modbus TCP und BKW/gPlug,
  Delta-Berechnung, MQTT-Publish) – wird nicht in diesem Backend-Feature
  umgesetzt, sondern auf dem Pi separat implementiert/konfiguriert.
* **Frontend-Anzeige** von Live-Daten / WebSocket-Bridge.
* **Admin-UI** zur MQTT-Konfiguration oder Broker-/ACL-Verwaltung.
* **Automatische Einheiten-/Geräteregistrierung** über MQTT.
* **Bidirektionale Kommunikation** (Befehle an Zähler).
* **TLS-Zertifikatsverwaltung** (manuell, siehe Topologie-Doku).
* **Aufbewahrungs-/Cleanup-Strategie** für Rohdaten (eigene Spec).
* **Beibehaltung des SFTP-Pfads** als Fallback – nicht Gegenstand dieser Spec.

## 8. Offene Fragen

* [ ] **Mapping Verbrauch/Einspeisung → messwerte:** Die `messwerte`-Tabelle hat
      `total`, `zev`, `zev_calculated`. Wie werden `verbrauch`/`einspeisung` aus
      MQTT konkret auf diese Felder abgebildet (z. B. `total = verbrauch`,
      Producer-Einspeisung → eigenes Feld/Vorzeichen)? `zev_calculated` wird
      vermutlich erst durch den Verteilungsalgorithmus (`SolarDistribution`)
      befüllt – ist das korrekt?
* [ ] **org_id-Typ:** Die bestehende `Messwerte`-Entity nutzt `org_id` als
      `BIGINT`/`Long`, während `generell.md`/ERD eine `UUID` nennen. Welcher Typ
      gilt für `zaehler_rohdaten` und im Topic? (Diese Spec folgt vorerst dem
      bestehenden `Messwerte`-Modell: `BIGINT`.)
* [ ] **Adressierung der Einheit im Topic:** Messpunkt (vorgeschlagen) oder doch
      interne `einheitId`? Ist `messpunkt` pro `org_id` eindeutig?
* [ ] **QoS / Persistenz auf Pi-Seite:** Welche QoS-Stufe und Pufferung liefert
      das Pi-Gateway (Store-and-Forward bei VPN-Ausfall – evtl. Bridge nach
      Variante C der Topologie-Doku)?
* [ ] **Aufbewahrung der Rohdaten:** Brauchen wir einen Cleanup-Job, und ab
      welchem Alter?
* [ ] **Mehrfach-Quelle pro Einheit:** Was passiert, wenn für dieselbe Einheit
      und denselben Zeitpunkt sowohl CSV- als auch MQTT-Daten vorliegen?
