# Swisseldex Datahub Anbindung (SDAT-CH / ebIX)

## 1. Ziel & Kontext - Warum wird das Feature benötigt?

* **Was soll erreicht werden:** Die ZEV-Anwendung soll an den Datahub swisseldex angeschlossen werden, um Messdaten (Lastgänge, 15-Minuten-Werte) automatisiert per Branchenstandard SDAT-CH im ebIX-Format zu empfangen und zu senden.
* **Warum machen wir das:**
  - Automatisierter Bezug von validierten Messdaten der Netzbetreiber (VNB) statt manueller CSV-Uploads
  - Branchenkonformer Datenaustausch gemäss VSE-Empfehlung (Verband Schweizerischer Elektrizitätsunternehmen)
  - Voraussetzung für die Abwicklung von virtuellen ZEV (vZEV), bei denen Messdaten vom Netzbetreiber via Datahub bezogen werden
  - Zukunftssicherheit: Swisseldex wird zur nationalen Datenplattform (nDP) gemäss StromVG ausgebaut
  - Über 99.5% aller Schweizer Messpunkte sind bereits via Swisseldex erreichbar
* **Aktueller Stand:** Messwerte werden manuell per CSV-Upload erfasst (`MesswerteUploadComponent`) oder via MQTT von Smart Metern empfangen (geplant). Es existiert keine standardisierte Schnittstelle zum Datahub der Elektrizitätsbranche. Die Einheit-Entity verfügt bereits über ein `messpunkt`-Feld (max. 50 Zeichen), das für die 33-stellige Messpunktbezeichnung gemäss MC-CH genutzt werden kann.

## 2. Funktionale Anforderungen (FR)

### FR-1: Swisseldex-Registrierung und Konfiguration

1. Der ZEV-Betreiber registriert sich als Marktpartner bei Swisseldex (externer Prozess)
2. Nach Registrierung erhält der Betreiber:
   - SFTP-Zugangsdaten für den Datahub
   - EIC-Code (Energy Identification Code) als eindeutige Marktpartner-Identifikation
3. Im Backend werden die Zugangsdaten konfiguriert:
   - SFTP-Host, Port, Username, Passwort/Key
   - Eigener EIC-Code (Sender/Empfänger-Identifikation)
   - Polling-Intervall für den Abruf neuer Nachrichten

**Konfiguration (application.yml):**
```yaml
swisseldex:
  enabled: ${SWISSELDEX_ENABLED:false}
  sftp:
    host: ${SWISSELDEX_SFTP_HOST:sftp.swisseldex.ch}
    port: ${SWISSELDEX_SFTP_PORT:22}
    username: ${SWISSELDEX_SFTP_USERNAME:}
    password: ${SWISSELDEX_SFTP_PASSWORD:}
    private-key-path: ${SWISSELDEX_SFTP_KEY_PATH:}
    inbox-directory: /inbox
    outbox-directory: /outbox
    archive-directory: /archive
  eic-code: ${SWISSELDEX_EIC_CODE:}
  polling:
    interval-minutes: ${SWISSELDEX_POLLING_INTERVAL:15}
    enabled: ${SWISSELDEX_POLLING_ENABLED:true}
```

### FR-2: Einstellungen-UI (Mandantenspezifisch)

1. In den Einstellungen (`/einstellungen`) wird ein neuer Abschnitt "Swisseldex Datahub" hinzugefügt
2. Folgende Felder sind konfigurierbar (gespeichert in `einstellungen.konfiguration` JSONB):
   - **Aktiviert** (Toggle): Swisseldex-Integration ein/aus
   - **EIC-Code** (Text): Eigener EIC-Code des Marktpartners
   - **Automatischer Import** (Toggle): Empfangene Messdaten automatisch importieren
3. Nur für Rolle `zev_admin` sichtbar

### FR-3: Messpunkt-Zuordnung

