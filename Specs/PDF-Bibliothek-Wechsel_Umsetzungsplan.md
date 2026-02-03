# Umsetzungsplan: Wechsel der PDF-Bibliothek auf JasperReports

## Zusammenfassung

Umstellung der Rechnungs- und Statistik-PDF-Generierung von OpenHTMLToPDF auf JasperReports. JasperReports ist eine ausgereifte Java-Bibliothek für professionelle Report-Generierung mit Template-basiertem Ansatz (JRXML), die native PDF-Ausgabe, präzise Positionierung und einfache Wartung der Templates ermöglicht.

---

## Betroffene Komponenten

### Zu ändernde Dateien

| Datei | Änderung |
|-------|----------|
| `backend-service/pom.xml` | JasperReports Dependency hinzufügen |
| `backend-service/src/main/java/ch/nacht/service/RechnungPdfService.java` | Neuimplementierung mit JasperReports |
| `backend-service/src/main/java/ch/nacht/service/StatistikPdfService.java` | Neuimplementierung mit JasperReports |

### Neue Dateien

| Datei | Beschreibung |
|-------|--------------|
| `backend-service/src/main/resources/reports/rechnung.jrxml` | JasperReports Template für Rechnungen |
| `backend-service/src/main/resources/reports/qr-zahlteil.jrxml` | JasperReports Sub-Report für QR-Zahlteil |
| `backend-service/src/main/resources/reports/statistik.jrxml` | JasperReports Template für Statistiken |

---

## Phasen-Tabelle

| Status | Phase | Beschreibung |
|--------|-------|--------------|
| [x] | 1. Dependencies | JasperReports Dependency zum pom.xml hinzufügen |
| [x] | 2. Rechnung-Template | JRXML-Template für Rechnungen erstellen |
| [x] | 3. QR-Zahlteil-Template | JRXML-Sub-Report für Swiss QR-Bill |
| [x] | 4. RechnungPdfService | Service auf JasperReports umstellen |
| [x] | 5. Statistik-Template | JRXML-Template für Statistiken erstellen |
| [x] | 6. StatistikPdfService | Service auf JasperReports umstellen |
| [x] | 7. Alte Dependencies entfernen | OpenHTMLToPDF aus pom.xml entfernen |

---

## Detaillierte Implementierung

### Phase 1: Dependencies

**Datei:** `backend-service/pom.xml`

```xml
<!-- JasperReports für PDF-Generierung -->
<dependency>
    <groupId>net.sf.jasperreports</groupId>
    <artifactId>jasperreports</artifactId>
    <version>6.21.0</version>
</dependency>
<!-- PDF-Font für JasperReports -->
<dependency>
    <groupId>net.sf.jasperreports</groupId>
    <artifactId>jasperreports-fonts</artifactId>
    <version>6.21.0</version>
</dependency>
```

**Hinweis:** Die QR-Bill-Bibliothek (`net.codecrete.qrbill`) bleibt für die QR-Code-Generierung erhalten.

---

### Phase 2: Rechnung-Template

**Datei:** `backend-service/src/main/resources/reports/rechnung.jrxml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<jasperReport xmlns="http://jasperreports.sourceforge.net/jasperreports"
              xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
              xsi:schemaLocation="http://jasperreports.sourceforge.net/jasperreports
              http://jasperreports.sourceforge.net/xsd/jasperreport.xsd"
              name="rechnung" pageWidth="595" pageHeight="842"
              columnWidth="555" leftMargin="20" rightMargin="20"
              topMargin="20" bottomMargin="20">

    <parameter name="RECHNUNG" class="ch.nacht.dto.RechnungDTO"/>
    <parameter name="TRANSLATIONS" class="java.util.Map"/>
    <parameter name="QR_CODE_IMAGE" class="java.awt.Image"/>

    <title>
        <band height="200">
            <!-- Header mit Rechnungssteller und Empfängeradresse -->
            <staticText>
                <reportElement x="0" y="0" width="200" height="20"/>
                <textElement><font size="9"/></textElement>
                <text><![CDATA[$P{TRANSLATIONS}.get("DATUM"):]]></text>
            </staticText>
            <textField>
                <reportElement x="80" y="0" width="120" height="20"/>
                <textElement><font size="9"/></textElement>
                <textFieldExpression><![CDATA[$P{RECHNUNG}.getErstellungsdatum()]]></textFieldExpression>
            </textField>
            <!-- ... weitere Header-Elemente ... -->
        </band>
    </title>

    <detail>
        <band height="300">
            <!-- Tarifzeilen-Tabelle -->
            <!-- Wird dynamisch aus rechnung.getTarifZeilen() gefüllt -->
        </band>
    </detail>

    <summary>
        <band height="100">
            <!-- Summen und Dankestext -->
        </band>
    </summary>
</jasperReport>
```

---

### Phase 3: QR-Zahlteil Template

**Datei:** `backend-service/src/main/resources/reports/qr-zahlteil.jrxml`

Sub-Report für den Swiss QR-Bill gemäss SIX-Standard:
- Empfangsschein (62mm Breite)
- Zahlteil (148mm Breite)
- QR-Code (46mm x 46mm)
- Präzise Positionierung nach SIX-Spezifikation

---

### Phase 4: RechnungPdfService

**Änderung in:** `backend-service/src/main/java/ch/nacht/service/RechnungPdfService.java`

