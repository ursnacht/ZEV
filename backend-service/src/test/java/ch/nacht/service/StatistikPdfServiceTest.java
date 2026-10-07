package ch.nacht.service;

import ch.nacht.dto.MonatsStatistikDTO;
import ch.nacht.dto.StatistikDTO;
import ch.nacht.entity.Translation;
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
        Translation gesamt = new Translation();
        gesamt.setKey("STATISTIK_GESAMTER_ZEITRAUM");
        gesamt.setDeutsch("Gesamter Zeitraum");
        gesamt.setEnglisch("Entire period");
        when(translations.findAll()).thenReturn(List.of(gesamt));

        OrganizationContextService kontext = mock(OrganizationContextService.class);
        when(kontext.getCurrentOrgName()).thenReturn("Test");

        pdfService = new StatistikPdfService(translations, kontext);
        // Aus dem .jrxml kompiliert, NICHT die .jasper-Dateien geladen: Die entstehen erst in der
        // Phase prepare-package, nach den Unit-Tests - ein Test saehe sonst ein veraltetes Template.
        // Genau das ist beim Schreiben dieses Tests passiert: Er lief gegen den alten Stand.
        pdfService.verwendeBerichte(kompiliere("statistik"), kompiliere("einheit-summen"));
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
}