1. Das bestehende `messpunkt`-Feld der Einheit wird auf 33 Zeichen erweitert (aktuell max. 50, reicht bereits)
2. Die Messpunkt-ID folgt dem Format gemäss MC-CH (Metering Code Schweiz):
   - 33-stellige Messpunktbezeichnung
   - Aufbau: Ländercode (CH) + Netzbetreiber-ID + Messpunktnummer
   - Beispiel: `CH1008853240100000000000000062714`
3. Die Messpunkt-ID wird für das Routing der ebIX-Nachrichten verwendet
4. Validierung: Format-Check bei Eingabe (Regex: `^CH[0-9A-Z]{31}$`)

### FR-4: Empfang von Messdaten (SDAT Import)

**Ablauf:**

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│   Swisseldex    │────>│  SFTP Polling   │────>│  ebIX Parser    │
│   Datahub       │     │  (Scheduled)    │     │  (XML → Entity) │
└─────────────────┘     └────────┬────────┘     └────────┬────────┘
                                 │                       │
                                 ▼                       ▼
                        ┌─────────────────┐     ┌─────────────────┐
                        │  Archivierung   │     │   messwerte     │
                        │  (Rohdaten XML) │     │  (Datenbank)    │
                        └─────────────────┘     └─────────────────┘
```

1. **Scheduled SFTP Polling:**
   - Ein Scheduled Job pollt den SFTP-Inbox-Ordner alle 15 Minuten (konfigurierbar)
   - Neue XML-Dateien werden heruntergeladen
   - Nach erfolgreichem Download wird die Datei im Archiv-Ordner abgelegt

2. **ebIX XML Parsing (Nachrichtentyp E66 - ValidatedMeteredData):**
   - Parse der ebIX-XML-Nachricht gemäss SDAT-CH Schema
   - Extraktion der Header-Informationen:
     - `DocumentType` (E66 = Validated Metered Data)
     - `SenderParty` (EIC-Code des Absenders, z.B. Netzbetreiber)
     - `ReceiverParty` (EIC-Code des Empfängers, z.B. ZEV-Betreiber)
   - Extraktion der Messdaten:
     - `MeteringPointIdentification` (33-stellige Messpunkt-ID)
     - `Period` mit `Resolution` (PT15M = 15-Minuten-Intervall)
     - `Observation`-Elemente mit `Position` und `Quantity` (kWh)

3. **Zuordnung und Persistierung:**
   - Messpunkt-ID aus der ebIX-Nachricht wird mit dem `messpunkt`-Feld der Einheiten abgeglichen
   - Bei Match: Messwerte werden in die `messwerte`-Tabelle geschrieben
   - Bei keinem Match: Nachricht wird geloggt und in Fehler-Queue abgelegt
   - Duplikat-Erkennung: Gleicher Zeitstempel + Einheit = Update statt Insert

**Beispiel ebIX XML (E66 - ValidatedMeteredData):**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<ValidatedMeteredData_MarketDocument
    xmlns="urn:ediel.org:measure:validatedmetereddata:0:1"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <HeaderInformation>
    <HeaderVersion>1</HeaderVersion>
    <InstanceDocument>
      <DocumentID>DOC-2026-001234</DocumentID>
      <DocumentType>E66</DocumentType>
      <CreationDateTime>2026-02-05T08:00:00Z</CreationDateTime>
    </InstanceDocument>
    <SenderParty>
      <PartyID codingScheme="EIC">11XNETZBETREIB-V</PartyID>
      <BusinessRoleCode>DDM</BusinessRoleCode>
    </SenderParty>
    <ReceiverParty>
      <PartyID codingScheme="EIC">11XZEVBETREIB--V</PartyID>
      <BusinessRoleCode>DDQ</BusinessRoleCode>
    </ReceiverParty>
  </HeaderInformation>
  <MeteringData>
    <MeteringPointIdentification codingScheme="Swiss">
      CH1008853240100000000000000062714
    </MeteringPointIdentification>
    <MeteringPointType>E17</MeteringPointType>
    <Period>
      <TimeInterval>
        <Start>2026-02-04T23:00:00Z</Start>
        <End>2026-02-05T23:00:00Z</End>
      </TimeInterval>
      <Resolution>PT15M</Resolution>
      <Observation>
        <Position>1</Position>
        <Quantity unit="KWH">0.325</Quantity>
        <QualityCode>56</QualityCode>
      </Observation>
      <Observation>
        <Position>2</Position>
        <Quantity unit="KWH">0.410</Quantity>
        <QualityCode>56</QualityCode>
      </Observation>
      <!-- ... 96 Observations pro Tag (24h * 4 pro Stunde) -->
    </Period>
  </MeteringData>
</ValidatedMeteredData_MarketDocument>
```

