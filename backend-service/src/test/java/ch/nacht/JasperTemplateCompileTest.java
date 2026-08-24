package ch.nacht;

import ch.nacht.dto.RechnungDTO;
import ch.nacht.dto.TarifZeileDTO;
import ch.nacht.entity.TarifTyp;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JasperTemplateCompileTest {

    @Test
    void testRechnungTemplateCompiles() throws Exception {
        InputStream stream = getClass().getResourceAsStream("/reports/rechnung.jrxml");
        assertNotNull(stream, "rechnung.jrxml not found");
        JasperReport report = JasperCompileManager.compileReport(stream);
        assertNotNull(report);
        System.out.println("rechnung.jrxml compiled successfully");
    }

    @Test
    void testStatistikTemplateCompiles() throws Exception {
        InputStream stream = getClass().getResourceAsStream("/reports/statistik.jrxml");
        assertNotNull(stream, "statistik.jrxml not found");
        JasperReport report = JasperCompileManager.compileReport(stream);
        assertNotNull(report);
        System.out.println("statistik.jrxml compiled successfully");
    }

    @Test
    void testEinheitSummenTemplateCompiles() throws Exception {
        InputStream stream = getClass().getResourceAsStream("/reports/einheit-summen.jrxml");
        assertNotNull(stream, "einheit-summen.jrxml not found");
        JasperReport report = JasperCompileManager.compileReport(stream);
        assertNotNull(report);
        System.out.println("einheit-summen.jrxml compiled successfully");
    }

    /**
     * Fuellt das Rechnungs-Template mit einer echten {@link RechnungDTO} und exportiert ein PDF.
     *
     * <p><b>Warum zusaetzlich zum Kompilieren:</b> Ein Template kompiliert auch dann, wenn die
     * deklarierten Feldtypen nicht zu der Bean passen, die es spaeter befuellt - der
     * {@code ClassCastException} kommt erst beim Fuellen. Genau das trat beim Wechsel der Betraege
     * von {@code double} auf {@link BigDecimal} auf.
     *
     * <p><b>Warum hier und nicht ueber {@code RechnungPdfService}:</b> Der Service laedt das
     * fertige {@code /reports/rechnung.jasper}, das der {@code jasperreports-maven-plugin} erst in
     * der Phase {@code prepare-package} erzeugt. Bei {@code mvn test} liegt dort noch das Binary
     * des vorherigen Laufs - ein Test darauf pruefte ein veraltetes Artefakt. Deshalb wird hier aus
     * dem {@code .jrxml} kompiliert und unmittelbar gefuellt.
     */
    @Test
    void testRechnungTemplateFuelltBetraegeAlsBigDecimal() throws Exception {
        InputStream stream = getClass().getResourceAsStream("/reports/rechnung.jrxml");
        assertNotNull(stream, "rechnung.jrxml not found");
        JasperReport report = JasperCompileManager.compileReport(stream);

        RechnungDTO rechnung = testRechnung();
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("RECHNUNG", rechnung);
        parameters.put("TRANSLATIONS", Map.of());
        // Wie im Betrieb, wenn die QR-Erzeugung scheitert: das Bildelement traegt
        // onErrorType="Blank" und muss null vertragen.
        parameters.put("QR_CODE_IMAGE", null);

        JasperPrint print = JasperFillManager.fillReport(report, parameters,
                new JRBeanCollectionDataSource(rechnung.getTarifZeilen()));
        byte[] pdf = JasperExportManager.exportReportToPdf(print);

        assertTrue(pdf.length > 0, "PDF darf nicht leer sein");
        assertEquals("%PDF", new String(pdf, 0, 4, StandardCharsets.US_ASCII),
                "Ergebnis muss ein PDF sein");
    }

    /**
     * Rechnung mit einer Tarifzeile und einer Rundungsdifferenz ungleich null - damit auch die
     * bedingte Rundungszeile des Templates ({@code getRundung().signum() != 0}) gefuellt wird und
     * nicht bloss uebersprungen.
     */
    private static RechnungDTO testRechnung() {
        RechnungDTO rechnung = new RechnungDTO();
        rechnung.setEinheitName("Wohnung 1");
        rechnung.setMesspunkt("CH1018601234500000000000000123456");
        rechnung.setMieterId(1L);
        rechnung.setMieterName("Max Muster");
        rechnung.setMieterStrasse("Musterstrasse 1");
        rechnung.setMieterPlzOrt("8000 Zuerich");
        rechnung.setVon(LocalDate.of(2026, 1, 1));
        rechnung.setBis(LocalDate.of(2026, 3, 31));
        rechnung.setErstellungsdatum(LocalDate.of(2026, 4, 1));

        rechnung.addTarifZeile(new TarifZeileDTO("ZEV Solarstrom",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31),
                new BigDecimal("123"), new BigDecimal("0.20000"), new BigDecimal("24.60000"),
                TarifTyp.ZEV));

        rechnung.setTotalBetrag(new BigDecimal("24.60000"));
        rechnung.setEndBetrag(new BigDecimal("24.60"));
        rechnung.setRundung(new BigDecimal("-0.00500"));

        rechnung.setZahlungsfrist("30 Tage");
        rechnung.setIban("CH93 0076 2011 6238 5295 7");
        rechnung.setStellerName("ZEV Musterhaus");
        rechnung.setStellerStrasse("Sonnenweg 5");
        rechnung.setStellerPlzOrt("3000 Bern");
        return rechnung;
    }
}
