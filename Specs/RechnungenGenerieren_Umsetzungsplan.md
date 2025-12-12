# Umsetzungsplan: Generierung Quartalsrechnungen

## Übersicht

Basierend auf der Spezifikation `RechnungenGenerieren.md` und dem Beispiel-PDF `StromRechnungAllgemein.pdf`.

**Ausgabeformat:** PDF mit Schweizer QR-Einzahlungsschein
**Konfiguration:** Alle Rechnungsparameter in `application.yml`

---

## Phase 1: Backend - Datenmodell erweitern

### 1.1 Flyway Migration für Einheit-Erweiterung
**Datei:** `V19__Add_Einheit_Mieter_Messpunkt.sql`

```sql
ALTER TABLE zev.einheit ADD COLUMN mietername VARCHAR(100);
ALTER TABLE zev.einheit ADD COLUMN messpunkt VARCHAR(50);
```

### 1.2 Entity Einheit erweitern
**Datei:** `backend-service/src/main/java/ch/nacht/entity/Einheit.java`

Neue Felder hinzufügen:
- `mietername` (String, max 100 Zeichen, nullable)
- `messpunkt` (String, max 50 Zeichen, nullable)

Mit Gettern/Settern und Validierung.

### 1.3 EinheitDTO erweitern
**Datei:** `backend-service/src/main/java/ch/nacht/dto/EinheitDTO.java`

Entsprechende Felder für API-Response/Request hinzufügen.

---

## Phase 2: Backend - Konfiguration

### 2.1 application.yml erweitern
**Datei:** `backend-service/src/main/resources/application.yml`

```yaml
rechnung:
  zahlungsfrist: "30 Tage"
  iban: "CH70 0630 0016 9464 5991 0"
  steller:
    name: "Urs Nacht"
    strasse: "Hangstrasse 14a"
    plz: "3044"
    ort: "Innerberg"
  adresse:
    strasse: "Mutachstrasse 13"
    plz: "3008"
    ort: "Bern"
  tarif:
    zev:
      bezeichnung: "vZEV PV Tarif"
      preis: 0.2
    ewb:
      bezeichnung: "Strombezug EWB"
      preis: 0.34192
```

### 2.2 Konfigurationsklasse erstellen
**Datei:** `backend-service/src/main/java/ch/nacht/config/RechnungConfig.java`

Spring `@ConfigurationProperties` Klasse für typsichere Konfiguration mit verschachtelten Klassen für:
- `Steller` (name, strasse, plz, ort)
- `Adresse` (strasse, plz, ort)
- `Tarif` mit `ZevTarif` und `EwbTarif` (bezeichnung, preis)

---

## Phase 3: Backend - Rechnungsgenerierung

### 3.1 RechnungDTO erstellen
**Datei:** `backend-service/src/main/java/ch/nacht/dto/RechnungDTO.java`

Enthält alle Daten für eine einzelne Rechnung:
- Einheit-ID, Name, Mietername, Messpunkt
- Zeitraum (von, bis)
- Erstellungsdatum
- ZEV-Menge und Betrag
- EWB-Menge und Betrag
- Total, Rundung, Endbetrag

### 3.2 RechnungService erstellen
**Datei:** `backend-service/src/main/java/ch/nacht/service/RechnungService.java`

Methoden:
- `berechneRechnung(Einheit, LocalDate von, LocalDate bis)` → RechnungDTO
  - Summe `zev_calculated` aus Messwerte für Zeitraum
  - Summe `total` aus Messwerte für Zeitraum
  - EWB = total - zev_calculated
  - Beträge berechnen (Menge × Preis)
  - Rundung auf 5 Rappen

### 3.3 RechnungPdfService erstellen
**Datei:** `backend-service/src/main/java/ch/nacht/service/RechnungPdfService.java`

Verwendet bestehende Bibliothek **OpenHTMLToPDF** (wie StatistikPdfService).

Methoden:
- `generateRechnung(RechnungDTO, String language)` → byte[]
- `generateRechnungen(List<RechnungDTO>, String language)` → Map<Long, byte[]>

PDF-Struktur (gemäss Vorlage):
1. **Seite 1:** Kopfdaten + Rechnungstabelle
   - Kopf: Datum, Zahlungsfrist, Zeitraum, Rechnungssteller, Empfänger
   - Tabelle: Messpunkt, ZEV-Tarif, EWB-Tarif, Total
2. **Seite 2:** Optional (nicht notwendig laut Spec)
3. **Seite 3:** QR-Einzahlungsschein (Empfangsschein + Zahlteil)

