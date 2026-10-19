package ch.nacht.service;

import ch.nacht.dto.MonatsStatistikDTO;
import ch.nacht.dto.StatistikDTO;
import ch.nacht.entity.Translation;
import ch.nacht.entity.Verteilmodus;
import ch.nacht.repository.TranslationRepository;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.JRPrintElement;
import net.sf.jasperreports.engine.JRPrintFrame;
import net.sf.jasperreports.engine.JRPrintPage;
import net.sf.jasperreports.engine.JRPrintText;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests des Statistik-PDF — gegen das <b>echte</b> Template (Specs/Statistik.md, Gesamt-Panel).
 *
 * <p>Der Bericht wird gefüllt, aber nicht als PDF exportiert: Der gefüllte {@link JasperPrint}
 * lässt sich direkt nach seinen Texten durchsuchen, ohne eine PDF-Bibliothek zum Auslesen.
 * {@code JasperTemplateCompileTest} prüft nur, dass das Template übersetzt — dass es sich mit
 * Daten auch <b>füllen</b> lässt, prüft erst dieser Test. Genau dort lag die Falle:
 * {@code Month.of(0)} wirft, und das Gesamt trägt Monat 0.
 */
class StatistikPdfServiceTest {

    private StatistikPdfService pdfService;

    @BeforeEach
    void setUp() throws JRException {
        TranslationRepository translations = mock(TranslationRepository.class);
        when(translations.findAll()).thenReturn(List.of(
                uebersetzung("STATISTIK_GESAMTER_ZEITRAUM", "Gesamter Zeitraum", "Entire period"),
                uebersetzung("SUMMEN_VERGLEICH", "Summen-Vergleich", "Sum comparison"),
                uebersetzung("STATISTIK_KENNZAHLEN", "Kennzahlen", "Key figures"),
                uebersetzung("KENNZAHL_AUTARKIEGRAD", "Autarkiegrad", "Self-sufficiency"),
                uebersetzung("KENNZAHL_EIGENVERBRAUCHSQUOTE", "Eigenverbrauchsquote", "Self-consumption rate"),
                uebersetzung("KENNZAHL_NETZBEZUGSQUOTE", "Netzbezugsquote", "Grid supply rate"),
                uebersetzung("KENNZAHL_EINSPEISEQUOTE", "Einspeisequote", "Feed-in rate"),
                uebersetzung("KENNZAHL_ZEV_EIGENVERBRAUCH", "ZEV-Eigenverbrauch", "ZEV self-consumption"),
                uebersetzung("KENNZAHL_AUTARKIEGRAD_GEMESSEN", "Autarkiegrad (gemessen)", "Self-sufficiency (measured)"),
                uebersetzung("KENNZAHL_NETZBEZUGSQUOTE_GEMESSEN", "Netzbezugsquote (gemessen)", "Grid supply rate (measured)"),
                uebersetzung("KENNZAHL_LUECKENHAFT", "lückenhaft", "incomplete"),
                uebersetzung("KENNZAHL_LUECKE_MESSUNG_HINWEIS", "Messung zu günstig", "measurement too favourable"),
                uebersetzung("KENNZAHL_LUECKE_VERTEILUNG_HINWEIS", "Verteilung zu ungünstig", "distribution too unfavourable"),
                uebersetzung("KENNZAHL_BATTERIE_GELADEN", "Batterie geladen", "Battery charged"),
                uebersetzung("KENNZAHL_BERECHNET_HINWEIS", "berechnet/geschätzt", "calculated/estimated")));

        OrganizationContextService kontext = mock(OrganizationContextService.class);
        when(kontext.getCurrentOrgName()).thenReturn("Test");

        pdfService = new StatistikPdfService(translations, kontext);
        // Aus dem .jrxml kompiliert, NICHT die .jasper-Dateien geladen: Die entstehen erst in der
        // Phase prepare-package, nach den Unit-Tests - ein Test saehe sonst ein veraltetes Template.
        // Genau das ist beim Schreiben dieses Tests passiert: Er lief gegen den alten Stand.
        pdfService.verwendeBerichte(kompiliere("statistik"), kompiliere("einheit-summen"));
    }

    private static Translation uebersetzung(String key, String deutsch, String englisch) {
        Translation t = new Translation();
        t.setKey(key);
        t.setDeutsch(deutsch);
        t.setEnglisch(englisch);
        return t;
    }

    private JasperReport kompiliere(String name) throws JRException {
        return JasperCompileManager.compileReport(
                getClass().getResourceAsStream("/reports/" + name + ".jrxml"));
    }

    private static MonatsStatistikDTO zeitraum(int jahr, int monat, LocalDate von, LocalDate bis) {
        MonatsStatistikDTO dto = new MonatsStatistikDTO();
        dto.setJahr(jahr);
        dto.setMonat(monat);
        dto.setVon(von);
        dto.setBis(bis);
        return dto;
    }

