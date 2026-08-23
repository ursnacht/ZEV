package ch.nacht.service;

import ch.nacht.dto.NkBerechnungDTO;
import ch.nacht.dto.NkMieterAbrechnungDTO;
import ch.nacht.dto.NkMieterBasisDTO;
import ch.nacht.dto.NkUmlageInfoDTO;
import ch.nacht.dto.NkZeileDTO;
import ch.nacht.entity.Mengeneinheit;
import ch.nacht.entity.NkAbrechnung;
import ch.nacht.entity.NkAkonto;
import ch.nacht.entity.NkPosition;
import ch.nacht.entity.NkPositionsart;
import ch.nacht.entity.NkVerbrauch;
import ch.nacht.entity.NkZusatz;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests der Rechenregeln aus {@code Specs/Nebenkosten/Abrechnung.md} (FR-2 bis FR-5).
 *
 * <p>Der Service ist rein — keine Mocks noetig, keine Datenbank. Genau deshalb liegt hier der
 * Schwerpunkt: Die zeitanteilige Umlage, die Zuschlagskaskade und die Rundung je Zeile sind die
 * Stellen, an denen ein Fehler Geld kostet und erst auf dem Beleg des Mieters auffiele.
 *
 * <p>Zwei Zahlenbeispiele der Spec sind woertlich uebernommen und dienen als Referenz:
 * <ul>
 *   <li>FR-2: 9 Wohnungen, 365 Tage, 900.00 CHF, eine Wohnung 90 Tage leer
 *       → Nenner 3285, voller Mieter 100.00, verteilt 875.34, nicht verteilt 24.66</li>
 *   <li>FR-4: Mietbeginn 15. Februar, Zeitraum 1.1.–30.6. eines Nicht-Schaltjahres → A = 4.50</li>
 * </ul>
 */
public class NkBerechnungServiceTest {

    /** Nicht-Schaltjahr — Voraussetzung beider Referenzbeispiele der Spec. */
    private static final LocalDate JAHR_VON = LocalDate.of(2025, 1, 1);
    private static final LocalDate JAHR_BIS = LocalDate.of(2025, 12, 31);

    private NkBerechnungService berechnungService;

    private NkMieterBasisDTO testMieter1;
    private NkMieterBasisDTO testMieter2;

    @BeforeEach
    void setUp() {
        berechnungService = new NkBerechnungService();

        testMieter1 = new NkMieterBasisDTO(1L, "Anna Achtsam", JAHR_VON, null, 1, new BigDecimal("150.00"));
        testMieter2 = new NkMieterBasisDTO(2L, "Bruno Bedacht", JAHR_VON, null, 1, new BigDecimal("200.00"));
    }

    // ==================== tageImZeitraum ====================

    @Test
    void tageImZeitraum_GanzesJahr_Returns365() {
        assertEquals(365, berechnungService.tageImZeitraum(JAHR_VON, JAHR_BIS));
    }

