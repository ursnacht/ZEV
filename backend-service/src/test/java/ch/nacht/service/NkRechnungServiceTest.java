package ch.nacht.service;

import ch.nacht.dto.EinstellungenDTO;
import ch.nacht.dto.NkAbrechnungDetailDTO;
import ch.nacht.dto.NkBerechnungDTO;
import ch.nacht.dto.NkMieterAbrechnungDTO;
import ch.nacht.dto.NkRechnungDTO;
import ch.nacht.dto.NkRechnungDownloadDTO;
import ch.nacht.dto.NkRechnungLaufDTO;
import ch.nacht.dto.NkRechnungZeileDTO;
import ch.nacht.dto.NkZeileDTO;
import ch.nacht.dto.RechnungKonfigurationDTO;
import ch.nacht.entity.Debitorherkunft;
import ch.nacht.entity.FeatureFlag;
import ch.nacht.entity.Mengeneinheit;
import ch.nacht.entity.Mieter;
import ch.nacht.entity.NkAbrechnung;
import ch.nacht.entity.NkPositionsart;
import ch.nacht.exception.FeatureDisabledException;
import ch.nacht.repository.MieterRepository;
import ch.nacht.service.RechnungStorageService.Rechnungsart;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests der Rechnungserstellung aus einer Nebenkostenabrechnung
 * ({@code Specs/Nebenkosten/RechnungenGenerieren.md}, FR-2 bis FR-6).
 *
 * <p>Der Schwerpunkt liegt auf den Zusicherungen, die man nicht sieht: dass <b>nicht neu
 * gerechnet</b> wird, dass ein Guthaben keine Forderung erzeugt, dass die Buchung <b>vor</b> dem
 * Ablegen passiert, und dass ein Fehler bei einem Mieter die uebrigen nicht mitnimmt.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NkRechnungServiceTest {

    private static final Long ORG_ID = 42L;
    private static final Long ABRECHNUNG_ID = 12L;
    private static final LocalDate VON = LocalDate.of(2026, 1, 1);
    private static final LocalDate BIS = LocalDate.of(2026, 12, 31);

    private static final byte[] PDF = new byte[]{37, 80, 68, 70}; // %PDF

    @Mock
    private NkAbrechnungService nkAbrechnungService;

    @Mock
    private NkRechnungPdfService nkRechnungPdfService;

    @Mock
    private RechnungStorageService rechnungStorageService;

    @Mock
    private DebitorService debitorService;

    @Mock
    private EinstellungenService einstellungenService;

    @Mock
    private MieterRepository mieterRepository;

    @Mock
    private FeatureFlagService featureFlagService;

    @Mock
    private OrganizationContextService organizationContextService;

    @Mock
    private HibernateFilterService hibernateFilterService;

    @InjectMocks
    private NkRechnungService service;

    private NkAbrechnung abrechnung;
    private NkMieterAbrechnungDTO mieterA;
    private NkMieterAbrechnungDTO mieterB;

    @BeforeEach
    void setUp() {
        when(organizationContextService.getCurrentOrgId()).thenReturn(ORG_ID);
        when(featureFlagService.isEnabled(ORG_ID, FeatureFlag.NEBENKOSTENABRECHNUNG)).thenReturn(true);
        when(einstellungenService.getEinstellungenOrThrow()).thenReturn(einstellungen());
        when(nkRechnungPdfService.generatePdf(any(), anyString())).thenReturn(PDF);
        when(rechnungStorageService.getFilename(anyString()))
                .thenAnswer(i -> i.getArgument(0, String.class).replace(" ", "_") + ".pdf");

        abrechnung = new NkAbrechnung();
        abrechnung.setId(ABRECHNUNG_ID);
        abrechnung.setBezeichnung("Nebenkosten 2026");
        abrechnung.setDatumVon(VON);
        abrechnung.setDatumBis(BIS);
        abrechnung.setAbgerechnet(true);

        // Nachzahlung: Kostentotal 313.51 - Akonto 600.00 waere negativ, deshalb hier bewusst
        // ein positiver Saldo mit einer Rundungsdifferenz (812.37 -> 812.35).
        mieterA = mieterBlock(45L, "Max Muster", "313.51", "600.00", "812.37");
        mieterB = mieterBlock(46L, "Erika Beispiel", "120.00", "600.00", "-480.00");

        when(mieterRepository.findFirstById(45L)).thenReturn(Optional.of(
                mieter(45L, "Musterstrasse 1", "8000", "Zuerich")));
        when(mieterRepository.findFirstById(46L)).thenReturn(Optional.of(
                mieter(46L, "Beispielweg 2", "3000", "Bern")));
    }

    // ==================== baueRechnungen: Zustand und Flag ====================

    @Test
    void baueRechnungen_NichtAbgerechnet_ThrowsIllegalState() {
        abrechnung.setAbgerechnet(false);
        when(nkAbrechnungService.getAbrechnungDetail(ABRECHNUNG_ID)).thenReturn(Optional.of(detail()));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.baueRechnungen(ABRECHNUNG_ID));

        // Der GlobalExceptionHandler bildet IllegalStateException auf 400 ab und gibt die
        // Meldung als Uebersetzungsschluessel an das Frontend weiter.
        assertEquals("NK_FEHLER_NICHT_ABGERECHNET", ex.getMessage());
    }

    @Test
    void baueRechnungen_AbrechnungFehlt_ThrowsNoSuchElement() {
        when(nkAbrechnungService.getAbrechnungDetail(ABRECHNUNG_ID)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> service.baueRechnungen(ABRECHNUNG_ID));
    }

    /**
     * Der Flag wird <b>selbst</b> geprueft, nicht als Nebenwirkung von
     * {@code NkAbrechnungService}: Der Aufruf dorthin darf gar nicht stattfinden.
     */
    @Test
    void baueRechnungen_FlagAus_ThrowsFeatureDisabled() {
        when(featureFlagService.isEnabled(ORG_ID, FeatureFlag.NEBENKOSTENABRECHNUNG)).thenReturn(false);

        assertThrows(FeatureDisabledException.class, () -> service.baueRechnungen(ABRECHNUNG_ID));

        verify(nkAbrechnungService, never()).getAbrechnungDetail(anyLong());
    }

    @Test
    void baueRechnungen_AktiviertDenOrgFilter() {
        when(nkAbrechnungService.getAbrechnungDetail(ABRECHNUNG_ID)).thenReturn(Optional.of(detail()));

        service.baueRechnungen(ABRECHNUNG_ID);

        verify(hibernateFilterService).enableOrgFilter();
    }

    // ==================== baueRechnungen: Abbildung ====================

    @Test
    void baueRechnungen_JeMieterEineRechnung() {
        when(nkAbrechnungService.getAbrechnungDetail(ABRECHNUNG_ID)).thenReturn(Optional.of(detail()));

        List<NkRechnungDTO> rechnungen = service.baueRechnungen(ABRECHNUNG_ID);

        assertEquals(2, rechnungen.size());
        assertEquals(45L, rechnungen.get(0).getMieterId());
        assertEquals("Erika Beispiel", rechnungen.get(1).getMieterName());
    }

    @Test
    void baueRechnungen_UebernimmtKopfdatenDerAbrechnung() {
        when(nkAbrechnungService.getAbrechnungDetail(ABRECHNUNG_ID)).thenReturn(Optional.of(detail()));

        NkRechnungDTO rechnung = service.baueRechnungen(ABRECHNUNG_ID).get(0);

        assertEquals(ABRECHNUNG_ID, rechnung.getAbrechnungId());
        assertEquals("Nebenkosten 2026", rechnung.getBezeichnung());
        assertEquals(VON, rechnung.getVon());
        assertEquals(BIS, rechnung.getBis());
        assertNotNull(rechnung.getErstellungsdatum());
    }

    /**
     * <b>Es wird nicht neu gerechnet.</b> Kostentotal, Akonto und Saldo stehen unveraendert so in
     * der Rechnung, wie die Abrechnung sie liefert — dieselbe Quelle, die die Maske anzeigt.
     */
    @Test
    void baueRechnungen_UebernimmtBetraegeUnveraendert() {
        when(nkAbrechnungService.getAbrechnungDetail(ABRECHNUNG_ID)).thenReturn(Optional.of(detail()));

        NkRechnungDTO rechnung = service.baueRechnungen(ABRECHNUNG_ID).get(0);

        assertEquals(0, rechnung.getKostentotal().compareTo(new BigDecimal("313.51")));
        assertEquals(0, rechnung.getAkontoTotal().compareTo(new BigDecimal("600.00")));
        assertEquals(0, rechnung.getSaldo().compareTo(new BigDecimal("812.37")));
    }

    /**
     * Gerundet wird <b>nur der Endbetrag</b> auf 5 Rappen, und die Differenz wird ausgewiesen:
     * 812.37 → 812.35, Rundung −0.02. Der QR-Zahlteil verlangt einen zahlbaren Betrag.
     */
    @Test
    void baueRechnungen_RundetEndbetragAuf5Rappen() {
        when(nkAbrechnungService.getAbrechnungDetail(ABRECHNUNG_ID)).thenReturn(Optional.of(detail()));

        NkRechnungDTO rechnung = service.baueRechnungen(ABRECHNUNG_ID).get(0);

        assertEquals(0, rechnung.getEndBetrag().compareTo(new BigDecimal("812.35")));
        assertEquals(0, rechnung.getRundung().compareTo(new BigDecimal("-0.02")));
        assertEquals(0, rechnung.getEndBetrag().remainder(new BigDecimal("0.05")).compareTo(BigDecimal.ZERO),
                "Der zahlbare Betrag muss ein Vielfaches von 5 Rappen sein");
    }

    @Test
    void baueRechnungen_SaldoBereitsAuf5Rappen_KeineRundung() {
        mieterA.setSaldo(new BigDecimal("100.00"));
        when(nkAbrechnungService.getAbrechnungDetail(ABRECHNUNG_ID)).thenReturn(Optional.of(detail()));

        NkRechnungDTO rechnung = service.baueRechnungen(ABRECHNUNG_ID).get(0);

        assertEquals(0, rechnung.getRundung().signum());
    }

    @Test
    void baueRechnungen_ZeilenMitAllenFeldern() {
        when(nkAbrechnungService.getAbrechnungDetail(ABRECHNUNG_ID)).thenReturn(Optional.of(detail()));

        List<NkRechnungZeileDTO> zeilen = service.baueRechnungen(ABRECHNUNG_ID).get(0).getZeilen();

        assertEquals(2, zeilen.size());
        assertEquals("Wasser", zeilen.get(0).getBezeichnung());
        assertEquals("M3", zeilen.get(0).getMengeneinheit(), "Uebersetzungsschluessel, nicht der Text");
        assertEquals(0, zeilen.get(0).getMenge().compareTo(new BigDecimal("34.5")));
        assertEquals(0, zeilen.get(0).getBetragProEinheit().compareTo(new BigDecimal("1.85")));
        assertEquals(0, zeilen.get(0).getBetrag().compareTo(new BigDecimal("63.83")));
    }

    /**
     * Ein {@code ZUSCHLAG} traegt an der Position keine Mengeneinheit - der CHECK-Constraint
     * verbietet sie. Auf der Rechnung steht in der Spalte „Preis" aber sein Zwischentotal, und
     * das ist ein Frankenbetrag: Die Einheit ist deshalb {@code CHF} („Fr.").
     *
     * <p>Dieser Test behauptete zuvor das Gegenteil ({@code null}, „sonst stuende auf der Rechnung
     * eine Einheit, die nie erfasst wurde"). Das Argument traegt nicht mehr, seit die Spalte
     * „Preis" bei diesen Arten gefuellt ist: Die Einheit beschreibt jetzt eine Groesse, die
     * tatsaechlich dasteht.
     */
    @Test
    void baueRechnungen_ZuschlagOhneEinheit_BekommtCHF() {
        when(nkAbrechnungService.getAbrechnungDetail(ABRECHNUNG_ID)).thenReturn(Optional.of(detail()));

        NkRechnungZeileDTO zuschlag = service.baueRechnungen(ABRECHNUNG_ID).get(0).getZeilen().get(1);

        assertEquals("CHF", zuschlag.getMengeneinheit(), "Uebersetzungsschluessel, nicht der Text");
        assertEquals(0, zuschlag.getProzentsatz().compareTo(new BigDecimal("3.00")));
    }

    @Test
    void baueRechnungen_AnteilOhneEinheit_BekommtCHF() {
        NkZeileDTO anteil = new NkZeileDTO();
        anteil.setArt(NkPositionsart.ANTEIL);
        anteil.setReihenfolge(1);
        anteil.setBezeichnung("Heizkosten");
        anteil.setProzentsatz(new BigDecimal("60.000"));
        anteil.setBezugsbetrag(new BigDecimal("2400.00"));
        anteil.setBetrag(new BigDecimal("1440.00"));

        NkAbrechnungDetailDTO detail = detail();
        detail.getBerechnung().getMieter().get(0).setZeilen(List.of(anteil));
        when(nkAbrechnungService.getAbrechnungDetail(ABRECHNUNG_ID)).thenReturn(Optional.of(detail));

        NkRechnungZeileDTO zeile = service.baueRechnungen(ABRECHNUNG_ID).get(0).getZeilen().get(0);

        assertEquals("CHF", zeile.getMengeneinheit());
    }

    /**
     * Eine Umlage fuehrt ihre Mengeneinheit selbst - sie wird NICHT durch {@code CHF} ersetzt.
     * Sonst stuende bei einer Wasserumlage „Fr." statt „m³".
     */
    @Test
    void baueRechnungen_UmlageBehaeltIhreEinheit() {
        NkZeileDTO umlage = new NkZeileDTO();
        umlage.setArt(NkPositionsart.UMLAGE);
        umlage.setReihenfolge(1);
        umlage.setBezeichnung("Wasser");
        umlage.setEinheit(Mengeneinheit.M3);
        umlage.setMenge(new BigDecimal("12.000"));
        umlage.setBezugsbetrag(new BigDecimal("1000.00"));
        umlage.setProzentsatz(new BigDecimal("50.000"));
        umlage.setBetrag(new BigDecimal("500.00"));

        NkAbrechnungDetailDTO detail = detail();
        detail.getBerechnung().getMieter().get(0).setZeilen(List.of(umlage));
        when(nkAbrechnungService.getAbrechnungDetail(ABRECHNUNG_ID)).thenReturn(Optional.of(detail));

        NkRechnungZeileDTO zeile = service.baueRechnungen(ABRECHNUNG_ID).get(0).getZeilen().get(0);

        assertEquals("M3", zeile.getMengeneinheit());
    }

    /**
     * <b>Jede</b> Mengeneinheit hat einen Uebersetzungsschluessel — auch eine kuenftig ergaenzte.
     *
     * <p>Der Waechter statt eines Tests je Wert: {@code MENGENEINHEIT_KEYS} ist eine
     * {@code Map.of} ohne Rueckfall. Fehlt ein Eintrag, liefert {@code get} still {@code null},
     * die Einheitenspalte der Rechnung bleibt leer und der Betrag daneben steht ohne Bezugsgroesse
     * da — ein Fehler, den niemand meldet. Genau das drohte beim Ergaenzen von {@code M2}.
     *
     * <p>Geprueft wird, DASS ein Schluessel da ist, nicht WELCHER: {@code MONAT} traegt
     * {@code MONATE}, weil die Rechnung die Mehrzahl zeigt („12 Monate"). Eine Gleichsetzung mit
     * dem Enum-Namen schriebe diese Abweichung als Regel fest, die sie nicht ist.
     */
    @Test
    void baueRechnungen_JedeMengeneinheit_HatEinenSchluessel() {
        for (Mengeneinheit einheit : Mengeneinheit.values()) {
            NkZeileDTO zeile = new NkZeileDTO();
            zeile.setArt(NkPositionsart.UMLAGE);
            zeile.setReihenfolge(1);
            zeile.setBezeichnung("Position " + einheit);
            zeile.setEinheit(einheit);
            zeile.setBetrag(new BigDecimal("10.00"));

            NkAbrechnungDetailDTO detail = detail();
            detail.getBerechnung().getMieter().get(0).setZeilen(List.of(zeile));
            when(nkAbrechnungService.getAbrechnungDetail(ABRECHNUNG_ID))
                    .thenReturn(Optional.of(detail));

            String schluessel = service.baueRechnungen(ABRECHNUNG_ID)
                    .get(0).getZeilen().get(0).getMengeneinheit();
            assertNotNull(schluessel, "Kein Uebersetzungsschluessel fuer " + einheit);
            assertFalse(schluessel.isBlank(), "Leerer Uebersetzungsschluessel fuer " + einheit);
        }
    }

    /** Die Flaecheneinheit der Kehrichtgrundgebuehr erscheint als eigener Schluessel. */
    @Test
    void baueRechnungen_UmlageNachFlaeche_TraegtM2() {
        NkZeileDTO umlage = new NkZeileDTO();
        umlage.setArt(NkPositionsart.UMLAGE);
        umlage.setReihenfolge(1);
        umlage.setBezeichnung("Kehrichtgrundgebuehr");
        umlage.setEinheit(Mengeneinheit.M2);
        umlage.setMenge(new BigDecimal("84.500"));
        umlage.setBetrag(new BigDecimal("120.00"));

        NkAbrechnungDetailDTO detail = detail();
        detail.getBerechnung().getMieter().get(0).setZeilen(List.of(umlage));
        when(nkAbrechnungService.getAbrechnungDetail(ABRECHNUNG_ID)).thenReturn(Optional.of(detail));

        NkRechnungZeileDTO zeile = service.baueRechnungen(ABRECHNUNG_ID).get(0).getZeilen().get(0);

        assertEquals("M2", zeile.getMengeneinheit());
        assertEquals(new BigDecimal("84.500"), zeile.getMenge());
    }

    /**
     * Die Bezugsgroesse gehoert auf die Rechnung: Ohne sie stand in der Spalte „Preis" bei einer
     * Umlage oder einem Zuschlag nichts, und der Betrag war fuer den Mieter nicht nachvollziehbar.
     */
    @Test
    void baueRechnungen_ReichtDenBezugsbetragDurch() {
        when(nkAbrechnungService.getAbrechnungDetail(ABRECHNUNG_ID)).thenReturn(Optional.of(detail()));

        List<NkRechnungZeileDTO> zeilen = service.baueRechnungen(ABRECHNUNG_ID).get(0).getZeilen();

        // Verbrauchszeile: dort ist der Preis je Einheit die Bezugsgroesse, das Feld bleibt frei.
        assertNull(zeilen.get(0).getBezugsbetrag());
        assertEquals(0, zeilen.get(1).getBezugsbetrag().compareTo(new BigDecimal("63.83")));
    }

    @Test
    void baueRechnungen_UebernimmtAkontoKorrektur() {
        mieterA.setAkontoKorrektur(new BigDecimal("-50.00"));
        when(nkAbrechnungService.getAbrechnungDetail(ABRECHNUNG_ID)).thenReturn(Optional.of(detail()));

        NkRechnungDTO rechnung = service.baueRechnungen(ABRECHNUNG_ID).get(0);

        assertEquals(0, rechnung.getAkontoKorrektur().compareTo(new BigDecimal("-50.00")));
    }

    /**
     * Die Korrektur ist <b>nie {@code null}</b>: Das Template fragt sie nach ihrem Vorzeichen, um
     * sie nur bei einer tatsaechlichen Korrektur auszuweisen. Ein {@code null} liefe dort in eine
     * NullPointerException — und die faellt erst beim Fuellen auf, nicht beim Kompilieren.
     */
    @Test
    void baueRechnungen_KorrekturNull_WirdZuNull_Sicher() {
        mieterA.setAkontoKorrektur(null);
        when(nkAbrechnungService.getAbrechnungDetail(ABRECHNUNG_ID)).thenReturn(Optional.of(detail()));

        NkRechnungDTO rechnung = service.baueRechnungen(ABRECHNUNG_ID).get(0);

        assertNotNull(rechnung.getAkontoKorrektur());
        assertEquals(0, rechnung.getAkontoKorrektur().signum());
    }

    @Test
    void baueRechnungen_UebernimmtAdresseDesMieters() {
        when(nkAbrechnungService.getAbrechnungDetail(ABRECHNUNG_ID)).thenReturn(Optional.of(detail()));

        NkRechnungDTO rechnung = service.baueRechnungen(ABRECHNUNG_ID).get(0);

        assertEquals("Musterstrasse 1", rechnung.getMieterStrasse());
        assertEquals("8000 Zuerich", rechnung.getMieterPlzOrt());
    }

    /**
     * Ohne Adresse entsteht die Rechnung trotzdem — ohne Zahlteil. Ein Abbruch waere die
     * schlechtere Wahl: Der Beleg ist auch ohne Einzahlungsschein gueltig.
     */
    @Test
    void baueRechnungen_MieterOhneAdresse_RechnungOhnePlzOrt() {
        when(mieterRepository.findFirstById(45L)).thenReturn(Optional.of(mieter(45L, null, null, null)));
        when(nkAbrechnungService.getAbrechnungDetail(ABRECHNUNG_ID)).thenReturn(Optional.of(detail()));

        NkRechnungDTO rechnung = service.baueRechnungen(ABRECHNUNG_ID).get(0);

        assertNull(rechnung.getMieterStrasse());
        assertNull(rechnung.getMieterPlzOrt());
    }

    @Test
    void baueRechnungen_MieterNichtGefunden_RechnungOhneAdresse() {
        when(mieterRepository.findFirstById(45L)).thenReturn(Optional.empty());
        when(nkAbrechnungService.getAbrechnungDetail(ABRECHNUNG_ID)).thenReturn(Optional.of(detail()));

        NkRechnungDTO rechnung = service.baueRechnungen(ABRECHNUNG_ID).get(0);

        assertNull(rechnung.getMieterPlzOrt());
        assertEquals("Max Muster", rechnung.getMieterName(), "Der Name kommt aus der Abrechnung");
    }

    @Test
    void baueRechnungen_UebernimmtStellerUndIban() {
        when(nkAbrechnungService.getAbrechnungDetail(ABRECHNUNG_ID)).thenReturn(Optional.of(detail()));

        NkRechnungDTO rechnung = service.baueRechnungen(ABRECHNUNG_ID).get(0);

        assertEquals("ZEV Musterhaus", rechnung.getStellerName());
        assertEquals("3000 Bern", rechnung.getStellerPlzOrt());
        assertEquals("CH93 0076 2011 6238 5295 7", rechnung.getIban());
        assertEquals("30 Tage", rechnung.getZahlungsfrist());
    }

    @Test
    void baueRechnungen_AbrechnungOhneMieter_LeereListe() {
        NkAbrechnungDetailDTO detail = detail();
        detail.getBerechnung().setMieter(List.of());
        when(nkAbrechnungService.getAbrechnungDetail(ABRECHNUNG_ID)).thenReturn(Optional.of(detail));

        assertTrue(service.baueRechnungen(ABRECHNUNG_ID).isEmpty());
    }

    /** Eine Abrechnung ohne Berechnungsblock darf nicht in eine NullPointerException laufen. */
    @Test
    void baueRechnungen_OhneBerechnung_LeereListe() {
        NkAbrechnungDetailDTO detail = detail();
        detail.setBerechnung(null);
        when(nkAbrechnungService.getAbrechnungDetail(ABRECHNUNG_ID)).thenReturn(Optional.of(detail));

        assertTrue(service.baueRechnungen(ABRECHNUNG_ID).isEmpty());
    }

    // ==================== Zeitraum auf der Rechnung (FR-8) ====================

    /**
     * Zieht ein Mieter mitten im Abrechnungszeitraum ein, nennt seine Rechnung den
     * <b>Mietbeginn</b> — nicht den Beginn der Abrechnung. Seine Betraege sind ohnehin nur fuer
     * die Miettage gerechnet; ein Zeitraum, in dem er noch nicht wohnte, waere ein Widerspruch
     * auf demselben Blatt.
     */
    @Test
    void baueRechnungen_MietbeginnNachAbrechnungsbeginn_ZeitraumAbMietbeginn() {
        LocalDate mietbeginn = LocalDate.of(2026, 5, 1);
        when(mieterRepository.findFirstById(45L))
                .thenReturn(Optional.of(mieter(45L, mietbeginn, null)));
        when(nkAbrechnungService.getAbrechnungDetail(ABRECHNUNG_ID)).thenReturn(Optional.of(detail()));

        NkRechnungDTO rechnung = service.baueRechnungen(ABRECHNUNG_ID).get(0);

        assertEquals(mietbeginn, rechnung.getZeitraumVon());
        assertEquals(BIS, rechnung.getZeitraumBis(), "Das Mietende fehlt - laeuft weiter");
    }

    /** Auszug mitten im Zeitraum: Die Rechnung endet am Mietende. */
    @Test
    void baueRechnungen_MietendeVorAbrechnungsende_ZeitraumBisMietende() {
        LocalDate mietende = LocalDate.of(2026, 9, 30);
        when(mieterRepository.findFirstById(45L))
                .thenReturn(Optional.of(mieter(45L, LocalDate.of(2025, 1, 1), mietende)));
        when(nkAbrechnungService.getAbrechnungDetail(ABRECHNUNG_ID)).thenReturn(Optional.of(detail()));

        NkRechnungDTO rechnung = service.baueRechnungen(ABRECHNUNG_ID).get(0);

        assertEquals(VON, rechnung.getZeitraumVon(), "Der Mietbeginn liegt vor der Abrechnung");
        assertEquals(mietende, rechnung.getZeitraumBis());
    }

    /**
     * Umschliesst das Mietverhaeltnis den ganzen Zeitraum, bleibt es beim Zeitraum der
     * Abrechnung — der haeufigste Fall, und er soll unveraendert aussehen.
     */
    @Test
    void baueRechnungen_MietverhaeltnisUmschliesstZeitraum_ZeitraumDerAbrechnung() {
        when(mieterRepository.findFirstById(45L)).thenReturn(Optional.of(
                mieter(45L, LocalDate.of(2020, 1, 1), LocalDate.of(2030, 12, 31))));
        when(nkAbrechnungService.getAbrechnungDetail(ABRECHNUNG_ID)).thenReturn(Optional.of(detail()));

        NkRechnungDTO rechnung = service.baueRechnungen(ABRECHNUNG_ID).get(0);

        assertEquals(VON, rechnung.getZeitraumVon());
        assertEquals(BIS, rechnung.getZeitraumBis());
    }

    /** Ohne Mieter im Stamm bleibt der Zeitraum der Abrechnung stehen. */
    @Test
    void baueRechnungen_MieterNichtGefunden_ZeitraumDerAbrechnung() {
        when(mieterRepository.findFirstById(45L)).thenReturn(Optional.empty());
        when(nkAbrechnungService.getAbrechnungDetail(ABRECHNUNG_ID)).thenReturn(Optional.of(detail()));

        NkRechnungDTO rechnung = service.baueRechnungen(ABRECHNUNG_ID).get(0);

        assertEquals(VON, rechnung.getZeitraumVon());
        assertEquals(BIS, rechnung.getZeitraumBis());
    }

    /**
     * Der beschnittene Zeitraum steht <b>nur</b> auf dem Papier: {@code von}/{@code bis} tragen
     * weiter den Zeitraum der Abrechnung.
     *
     * <p>Sie werden anderswo gebraucht — die gebuchte Forderung und die Kopfzeile des
     * Rechnungslaufs beziehen sich auf den Lauf als Ganzes. Wuerden sie je Mieter beschnitten,
     * nennte die Kopfzeile den Zeitraum des zuletzt verarbeiteten Mieters.
     */
    @Test
    void baueRechnungen_KurzesMietverhaeltnis_VonUndBisBleibenDerAbrechnungszeitraum() {
        when(mieterRepository.findFirstById(45L)).thenReturn(Optional.of(
                mieter(45L, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 9, 30))));
        when(nkAbrechnungService.getAbrechnungDetail(ABRECHNUNG_ID)).thenReturn(Optional.of(detail()));

        NkRechnungDTO rechnung = service.baueRechnungen(ABRECHNUNG_ID).get(0);

        assertEquals(VON, rechnung.getVon());
        assertEquals(BIS, rechnung.getBis());
        assertEquals(LocalDate.of(2026, 5, 1), rechnung.getZeitraumVon());
        assertEquals(LocalDate.of(2026, 9, 30), rechnung.getZeitraumBis());
    }

    /** Und die Forderung wird weiter auf den Zeitraum der Abrechnung gebucht. */
    @Test
    void erzeugeRechnungen_KurzesMietverhaeltnis_ForderungMitAbrechnungszeitraum() {
        when(mieterRepository.findFirstById(45L)).thenReturn(Optional.of(
                mieter(45L, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 9, 30))));
        when(nkAbrechnungService.getAbrechnungDetail(ABRECHNUNG_ID)).thenReturn(Optional.of(detail()));

        service.erzeugeRechnungen(ABRECHNUNG_ID, "de");

        verify(debitorService).upsertFromRechnung(eq(45L), any(),
                eq(VON), eq(BIS), eq(Debitorherkunft.NK));
    }

    // ==================== erzeugeRechnungen: Buchung ====================

    @Test
    void erzeugeRechnungen_Nachzahlung_BuchtForderungMitHerkunftNK() {
        when(nkAbrechnungService.getAbrechnungDetail(ABRECHNUNG_ID)).thenReturn(Optional.of(detail()));

        service.erzeugeRechnungen(ABRECHNUNG_ID, "de");

        verify(debitorService).upsertFromRechnung(eq(45L), eq(new BigDecimal("812.35")),
                eq(VON), eq(BIS), eq(Debitorherkunft.NK));
    }

    /**
     * Ein Guthaben erzeugt ein PDF, aber <b>keine</b> Forderung: {@code debitor.betrag} traegt
     * {@code CHECK (betrag > 0)}.
     */
    @Test
    void erzeugeRechnungen_Guthaben_KeineForderungAberPdf() {
        when(nkAbrechnungService.getAbrechnungDetail(ABRECHNUNG_ID)).thenReturn(Optional.of(detail()));

        NkRechnungLaufDTO lauf = service.erzeugeRechnungen(ABRECHNUNG_ID, "de");

        verify(debitorService, never()).upsertFromRechnung(eq(46L), any(), any(), any(), any());
        assertFalse(lauf.getRechnungen().get(1).isForderungGebucht());
        assertNotNull(lauf.getRechnungen().get(1).getFilename(), "Das PDF entsteht trotzdem");
    }

    @Test
    void erzeugeRechnungen_SaldoGenauNull_KeineForderung() {
        mieterA.setSaldo(BigDecimal.ZERO);
        when(nkAbrechnungService.getAbrechnungDetail(ABRECHNUNG_ID)).thenReturn(Optional.of(detail()));

        NkRechnungLaufDTO lauf = service.erzeugeRechnungen(ABRECHNUNG_ID, "de");

        verify(debitorService, never()).upsertFromRechnung(eq(45L), any(), any(), any(), any());
        assertEquals(0, lauf.getAnzahlForderungen());
    }

    /**
     * <b>Erst buchen, dann ablegen</b> — dieselbe Reihenfolge wie im ZEV-Pfad. Scheitert die
     * Buchung, entsteht kein Beleg.
     */
    @Test
    void erzeugeRechnungen_BuchtVorDemAblegen() {
        when(nkAbrechnungService.getAbrechnungDetail(ABRECHNUNG_ID)).thenReturn(Optional.of(detail()));

        service.erzeugeRechnungen(ABRECHNUNG_ID, "de");

        InOrder reihenfolge = inOrder(debitorService, rechnungStorageService);
        reihenfolge.verify(debitorService).upsertFromRechnung(eq(45L), any(), any(), any(), any());
        reihenfolge.verify(rechnungStorageService).store(eq(Rechnungsart.NK), anyString(), any(), anyString());
    }

    @Test
    void erzeugeRechnungen_LegtPdfUnterArtNKAb() {
        when(nkAbrechnungService.getAbrechnungDetail(ABRECHNUNG_ID)).thenReturn(Optional.of(detail()));

        service.erzeugeRechnungen(ABRECHNUNG_ID, "de");

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(rechnungStorageService, org.mockito.Mockito.times(2))
                .store(eq(Rechnungsart.NK), key.capture(), eq(PDF), anyString());
        assertEquals("12_45", key.getAllValues().get(0), "Schluessel aus Abrechnung und Mieter");
        assertEquals("12_46", key.getAllValues().get(1));
    }

    /** Der Lauf raeumt die Ablage <b>nicht</b> auf — sonst nähme er die ZEV-Downloads mit. */
    @Test
    void erzeugeRechnungen_RaeumtNichtAuf() {
        when(nkAbrechnungService.getAbrechnungDetail(ABRECHNUNG_ID)).thenReturn(Optional.of(detail()));

        service.erzeugeRechnungen(ABRECHNUNG_ID, "de");

        verify(rechnungStorageService, never()).clearArt(any());
    }

    // ==================== erzeugeRechnungen: Ergebnis ====================

    @Test
    void erzeugeRechnungen_ErgebnisTraegtKopfUndKennzahlen() {
        when(nkAbrechnungService.getAbrechnungDetail(ABRECHNUNG_ID)).thenReturn(Optional.of(detail()));

        NkRechnungLaufDTO lauf = service.erzeugeRechnungen(ABRECHNUNG_ID, "de");

        assertEquals(ABRECHNUNG_ID, lauf.getAbrechnungId());
        assertEquals("Nebenkosten 2026", lauf.getBezeichnung());
        assertEquals(VON, lauf.getVon());
        assertEquals(BIS, lauf.getBis());
        assertEquals(2, lauf.getAnzahlRechnungen());
        assertEquals(1, lauf.getAnzahlForderungen(), "Nur die Nachzahlung wird gebucht");
        assertEquals(0, lauf.getSummeForderungen().compareTo(new BigDecimal("812.35")));
    }

    /**
     * Die Zeile traegt den <b>gerundeten</b> Betrag — denselben Wert wie PDF und Debitor. Stuende
     * dort der ungerundete Saldo, wichen Bildschirm und Beleg um zwei Rappen ab.
     */
    @Test
    void erzeugeRechnungen_ErgebniszeileTraegtGerundetenBetrag() {
        when(nkAbrechnungService.getAbrechnungDetail(ABRECHNUNG_ID)).thenReturn(Optional.of(detail()));

        NkRechnungLaufDTO lauf = service.erzeugeRechnungen(ABRECHNUNG_ID, "de");

        assertEquals(0, lauf.getRechnungen().get(0).getSaldo().compareTo(new BigDecimal("812.35")));
    }

    @Test
    void erzeugeRechnungen_DateinameIstLesbar() {
        when(nkAbrechnungService.getAbrechnungDetail(ABRECHNUNG_ID)).thenReturn(Optional.of(detail()));

        NkRechnungLaufDTO lauf = service.erzeugeRechnungen(ABRECHNUNG_ID, "de");

        // Nicht "12_45.pdf": Der Schluessel besteht aus zwei IDs und ist im Download-Ordner
        // nicht wiederzuerkennen.
        assertEquals("Nebenkosten_Nebenkosten_2026_Max_Muster.pdf",
                lauf.getRechnungen().get(0).getFilename());
    }

    @Test
    void erzeugeRechnungen_OhneSprache_NutztDeutsch() {
        when(nkAbrechnungService.getAbrechnungDetail(ABRECHNUNG_ID)).thenReturn(Optional.of(detail()));

        service.erzeugeRechnungen(ABRECHNUNG_ID, null);

        // Zwei Mieter, zwei PDF - beide in der Ersatzsprache.
        verify(nkRechnungPdfService, org.mockito.Mockito.times(2)).generatePdf(any(), eq("de"));
    }

    @Test
    void erzeugeRechnungen_FlagAus_ThrowsFeatureDisabled() {
        when(featureFlagService.isEnabled(ORG_ID, FeatureFlag.NEBENKOSTENABRECHNUNG)).thenReturn(false);

        assertThrows(FeatureDisabledException.class,
                () -> service.erzeugeRechnungen(ABRECHNUNG_ID, "de"));

        verify(debitorService, never()).upsertFromRechnung(any(), any(), any(), any(), any());
    }

    // ==================== erzeugeRechnungen: Einzelfehler ====================

    /**
     * Ein Fehler bei einem Mieter nimmt die uebrigen <b>nicht</b> mit.
     *
     * <p>Deshalb laeuft der Lauf auch nicht in einer gemeinsamen Transaktion: Sie waere nach der
     * ersten gescheiterten Buchung rollback-only, und der Commit am Ende verwuerfe auch die
     * erfolgreichen Rechnungen.
     */
    @Test
    void erzeugeRechnungen_FehlerBeiEinemMieter_UebrigeEntstehen() {
        when(nkAbrechnungService.getAbrechnungDetail(ABRECHNUNG_ID)).thenReturn(Optional.of(detail()));
        when(nkRechnungPdfService.generatePdf(any(), anyString()))
                .thenThrow(new RuntimeException("PDF kaputt"))
                .thenReturn(PDF);

        NkRechnungLaufDTO lauf = service.erzeugeRechnungen(ABRECHNUNG_ID, "de");

        assertEquals("NK_FEHLER_RECHNUNG_MIETER", lauf.getRechnungen().get(0).getFehler());
        assertNull(lauf.getRechnungen().get(0).getFilename(), "Ohne PDF kein Download");
        assertNull(lauf.getRechnungen().get(1).getFehler(), "Der zweite Mieter ist unberuehrt");
        assertEquals(1, lauf.getAnzahlRechnungen(), "Nur die erfolgreiche zaehlt");
        assertEquals(2, lauf.getRechnungen().size(), "Die Fehlerzeile bleibt sichtbar");
    }

    /**
     * Scheitert das Ablegen <b>nach</b> der Buchung, bleibt die Forderung stehen und die Zeile
     * traegt den Fehler: Die Rechnung ist reproduzierbar, eine Forderung ohne Beleg also
     * behebbar. Ein Beleg ohne Forderung fehlte dagegen in der Debitorenkontrolle.
     */
    @Test
    void erzeugeRechnungen_AblegenScheitert_ForderungBleibt() {
        when(nkAbrechnungService.getAbrechnungDetail(ABRECHNUNG_ID)).thenReturn(Optional.of(detail()));
        org.mockito.Mockito.doThrow(new RuntimeException("Ablage kaputt"))
                .when(rechnungStorageService).store(eq(Rechnungsart.NK), eq("12_45"), any(), anyString());

        NkRechnungLaufDTO lauf = service.erzeugeRechnungen(ABRECHNUNG_ID, "de");

        verify(debitorService).upsertFromRechnung(eq(45L), any(), any(), any(), eq(Debitorherkunft.NK));
        assertEquals("NK_FEHLER_RECHNUNG_MIETER", lauf.getRechnungen().get(0).getFehler());
    }

    @Test
    void erzeugeRechnungen_BuchungScheitert_KeinPdfAbgelegt() {
        when(nkAbrechnungService.getAbrechnungDetail(ABRECHNUNG_ID)).thenReturn(Optional.of(detail()));
        org.mockito.Mockito.doThrow(new RuntimeException("Upsert kaputt"))
                .when(debitorService).upsertFromRechnung(eq(45L), any(), any(), any(), any());

        NkRechnungLaufDTO lauf = service.erzeugeRechnungen(ABRECHNUNG_ID, "de");

        verify(rechnungStorageService, never()).store(eq(Rechnungsart.NK), eq("12_45"), any(), anyString());
        assertEquals("NK_FEHLER_RECHNUNG_MIETER", lauf.getRechnungen().get(0).getFehler());
        assertNull(lauf.getRechnungen().get(1).getFehler());
    }

    // ==================== ladePdf ====================

    @Test
    void ladePdf_Vorhanden_LiefertPdfUndDateiname() {
        when(rechnungStorageService.get(Rechnungsart.NK, "12_45")).thenReturn(Optional.of(PDF));
        when(rechnungStorageService.getFilename(Rechnungsart.NK, "12_45")).thenReturn("Nebenkosten_Max.pdf");

        Optional<NkRechnungDownloadDTO> download = service.ladePdf(ABRECHNUNG_ID, 45L);

        assertTrue(download.isPresent());
        assertEquals("Nebenkosten_Max.pdf", download.get().filename());
        assertEquals(PDF, download.get().pdf());
    }

    @Test
    void ladePdf_Abgelaufen_ReturnsEmpty() {
        when(rechnungStorageService.get(Rechnungsart.NK, "12_45")).thenReturn(Optional.empty());

        assertTrue(service.ladePdf(ABRECHNUNG_ID, 45L).isEmpty());
    }

    @Test
    void ladePdf_FlagAus_ThrowsFeatureDisabled() {
        when(featureFlagService.isEnabled(ORG_ID, FeatureFlag.NEBENKOSTENABRECHNUNG)).thenReturn(false);

        assertThrows(FeatureDisabledException.class, () -> service.ladePdf(ABRECHNUNG_ID, 45L));

        verify(rechnungStorageService, never()).get(any(), anyString());
    }

    // ==================== Helpers ====================

    private NkAbrechnungDetailDTO detail() {
        NkBerechnungDTO berechnung = new NkBerechnungDTO();
        berechnung.setMieter(List.of(mieterA, mieterB));

        NkAbrechnungDetailDTO detail = new NkAbrechnungDetailDTO();
        detail.setAbrechnung(abrechnung);
        detail.setBerechnung(berechnung);
        return detail;
    }

    /** Mieterblock mit zwei Zeilen: eine mit Mengeneinheit, eine mit Prozentsatz. */
    private NkMieterAbrechnungDTO mieterBlock(Long mieterId, String name,
                                              String kostentotal, String akonto, String saldo) {
        NkZeileDTO verbrauch = new NkZeileDTO();
        verbrauch.setArt(NkPositionsart.VERBRAUCH);
        verbrauch.setBezeichnung("Wasser");
        verbrauch.setEinheit(Mengeneinheit.M3);
        verbrauch.setMenge(new BigDecimal("34.500"));
        verbrauch.setBetragProEinheit(new BigDecimal("1.8500"));
        verbrauch.setBetrag(new BigDecimal("63.83"));

        NkZeileDTO zuschlag = new NkZeileDTO();
        zuschlag.setArt(NkPositionsart.ZUSCHLAG);
        zuschlag.setBezeichnung("Verwaltungskosten");
        zuschlag.setProzentsatz(new BigDecimal("3.00"));
        // Zwischentotal, auf dem der Zuschlag rechnet - steht auf der Rechnung in der Spalte Preis.
        zuschlag.setBezugsbetrag(new BigDecimal("63.83"));
        zuschlag.setBetrag(new BigDecimal("9.13"));

        NkMieterAbrechnungDTO block = new NkMieterAbrechnungDTO();
        block.setMieterId(mieterId);
        block.setName(name);
        block.setZeilen(List.of(verbrauch, zuschlag));
        block.setKostentotal(new BigDecimal(kostentotal));
        block.setAkontoAnzahlMonate(new BigDecimal("12"));
        block.setAkontoBetragProMonat(new BigDecimal("50.00"));
        block.setAkontoKorrektur(BigDecimal.ZERO);
        block.setAkontoTotal(new BigDecimal(akonto));
        block.setSaldo(new BigDecimal(saldo));
        return block;
    }

    private Mieter mieter(Long id, String strasse, String plz, String ort) {
        Mieter mieter = new Mieter();
        mieter.setId(id);
        mieter.setStrasse(strasse);
        mieter.setPlz(plz);
        mieter.setOrt(ort);
        return mieter;
    }

    /** Mieter mit Mietdauer; {@code mietende} darf {@code null} sein ("laeuft weiter"). */
    private Mieter mieter(Long id, LocalDate mietbeginn, LocalDate mietende) {
        Mieter mieter = mieter(id, "Musterstrasse 1", "8000", "Zuerich");
        mieter.setMietbeginn(mietbeginn);
        mieter.setMietende(mietende);
        return mieter;
    }

    private EinstellungenDTO einstellungen() {
        RechnungKonfigurationDTO.StellerDTO steller = new RechnungKonfigurationDTO.StellerDTO();
        steller.setName("ZEV Musterhaus");
        steller.setStrasse("Sonnenweg 5");
        steller.setPlz("3000");
        steller.setOrt("Bern");

        RechnungKonfigurationDTO config = new RechnungKonfigurationDTO();
        config.setZahlungsfrist("30 Tage");
        config.setIban("CH93 0076 2011 6238 5295 7");
        config.setSteller(steller);

        EinstellungenDTO einstellungen = new EinstellungenDTO();
        einstellungen.setRechnung(config);
        return einstellungen;
    }
}