### FR-5: Versand von Messdaten (SDAT Export)

1. Der ZEV-Betreiber kann Messdaten für Producer-Einheiten (Solaranlage) an den Datahub senden
2. Generierung einer ebIX XML-Nachricht (E66) mit:
   - Eigener EIC-Code als Sender
   - Empfänger-EIC-Code (konfigurierbar pro Einheit oder global)
   - Aggregierte 15-Minuten-Werte aus der `messwerte`-Tabelle
3. Upload der XML-Datei in den SFTP-Outbox-Ordner
4. Der Export kann manuell ausgelöst oder automatisch per Schedule versendet werden

### FR-6: Import-Protokoll und Monitoring

1. **Neue Tabelle `sdat_import_log`:**

```sql
CREATE TABLE sdat_import_log (
    id BIGSERIAL PRIMARY KEY,
    org_id UUID NOT NULL,
    dateiname VARCHAR(255) NOT NULL,
    document_id VARCHAR(100),
    document_type VARCHAR(10) NOT NULL,
    sender_eic VARCHAR(50),
    empfangen_am TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    status VARCHAR(20) NOT NULL DEFAULT 'NEU',
    messpunkt_id VARCHAR(33),
    anzahl_werte INTEGER,
    zeitraum_von TIMESTAMP WITH TIME ZONE,
    zeitraum_bis TIMESTAMP WITH TIME ZONE,
    fehler_meldung TEXT,
    verarbeitet_am TIMESTAMP WITH TIME ZONE,
    roh_xml TEXT
);

CREATE INDEX idx_sdat_import_log_org ON sdat_import_log(org_id);
CREATE INDEX idx_sdat_import_log_status ON sdat_import_log(status);
```

| Status | Beschreibung |
|--------|-------------|
| `NEU` | Datei empfangen, noch nicht verarbeitet |
| `VERARBEITET` | Erfolgreich geparst und Messwerte importiert |
| `FEHLER` | Fehler beim Parsen oder Zuordnen |
| `IGNORIERT` | Nachrichtentyp nicht relevant (z.B. Wechselprozess) |

2. **Prometheus-Metriken:**
   - `zev_sdat_messages_received_total` (Counter, Label: document_type)
   - `zev_sdat_messages_processed_total` (Counter, Label: status)
   - `zev_sdat_import_duration_seconds` (Histogram)
   - `zev_sdat_polling_last_run_timestamp` (Gauge)
   - `zev_sdat_sftp_connection_errors_total` (Counter)

3. **Frontend-Anzeige:**
   - Neue Seite `/sdat-log` (Rolle: `zev_admin`) mit Tabelle der Import-Protokolle
   - Spalten: Datum, Dateiname, Typ, Messpunkt, Status, Anzahl Werte, Fehler
   - Filter nach Status und Zeitraum

### FR-7: Erweiterung messwerte-Tabelle

```sql
ALTER TABLE messwerte ADD COLUMN IF NOT EXISTS quelle VARCHAR(20) DEFAULT 'CSV';
-- Mögliche Werte: 'CSV', 'MQTT', 'SDAT'

ALTER TABLE messwerte ADD COLUMN IF NOT EXISTS qualitaet VARCHAR(10);
-- Quality Code aus ebIX (z.B. '56' = validated)
```

### FR-8: Bestätigungsnachrichten (Acknowledgement)

1. Nach Empfang einer gültigen Nachricht wird eine Bestätigung (DocumentType 312) generiert
2. Bei Fehlern wird ein Model Error Report (DocumentType 313) erstellt
3. Bestätigungen werden automatisch in den SFTP-Outbox-Ordner geschrieben

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt?

