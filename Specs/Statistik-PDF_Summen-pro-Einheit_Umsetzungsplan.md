# Plan: "Summen pro Einheit"-Tabelle im Statistik-PDF

## Ausgangslage

Das Statistik-PDF (`statistik.jrxml`) zeigt derzeit pro Monat:
- Monatsheader mit Zeitraum
- Datenstatus
- Summen-Tabelle (5 Werte: ProducerTotal, ConsumerTotal, ProducerZev, ConsumerZev, ConsumerZevCalculated)
- Summen-Vergleich (A=B, A=C, B=C)

**Fehlt:** Die "Summen pro Einheit"-Tabelle, die auf der Webseite `/statistik` pro Monat angezeigt wird.

Die Daten sind bereits vorhanden: `MonatsStatistikDTO.einheitSummen` (Liste von `EinheitSummenDTO`) wird vom `StatistikService` berechnet und befüllt.

## Ansatz: Subreport

Da die Anzahl Einheiten dynamisch ist, verwenden wir einen **JasperReports-Subreport**. Die Liste `einheitSummen` wird pro Monat als DataSource an den Subreport übergeben. Der Subreport iteriert über die Einheiten und rendert die Tabelle.

## Zu ändernde Dateien

### 1. NEU: `backend-service/src/main/resources/reports/einheit-summen.jrxml`

Subreport-Template mit:
- **Parameter:** `TRANSLATIONS` (Map), `SPRACHE` (String)
- **Fields:** `einheitName` (String), `einheitTyp` (EinheitTyp), `summeTotal` (Double), `summeZev` (Double), `summeZevCalculated` (Double)
- **columnHeader-Band:** Abschnittstitel "Summen pro Einheit" + Tabellenkopf mit 5 Spalten:
  - Einheit | Typ | Total (kWh) | ZEV (kWh) | ZEV berechnet (kWh)
- **detail-Band:** Eine Zeile pro Einheit
  - Typ-Spalte: `PRODUCER` → translate("PRODUZENT"), `CONSUMER` → translate("KONSUMENT")
  - ZEV berechnet: Nur für CONSUMER anzeigen, bei PRODUCER "-"
  - Zahlenformat: `#,##0.000`

Spaltenbreiten (gesamt 555):

| Spalte | Breite | Ausrichtung |
|--------|--------|-------------|
| Einheit | 170 | links |
| Typ | 85 | links |
| Total | 100 | rechts |
| ZEV | 100 | rechts |
| ZEV berechnet | 100 | rechts |

### 2. ÄNDERN: `backend-service/src/main/resources/reports/statistik.jrxml`

- Neues Field hinzufügen: `einheitSummen` (java.util.List)
- Neuen Parameter hinzufügen: `EINHEIT_SUMMEN_SUBREPORT` (JasperReport)
- Detail-Band-Höhe erhöhen: 220 → 280 (Platz für Subreport-Minimum)
- Subreport-Element am Ende des Detail-Bands einfügen (y=225):
  - `printWhenExpression`: Nur wenn `einheitSummen` nicht leer
  - DataSource: `new JRBeanCollectionDataSource($F{einheitSummen})`
  - Parameter durchreichen: `TRANSLATIONS`, `SPRACHE`

### 3. ÄNDERN: `backend-service/src/main/java/ch/nacht/service/StatistikPdfService.java`

- Neues Feld: `private JasperReport compiledEinheitSummenReport;`
- In `init()`: `einheit-summen.jasper` laden (analog zu compiledReport)
- In `generatePdf()`: Parameter `EINHEIT_SUMMEN_SUBREPORT` zur Map hinzufügen

### 4. ÄNDERN: `backend-service/src/test/java/ch/nacht/JasperTemplateCompileTest.java`

- Neuen Test `testEinheitSummenTemplateCompiles()` hinzufügen (analog zu den bestehenden Tests)

## Verifizierung

1. `mvn test -pl backend-service -Dtest=JasperTemplateCompileTest` - Templates kompilieren
2. `mvn compile -pl backend-service` - Build inkl. Jasper-Kompilierung
3. Manuell: PDF-Export über `/api/statistik/export/pdf` testen