### 3.4 QR-Code Generierung
**Neue Abhängigkeit in pom.xml:**
```xml
<dependency>
    <groupId>net.codecrete.qrbill</groupId>
    <artifactId>qrbill-generator</artifactId>
    <version>3.3.0</version>
</dependency>
```

Die Bibliothek `qrbill-generator` ist speziell für Schweizer QR-Rechnungen und generiert:
- Korrekten Swiss QR Code
- Empfangsschein-Layout
- Zahlteil-Layout
- Adresstyp "S" (strukturiert) wie spezifiziert

### 3.5 RechnungController erstellen
**Datei:** `backend-service/src/main/java/ch/nacht/controller/RechnungController.java`

Endpoints:
```
POST /api/rechnungen/generate
  Body: { "von": "2024-01-01", "bis": "2024-03-31", "einheitIds": [1, 2, 3] }
  Response: { "rechnungen": [{ "einheitId": 1, "einheitName": "Wohnung A", "filename": "Wohnung_A.pdf" }] }

GET /api/rechnungen/download/{einheitName}
  Response: PDF-Datei als Attachment
  Filename: {einheitName}.pdf (z.B. "Wohnung_A.pdf")
```

Sicherheit: `@PreAuthorize("hasRole('zev_admin')")`

### 3.6 Temporäre Speicherung
**Datei:** `backend-service/src/main/java/ch/nacht/service/RechnungStorageService.java`

- In-Memory Cache (ConcurrentHashMap) für generierte PDFs
- **Einheitenname als Key** (URL-encoded für Sonderzeichen)
- Automatische Bereinigung nach 30 Minuten (ScheduledExecutorService)
- Keine Datenbankpersistenz

**Dateinamen-Format:** `{Einheitenname}.pdf`
- Leerzeichen werden durch Unterstriche ersetzt
- Sonderzeichen werden entfernt oder ersetzt
- Beispiel: "Wohnung A" → "Wohnung_A.pdf"

---

## Phase 4: Frontend - Einheit-Formular erweitern

### 4.1 Einheit Model erweitern
**Datei:** `frontend-service/src/app/models/einheit.model.ts`

```typescript
export interface Einheit {
  id?: number;
  name: string;
  typ: 'PRODUCER' | 'CONSUMER';
  mietername?: string;    // NEU
  messpunkt?: string;     // NEU
}
```

### 4.2 EinheitFormComponent erweitern
**Datei:** `frontend-service/src/app/components/einheit-form/einheit-form.component.html`

Zwei neue Eingabefelder hinzufügen (nur für CONSUMER sichtbar):
- Mietername (Text, max 100 Zeichen)
- Messpunkt (Text, max 50 Zeichen)

---

## Phase 5: Frontend - Rechnungsseite

### 5.1 Routing erweitern
**Datei:** `frontend-service/src/app/app.routes.ts`

Neue Route: `/rechnungen` → RechnungenComponent

### 5.2 Navigation erweitern
**Datei:** `frontend-service/src/app/components/navbar/navbar.component.html`

Neuer Menüeintrag "Rechnungen" (nur für zev_admin)

### 5.3 RechnungenComponent erstellen
**Dateien:**
- `frontend-service/src/app/components/rechnungen/rechnungen.component.ts`
- `frontend-service/src/app/components/rechnungen/rechnungen.component.html`
- `frontend-service/src/app/components/rechnungen/rechnungen.component.css`

**Layout:**
1. **Zeitraum-Auswahl** (analog Statistik)
   - Datum von / Datum bis
   - Auto-Fill: Bei Änderung von "Datum von" wird "Datum bis" auf Monatsende gesetzt

2. **Konsumenten-Auswahl** (analog Grafiken Messwerte)
   - Checkbox-Liste aller Consumer
   - "Alle auswählen" Checkbox
   - Scrollbar bei vielen Einheiten

3. **Generieren-Button**
   - Disabled wenn: kein Zeitraum oder keine Auswahl
   - Loading-State während Generierung

4. **Ergebnisliste** (nach Generierung)
   - Tabelle mit: Einheit-Name, Mietername, Betrag
   - Download-Button pro Rechnung
   - Hinweis: "Rechnungen sind nur temporär verfügbar"

### 5.4 RechnungService (Frontend)
**Datei:** `frontend-service/src/app/services/rechnung.service.ts`

Methoden:
- `generateRechnungen(von, bis, einheitIds)` → Observable<GenerierteRechnungen>
- `downloadRechnung(downloadId, filename)` → Blob-Download

---

## Phase 6: Übersetzungen

### 6.1 Flyway Migration für Translations
**Datei:** `V20__Add_Rechnung_Translations.sql`