**SFTP-Verbindung:**
* [ ] Backend kann sich per SFTP mit dem Swisseldex Datahub verbinden
* [ ] Polling-Job läuft gemäss konfiguriertem Intervall
* [ ] Bei Verbindungsfehlern wird Reconnect mit Exponential Backoff durchgeführt
* [ ] SFTP-Zugangsdaten werden ausschliesslich über Umgebungsvariablen konfiguriert

**ebIX Parsing:**
* [ ] E66 (ValidatedMeteredData) Nachrichten werden korrekt geparst
* [ ] 15-Minuten-Werte (PT15M) werden korrekt in `messwerte` übernommen
* [ ] Messpunkt-ID wird korrekt der Einheit zugeordnet
* [ ] Quality Code wird in der Datenbank gespeichert
* [ ] Ungültige XML-Nachrichten werden geloggt und im Fehler-Log abgelegt

**Import:**
* [ ] Empfangene Messdaten erscheinen in der `messwerte`-Tabelle mit `quelle = 'SDAT'`
* [ ] Duplikate werden erkannt und aktualisiert (kein doppelter Import)
* [ ] Import-Protokoll wird für jede empfangene Datei erstellt
* [ ] Nicht zuordenbare Messpunkte werden als Fehler protokolliert

**Export:**
* [ ] Messdaten können als E66-Nachricht im ebIX-Format exportiert werden
* [ ] Generierte XML-Dateien sind Schema-konform
* [ ] Upload in den SFTP-Outbox-Ordner funktioniert

**Bestätigungen:**
* [ ] Bestätigung (312) wird nach erfolgreichem Import generiert
* [ ] Fehlerreport (313) wird bei ungültigen Nachrichten generiert

**Monitoring:**
* [ ] Import-Log ist im Frontend unter `/sdat-log` einsehbar
* [ ] Prometheus-Metriken sind unter `/actuator/prometheus` verfügbar
* [ ] Health-Check `/actuator/health/swisseldex` zeigt SFTP-Verbindungsstatus

**Einstellungen:**
* [ ] Swisseldex-Konfiguration ist pro Mandant in den Einstellungen pflegbar
* [ ] Feature kann pro Mandant aktiviert/deaktiviert werden

## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Performance
* SFTP-Polling darf den normalen Betrieb nicht beeinträchtigen (asynchrone Verarbeitung)
* Parsing einer ebIX-Nachricht mit 96 Observations (1 Tag) < 500ms
* Batch-Import von mehreren Dateien parallel möglich (Thread-Pool konfigurierbar)

### NFR-2: Sicherheit
* SFTP-Verbindung ausschliesslich über verschlüsselte Kanäle (SSH/TLS)
* Zugangsdaten nur über Umgebungsvariablen, niemals im Code oder in der Datenbank
* Private Keys werden im Dateisystem gespeichert (konfigurierbar)
* Empfangene XML-Nachrichten werden auf XXE-Injection geprüft (Secure XML Parsing)
* Nur Nachrichten mit korrektem Empfänger-EIC-Code werden verarbeitet

### NFR-3: Zuverlässigkeit
* Bei SFTP-Verbindungsabbruch: Automatischer Reconnect mit Exponential Backoff
* Bereits verarbeitete Dateien werden nicht erneut importiert (Dateiname + Document-ID als Duplikat-Check)
* Rohe XML-Dateien werden für Audit-Zwecke archiviert (konfigurierbare Aufbewahrungsdauer)
* Transaktionale Verarbeitung: Import entweder vollständig oder gar nicht

### NFR-4: Kompatibilität
* Bestehende CSV-Upload-Funktionalität bleibt vollständig erhalten
* Messwerte aus allen Quellen (CSV, MQTT, SDAT) werden gleich behandelt
* Keine Breaking Changes an der `messwerte`-Tabelle (nur additive Spalten)
* SDAT-CH Version 2025 kompatibel (Produktionsbereitschaft ab 24.03.2026)
* Multi-Tenancy: Jeder Mandant hat eigene Swisseldex-Konfiguration