    @Test
    void tageImZeitraum_Schaltjahr_Returns366() {
        assertEquals(366, berechnungService.tageImZeitraum(
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)));
    }

    @Test
    void tageImZeitraum_EinTag_Returns1() {
        // Beide Enden eingeschlossen - ein Zeitraum von einem Tag ist ein Tag, nicht null.
        assertEquals(1, berechnungService.tageImZeitraum(JAHR_VON, JAHR_VON));
    }

    // ==================== miettageImZeitraum ====================

    @Test
    void miettageImZeitraum_VollerZeitraum_ReturnsAlleTage() {
        assertEquals(365, berechnungService.miettageImZeitraum(testMieter1, JAHR_VON, JAHR_BIS));
    }

    @Test
    void miettageImZeitraum_OhneMietende_ZaehltBisZeitraumende() {
        // Fehlendes Mietende heisst "laeuft weiter", nicht "nie".
        NkMieterBasisDTO offen = mieter(3L, LocalDate.of(2025, 7, 1), null, 1);

        assertEquals(184, berechnungService.miettageImZeitraum(offen, JAHR_VON, JAHR_BIS));
    }

    @Test
    void miettageImZeitraum_AuszugZurMonatsmitte_ZaehltTaggenau() {
        // Akzeptanzkriterium: geprueft mit einem Mieter, der zur Monatsmitte auszieht.
        NkMieterBasisDTO auszug = mieter(3L, JAHR_VON, LocalDate.of(2025, 6, 15), 1);

        // Januar 31 + Februar 28 + Maerz 31 + April 30 + Mai 31 + 15 = 166
        assertEquals(166, berechnungService.miettageImZeitraum(auszug, JAHR_VON, JAHR_BIS));
    }

    @Test
    void miettageImZeitraum_MietbeginnVorZeitraum_ZaehltAbZeitraumbeginn() {
        NkMieterBasisDTO alt = mieter(3L, LocalDate.of(2020, 5, 1), null, 1);

        assertEquals(365, berechnungService.miettageImZeitraum(alt, JAHR_VON, JAHR_BIS));
    }

    @Test
    void miettageImZeitraum_AusserhalbDesZeitraums_ReturnsZero() {
        NkMieterBasisDTO frueher = mieter(3L, LocalDate.of(2020, 1, 1), LocalDate.of(2020, 12, 31), 1);

        assertEquals(0, berechnungService.miettageImZeitraum(frueher, JAHR_VON, JAHR_BIS));
    }

    // ==================== anzahlMonate (FR-4) ====================

    @Test
    void anzahlMonate_MietbeginnMitteFebruar_Returns4Punkt50() {
        // Referenzwert der Spec (FR-4): Januar 0, Februar 14/28 = 0.50, Maerz bis Juni je 1.00.
        NkMieterBasisDTO neu = mieter(3L, LocalDate.of(2025, 2, 15), null, 1);

        BigDecimal result = berechnungService.anzahlMonate(
                neu, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 6, 30));

        assertEquals(new BigDecimal("4.50"), result);
    }

    @Test
    void anzahlMonate_GanzesJahr_Returns12() {
        assertEquals(new BigDecimal("12.00"),
                berechnungService.anzahlMonate(testMieter1, JAHR_VON, JAHR_BIS));
    }

    @Test
    void anzahlMonate_AngebrochenerMonat_ZaehltAnteiligNichtVoll() {
        // Ein einzelner Tag im Januar ist 1/31, nicht ein ganzer Monat.
        NkMieterBasisDTO einTag = mieter(3L, LocalDate.of(2025, 1, 31), LocalDate.of(2025, 1, 31), 1);

        assertEquals(new BigDecimal("0.03"),
                berechnungService.anzahlMonate(einTag, JAHR_VON, JAHR_BIS));
    }

    @Test
    void anzahlMonate_AusserhalbDesZeitraums_ReturnsZero() {
        NkMieterBasisDTO frueher = mieter(3L, LocalDate.of(2020, 1, 1), LocalDate.of(2020, 12, 31), 1);

        assertEquals(new BigDecimal("0.00"),
                berechnungService.anzahlMonate(frueher, JAHR_VON, JAHR_BIS));
    }

    // ==================== Umlage (FR-2) ====================

    @Test
    void berechne_NeunWohnungenEineNeunzigTageLeer_EntsprichtDemBeispielDerSpec() {
        // Referenzbeispiel aus FR-2: 9 Wohnungen, 365 Tage, Allgemeinstrom 900.00,
        // Wohnung 5 steht 90 Tage leer -> Nenner 3285, voller Mieter 100.00,
        // Summe verteilt 875.34, nicht verteilt 24.66.
        NkAbrechnung abrechnung = abrechnung(JAHR_VON, JAHR_BIS, 9);
        NkPosition allgemeinstrom = umlage(10L, 1, "Allgemeinstrom", "900.00", null);

        List<NkMieterBasisDTO> mieter = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            mieter.add(mieter((long) i, JAHR_VON, null, 1));
        }
        // Wohnung 5 ist die ersten 90 Tage (1.1.-31.3.) leer.
        mieter.add(mieter(9L, LocalDate.of(2025, 4, 1), null, 1));

        NkBerechnungDTO result = berechnungService.berechne(
                abrechnung, List.of(allgemeinstrom), List.of(), List.of(), List.of(), mieter);

        assertEquals(3285, result.getNenner());
        assertEquals(3195, result.getSummeTage());

        // Mieter mit voller Dauer: 900.00 x 365 / 3285 = 100.00
        assertEquals(new BigDecimal("100.00"), result.getMieter().get(0).getZeilen().get(0).getBetrag());
        // Mieter mit 275 Tagen: 900.00 x 275 / 3285 = 75.34
        assertEquals(new BigDecimal("75.34"), result.getMieter().get(8).getZeilen().get(0).getBetrag());

        NkUmlageInfoDTO info = result.getUmlagen().get(0);
        assertEquals(new BigDecimal("900.00"), info.getTotalbetrag());
        assertEquals(new BigDecimal("875.34"), info.getSummeVerteilt());
        assertEquals(new BigDecimal("24.66"), info.getNichtVerteilt());
    }

    @Test
    void berechne_AlleWohnungenGanzenZeitraum_JederErhaeltDenselbenAnteil() {
        // "1/9 Allgemeinstrom" entsteht von selbst, ohne dass ein Schluessel zu erfassen waere.
        NkAbrechnung abrechnung = abrechnung(JAHR_VON, JAHR_BIS, 2);
        NkPosition position = umlage(10L, 1, "Allgemeinstrom", "400.00", null);

        NkBerechnungDTO result = berechnungService.berechne(
                abrechnung, List.of(position), List.of(), List.of(), List.of(),
                List.of(testMieter1, testMieter2));

        assertEquals(new BigDecimal("200.00"), result.getMieter().get(0).getZeilen().get(0).getBetrag());
        assertEquals(new BigDecimal("200.00"), result.getMieter().get(1).getZeilen().get(0).getBetrag());
        assertEquals(new BigDecimal("0.00"), result.getUmlagen().get(0).getNichtVerteilt());
    }

    @Test
    void berechne_MieterMitZweiWohnungen_TraegtDenDoppeltenAnteil() {
        NkAbrechnung abrechnung = abrechnung(JAHR_VON, JAHR_BIS, 3);
        NkPosition position = umlage(10L, 1, "Allgemeinstrom", "300.00", null);
        NkMieterBasisDTO doppelt = mieter(3L, JAHR_VON, null, 2);

        NkBerechnungDTO result = berechnungService.berechne(
                abrechnung, List.of(position), List.of(), List.of(), List.of(),
                List.of(testMieter1, doppelt));

        assertEquals(365, result.getMieter().get(0).getTage());
        assertEquals(730, result.getMieter().get(1).getTage());
        assertEquals(new BigDecimal("100.00"), result.getMieter().get(0).getZeilen().get(0).getBetrag());
        assertEquals(new BigDecimal("200.00"), result.getMieter().get(1).getZeilen().get(0).getBetrag());
    }

    @Test
    void berechne_MieterOhneWohnung_ErhaeltKeinenUmlageanteil() {
        // Edge Case: Tage(i) = 0. Zulaessig, aber fast immer ein Datenfehler - deshalb markiert.
        NkAbrechnung abrechnung = abrechnung(JAHR_VON, JAHR_BIS, 2);
        NkPosition position = umlage(10L, 1, "Allgemeinstrom", "400.00", null);
        NkMieterBasisDTO ohneEinheit = mieter(3L, JAHR_VON, null, 0);

        NkBerechnungDTO result = berechnungService.berechne(
                abrechnung, List.of(position), List.of(), List.of(), List.of(),
                List.of(testMieter1, ohneEinheit));

        NkMieterAbrechnungDTO block = result.getMieter().get(1);
        assertTrue(block.isOhneWohnung());
        assertEquals(0, block.getTage());
        assertEquals(new BigDecimal("0.00"), block.getZeilen().get(0).getBetrag());
    }

    @Test
    void berechne_UmlageMitGesamtmenge_VerteiltAuchDieMenge() {
        NkAbrechnung abrechnung = abrechnung(JAHR_VON, JAHR_BIS, 2);
        NkPosition position = umlage(10L, 1, "Regenwasser", "400.00", "500.000");

        NkBerechnungDTO result = berechnungService.berechne(
                abrechnung, List.of(position), List.of(), List.of(), List.of(),
                List.of(testMieter1, testMieter2));

        assertEquals(new BigDecimal("250.000"), result.getMieter().get(0).getZeilen().get(0).getMenge());
        assertEquals(Mengeneinheit.M3, result.getMieter().get(0).getZeilen().get(0).getEinheit());
    }

    @Test
    void berechne_UmlageOhneGesamtmenge_MengeBleibtLeer() {
        NkAbrechnung abrechnung = abrechnung(JAHR_VON, JAHR_BIS, 2);
        NkPosition position = umlage(10L, 1, "Allgemeinstrom", "400.00", null);

        NkBerechnungDTO result = berechnungService.berechne(
                abrechnung, List.of(position), List.of(), List.of(), List.of(),
                List.of(testMieter1, testMieter2));

        assertNull(result.getMieter().get(0).getZeilen().get(0).getMenge());
        assertNotNull(result.getMieter().get(0).getZeilen().get(0).getBetrag());
    }

    // ==================== Anteil (FR-2) ====================
    // Heizkosten: Der Totalbetrag steht an der Position, der Verteilschluessel je Mieter in
    // Prozent. Der Prozentsatz liegt dort, wo bei VERBRAUCH die Menge liegt.

    @Test
    void berechne_Anteil_VerteiltDenTotalbetragNachProzentsatz() {
        NkAbrechnung abrechnung = abrechnung(JAHR_VON, JAHR_BIS, 2);
        NkPosition heizung = anteil(10L, 1, "Heizkosten", "2400.00");

        NkBerechnungDTO result = berechnungService.berechne(
                abrechnung, List.of(heizung),
                List.of(new NkVerbrauch(10L, 1L, new BigDecimal("60.000")),
                        new NkVerbrauch(10L, 2L, new BigDecimal("40.000"))),
                List.of(), List.of(), List.of(testMieter1, testMieter2));

        // 2400.00 x 60% = 1440.00 / x 40% = 960.00
        assertEquals(new BigDecimal("1440.00"), result.getMieter().get(0).getZeilen().get(0).getBetrag());
        assertEquals(new BigDecimal("960.00"), result.getMieter().get(1).getZeilen().get(0).getBetrag());
        assertEquals(new BigDecimal("60.000"), result.getMieter().get(0).getZeilen().get(0).getProzentsatz());
    }

    @Test
    void berechne_Anteil_WeistDieSummeDerProzentsaetzeAus() {
        // Die Kontrollzahl, um die es fachlich geht: Ergeben die Anteile zusammen 100%?
        NkAbrechnung abrechnung = abrechnung(JAHR_VON, JAHR_BIS, 2);
        NkPosition heizung = anteil(10L, 1, "Heizkosten", "2400.00");

        NkBerechnungDTO result = berechnungService.berechne(
                abrechnung, List.of(heizung),
                List.of(new NkVerbrauch(10L, 1L, new BigDecimal("60.000")),
                        new NkVerbrauch(10L, 2L, new BigDecimal("40.000"))),
                List.of(), List.of(), List.of(testMieter1, testMieter2));

        NkUmlageInfoDTO info = result.getUmlagen().get(0);
        assertEquals(NkPositionsart.ANTEIL, info.getArt());
        assertEquals(new BigDecimal("100.000"), info.getSummeProzent());
        assertEquals(new BigDecimal("2400.00"), info.getSummeVerteilt());
        assertEquals(new BigDecimal("0.00"), info.getNichtVerteilt());
    }

    @Test
    void berechne_AnteilUnter100Prozent_LaesstDenRestUnverteilt() {
        // Nicht abgewiesen, nur ausgewiesen: Eine halb erfasste Abrechnung muss speicherbar sein.
        NkAbrechnung abrechnung = abrechnung(JAHR_VON, JAHR_BIS, 2);
        NkPosition heizung = anteil(10L, 1, "Heizkosten", "1000.00");

        NkBerechnungDTO result = berechnungService.berechne(
                abrechnung, List.of(heizung),
                List.of(new NkVerbrauch(10L, 1L, new BigDecimal("30.000"))),
                List.of(), List.of(), List.of(testMieter1, testMieter2));

        NkUmlageInfoDTO info = result.getUmlagen().get(0);
        assertEquals(new BigDecimal("30.000"), info.getSummeProzent());
        assertEquals(new BigDecimal("300.00"), info.getSummeVerteilt());
        assertEquals(new BigDecimal("700.00"), info.getNichtVerteilt());
        assertEquals(new BigDecimal("0.00"), result.getMieter().get(1).getZeilen().get(0).getBetrag());
    }

    @Test
    void berechne_Anteil_IstUnabhaengigVonDenMiettagen() {
        // Anders als die Umlage: Der Schluessel kommt von aussen, ein Leerstand veraendert ihn nicht.
        NkAbrechnung abrechnung = abrechnung(JAHR_VON, JAHR_BIS, 9);
        NkPosition heizung = anteil(10L, 1, "Heizkosten", "1000.00");

        NkBerechnungDTO result = berechnungService.berechne(
                abrechnung, List.of(heizung),
                List.of(new NkVerbrauch(10L, 1L, new BigDecimal("100.000"))),
                List.of(), List.of(), List.of(testMieter1));

        assertEquals(new BigDecimal("1000.00"), result.getMieter().get(0).getZeilen().get(0).getBetrag());
        assertEquals(new BigDecimal("0.00"), result.getUmlagen().get(0).getNichtVerteilt());
    }

    @Test
    void berechne_AnteilVorZuschlag_ZaehltInDieBemessungsgrundlage() {
        NkAbrechnung abrechnung = abrechnung(JAHR_VON, JAHR_BIS, 2);
        NkPosition heizung = anteil(10L, 1, "Heizkosten", "1000.00");
        NkPosition verwaltung = zuschlag(11L, 2, "Verwaltung", "10.00");

        NkBerechnungDTO result = berechnungService.berechne(
                abrechnung, List.of(heizung, verwaltung),
                List.of(new NkVerbrauch(10L, 1L, new BigDecimal("50.000"))),
                List.of(), List.of(), List.of(testMieter1));

        // 1000.00 x 50% = 500.00, davon 10% = 50.00
        assertEquals(new BigDecimal("500.00"), result.getMieter().get(0).getZeilen().get(0).getBetrag());
        assertEquals(new BigDecimal("50.00"), result.getMieter().get(0).getZeilen().get(1).getBetrag());
    }

    // ==================== Verbrauch (FR-2 / FR-3) ====================

    @Test
    void berechne_Verbrauch_BetragIstMengeMalBetragProEinheit() {
        NkAbrechnung abrechnung = abrechnung(JAHR_VON, JAHR_BIS, 2);
        NkPosition warmwasser = verbrauch(10L, 1, "Warmwasser", "3.5000");
        NkVerbrauch menge = new NkVerbrauch(10L, testMieter1.getMieterId(), new BigDecimal("12.500"));

        NkBerechnungDTO result = berechnungService.berechne(
                abrechnung, List.of(warmwasser), List.of(menge), List.of(), List.of(),
                List.of(testMieter1, testMieter2));

        // 12.500 x 3.5000 = 43.75
        assertEquals(new BigDecimal("43.75"), result.getMieter().get(0).getZeilen().get(0).getBetrag());
        assertEquals(new BigDecimal("3.5000"), result.getMieter().get(0).getZeilen().get(0).getBetragProEinheit());
    }

    @Test
    void berechne_VerbrauchOhneErfassteMenge_ZaehltAlsNullUndNichtAlsFehler() {
        // Edge Case: sonst liesse sich eine Abrechnung nicht zwischenspeichern.
        NkAbrechnung abrechnung = abrechnung(JAHR_VON, JAHR_BIS, 2);
        NkPosition warmwasser = verbrauch(10L, 1, "Warmwasser", "3.5000");

        NkBerechnungDTO result = berechnungService.berechne(
                abrechnung, List.of(warmwasser), List.of(), List.of(), List.of(),
                List.of(testMieter1, testMieter2));

        NkZeileDTO zeile = result.getMieter().get(0).getZeilen().get(0);
        assertNull(zeile.getMenge());
        assertEquals(new BigDecimal("0.00"), zeile.getBetrag());
    }

    @Test
    void berechne_VerbrauchJeMieterUnterschiedlich_TrenntDieMengen() {
        NkAbrechnung abrechnung = abrechnung(JAHR_VON, JAHR_BIS, 2);
        NkPosition warmwasser = verbrauch(10L, 1, "Warmwasser", "2.0000");

        NkBerechnungDTO result = berechnungService.berechne(
                abrechnung, List.of(warmwasser),
                List.of(new NkVerbrauch(10L, 1L, new BigDecimal("10.000")),
                        new NkVerbrauch(10L, 2L, new BigDecimal("30.000"))),
                List.of(), List.of(), List.of(testMieter1, testMieter2));

        assertEquals(new BigDecimal("20.00"), result.getMieter().get(0).getZeilen().get(0).getBetrag());
        assertEquals(new BigDecimal("60.00"), result.getMieter().get(1).getZeilen().get(0).getBetrag());
    }

    // ==================== Zuschlag / Kaskade (FR-2) ====================

    @Test
    void berechne_Zuschlag_RechnetAufDieSummeDerZeilenDavor() {
        NkAbrechnung abrechnung = abrechnung(JAHR_VON, JAHR_BIS, 1);
        NkPosition umlage = umlage(10L, 1, "Allgemeinstrom", "200.00", null);
        NkPosition verwaltung = zuschlag(11L, 2, "Verwaltung", "5.00");

        NkBerechnungDTO result = berechnungService.berechne(
                abrechnung, List.of(umlage, verwaltung), List.of(), List.of(), List.of(),
                List.of(testMieter1));

        assertEquals(new BigDecimal("200.00"), result.getMieter().get(0).getZeilen().get(0).getBetrag());
        assertEquals(new BigDecimal("10.00"), result.getMieter().get(0).getZeilen().get(1).getBetrag());
    }

    @Test
    void berechne_ZweiZuschlaege_KaskadierenAufeinander() {
        // Ein zweiter Zuschlag schliesst den ersten ein (FR-2).
        NkAbrechnung abrechnung = abrechnung(JAHR_VON, JAHR_BIS, 1);
        NkPosition umlage = umlage(10L, 1, "Allgemeinstrom", "100.00", null);
        NkPosition ersterZuschlag = zuschlag(11L, 2, "Verwaltung", "10.00");
        NkPosition zweiterZuschlag = zuschlag(12L, 3, "Bearbeitung", "10.00");

        NkBerechnungDTO result = berechnungService.berechne(
                abrechnung, List.of(umlage, ersterZuschlag, zweiterZuschlag),
                List.of(), List.of(), List.of(), List.of(testMieter1));

        List<NkZeileDTO> zeilen = result.getMieter().get(0).getZeilen();
        assertEquals(new BigDecimal("10.00"), zeilen.get(1).getBetrag());
        // 10 % auf 110.00, nicht auf 100.00 - der zweite Zuschlag schliesst den ersten ein.
        assertEquals(new BigDecimal("11.00"), zeilen.get(2).getBetrag());
        assertEquals(new BigDecimal("121.00"), result.getMieter().get(0).getKostentotal());
    }

    @Test
    void berechne_UmgekehrteReihenfolge_AendertDasErgebnisDesZuschlags() {
        // Die reihenfolge ist fachlich tragend, nicht bloss Anzeige.
        NkAbrechnung abrechnung = abrechnung(JAHR_VON, JAHR_BIS, 1);
        NkPosition zuschlagVorne = zuschlag(11L, 1, "Verwaltung", "10.00");
        NkPosition umlageHinten = umlage(10L, 2, "Allgemeinstrom", "100.00", null);

        NkBerechnungDTO result = berechnungService.berechne(
                abrechnung, List.of(umlageHinten, zuschlagVorne),
                List.of(), List.of(), List.of(), List.of(testMieter1));

        List<NkZeileDTO> zeilen = result.getMieter().get(0).getZeilen();
        // Der Zuschlag steht vorne - vor ihm liegt nichts, also 0.00.
        assertEquals(NkPositionsart.ZUSCHLAG, zeilen.get(0).getArt());
        assertEquals(new BigDecimal("0.00"), zeilen.get(0).getBetrag());
        assertEquals(new BigDecimal("100.00"), result.getMieter().get(0).getKostentotal());
    }

    // ==================== Zusatzpositionen (FR-3) ====================

    @Test
    void berechne_Zusatzposition_BetragIstMengeMalBetragProEinheit() {
        NkAbrechnung abrechnung = abrechnung(JAHR_VON, JAHR_BIS, 1);
        NkZusatz zusatz = zusatz(20L, testMieter1.getMieterId(), 1, "Saunagaenge", "4.000", "6.5000");

        NkBerechnungDTO result = berechnungService.berechne(
                abrechnung, List.of(), List.of(), List.of(zusatz), List.of(), List.of(testMieter1));

        NkZeileDTO zeile = result.getMieter().get(0).getZeilen().get(0);
        assertEquals(Long.valueOf(20L), zeile.getZusatzId());
        assertNull(zeile.getPositionId());
        assertEquals(new BigDecimal("26.00"), zeile.getBetrag());
    }

    @Test
    void berechne_Zusatzposition_ZaehltInDieZuschlagskaskade() {
        // Ohne den gemeinsamen Nummernraum waeren Zusatzpositionen von der Kaskade nicht erfasst.
        NkAbrechnung abrechnung = abrechnung(JAHR_VON, JAHR_BIS, 1);
        NkZusatz zusatz = zusatz(20L, testMieter1.getMieterId(), 1, "Saunagaenge", "10.000", "5.0000");
        NkPosition verwaltung = zuschlag(11L, 2, "Verwaltung", "10.00");

        NkBerechnungDTO result = berechnungService.berechne(
                abrechnung, List.of(verwaltung), List.of(), List.of(zusatz), List.of(),
                List.of(testMieter1));

        List<NkZeileDTO> zeilen = result.getMieter().get(0).getZeilen();
        assertEquals(new BigDecimal("50.00"), zeilen.get(0).getBetrag());
        assertEquals(new BigDecimal("5.00"), zeilen.get(1).getBetrag());
    }

    @Test
    void berechne_GleicheReihenfolge_AllgemeinePositionVorZusatzposition() {
        // Bei Gleichstand kommt die allgemeine Position zuerst - damit ist die Kaskade
        // deterministisch (FR-2).
        NkAbrechnung abrechnung = abrechnung(JAHR_VON, JAHR_BIS, 1);
        NkPosition umlage = umlage(10L, 1, "Allgemeinstrom", "100.00", null);
        NkPosition verwaltung = zuschlag(11L, 2, "Verwaltung", "10.00");
        NkZusatz zusatz = zusatz(20L, testMieter1.getMieterId(), 2, "Saunagaenge", "10.000", "5.0000");

        NkBerechnungDTO result = berechnungService.berechne(
                abrechnung, List.of(umlage, verwaltung), List.of(), List.of(zusatz), List.of(),
                List.of(testMieter1));

        List<NkZeileDTO> zeilen = result.getMieter().get(0).getZeilen();
        assertEquals("Allgemeinstrom", zeilen.get(0).getBezeichnung());
        assertEquals("Verwaltung", zeilen.get(1).getBezeichnung());
        assertEquals("Saunagaenge", zeilen.get(2).getBezeichnung());
        // Der Zuschlag rechnet auf 100.00, nicht auf 150.00.
        assertEquals(new BigDecimal("10.00"), zeilen.get(1).getBetrag());
        assertEquals(new BigDecimal("160.00"), result.getMieter().get(0).getKostentotal());
    }

    @Test
    void berechne_ZusatzpositionEinesAnderenMieters_ErscheintNichtImFremdenBlock() {
        NkAbrechnung abrechnung = abrechnung(JAHR_VON, JAHR_BIS, 2);
        NkZusatz nurFuerMieter1 = zusatz(20L, 1L, 1, "Saunagaenge", "4.000", "6.5000");

        NkBerechnungDTO result = berechnungService.berechne(
                abrechnung, List.of(), List.of(), List.of(nurFuerMieter1), List.of(),
                List.of(testMieter1, testMieter2));

        assertEquals(1, result.getMieter().get(0).getZeilen().size());
        assertTrue(result.getMieter().get(1).getZeilen().isEmpty());
    }

    // ==================== Kostentotal und Rundung (FR-5) ====================

    @Test
    void berechne_Kostentotal_IstSummeAllerZeilenDesMieters() {
        NkAbrechnung abrechnung = abrechnung(JAHR_VON, JAHR_BIS, 1);
        NkPosition umlage = umlage(10L, 1, "Allgemeinstrom", "120.00", null);
        NkPosition warmwasser = verbrauch(11L, 2, "Warmwasser", "2.0000");
        NkZusatz zusatz = zusatz(20L, 1L, 3, "Sauna", "2.000", "5.0000");

        NkBerechnungDTO result = berechnungService.berechne(
                abrechnung, List.of(umlage, warmwasser),
                List.of(new NkVerbrauch(11L, 1L, new BigDecimal("5.000"))),
                List.of(zusatz), List.of(), List.of(testMieter1));

        // 120.00 + 10.00 + 10.00
        assertEquals(new BigDecimal("140.00"), result.getMieter().get(0).getKostentotal());
    }

    @Test
    void berechne_Zeilenbetrag_WirdAufEinenRappenGerundet() {
        // 100.00 / 3 = 33.333... -> 33.33 je Zeile; die Summe bleibt bei 99.99 stehen.
        NkAbrechnung abrechnung = abrechnung(JAHR_VON, JAHR_BIS, 3);
        NkPosition position = umlage(10L, 1, "Allgemeinstrom", "100.00", null);
        List<NkMieterBasisDTO> mieter = List.of(
                mieter(1L, JAHR_VON, null, 1), mieter(2L, JAHR_VON, null, 1), mieter(3L, JAHR_VON, null, 1));

        NkBerechnungDTO result = berechnungService.berechne(
                abrechnung, List.of(position), List.of(), List.of(), List.of(), mieter);

        assertEquals(new BigDecimal("33.33"), result.getMieter().get(0).getZeilen().get(0).getBetrag());
        assertEquals(new BigDecimal("99.99"), result.getUmlagen().get(0).getSummeVerteilt());
        // Die Differenz wird ausgewiesen, nicht ausgeglichen.
        assertEquals(new BigDecimal("0.01"), result.getUmlagen().get(0).getRundungsdifferenz());
    }

    @Test
    void berechne_RundungsdifferenzUndLeerstand_WerdenGetrenntAusgewiesen() {
        // 3 Wohnungen, nur 2 belegt: 33.33 Leerstandsanteil, zusaetzlich 0.01 aus dem Runden.
        NkAbrechnung abrechnung = abrechnung(JAHR_VON, JAHR_BIS, 3);
        NkPosition position = umlage(10L, 1, "Allgemeinstrom", "100.00", null);

        NkBerechnungDTO result = berechnungService.berechne(
                abrechnung, List.of(position), List.of(), List.of(), List.of(),
                List.of(testMieter1, testMieter2));

        NkUmlageInfoDTO info = result.getUmlagen().get(0);
        assertEquals(new BigDecimal("66.66"), info.getSummeVerteilt());
        assertEquals(new BigDecimal("33.33"), info.getNichtVerteilt());
        assertEquals(new BigDecimal("0.01"), info.getRundungsdifferenz());
    }

    // ==================== Akonto und Saldo (FR-4) ====================

    @Test
    void berechne_ErfasstesAkonto_TotalIstMonateMalBetragPlusKorrektur() {
        NkAbrechnung abrechnung = abrechnung(JAHR_VON, JAHR_BIS, 1);
        NkPosition position = umlage(10L, 1, "Allgemeinstrom", "1200.00", null);
        NkAkonto akonto = akonto(1L, "12.00", "150.00", "50.00");

        NkBerechnungDTO result = berechnungService.berechne(
                abrechnung, List.of(position), List.of(), List.of(), List.of(akonto),
                List.of(testMieter1));

        NkMieterAbrechnungDTO block = result.getMieter().get(0);
        // 12.00 x 150.00 + 50.00 = 1850.00
        assertEquals(new BigDecimal("1850.00"), block.getAkontoTotal());
        // Kostentotal 1200.00 - 1850.00 = -650.00 -> Guthaben
        assertEquals(new BigDecimal("-650.00"), block.getSaldo());
    }

    @Test
    void berechne_NegativeKorrektur_ReduziertDasAkontoTotal() {
        NkAbrechnung abrechnung = abrechnung(JAHR_VON, JAHR_BIS, 1);
        NkAkonto akonto = akonto(1L, "12.00", "100.00", "-200.00");

        NkBerechnungDTO result = berechnungService.berechne(
                abrechnung, List.of(), List.of(), List.of(), List.of(akonto), List.of(testMieter1));

        assertEquals(new BigDecimal("1000.00"), result.getMieter().get(0).getAkontoTotal());
    }

    @Test
    void berechne_PositiverSaldo_IstEineNachzahlung() {
        NkAbrechnung abrechnung = abrechnung(JAHR_VON, JAHR_BIS, 1);
        NkPosition position = umlage(10L, 1, "Allgemeinstrom", "1500.00", null);
        NkAkonto akonto = akonto(1L, "12.00", "100.00", "0.00");

        NkBerechnungDTO result = berechnungService.berechne(
                abrechnung, List.of(position), List.of(), List.of(), List.of(akonto),
                List.of(testMieter1));

        assertEquals(new BigDecimal("300.00"), result.getMieter().get(0).getSaldo());
    }

    @Test
    void berechne_OhneErfasstesAkonto_SchlaegtMietdauerUndStammdatumVor() {
        NkAbrechnung abrechnung = abrechnung(JAHR_VON, JAHR_BIS, 1);

        NkBerechnungDTO result = berechnungService.berechne(
                abrechnung, List.of(), List.of(), List.of(), List.of(), List.of(testMieter1));

        NkMieterAbrechnungDTO block = result.getMieter().get(0);
        assertEquals(new BigDecimal("12.00"), block.getAkontoAnzahlMonate());
        assertEquals(new BigDecimal("150.00"), block.getAkontoBetragProMonat());
        assertEquals(new BigDecimal("0.00"), block.getAkontoKorrektur());
        assertEquals(new BigDecimal("1800.00"), block.getAkontoTotal());
    }

    @Test
    void berechne_OhneAkontoStammdatum_BleibtDerVorschlagNull() {
        // Bestandsmieter haben keinen Wert - die Abrechnung muss trotzdem rechenbar bleiben.
        NkAbrechnung abrechnung = abrechnung(JAHR_VON, JAHR_BIS, 1);
        NkMieterBasisDTO ohneStammdatum = new NkMieterBasisDTO(3L, "Carla Cool", JAHR_VON, null, 1, null);

        NkBerechnungDTO result = berechnungService.berechne(
                abrechnung, List.of(), List.of(), List.of(), List.of(), List.of(ohneStammdatum));

        assertEquals(new BigDecimal("0.00"), result.getMieter().get(0).getAkontoBetragProMonat());
        assertEquals(new BigDecimal("0.00"), result.getMieter().get(0).getAkontoTotal());
    }

    @Test
    void berechne_ErfasstesAkonto_UeberschreibtDenVorschlag() {
        // Der Vorschlag waere 12.00 x 150.00; erfasst ist etwas anderes und das gilt.
        NkAbrechnung abrechnung = abrechnung(JAHR_VON, JAHR_BIS, 1);
        NkAkonto abweichend = akonto(testMieter1.getMieterId(), "6.00", "80.00", "0.00");

        NkBerechnungDTO result = berechnungService.berechne(
                abrechnung, List.of(), List.of(), List.of(), List.of(abweichend), List.of(testMieter1));

        NkMieterAbrechnungDTO block = result.getMieter().get(0);
        assertEquals(new BigDecimal("6.00"), block.getAkontoAnzahlMonate());
        assertEquals(new BigDecimal("80.00"), block.getAkontoBetragProMonat());
        assertEquals(new BigDecimal("480.00"), block.getAkontoTotal());
    }

    // ==================== Edge Cases ====================

    @Test
    void berechne_OhneMieter_LiefertKeineBloeckeUndKeineDivisionDurchNull() {
        NkAbrechnung abrechnung = abrechnung(JAHR_VON, JAHR_BIS, 9);
        NkPosition position = umlage(10L, 1, "Allgemeinstrom", "900.00", null);

        NkBerechnungDTO result = berechnungService.berechne(
                abrechnung, List.of(position), List.of(), List.of(), List.of(), List.of());

        assertTrue(result.getMieter().isEmpty());
        assertEquals(0, result.getSummeTage());
        // Alles bleibt unverteilt, aber es fliegt keine ArithmeticException.
        assertEquals(new BigDecimal("900.00"), result.getUmlagen().get(0).getNichtVerteilt());
    }

    @Test
    void berechne_NennerNull_ErgibtNullBetraegeStattDivisionDurchNull() {
        NkAbrechnung abrechnung = abrechnung(JAHR_VON, JAHR_BIS, 0);
        NkPosition position = umlage(10L, 1, "Allgemeinstrom", "900.00", null);

        NkBerechnungDTO result = berechnungService.berechne(
                abrechnung, List.of(position), List.of(), List.of(), List.of(),
                List.of(testMieter1));

        assertEquals(0, result.getNenner());
        assertEquals(new BigDecimal("0.00"), result.getMieter().get(0).getZeilen().get(0).getBetrag());
    }

    @Test
    void berechne_LeerePositionsliste_LiefertLeereBloeckeOhneFehler() {
        NkAbrechnung abrechnung = abrechnung(JAHR_VON, JAHR_BIS, 2);

        NkBerechnungDTO result = berechnungService.berechne(
                abrechnung, List.of(), List.of(), List.of(), List.of(),
                List.of(testMieter1, testMieter2));

        assertEquals(2, result.getMieter().size());
        assertTrue(result.getMieter().get(0).getZeilen().isEmpty());
        assertEquals(new BigDecimal("0.00"), result.getMieter().get(0).getKostentotal());
        assertTrue(result.getUmlagen().isEmpty());
    }

    @Test
    void berechne_UnsortiertePositionen_WerdenSelbstNachReihenfolgeGeordnet() {
        NkAbrechnung abrechnung = abrechnung(JAHR_VON, JAHR_BIS, 1);
        NkPosition verwaltung = zuschlag(11L, 2, "Verwaltung", "10.00");
        NkPosition umlage = umlage(10L, 1, "Allgemeinstrom", "100.00", null);

        NkBerechnungDTO result = berechnungService.berechne(
                abrechnung, List.of(verwaltung, umlage), List.of(), List.of(), List.of(),
                List.of(testMieter1));

        List<NkZeileDTO> zeilen = result.getMieter().get(0).getZeilen();
        assertEquals("Allgemeinstrom", zeilen.get(0).getBezeichnung());
        assertEquals(new BigDecimal("10.00"), zeilen.get(1).getBetrag());
    }

    // ==================== Testdaten ====================

    private NkAbrechnung abrechnung(LocalDate von, LocalDate bis, int anzahlWohnungen) {
        NkAbrechnung abrechnung = new NkAbrechnung();
        abrechnung.setId(1L);
        abrechnung.setOrgId(1L);
        abrechnung.setBezeichnung("Nebenkostenabrechnung 2025");
        abrechnung.setDatumVon(von);
        abrechnung.setDatumBis(bis);
        abrechnung.setAnzahlWohnungen(anzahlWohnungen);
        return abrechnung;
    }

    private NkMieterBasisDTO mieter(Long id, LocalDate von, LocalDate bis, int anzahlWohnungen) {
        return new NkMieterBasisDTO(id, "Mieter " + id, von, bis, anzahlWohnungen, new BigDecimal("100.00"));
    }

    private NkPosition umlage(Long id, int reihenfolge, String bezeichnung,
                              String totalbetrag, String gesamtmenge) {
        NkPosition position = basisPosition(id, reihenfolge, bezeichnung, NkPositionsart.UMLAGE);
        position.setEinheit(Mengeneinheit.M3);
        position.setTotalbetrag(new BigDecimal(totalbetrag));
        position.setGesamtmenge(gesamtmenge != null ? new BigDecimal(gesamtmenge) : null);
        return position;
    }

    private NkPosition verbrauch(Long id, int reihenfolge, String bezeichnung, String betragProEinheit) {
        NkPosition position = basisPosition(id, reihenfolge, bezeichnung, NkPositionsart.VERBRAUCH);
        position.setEinheit(Mengeneinheit.M3);
        position.setBetragProEinheit(new BigDecimal(betragProEinheit));
        return position;
    }

    private NkPosition anteil(Long id, int reihenfolge, String bezeichnung, String totalbetrag) {
        NkPosition position = basisPosition(id, reihenfolge, bezeichnung, NkPositionsart.ANTEIL);
        position.setTotalbetrag(new BigDecimal(totalbetrag));
        return position;
    }

    private NkPosition zuschlag(Long id, int reihenfolge, String bezeichnung, String prozentsatz) {
        NkPosition position = basisPosition(id, reihenfolge, bezeichnung, NkPositionsart.ZUSCHLAG);
        position.setProzentsatz(new BigDecimal(prozentsatz));
        return position;
    }

    private NkPosition basisPosition(Long id, int reihenfolge, String bezeichnung, NkPositionsart art) {
        NkPosition position = new NkPosition();
        position.setId(id);
        position.setOrgId(1L);
        position.setAbrechnungId(1L);
        position.setArt(art);
        position.setBezeichnung(bezeichnung);
        position.setReihenfolge(reihenfolge);
        return position;
    }

    private NkZusatz zusatz(Long id, Long mieterId, int reihenfolge, String bezeichnung,
                            String menge, String betragProEinheit) {
        NkZusatz zusatz = new NkZusatz();
        zusatz.setId(id);
        zusatz.setOrgId(1L);
        zusatz.setAbrechnungId(1L);
        zusatz.setMieterId(mieterId);
        zusatz.setReihenfolge(reihenfolge);
        zusatz.setBezeichnung(bezeichnung);
        zusatz.setEinheit(Mengeneinheit.STUECK);
        zusatz.setMenge(new BigDecimal(menge));
        zusatz.setBetragProEinheit(new BigDecimal(betragProEinheit));
        return zusatz;
    }

    private NkAkonto akonto(Long mieterId, String anzahlMonate, String betragProMonat, String korrektur) {
        NkAkonto akonto = new NkAkonto();
        akonto.setId(30L);
        akonto.setOrgId(1L);
        akonto.setAbrechnungId(1L);
        akonto.setMieterId(mieterId);
        akonto.setAnzahlMonate(new BigDecimal(anzahlMonate));
        akonto.setBetragProMonat(new BigDecimal(betragProMonat));
        akonto.setKorrektur(new BigDecimal(korrektur));
        return akonto;
    }
}