    private static StatistikDTO quartal() {
        StatistikDTO statistik = new StatistikDTO();
        statistik.setMonate(new ArrayList<>(List.of(
                zeitraum(2024, 1, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31)),
                zeitraum(2024, 2, LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 29)))));
        statistik.setGesamt(zeitraum(0, 0, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 2, 29)));
        return statistik;
    }

    /**
     * Ein Monat mit den Quoten aus dem Beispiel der Spec (Cz=600, C=1000; Pz=900, P=1200), ohne
     * Bilanz- und Batteriewerte. Die Tests schalten die Teile zu, um die es ihnen geht.
     */
    private static StatistikDTO einMonat(Verteilmodus modus) {
        MonatsStatistikDTO monat = zeitraum(2024, 1, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31));
        monat.setAutarkiegrad(0.6);
        monat.setEigenverbrauchsquote(0.75);
        monat.setNetzbezugsquote(0.4);
        monat.setEinspeisequote(0.25);
        monat.setZevEigenverbrauch(600.0);

        StatistikDTO statistik = new StatistikDTO();
        statistik.setVerteilmodus(modus);
        statistik.setMonate(new ArrayList<>(List.of(monat)));
        return statistik;
    }

    private static MonatsStatistikDTO monat(StatistikDTO statistik) {
        return statistik.getMonate().get(0);
    }

    private static boolean enthaelt(List<String> texte, String teil) {
        return erstesVorkommen(texte, teil) >= 0;
    }

    /** Alle Texte des gefüllten Berichts, in Reihenfolge — auch die in Rahmen geschachtelten. */
    private static List<String> texte(JasperPrint print) {
        List<String> texte = new ArrayList<>();
        for (JRPrintPage seite : print.getPages()) {
            sammle(seite.getElements(), texte);
        }
        return texte;
    }

    private static void sammle(List<JRPrintElement> elemente, List<String> texte) {
        for (JRPrintElement element : elemente) {
            if (element instanceof JRPrintText text && text.getFullText() != null) {
                texte.add(text.getFullText());
            } else if (element instanceof JRPrintFrame rahmen) {
                sammle(rahmen.getElements(), texte);
            }
        }
    }

    private static int erstesVorkommen(List<String> texte, String teil) {
        for (int i = 0; i < texte.size(); i++) {
            if (texte.get(i).contains(teil)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * <b>Das Gesamt steht im PDF — vor den Monaten.</b>
     *
     * <p>Ohne die Anpassung der Überschrift bräche der Export hier mit {@code DateTimeException}
     * ab: {@code Month.of(0)}.
     */
    @Test
    void fuelle_MitGesamt_StehtVorDenMonaten() throws Exception {
        List<String> texte = texte(pdfService.fuelle(quartal(), "de"));

        int gesamt = erstesVorkommen(texte, "Gesamter Zeitraum (01.01.2024 - 29.02.2024)");
        int januar = erstesVorkommen(texte, "Januar 2024");
        int februar = erstesVorkommen(texte, "Februar 2024");

        assertTrue(gesamt >= 0, "Gesamt-Überschrift fehlt: " + texte);
        assertTrue(januar > gesamt, "Das Gesamt steht vor dem ersten Monat");
        assertTrue(februar > januar);
    }

    /** Englisch: dieselbe Übersetzung wie auf der Seite. */
    @Test
    void fuelle_Englisch_UebersetztDieUeberschrift() throws Exception {
        List<String> texte = texte(pdfService.fuelle(quartal(), "en"));

        assertTrue(erstesVorkommen(texte, "Entire period (01.01.2024 - 29.02.2024)") >= 0, texte.toString());
    }

    /** Ohne Gesamt (z. B. ein älterer Cache-Eintrag) bleibt das PDF wie bisher: nur Monate. */
    @Test
    void fuelle_OhneGesamt_NurMonate() throws Exception {
        StatistikDTO statistik = quartal();
        statistik.setGesamt(null);

        List<String> texte = texte(pdfService.fuelle(statistik, "de"));

        assertEquals(-1, erstesVorkommen(texte, "Gesamter Zeitraum"));
        assertTrue(erstesVorkommen(texte, "Januar 2024") >= 0);
    }

    /**
     * <b>Die Monatsliste der Statistik bleibt unverändert.</b>
     *
     * <p>Die Statistik stammt aus dem Cache. Fügte der PDF-Export das Gesamt in ihre Monatsliste
     * ein, stünde es dort dauerhaft — und die Seite zeigte es danach ein zweites Mal.
     */
    @Test
    void baender_VeraendertDieGecachteMonatslisteNicht() throws Exception {
        StatistikDTO statistik = quartal();

        List<MonatsStatistikDTO> baender = StatistikPdfService.baender(statistik);
        pdfService.fuelle(statistik, "de");

        assertEquals(3, baender.size());
        assertEquals(2, statistik.getMonate().size(), "Gesamt darf nicht in die Monatsliste geraten");
        assertEquals(0, baender.get(0).getMonat());
    }

    /** Das komplette PDF entsteht — nicht nur der gefüllte Bericht. */
    @Test
    void generatePdf_MitGesamt_ErzeugtEinPdf() {
        byte[] pdf = pdfService.generatePdf(quartal(), "de");

        assertTrue(pdf.length > 0);
        assertEquals("%PDF", new String(pdf, 0, 4));
    }

    // ==================== Kennzahlen im PDF (FR-12 bis FR-16) ====================

    /** Die fünf Quoten-Kennzahlen stehen je Monat im PDF, mit Bezeichnung und Wert. */
    @Test
    void fuelle_ZeigtDieKennzahlen() throws Exception {
        List<String> texte = texte(pdfService.fuelle(einMonat(Verteilmodus.BILANZ), "de"));

        assertTrue(enthaelt(texte, "Kennzahlen"), texte.toString());
        assertTrue(enthaelt(texte, "Autarkiegrad:"), texte.toString());
        assertTrue(enthaelt(texte, "Eigenverbrauchsquote:"), texte.toString());
        assertTrue(enthaelt(texte, "Netzbezugsquote:"), texte.toString());
        assertTrue(enthaelt(texte, "Einspeisequote:"), texte.toString());
        assertTrue(enthaelt(texte, "ZEV-Eigenverbrauch:"), texte.toString());
        assertTrue(texte.contains("60.0 %"), texte.toString());
        assertTrue(texte.contains("75.0 %"), texte.toString());
        assertTrue(texte.contains("600.000 kWh"), texte.toString());
    }

    /** Mit BEZUG-Einheit erscheinen die gemessenen Zeilen samt Lücken-Hinweis — wie am Bildschirm. */
    @Test
    void fuelle_GemesseneKennzahlenMitLuecken_ZeigtZeilenUndHinweis() throws Exception {
        StatistikDTO statistik = einMonat(Verteilmodus.PRODUCER_MESSUNG);
        monat(statistik).setBilanzKennzahlenVerfuegbar(true);
        monat(statistik).setAutarkiegradGemessen(0.962);
        monat(statistik).setNetzbezugsquoteGemessen(0.038);
        monat(statistik).setBilanzBezugLueckenhaft(true);

        List<String> texte = texte(pdfService.fuelle(statistik, "de"));

        assertTrue(enthaelt(texte, "Autarkiegrad (gemessen)"), texte.toString());
        assertTrue(enthaelt(texte, "Netzbezugsquote (gemessen)"), texte.toString());
        assertTrue(texte.contains("96.2 %"), texte.toString());
        assertTrue(texte.contains("3.8 %"), texte.toString());
        assertTrue(texte.contains("Autarkiegrad (gemessen) (lückenhaft):"), texte.toString());
        assertTrue(texte.contains("Netzbezugsquote (gemessen) (lückenhaft):"), texte.toString());
        assertTrue(enthaelt(texte, "lückenhaft: Messung zu günstig"), texte.toString());
        // Producer-Messung: die gerechneten Zeilen bleiben ungekennzeichnet (FR-12.6)
        assertTrue(texte.contains("Autarkiegrad:"), texte.toString());
        assertTrue(texte.contains("Netzbezugsquote:"), texte.toString());
        assertFalse(enthaelt(texte, "Verteilung zu ungünstig"), texte.toString());
    }

    /**
     * Bilanzmodus mit Lücken: wie am Bildschirm je Richtung ein eigener Hinweis (FR-12.6) — die
     * gemessenen Zeilen „zu günstig“, die gerechneten „zu ungünstig“, beide gekennzeichnet.
     */
    @Test
    void fuelle_GemesseneKennzahlenMitVerteilungsluecken_ZeigtVerteilungsHinweis() throws Exception {
        StatistikDTO statistik = einMonat(Verteilmodus.BILANZ);
        monat(statistik).setBilanzKennzahlenVerfuegbar(true);
        monat(statistik).setAutarkiegradGemessen(0.962);
        monat(statistik).setNetzbezugsquoteGemessen(0.038);
        monat(statistik).setBilanzBezugLueckenhaft(true);
        monat(statistik).setVerteilungLueckenhaft(true);

        List<String> texte = texte(pdfService.fuelle(statistik, "de"));

        assertTrue(texte.contains("Autarkiegrad (lückenhaft):"), texte.toString());
        assertTrue(texte.contains("Netzbezugsquote (lückenhaft):"), texte.toString());
        assertTrue(texte.contains("Autarkiegrad (gemessen) (lückenhaft):"), texte.toString());
        assertTrue(texte.contains("Netzbezugsquote (gemessen) (lückenhaft):"), texte.toString());
        assertTrue(enthaelt(texte, "Autarkiegrad (gemessen), Netzbezugsquote (gemessen) – lückenhaft: Messung zu günstig"),
                texte.toString());
        assertTrue(enthaelt(texte, "Autarkiegrad, Netzbezugsquote – lückenhaft: Verteilung zu ungünstig"),
                texte.toString());
    }

    /** Ohne Lücken: gemessene Zeilen ja, Hinweis nein. */
    @Test
    void fuelle_GemesseneKennzahlenOhneLuecken_KeinHinweis() throws Exception {
        StatistikDTO statistik = einMonat(Verteilmodus.BILANZ);
        monat(statistik).setBilanzKennzahlenVerfuegbar(true);
        monat(statistik).setAutarkiegradGemessen(0.962);
        monat(statistik).setNetzbezugsquoteGemessen(0.038);

        List<String> texte = texte(pdfService.fuelle(statistik, "de"));

        assertTrue(enthaelt(texte, "Autarkiegrad (gemessen):"), texte.toString());
        assertFalse(enthaelt(texte, "lückenhaft"), texte.toString());
    }

    /** Ohne BEZUG-Einheit entfallen die gemessenen Zeilen ganz. */
    @Test
    void fuelle_OhneBezugEinheit_KeineGemessenenZeilen() throws Exception {
        List<String> texte = texte(pdfService.fuelle(einMonat(Verteilmodus.BILANZ), "de"));

        assertFalse(enthaelt(texte, "(gemessen)"), texte.toString());
        assertFalse(enthaelt(texte, "lückenhaft"), texte.toString());
    }

    /** Batteriewerte aus der SPEICHER-Einheit sind gemessen: kein "berechnet"-Hinweis (FR-13a). */
    @Test
    void fuelle_BatterieGemessen_OhneBerechnetHinweis() throws Exception {
        StatistikDTO statistik = einMonat(Verteilmodus.BILANZ);
        monat(statistik).setBatterieKennzahlenVerfuegbar(true);
        monat(statistik).setBatterieGemessen(true);
        monat(statistik).setBatterieGeladen(100.0);
        monat(statistik).setBatterieEntladen(92.0);
        monat(statistik).setBatterieNetto(8.0);
        monat(statistik).setBatterieWirkungsgrad(0.92);

        List<String> texte = texte(pdfService.fuelle(statistik, "de"));

        assertTrue(enthaelt(texte, "Batterie geladen:"), texte.toString());
        assertFalse(enthaelt(texte, "berechnet/geschätzt"), texte.toString());
    }

    /** Batteriewerte aus der Energiebilanz (Residuum) tragen den "berechnet"-Hinweis. */
    @Test
    void fuelle_BatterieAusBilanz_MitBerechnetHinweis() throws Exception {
        StatistikDTO statistik = einMonat(Verteilmodus.BILANZ);
        monat(statistik).setBatterieKennzahlenVerfuegbar(true);
        monat(statistik).setBatterieGemessen(false);
        monat(statistik).setBatterieGeladen(7.0);
        monat(statistik).setBatterieEntladen(3.0);
        monat(statistik).setBatterieNetto(4.0);
        monat(statistik).setBatterieWirkungsgrad(3.0 / 7.0);

        List<String> texte = texte(pdfService.fuelle(statistik, "de"));

        assertTrue(enthaelt(texte, "Batterie geladen:"), texte.toString());
        assertTrue(enthaelt(texte, "berechnet/geschätzt"), texte.toString());
    }

    /** Bilanzmodus: Der Summen-Vergleich entfällt, die Kennzahlen ersetzen ihn. */
    @Test
    void fuelle_Bilanzmodus_OhneSummenVergleich() throws Exception {
        List<String> texte = texte(pdfService.fuelle(einMonat(Verteilmodus.BILANZ), "de"));

        assertFalse(enthaelt(texte, "Summen-Vergleich"), texte.toString());
        assertFalse(enthaelt(texte, "A = B:"), texte.toString());
        assertTrue(enthaelt(texte, "Kennzahlen"), texte.toString());
    }

    /** Producer-Messung: Der Summen-Vergleich bleibt, die Kennzahlen stehen zusätzlich. */
    @Test
    void fuelle_ProducerModus_MitSummenVergleich() throws Exception {
        List<String> texte = texte(pdfService.fuelle(einMonat(Verteilmodus.PRODUCER_MESSUNG), "de"));

        assertTrue(enthaelt(texte, "Summen-Vergleich"), texte.toString());
        assertTrue(enthaelt(texte, "A = B:"), texte.toString());
        assertTrue(enthaelt(texte, "Kennzahlen"), texte.toString());
    }
}