### NFR-5: Standards-Konformität
* ebIX XML gemäss SDAT-CH Anhang 4: XML-Schemas
* Messpunkt-Identifikation gemäss MC-CH (Metering Code Schweiz)
* EIC-Codes gemäss ENTSO-E Coding Scheme
* Zeitstempel in UTC gemäss ISO 8601

## 5. Edge Cases & Fehlerbehandlung

**SFTP-Verbindung:**

| Szenario | Verhalten |
|----------|-----------|
| SFTP nicht erreichbar | Warnung loggen, Retry beim nächsten Polling-Intervall |
| Authentifizierung fehlgeschlagen | Fehler loggen, Feature deaktivieren, Admin benachrichtigen |
| Timeout beim Download | Datei beim nächsten Polling erneut versuchen |
| Leerer Inbox-Ordner | Normal, kein Fehler |

**ebIX Parsing:**

| Szenario | Verhalten |
|----------|-----------|
| Ungültiges XML | Fehler loggen, Datei in Fehler-Ordner verschieben, Import-Log mit Status FEHLER |
| Unbekannter DocumentType | Nachricht ignorieren, Import-Log mit Status IGNORIERT |
| Messpunkt nicht gefunden | Fehler loggen, Import-Log mit Status FEHLER, Datei archivieren |
| Fehlende Observations | Lücken akzeptieren, nur vorhandene Werte importieren |
| Negative Quantity-Werte | Akzeptieren (z.B. Einspeisung wird als negativer Wert geliefert) |
| Quality Code != 56 (validated) | Wert importieren, aber Quality Code speichern für spätere Auswertung |
| Mehrere MeteringData-Blöcke | Jeden Block einzeln verarbeiten |
| Zeitstempel nicht in UTC | Konvertierung in UTC vor Speicherung |

**Datenintegrität:**

| Szenario | Verhalten |
|----------|-----------|
| Duplikat (gleicher Zeitstempel + Einheit) | Bestehenden Wert aktualisieren, Quality Code aktualisieren |
| Überschneidung mit CSV-Daten | SDAT-Wert hat Vorrang (höhere Qualität, da validiert) |
| Datei bereits importiert (gleiche Document-ID) | Ignorieren, Import-Log mit IGNORIERT |
| Concurrent Import (mehrere Dateien gleichzeitig) | Transaktionale Isolation gewährleisten |

## 6. Abgrenzung / Out of Scope

* **Nicht** umgesetzt in dieser Phase:
  - Wechselprozesse (Lieferantenwechsel) via SDAT-CH
  - Stammdatenaustausch (E07 - Master data metering point)
  - Automatische Messpunkt-Registrierung bei Swisseldex (manueller Prozess)
  - SDAT-CH-Hub Integration (nur Swisseldex Datahub)
  - AS4-Protokoll (nur SFTP in Phase 1)
  - Echtzeit-Datenübertragung (nur Batch via Polling)
  - Frontend für den manuellen ebIX-Export
  - Rechnungsstellung via SDAT (C05 - Invoice)

## 7. Offene Fragen

* [ ] Muss der ZEV-Betreiber als eigenständiger Marktpartner bei Swisseldex registriert sein, oder kann er über einen bestehenden Energiedienstleister angebunden werden?
* [ ] Welche Kosten fallen für die Swisseldex-Nutzung an? (Richtwert: < 0.80 CHF/Messpunkt/Jahr)
* [ ] Ist eine Testumgebung (Sandbox) von Swisseldex für die Entwicklung verfügbar?
* [ ] Sollen neben E66 (ValidatedMeteredData) weitere Nachrichtentypen unterstützt werden?
* [ ] Wie wird mit der Umstellung auf SDAT-CH Version 2025 (produktiv ab 24.03.2026) umgegangen?
* [ ] Soll der Export (FR-5) in Phase 1 bereits umgesetzt werden, oder nur der Import?
* [ ] Wie lange sollen die archivierten XML-Rohdaten aufbewahrt werden?
* [ ] Muss die Anwendung auch den SDAT-CH-Hub (Encontrol) neben dem Swisseldex Datahub unterstützen?