Neue Übersetzungsschlüssel (DE/EN):
- RECHNUNGEN / Invoices
- RECHNUNGEN_GENERIEREN / Generate Invoices
- ZEITRAUM_WAEHLEN / Select Period
- KONSUMENTEN_WAEHLEN / Select Consumers
- ALLE_AUSWAEHLEN / Select All
- GENERIEREN / Generate
- DOWNLOAD / Download
- MIETERNAME / Tenant Name
- MESSPUNKT / Metering Point
- RECHNUNG_ERSTELLT / Invoice Created
- RECHNUNGEN_TEMPORAER / Invoices are temporary
- STROMRECHNUNG / Electricity Invoice
- ZAHLUNGSFRIST / Payment Terms
- RECHNUNGSSTELLER / Invoicer
- ENERGIEBEZUG / Energy Consumption
- ZEV_TARIF / ZEV Tariff
- EWB_TARIF / Grid Tariff
- TOTAL_BETRAG / Total Amount
- RUNDUNG / Rounding
- EMPFANGSSCHEIN / Receipt
- ZAHLTEIL / Payment Part
- KONTO_ZAHLBAR_AN / Account/Payable to
- ZAHLBAR_DURCH / Payable by
- WAEHRUNG / Currency
- BETRAG / Amount

---

## Phase 7: Testing

### 7.1 Unit Tests
**Dateien:**
- `RechnungServiceTest.java` - Berechnungslogik testen
- `RechnungPdfServiceTest.java` - PDF-Generierung testen

### 7.2 Integration Tests
**Datei:** `RechnungControllerIT.java`
- Endpoint-Tests mit Testcontainers
- Validierung der generierten PDFs

### 7.3 E2E Tests
**Datei:** `frontend-service/tests/rechnungen.spec.ts`
- Navigation zur Rechnungsseite
- Zeitraum- und Konsumenten-Auswahl
- Generierung und Download

---

## Technische Details

### Rundung auf 5 Rappen
```java
public static double roundTo5Rappen(double amount) {
    return Math.round(amount * 20.0) / 20.0;
}
```

### QR-Code Daten (Swiss QR Bill)
```
Adresstyp: S (strukturiert)
IBAN: aus application.yml
Empfänger: Rechnungssteller aus application.yml
Zahler: Mietername + Adresse aus application.yml
Betrag: Gerundeter Endbetrag
Währung: CHF
Zusätzliche Informationen: "Stromrechnung [Einheit-Name]"
```

### Berechnung Beispiel
```
Zeitraum: 01.07.2025 - 30.09.2025
Einheit: Wohnung A

Aus Messwerte:
- Summe zev_calculated: 1182 kWh
- Summe total: 1335 kWh

Berechnung:
- ZEV: 1182 kWh × 0.20 CHF = 236.40 CHF
- EWB: (1335 - 1182) kWh × 0.34192 CHF = 153 × 0.34192 = 52.31 CHF
- Total: 288.71 CHF
- Rundung: -0.01 CHF
- Endbetrag: 288.70 CHF
```

---

## Abhängigkeiten

### Neue Maven Dependencies
```xml
<!-- Swiss QR Bill Generator -->
<dependency>
    <groupId>net.codecrete.qrbill</groupId>
    <artifactId>qrbill-generator</artifactId>
    <version>3.3.0</version>
</dependency>
```

### Bestehende Dependencies (bereits vorhanden)
- OpenHTMLToPDF (für PDF-Generierung)
- Spring Security (für Rollenprüfung)

---

## Reihenfolge der Umsetzung

1. **Backend Datenmodell** (Phase 1) - Basis für alles
2. **Backend Konfiguration** (Phase 2) - Rechnungsparameter
3. **Frontend Einheit-Formular** (Phase 4) - Mietername/Messpunkt erfassen
4. **Backend Rechnungsgenerierung** (Phase 3) - Kernlogik
5. **Frontend Rechnungsseite** (Phase 5) - UI für Generierung
6. **Übersetzungen** (Phase 6) - Mehrsprachigkeit
7. **Testing** (Phase 7) - Qualitätssicherung

---

## Offene Punkte / Annahmen

1. **Keine Rechnungsnummer:** Wie spezifiziert wird keine Rechnungsnummer generiert
2. **Seite 2 nicht notwendig:** Die Info-Seite aus dem Beispiel-PDF wird weggelassen
3. **Temporäre Speicherung:** PDFs werden 30 Minuten im Speicher gehalten
4. **Nur Consumer:** Rechnungen werden nur für Einheiten vom Typ CONSUMER generiert
5. **Validierung:** Mietername und Messpunkt müssen für Rechnungsgenerierung vorhanden sein
