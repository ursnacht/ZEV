package ch.nacht;

import ch.nacht.dto.NkRechnungDTO;
import ch.nacht.dto.NkRechnungZeileDTO;
import ch.nacht.dto.RechnungDTO;
import ch.nacht.dto.TarifZeileDTO;
import ch.nacht.entity.TarifTyp;
import ch.nacht.service.RechnungService;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JRPrintElement;
import net.sf.jasperreports.engine.JRPrintPage;
import net.sf.jasperreports.engine.JRPrintText;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    // ==================== Nebenkostenrechnung ====================

    @Test
    void testNkRechnungTemplateCompiles() throws Exception {
        InputStream stream = getClass().getResourceAsStream("/reports/nk-rechnung.jrxml");
        assertNotNull(stream, "nk-rechnung.jrxml not found");
        JasperReport report = JasperCompileManager.compileReport(stream);
        assertNotNull(report);
        System.out.println("nk-rechnung.jrxml compiled successfully");
    }

    /**
     * Fuellt das NK-Template mit einer Nachzahlung — der Fall, in dem der QR-Zahlteil gedruckt
     * wird und alle Betragsfelder belegt sind.
     *
     * <p>Zum Warum siehe {@link #testRechnungTemplateFuelltBetraegeAlsBigDecimal()}: Ein Template
     * kompiliert auch mit falschen Feldtypen, der Fehler kommt erst beim Fuellen.
     */
    @Test
    void testNkRechnungTemplateFuelltNachzahlung() throws Exception {
        JasperReport report = kompiliereNkTemplate();
        NkRechnungDTO rechnung = testNkRechnung(new BigDecimal("812.37"));

        byte[] pdf = fuelleNk(report, rechnung);

        assertTrue(pdf.length > 0, "PDF darf nicht leer sein");
        assertEquals("%PDF", new String(pdf, 0, 4, StandardCharsets.US_ASCII),
                "Ergebnis muss ein PDF sein");
    }

    /**
     * Dasselbe Template mit einem <b>Guthaben</b>.
     *
     * <p>Eigener Test, weil hier andere Zweige des Templates greifen: Der ganze
     * {@code lastPageFooter} faellt ueber {@code printWhenExpression} weg, die Saldozeile
     * beschriftet sich als Guthaben, und der Hinweis „keine Forderung" erscheint. Ein Fehler in
     * einem dieser Ausdruecke waere im Nachzahlungsfall unsichtbar.
     */
    @Test
    void testNkRechnungTemplateFuelltGuthaben() throws Exception {
        JasperReport report = kompiliereNkTemplate();
        NkRechnungDTO rechnung = testNkRechnung(new BigDecimal("-120.03"));

        byte[] pdf = fuelleNk(report, rechnung);

        assertEquals("%PDF", new String(pdf, 0, 4, StandardCharsets.US_ASCII),
                "Ergebnis muss ein PDF sein");
    }

    /**
     * Akonto-Zeile <b>mit</b> Korrektur — der Ausdruck fragt die Korrektur nach ihrem Vorzeichen
     * und wird nur in diesem Fall vollstaendig ausgewertet.
     *
     * <p>Eigener Test, weil der Zweig sonst nie laeuft: Die uebrigen Fuelltests tragen eine
     * Korrektur von 0. Ein Fehler im Ausdruck — etwa ein {@code null} auf der Korrektur — faellt
     * ausserdem erst beim Fuellen auf, nicht beim Kompilieren.
     */
    @Test
    void testNkRechnungTemplateFuelltAkontoKorrektur() throws Exception {
        JasperReport report = kompiliereNkTemplate();
        NkRechnungDTO rechnung = testNkRechnung(new BigDecimal("812.37"));
        rechnung.setAkontoAnzahlMonate(new BigDecimal("13"));
        rechnung.setAkontoBetragProMonat(new BigDecimal("130.00"));
        rechnung.setAkontoKorrektur(new BigDecimal("-50.00"));

        String text = gedruckterText(fuelleNkPrint(report, rechnung));

        assertTrue(text.contains("(13 x 130.00, -50.00)"),
                "Die Korrektur muss in der Akonto-Zeile stehen. Gedruckt wurde:\n" + text);
    }

    /** Ohne Korrektur bleibt die Klammer bei der Herleitung — eine „0.00" erklaert nichts. */
    @Test
    void testNkRechnungTemplateOhneAkontoKorrektur() throws Exception {
        JasperReport report = kompiliereNkTemplate();
        NkRechnungDTO rechnung = testNkRechnung(new BigDecimal("812.37"));
        rechnung.setAkontoAnzahlMonate(new BigDecimal("12"));
        rechnung.setAkontoBetragProMonat(new BigDecimal("50.00"));
        rechnung.setAkontoKorrektur(BigDecimal.ZERO);

        String text = gedruckterText(fuelleNkPrint(report, rechnung));

        assertTrue(text.contains("(12 x 50.00)"), "Gedruckt wurde:\n" + text);
        // Auf das Komma pruefen, nicht auf "0.00": Die Herleitung selbst endet auf "50.00)".
        assertFalse(text.contains(", 0.00)"), "Eine Korrektur von 0 darf nicht erscheinen");
    }

    /**
     * Zahlenformat nach {@code Specs/generell.md}: Punkt als Dezimal-, ASCII-Hochkomma als
     * Tausendertrenner. Ein {@code pattern} im Template oder ein {@code String.format} ohne
     * {@code Locale.ROOT} liefert je Umgebung ein Komma — und faellt auf einer Maschine mit
     * anderer Locale erst im Betrieb auf.
     */
    @Test
    void testNkRechnungTemplateFormatiertBetraegeSchweizerisch() throws Exception {
        JasperReport report = kompiliereNkTemplate();
        NkRechnungDTO rechnung = testNkRechnung(new BigDecimal("1234.57"));
        rechnung.setKostentotal(new BigDecimal("1234.57"));

        String text = gedruckterText(fuelleNkPrint(report, rechnung));

        assertTrue(text.contains("1'234.55"),
                "Der zahlbare Betrag muss als 1'234.55 erscheinen. Gedruckt wurde:\n" + text);
        assertFalse(text.contains("1,234"), "Kein Komma als Tausendertrenner");
    }

    private static JasperReport kompiliereNkTemplate() throws Exception {
        InputStream stream = JasperTemplateCompileTest.class
                .getResourceAsStream("/reports/nk-rechnung.jrxml");
        assertNotNull(stream, "nk-rechnung.jrxml not found");
        return JasperCompileManager.compileReport(stream);
    }

    private static byte[] fuelleNk(JasperReport report, NkRechnungDTO rechnung) throws Exception {
        return JasperExportManager.exportReportToPdf(fuelleNkPrint(report, rechnung));
    }

    private static JasperPrint fuelleNkPrint(JasperReport report, NkRechnungDTO rechnung)
            throws Exception {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("RECHNUNG", rechnung);
        parameters.put("TRANSLATIONS", Map.of());
        // Wie im Betrieb, wenn die QR-Erzeugung scheitert oder ein Guthaben vorliegt:
        // das Bildelement traegt onErrorType="Blank" und muss null vertragen.
        parameters.put("QR_CODE_IMAGE", null);

        return JasperFillManager.fillReport(report, parameters,
                new JRBeanCollectionDataSource(rechnung.getZeilen()));
    }

    /**
     * Der gedruckte Text des Berichts.
     *
     * <p>Erst damit pruefen die Fuelltests, was auf dem Papier <b>steht</b>, und nicht nur, dass
     * das Fuellen nicht abbricht. Die Baender setzen ihre Elemente direkt, ohne Rahmen — eine
     * Rekursion ueber {@code JRPrintFrame} braucht es hier deshalb nicht.
     */
    private static String gedruckterText(JasperPrint print) {
        StringBuilder text = new StringBuilder();
        for (JRPrintPage seite : print.getPages()) {
            for (JRPrintElement element : seite.getElements()) {
                if (element instanceof JRPrintText t) {
                    text.append(t.getFullText()).append('\n');
                }
            }
        }
        return text.toString();
    }

    /**
     * NK-Rechnung mit einer Zeile je Positionsart, damit auch die Spalten getroffen werden, die
     * nur eine Art fuellt (Menge, Preis, Prozentsatz).
     *
     * @param saldo ungerundeter Saldo; er bestimmt Rundung und Endbetrag
     */
    private static NkRechnungDTO testNkRechnung(BigDecimal saldo) {
        NkRechnungDTO rechnung = new NkRechnungDTO();
        rechnung.setAbrechnungId(12L);
        rechnung.setBezeichnung("Nebenkosten 2026");
        rechnung.setVon(LocalDate.of(2026, 1, 1));
        rechnung.setBis(LocalDate.of(2026, 12, 31));
        rechnung.setErstellungsdatum(LocalDate.of(2027, 1, 15));

        rechnung.setMieterId(45L);
        rechnung.setMieterName("Max Muster");
        rechnung.setMieterStrasse("Musterstrasse 1");
        rechnung.setMieterPlzOrt("8000 Zuerich");

        NkRechnungZeileDTO umlage = new NkRechnungZeileDTO();
        umlage.setBezeichnung("Allgemeinstrom");
        umlage.setBetrag(new BigDecimal("240.55"));
        rechnung.getZeilen().add(umlage);

        NkRechnungZeileDTO verbrauch = new NkRechnungZeileDTO();
        verbrauch.setBezeichnung("Wasser");
        verbrauch.setMengeneinheit("M3");
        verbrauch.setMenge(new BigDecimal("34.500"));
        verbrauch.setBetragProEinheit(new BigDecimal("1.8500"));
        verbrauch.setBetrag(new BigDecimal("63.83"));
        rechnung.getZeilen().add(verbrauch);

        NkRechnungZeileDTO zuschlag = new NkRechnungZeileDTO();
        zuschlag.setBezeichnung("Verwaltungskosten");
        zuschlag.setProzentsatz(new BigDecimal("3.00"));
        zuschlag.setBetrag(new BigDecimal("9.13"));
        rechnung.getZeilen().add(zuschlag);

        rechnung.setKostentotal(new BigDecimal("313.51"));
        rechnung.setAkontoAnzahlMonate(new BigDecimal("12"));
        rechnung.setAkontoBetragProMonat(new BigDecimal("50.00"));
        rechnung.setAkontoKorrektur(BigDecimal.ZERO);
        rechnung.setAkontoTotal(new BigDecimal("600.00"));

        BigDecimal endBetrag = RechnungService.roundTo5Rappen(saldo);
        rechnung.setSaldo(saldo);
        rechnung.setEndBetrag(endBetrag);
        rechnung.setRundung(endBetrag.subtract(saldo));

        rechnung.setZahlungsfrist("30 Tage");
        rechnung.setIban("CH93 0076 2011 6238 5295 7");
        rechnung.setStellerName("ZEV Musterhaus");
        rechnung.setStellerStrasse("Sonnenweg 5");
        rechnung.setStellerPlzOrt("3000 Bern");
        return rechnung;
    }
}