## 8. Technische Details

### Architektur-Übersicht

```
┌───────────────────────────────────────────────┐
│                    ZEV Backend                │
│                                               │
│  ┌─────────────────┐   ┌──────────────────┐   │
│  │ SwisseldexSftp  │   │  EbixXmlParser   │   │
│  │ PollingService  │-->│    Service       │   │
│  │ (Scheduled)     │   │                  │   │
│  └────────┬────────┘   └────────┬─────────┘   │
│           │                     │             │
│           ▼                     ▼             │
│  ┌─────────────────┐   ┌──────────────────┐   │
│  │   SdatImport    │   │  Messwerte       │   │
│  │   LogRepository │   │  Repository      │   │
│  └─────────────────┘   └──────────────────┘   │
│                                               │
│  ┌─────────────────┐   ┌──────────────────┐   │
│  │ Swisseldex      │   │  EbixXmlBuilder  │   │
│  │ ExportService   │-->│    Service       │   │
│  │ (Manual/Sched.) │   │                  │   │
│  └─────────────────┘   └──────────────────┘   │
└───────────────────────────────────────────────┘
         │                        ▲
         ▼                        │
┌─────────────────┐      ┌─────────────────┐
│   Swisseldex    │      │    messwerte    │
│   Datahub       │      │   (Datenbank)   │
│   (SFTP)        │      └─────────────────┘
└─────────────────┘
```

### Backend-Komponenten

| Komponente | Beschreibung |
|------------|-------------|
| `SwisseldexSftpService` | SFTP-Verbindung, Datei-Download/Upload, Archivierung |
| `SwisseldexPollingService` | Scheduled Job für SFTP-Polling |
| `EbixXmlParserService` | Parsing von ebIX XML-Nachrichten (JAXB oder StAX) |
| `EbixXmlBuilderService` | Generierung von ebIX XML-Nachrichten für Export |
| `SdatImportService` | Orchestrierung: Parsing → Zuordnung → Persistierung |
| `SdatExportService` | Orchestrierung: Daten sammeln → XML generieren → SFTP Upload |
| `SdatImportLogRepository` | Persistierung des Import-Protokolls |
| `SdatImportLogController` | REST-Endpunkt für Import-Log Abfrage (`/api/sdat-log`) |

### Spring Boot Dependencies

```xml
<!-- SFTP Client -->
<dependency>
    <groupId>org.springframework.integration</groupId>
    <artifactId>spring-integration-sftp</artifactId>
</dependency>

<!-- JAXB für XML-Parsing (ebIX) -->
<dependency>
    <groupId>jakarta.xml.bind</groupId>
    <artifactId>jakarta.xml.bind-api</artifactId>
</dependency>
<dependency>
    <groupId>org.glassfish.jaxb</groupId>
    <artifactId>jaxb-runtime</artifactId>
</dependency>
```

### Flyway Migration

```sql
-- V[next]__swisseldex_integration.sql

-- Import-Log Tabelle
CREATE TABLE zev.sdat_import_log (
    id BIGSERIAL PRIMARY KEY,
    org_id UUID NOT NULL,
    dateiname VARCHAR(255) NOT NULL,
    document_id VARCHAR(100),
    document_type VARCHAR(10) NOT NULL,
    sender_eic VARCHAR(50),
    empfangen_am TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    status VARCHAR(20) NOT NULL DEFAULT 'NEU',
    messpunkt_id VARCHAR(33),
    anzahl_werte INTEGER,
    zeitraum_von TIMESTAMP WITH TIME ZONE,
    zeitraum_bis TIMESTAMP WITH TIME ZONE,
    fehler_meldung TEXT,
    verarbeitet_am TIMESTAMP WITH TIME ZONE,
    roh_xml TEXT
);

CREATE INDEX idx_sdat_import_log_org ON zev.sdat_import_log(org_id);
CREATE INDEX idx_sdat_import_log_status ON zev.sdat_import_log(status);
CREATE INDEX idx_sdat_import_log_datum ON zev.sdat_import_log(empfangen_am);

-- Erweiterung messwerte
ALTER TABLE zev.messwerte ADD COLUMN IF NOT EXISTS quelle VARCHAR(20) DEFAULT 'CSV';
ALTER TABLE zev.messwerte ADD COLUMN IF NOT EXISTS qualitaet VARCHAR(10);

-- Sequence für sdat_import_log
CREATE SEQUENCE zev.sdat_import_log_seq START WITH 1 INCREMENT BY 1;
```