```java
@Service
public class RechnungPdfService {

    private static final Logger log = LoggerFactory.getLogger(RechnungPdfService.class);
    private final TranslationRepository translationRepository;

    private JasperReport compiledReport;
    private JasperReport compiledQrReport;

    @PostConstruct
    public void init() throws JRException {
        // Compile templates at startup
        InputStream reportStream = getClass().getResourceAsStream("/reports/rechnung.jrxml");
        compiledReport = JasperCompileManager.compileReport(reportStream);

        InputStream qrStream = getClass().getResourceAsStream("/reports/qr-zahlteil.jrxml");
        compiledQrReport = JasperCompileManager.compileReport(qrStream);
    }

    public byte[] generatePdf(RechnungDTO rechnung, String sprache) {
        log.info("Generating invoice PDF for unit: {}", rechnung.getEinheitName());

        Map<String, String> translations = loadTranslations(sprache);
        byte[] qrCodeImage = generateQrCodeBytes(rechnung);

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("RECHNUNG", rechnung);
        parameters.put("TRANSLATIONS", translations);
        parameters.put("QR_CODE_IMAGE", qrCodeImage);
        parameters.put("SUBREPORT_QR", compiledQrReport);

        // Tarifzeilen als DataSource
        JRBeanCollectionDataSource tarifDataSource =
            new JRBeanCollectionDataSource(rechnung.getTarifZeilen());

        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            JasperPrint jasperPrint = JasperFillManager.fillReport(
                compiledReport, parameters, tarifDataSource);
            JasperExportManager.exportReportToPdfStream(jasperPrint, os);

            log.info("Invoice PDF generated, size: {} bytes", os.size());
            return os.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate PDF: {}", e.getMessage(), e);
            throw new RuntimeException("PDF generation failed", e);
        }
    }

    private byte[] generateQrCodeBytes(RechnungDTO rechnung) {
        // Bestehende QR-Code Logik aus qrbill-generator
        // ...
    }
}
```

---

### Phase 5: Statistik-Template

**Datei:** `backend-service/src/main/resources/reports/statistik.jrxml`

Template für Statistik-Reports mit:
- Übersicht (Messwerte-Status, fehlende Einheiten)
- Monatliche Abschnitte (Sub-Reports)
- Summentabellen
- Vergleichsabschnitte
- Einheiten-Tabellen
- Abweichungslisten

---

### Phase 6: StatistikPdfService

**Änderung in:** `backend-service/src/main/java/ch/nacht/service/StatistikPdfService.java`

```java
@Service
public class StatistikPdfService {

    private JasperReport compiledReport;

    @PostConstruct
    public void init() throws JRException {
        InputStream reportStream = getClass().getResourceAsStream("/reports/statistik.jrxml");
        compiledReport = JasperCompileManager.compileReport(reportStream);
    }

    public byte[] generatePdf(StatistikDTO statistik, String sprache) {
        Map<String, String> translations = loadTranslations(sprache);

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("STATISTIK", statistik);
        parameters.put("TRANSLATIONS", translations);
        parameters.put("SPRACHE", sprache);

        JRBeanCollectionDataSource monateDataSource =
            new JRBeanCollectionDataSource(statistik.getMonate());

        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            JasperPrint jasperPrint = JasperFillManager.fillReport(
                compiledReport, parameters, monateDataSource);
            JasperExportManager.exportReportToPdfStream(jasperPrint, os);

            return os.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("PDF generation failed", e);
        }
    }
}
```

---

### Phase 7: Alte Dependencies entfernen

**Änderung in:** `backend-service/pom.xml`

```xml
<!-- Entfernen: -->
<!--
<dependency>
    <groupId>com.openhtmltopdf</groupId>
    <artifactId>openhtmltopdf-pdfbox</artifactId>
    <version>1.0.10</version>
</dependency>
-->
```

---

## Validierungen

### Template-Validierungen

1. **JRXML-Syntax:** Templates müssen gültiges XML sein
2. **Feld-Referenzen:** Alle referenzierten Felder müssen in DTOs existieren
3. **Sub-Reports:** Pfade zu Sub-Reports müssen korrekt sein

### PDF-Validierungen

1. **QR-Code:** Validierung mit SIX QR-Bill Validator
2. **Layout:** Präzise Positionierung gemäss Spezifikation
3. **Fonts:** Eingebettete Fonts für konsistente Darstellung

---

## Offene Punkte / Annahmen

1. **Annahme:** JasperReports Templates werden zur Compile-Zeit validiert und zur Laufzeit gecached.

2. **Annahme:** Die QR-Code-Generierung bleibt mit der bestehenden `qrbill-generator`-Bibliothek. Der QR-Code wird als Bild in das JasperReports-Template eingefügt.

3. **Vorteil JasperReports:**
   - Native PDF-Generierung (kein Umweg über HTML)
   - Präzise Positionierung in mm/pt
   - Template-basiert: Designer können Templates anpassen
   - Professionelle Report-Bibliothek mit grosser Community

4. **Template-Editor:** JasperReports Templates können mit Jaspersoft Studio (Eclipse-basiert) visuell bearbeitet werden.

---

## Risiken und Mitigationen

| Risiko | Mitigation |
|--------|------------|
| Template-Komplexität für QR-Zahlteil | Exakte Maße aus SIX-Spezifikation verwenden |
| Font-Probleme | Fonts im Template einbetten |
| Grosse JAR-Grösse | JasperReports bringt ~10MB Dependencies |

---

## Testplan

1. **Unit-Tests:**
   - Template-Kompilierung
   - PDF-Generierung mit Mock-Daten

2. **Integrationstests:**
   - Vollständige Rechnung mit echten Daten
   - QR-Code-Validierung

3. **Manuelle Tests:**
   - PDF in verschiedenen Viewern öffnen
   - QR-Zahlteil mit Banking-App scannen
   - Vergleich mit bisherigem Output