### Docker-Compose Erweiterung

```yaml
# Keine zusätzlichen Services nötig - SFTP-Verbindung geht direkt zum Swisseldex Datahub
# Für lokale Entwicklung/Tests: SFTP-Testserver
sftp-test:
  image: atmoz/sftp:latest
  container_name: sftp-test
  ports:
    - "2222:22"
  volumes:
    - ./sftp-test/inbox:/home/testuser/inbox
    - ./sftp-test/outbox:/home/testuser/outbox
    - ./sftp-test/archive:/home/testuser/archive
  command: testuser:testpass:1001
  networks:
    - zev-network
  profiles:
    - dev
```

### Frontend-Route

| Route | Component | Roles |
|-------|-----------|-------|
| `/sdat-log` | SdatImportLogComponent | zev_admin |

## 9. Testplan

### Unit Tests
- `EbixXmlParserServiceTest` - Parsing verschiedener ebIX-Nachrichtentypen (E66, 312, 313)
- `EbixXmlBuilderServiceTest` - Generierung von ebIX-XML-Nachrichten
- `SdatImportServiceTest` - Messpunkt-Zuordnung, Duplikat-Erkennung, Fehlerbehandlung
- `MesspunktValidatorTest` - Validierung der 33-stelligen Messpunktbezeichnung

### Integration Tests
- `SwisseldexSftpServiceIT` - SFTP-Verbindung mit Testcontainers (atmoz/sftp)
  - Datei-Download aus Inbox
  - Datei-Upload in Outbox
  - Archivierung nach Verarbeitung
- `SdatImportIT` - Gesamter Import-Flow mit TestContainers (SFTP + PostgreSQL)
  - ebIX-Datei in Inbox legen → Polling triggern → Prüfe messwerte-Einträge
  - Duplikat-Erkennung prüfen
  - Fehler bei unbekanntem Messpunkt prüfen
- `SdatExportIT` - Gesamter Export-Flow
  - Messwerte in DB → Export triggern → Prüfe XML im Outbox

### E2E Tests
- SDAT Import-Log Seite: Tabelle wird angezeigt, Filter funktionieren
- Einstellungen: Swisseldex-Konfiguration kann gespeichert werden

## 10. Referenzen

- [Swisseldex Datahub](https://www.swisseldex.ch/datahub/)
- [SDAT-CH Hub](https://www.sdat-ch.ch/)
- [VSE - SDAT-CH Grundlagen und Definitionen](https://www.strom.ch/de/media/13633/download)
- [VSE - SDAT-CH Anhang XML-Schemas](https://www.strom.ch/de/media/13631/download)
- [VSE - Branchenempfehlung SDAT-CH](https://www.strom.ch/de/media/13628/download)
- [VSE - Metering Code Schweiz (MC-CH)](https://www.strom.ch/de/media/13608/download)
- [ebIX - European Forum for energy business Information eXchange](https://www.ebix.org/artikel/documents)
- [ebIXhelp - Codes und Nachrichtentypen](https://www.ebixhelp.ch/codes/)
- [Swisseldex Datahub User Manual (PDF)](https://manuals.datahub.swisseldex.ch/production/user-manual-de.pdf)
- [Electrosuisse - Swisseldex Datahub etabliert sich](https://www.electrosuisse.ch/de/swisseldex-datahub-technische-branchenloesung-etabliert-sich/)
- [Dispatcher - Open-Source SDAT-CH Marktkommunikation](https://www.dispatcher.ch/)
