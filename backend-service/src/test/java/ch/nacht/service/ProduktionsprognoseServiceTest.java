package ch.nacht.service;

import ch.nacht.dto.PrognosepunktDTO;
import ch.nacht.dto.SteuerKonfigurationDTO;
import ch.nacht.entity.EinheitTyp;
import ch.nacht.entity.Einstrahlungsprognose;
import ch.nacht.entity.FeatureFlag;
import ch.nacht.exception.FeatureDisabledException;
import ch.nacht.repository.EinheitRepository;
import ch.nacht.repository.EinstrahlungsprognoseRepository;
import ch.nacht.repository.MesswerteRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit-Tests fuer {@link ProduktionsprognoseService} — den <b>gelernten</b> Umrechnungsfaktor von
 * W/m² auf kWh (Specs/Ladeplanung.md, FR-3).
 *
 * <p><b>Warum diese Klasse so ausfuehrlich geprueft wird.</b> Der Faktor ist eine einzige Zahl,
 * und fast jeder Fehler in seiner Herleitung ergibt wieder eine plausible Zahl. Ein um eine
 * Viertelstunde versetzt gelernter Faktor, ein ohne Speicherfluss gelernter Faktor und ein
 * richtiger Faktor sehen in der Anzeige gleich aus — nur die Ladeplanung, die spaeter darauf
 * aufsetzt, entschiede anders. Genau deshalb steht hinter jedem Fall hier eine Zahl, die sich
 * von der Zahl des jeweils falschen Verhaltens unterscheidet.
 *
 * <p>{@code umrechnungsfaktor} ist privat und wird ueber {@link
 * ProduktionsprognoseService#getPrognose(LocalDate)} geprueft: Der Faktor steht an jedem
 * {@link PrognosepunktDTO}, und die erwartete Erzeugung ist sein Produkt mit der Einstrahlung.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ProduktionsprognoseServiceTest {

    private static final Long ORG_ID = 7L;

    /** Der gefragte Tag. */
    private static final LocalDate DATUM = LocalDate.of(2026, 9, 25);

    /** Erster Zeitpunkt der Lernhistorie — Intervall<b>beginn</b>, wie in der Prognose. */
    private static final LocalDateTime HELL_START = LocalDateTime.of(2026, 8, 28, 10, 0);

    /** Mindestzahl heller Intervalle, ab der ein Faktor gebildet wird (MIN_PUNKTE im Service). */
    private static final int MIN_PUNKTE = 150;

    @Mock
    private EinstrahlungsprognoseRepository prognoseRepository;

    @Mock
    private MesswerteRepository messwerteRepository;

    @Mock
    private EinheitRepository einheitRepository;

    @Mock
    private EinstellungenService einstellungenService;

    @Mock
    private FeatureFlagService featureFlagService;

    @Mock
    private OrganizationContextService organizationContextService;

    @Mock
    private HibernateFilterService hibernateFilterService;

    @InjectMocks
    private ProduktionsprognoseService produktionsprognoseService;

    private SteuerKonfigurationDTO konfiguration;

    @BeforeEach
    void setUp() {
        konfiguration = new SteuerKonfigurationDTO();
        when(organizationContextService.getCurrentOrgId()).thenReturn(ORG_ID);
        when(featureFlagService.isEnabled(ORG_ID, FeatureFlag.EINSPEISESTEUERUNG)).thenReturn(true);
        when(einstellungenService.getSteuerKonfiguration(ORG_ID)).thenReturn(konfiguration);

        // Vorgabe: Der gefragte Tag hat genau einen Prognosepunkt mit 400 W/m².
        when(prognoseRepository.findByZeitBetween(any(), any()))
                .thenReturn(List.of(prognosepunkt(DATUM.atTime(12, 0), "400.00")));
        when(prognoseRepository.findGtiJeIntervall(any(), any())).thenReturn(List.of());
        when(messwerteRepository.sumBilanzKomponentenPerZeitBetween(any(), any()))
                .thenReturn(List.of());
        when(einheitRepository.existsByTyp(EinheitTyp.SPEICHER)).thenReturn(false);
    }

    // ==================== Feature-Flag und leere Prognose ====================

    /**
     * Ohne Flag gibt es keine Prognose — und zwar bevor irgendetwas gelesen wird.
     *
     * <p>Die Pruefung steht vor dem Mandantenfilter; waere sie danach, liefe eine Abfrage fuer
     * einen Mandanten, der das Feature gar nicht hat.
     */
    @Test
    void getPrognose_FeatureDeaktiviert_WirftFeatureDisabledException() {
        when(featureFlagService.isEnabled(ORG_ID, FeatureFlag.EINSPEISESTEUERUNG)).thenReturn(false);

        FeatureDisabledException e = assertThrows(FeatureDisabledException.class,
                () -> produktionsprognoseService.getPrognose(DATUM));

        assertEquals("FEATURE_FLAG_DEAKTIVIERT", e.getMessage());
        verify(hibernateFilterService, never()).enableOrgFilter();
        verify(prognoseRepository, never()).findByZeitBetween(any(), any());
    }

    /**
     * Kein Prognosewert fuer den Tag → leere Liste, und der Faktor wird gar nicht erst gelernt.
     *
     * <p>Der Lernlauf liest 28 Tage Messwerte; ihn fuer einen Tag ohne Prognose auszufuehren waere
     * Arbeit ohne Abnehmer.
     */
    @Test
    void getPrognose_OhnePrognosedaten_LiefertLeereListe() {
        when(prognoseRepository.findByZeitBetween(any(), any())).thenReturn(List.of());

        List<PrognosepunktDTO> punkte = produktionsprognoseService.getPrognose(DATUM);

        assertTrue(punkte.isEmpty());
        verify(hibernateFilterService).enableOrgFilter();
        verify(prognoseRepository, never()).findGtiJeIntervall(any(), any());
        verify(messwerteRepository, never()).sumBilanzKomponentenPerZeitBetween(any(), any());
    }

    /** Der Mandantenfilter ist vor dem Lesen aktiv — sonst saehe der Tag fremde Anlagen. */
    @Test
    void getPrognose_LiestUnterMandantenfilter() {
        produktionsprognoseService.getPrognose(DATUM);

        verify(hibernateFilterService).enableOrgFilter();
    }

    /**
     * Der gefragte Tag wird als Halboffenes Intervall gelesen: 00:00 einschliesslich bis 00:00 des
     * Folgetags ausschliesslich.
     *
     * <p>Mit {@code <=} am Ende truege das erste Intervall des Folgetags in die Tagesansicht.
     */
    @Test
    void getPrognose_LiestGenauDenOrtstag() {
        produktionsprognoseService.getPrognose(DATUM);

        verify(prognoseRepository).findByZeitBetween(
                eq(LocalDateTime.of(2026, 9, 25, 0, 0)),
                eq(LocalDateTime.of(2026, 9, 26, 0, 0)));
    }

    // ==================== Der gelernte Faktor: Speicher ====================

    /**
     * <b>Ohne</b> {@code SPEICHER}-Einheit bleibt es beim blossen Zaehlerwert.
     *
     * <p>Erzeugung 2.000 kWh bei 100 W/m² ergibt 0.02 kWh je W/m². Der Speicherfluss wird gar
     * nicht erst abgefragt — es gibt keinen.
     */
    @Test
    void getPrognose_OhneSpeicherEinheit_LerntNurDenZaehlerwert() {
        when(einheitRepository.existsByTyp(EinheitTyp.SPEICHER)).thenReturn(false);
        lernhistorie(MIN_PUNKTE);
        messwerte(messwert(ende(0), "2.000"));

        PrognosepunktDTO punkt = einzigerPunkt();

        assertEquals(new BigDecimal("0.02000000"), punkt.getFaktor());
        verify(messwerteRepository, never())
                .sumLadungEntladungPerZeitBetween(any(), any(), any());
    }

    /**
     * <b>Mit</b> Speicher gilt {@code produktion + ladung − entladung}.
     *
     * <p>Der Erzeugungszaehler sieht nur, was der Hybrid-Wechselrichter wechselstromseitig abgibt.
     * 2.000 am Zaehler, 1.500 in die Batterie, 0.500 heraus sind 3.000 erzeugte kWh — bei 100 W/m²
     * also 0.03. Ein Faktor von 0.02 hiesse, dass der Speicherfluss fehlt; ein Faktor von 0.035,
     * dass die Entladung faelschlich addiert wurde. Beide Zahlen saehen fuer sich plausibel aus.
     */
    @Test
    void getPrognose_MitSpeicherEinheit_RechnetLadungHinzuUndEntladungAb() {
        when(einheitRepository.existsByTyp(EinheitTyp.SPEICHER)).thenReturn(true);
        lernhistorie(MIN_PUNKTE);
        messwerte(messwert(ende(0), "2.000"));
        speicherfluss(new Object[]{ende(0), new BigDecimal("1.500"), new BigDecimal("0.500")});

        PrognosepunktDTO punkt = einzigerPunkt();

        assertEquals(new BigDecimal("0.03000000"), punkt.getFaktor());
    }

    /**
     * Der Speicherfluss wird im <b>selben verschobenen Fenster</b> geholt wie die Messwerte.
     *
     * <p>Er ist nach Intervall<b>ende</b> geschluesselt, wie {@code messwerte.zeit}. Liefe er ueber
     * das unverschobene Fenster, fehlte am Rand je ein Intervall — still, und nur am Rand.
     */
    @Test
    void getPrognose_MitSpeicher_FragtSpeicherflussImSelbenFenster() {
        when(einheitRepository.existsByTyp(EinheitTyp.SPEICHER)).thenReturn(true);
        lernhistorie(MIN_PUNKTE);
        messwerte(messwert(ende(0), "2.000"));

        produktionsprognoseService.getPrognose(DATUM);

        verify(messwerteRepository).sumLadungEntladungPerZeitBetween(
                eq(EinheitTyp.SPEICHER),
                eq(LocalDateTime.of(2026, 8, 28, 0, 15)),
                eq(LocalDateTime.of(2026, 9, 25, 0, 15)));
    }

    // ==================== Der gelernte Faktor: Zeitversatz ====================

    /**
     * <b>Der Zeitversatz.</b> {@code messwerte.zeit} traegt das Intervall<b>ende</b>,
     * {@code einstrahlungsprognose.zeit} den <b>Beginn</b>.
     *
     * <p>Der Messwert zu 10:15 gehoert zur Einstrahlung von 10:00, nicht zu der von 10:15. Damit
     * das nicht bloss zufaellig stimmt, tragen die beiden fraglichen Intervalle
     * <b>verschiedene</b> Einstrahlung: 10:00 hat 200 W/m², 10:15 hat 500. Eine Erzeugung von
     * 1.000 kWh ergibt richtig verschoben 1/200 = 0.005, ohne Verschiebung 1/500 = 0.002.
     *
     * <p>Ohne diesen Test waere der Fehler unsichtbar: Beide Zahlen sind Faktoren in derselben
     * Groessenordnung, und keine Anzeige verriete, welcher der richtige ist.
     */
    @Test
    void getPrognose_MesswertZeitIstIntervallende_VerschiebtUmEineViertelstunde() {
        List<Object[]> historie = gtiPunkte(MIN_PUNKTE);
        historie.get(0)[1] = new BigDecimal("200.00");   // Beginn 10:00
        historie.get(1)[1] = new BigDecimal("500.00");   // Beginn 10:15
        when(prognoseRepository.findGtiJeIntervall(any(), any())).thenReturn(historie);
        // Ein Messwert, gestempelt auf das ENDE 10:15 - er gehoert zum Beginn 10:00.
        messwerte(messwert(LocalDateTime.of(2026, 8, 28, 10, 15), "1.000"));

        PrognosepunktDTO punkt = einzigerPunkt();

        assertEquals(new BigDecimal("0.00500000"), punkt.getFaktor());
    }

    /**
     * Die Messwerte werden ueber das um eine Viertelstunde <b>nach hinten</b> verschobene Fenster
     * geholt.
     *
     * <p>Die Historie umfasst 2026-08-28 bis 2026-09-25 (Beginn-Zeitstempel). Dieselben Intervalle
     * tragen als Messwert die Enden 00:15 bis 00:15 — ohne die Verschiebung fehlte das letzte
     * Intervall des Zeitraums und das erste stammte aus dem Tag davor.
     */
    @Test
    void getPrognose_FragtMesswerteImVerschobenenFenster() {
        lernhistorie(MIN_PUNKTE);

        produktionsprognoseService.getPrognose(DATUM);

        verify(messwerteRepository).sumBilanzKomponentenPerZeitBetween(
                eq(LocalDateTime.of(2026, 8, 28, 0, 15)),
                eq(LocalDateTime.of(2026, 9, 25, 0, 15)));
    }

    /**
     * Ein Messwert ohne passendes Einstrahlungsintervall wird uebersprungen — er zaehlt weder in
     * die Erzeugungs- noch in die Einstrahlungssumme.
     *
     * <p>Das sind die Nachtstunden: {@code findGtiJeIntervall} liefert nur helle Intervalle. Fiele
     * ein Nachtverbrauch in die Erzeugungssumme, ohne dass Einstrahlung dagegen stuende, waere der
     * Faktor zu hoch.
     */
    @Test
    void getPrognose_MesswertOhnePassendeEinstrahlung_WirdUebersprungen() {
        lernhistorie(MIN_PUNKTE);
        messwerte(
                messwert(ende(0), "2.000"),
                // 20.09. um 03:00 liegt in der Nacht - dafuer gibt es kein helles Intervall
                messwert(LocalDateTime.of(2026, 9, 20, 3, 0), "9.000"));

        PrognosepunktDTO punkt = einzigerPunkt();

        // Nur der erste Messwert zaehlt: 2.000 / 100
        assertEquals(new BigDecimal("0.02000000"), punkt.getFaktor());
    }

    // ==================== Der gelernte Faktor: Mindestzahl Punkte ====================

    /**
     * Unter {@value #MIN_PUNKTE} hellen Intervallen wird <b>kein</b> Faktor gebildet — und die
     * erwartete Erzeugung bleibt <b>leer statt 0</b>.
     *
     * <p>Das ist der Kern: Eine 0 hiesse „nichts erwartet" und saehe aus wie eine Aussage. Leer
     * heisst „noch nicht gelernt" — und nur das trifft zu. Wer die 0 anzeigte, zeigte eine Anlage,
     * die nichts produziert.
     */
    @Test
    void getPrognose_WenigerAlsMinPunkte_LaesstErwarteteErzeugungLeer() {
        lernhistorie(MIN_PUNKTE - 1);
        messwerte(messwert(ende(0), "2.000"));

        PrognosepunktDTO punkt = einzigerPunkt();

        assertNull(punkt.getFaktor());
        assertNull(punkt.getErwarteteErzeugung());
        // Die Einstrahlung selbst steht trotzdem - sie ist bekannt, auch ohne Faktor
        assertEquals(new BigDecimal("400.00"), punkt.getGti());
    }

    /** Genau {@value #MIN_PUNKTE} helle Intervalle reichen — die Grenze ist einschliesslich. */
    @Test
    void getPrognose_GenauMinPunkte_LerntFaktor() {
        lernhistorie(MIN_PUNKTE);
        messwerte(messwert(ende(0), "2.000"));

        PrognosepunktDTO punkt = einzigerPunkt();

        assertNotNull(punkt.getFaktor());
        assertEquals(new BigDecimal("0.02000000"), punkt.getFaktor());
    }

    // ==================== Der gelernte Faktor: Randfaelle der Summen ====================

    /**
     * Eine negative Zwischensumme wird auf 0 gekappt, nicht abgezogen.
     *
     * <p>Sie entsteht, wenn die Batterie entlaedt, waehrend die Sonne schon scheint und der
     * Zaehler wenig sieht. Ohne Kappung zoege dieses eine Intervall (−4.900) die Summe des
     * anderen (3.000) ins Negative, und es gaebe gar keinen Faktor — die Prognose fiele wegen
     * eines einzelnen Intervalls ganz aus.
     */
    @Test
    void getPrognose_NegativeZwischensumme_WirdAufNullGekappt() {
        when(einheitRepository.existsByTyp(EinheitTyp.SPEICHER)).thenReturn(true);
        lernhistorie(MIN_PUNKTE);
        messwerte(
                messwert(ende(0), "0.100"),   // + 0.000 − 5.000 = −4.900 → 0
                messwert(ende(1), "3.000"));
        speicherfluss(new Object[]{ende(0), new BigDecimal("0.000"), new BigDecimal("5.000")});

        PrognosepunktDTO punkt = einzigerPunkt();

        // 0 + 3.000 ueber 200 W/m² = 0.015; ohne Kappung waere die Summe negativ und der Faktor leer
        assertEquals(new BigDecimal("0.01500000"), punkt.getFaktor());
    }

    /**
     * Keine Messwerte in der Historie → Einstrahlungssumme 0 → kein Faktor.
     *
     * <p>Ohne diese Wache teilte der Service durch 0.
     */
    @Test
    void getPrognose_OhneMesswerte_KeinFaktor() {
        lernhistorie(MIN_PUNKTE);
        messwerte();

        PrognosepunktDTO punkt = einzigerPunkt();

        assertNull(punkt.getFaktor());
        assertNull(punkt.getErwarteteErzeugung());
    }

    /**
     * Messwerte, aber durchweg Erzeugung 0 → kein Faktor.
     *
     * <p>Ein Faktor von 0 ergaebe fuer jedes Intervall eine erwartete Erzeugung von 0 — dieselbe
     * irrefuehrende Aussage wie oben, nur auf anderem Weg.
     */
    @Test
    void getPrognose_ErzeugungSummeNull_KeinFaktor() {
        lernhistorie(MIN_PUNKTE);
        messwerte(messwert(ende(0), "0.000"), messwert(ende(1), "0.000"));

        PrognosepunktDTO punkt = einzigerPunkt();

        assertNull(punkt.getFaktor());
        assertNull(punkt.getErwarteteErzeugung());
    }

    /**
     * Die Aggregate kommen aus JPQL und koennen als {@code Double} ankommen, nicht als
     * {@link BigDecimal}.
     *
     * <p>Ein fester Cast auf {@code BigDecimal} flaege erst zur Laufzeit auf — in einer Query, die
     * kein Compiler liest.
     */
    @Test
    void getPrognose_MesswerteAlsDouble_WerdenUmgerechnet() {
        lernhistorie(MIN_PUNKTE);
        when(messwerteRepository.sumBilanzKomponentenPerZeitBetween(any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{ende(0), Double.valueOf(2.0), 0.0, 0.0, 0.0}));

        PrognosepunktDTO punkt = einzigerPunkt();

        assertEquals(new BigDecimal("0.02000000"), punkt.getFaktor());
    }

    /**
     * Ein {@code null}-Aggregat zaehlt als 0 und wirft nicht.
     *
     * <p>{@code COALESCE} in der Query sollte das verhindern; die Wache im Service ist die zweite
     * Reihe, und ein Nachtintervall ohne Produzenten-Messwert traefe sie.
     */
    @Test
    void getPrognose_MesswertAggregatNull_ZaehltAlsNull() {
        lernhistorie(MIN_PUNKTE);
        messwerte(
                new Object[]{ende(0), null, null, null, null},
                messwert(ende(1), "3.000"));

        PrognosepunktDTO punkt = einzigerPunkt();

        // 0 + 3.000 ueber 200 W/m²
        assertEquals(new BigDecimal("0.01500000"), punkt.getFaktor());
    }

    // ==================== Die Umrechnung der Prognosepunkte ====================

    /**
     * Die erwartete Erzeugung ist {@code gti × faktor}, auf drei Stellen gerundet — dieselbe
     * Genauigkeit wie {@code NUMERIC(12,3)} einer Energiemenge.
     *
     * <p>400 W/m² bei 0.00666667 kWh je W/m² sind 2.66666800 → 2.667.
     */
    @Test
    void getPrognose_RundetErwarteteErzeugungAufDreiStellen() {
        lernhistorie(MIN_PUNKTE);
        // 2.000 / 300 = 0.00666667 (8 Stellen, HALF_UP)
        List<Object[]> historie = gtiPunkte(MIN_PUNKTE);
        historie.get(0)[1] = new BigDecimal("300.00");
        when(prognoseRepository.findGtiJeIntervall(any(), any())).thenReturn(historie);
        messwerte(messwert(ende(0), "2.000"));

        PrognosepunktDTO punkt = einzigerPunkt();

        assertEquals(new BigDecimal("0.00666667"), punkt.getFaktor());
        assertEquals(new BigDecimal("2.667"), punkt.getErwarteteErzeugung());
        assertEquals(3, punkt.getErwarteteErzeugung().scale());
    }

    /**
     * Jeder Punkt des Tages traegt Zeit, Einstrahlung, Faktor und erwartete Erzeugung — in der
     * Reihenfolge, in der das Repository liefert.
     *
     * <p>Der Faktor steht bewusst an <b>jedem</b> Punkt: Wer der Prognose misstraut, soll die
     * Rechnung nachvollziehen koennen, statt sie glauben zu muessen.
     */
    @Test
    void getPrognose_MehrereIntervalle_UebernimmtZeitUndGtiUndFaktor() {
        lernhistorie(MIN_PUNKTE);
        messwerte(messwert(ende(0), "2.000"));
        when(prognoseRepository.findByZeitBetween(any(), any())).thenReturn(List.of(
                prognosepunkt(DATUM.atTime(12, 0), "400.00"),
                prognosepunkt(DATUM.atTime(12, 15), "0.00")));

        List<PrognosepunktDTO> punkte = produktionsprognoseService.getPrognose(DATUM);

        assertEquals(2, punkte.size());
        assertEquals(DATUM.atTime(12, 0), punkte.get(0).getZeit());
        assertEquals(new BigDecimal("400.00"), punkte.get(0).getGti());
        assertEquals(new BigDecimal("8.000"), punkte.get(0).getErwarteteErzeugung());
        assertEquals(DATUM.atTime(12, 15), punkte.get(1).getZeit());
        // Nachtintervall: 0 Einstrahlung ergibt 0 erwartete Erzeugung - hier ist die 0 richtig
        assertEquals(new BigDecimal("0.000"), punkte.get(1).getErwarteteErzeugung());
        assertEquals(new BigDecimal("0.02000000"), punkte.get(1).getFaktor());
    }

    // ==================== Konfiguration ====================

    /**
     * Ohne erfasste Tageszahl gilt die Vorgabe von 28 Tagen — die Historie beginnt 28 Tage vor dem
     * gefragten Tag.
     */
    @Test
    void getPrognose_OhneHistorieTage_LerntUeberDieVorgabe() {
        produktionsprognoseService.getPrognose(DATUM);

        verify(prognoseRepository).findGtiJeIntervall(
                eq(LocalDateTime.of(2026, 8, 28, 0, 0)),
                eq(LocalDateTime.of(2026, 9, 25, 0, 0)));
    }

    /**
     * Eine erfasste Tageszahl wird verwendet — und der gefragte Tag selbst bleibt
     * <b>ausgeschlossen</b>.
     *
     * <p>Sein Verlauf war zum Entscheidungszeitpunkt noch nicht bekannt. Fuer die Anzeige macht es
     * kaum einen Unterschied, fuer das spaetere Nachrechnen den ganzen: Der Faktor duerfte sonst
     * aus dem Tag lernen, den er vorhersagen soll.
     */
    @Test
    void getPrognose_MitHistorieTage_LerntUeberDenErfasstenZeitraum() {
        konfiguration.setHistorieTage(7);

        produktionsprognoseService.getPrognose(DATUM);

        verify(prognoseRepository).findGtiJeIntervall(
                eq(LocalDateTime.of(2026, 9, 18, 0, 0)),
                eq(LocalDateTime.of(2026, 9, 25, 0, 0)));
    }

    // ==================== Hilfsmittel ====================

    /** Ruft {@code getPrognose} und liefert den einen erwarteten Punkt des Tages. */
    private PrognosepunktDTO einzigerPunkt() {
        List<PrognosepunktDTO> punkte = produktionsprognoseService.getPrognose(DATUM);
        assertEquals(1, punkte.size());
        return punkte.get(0);
    }

    /** Einstrahlungshistorie mit {@code anzahl} hellen Intervallen zu je 100 W/m². */
    private void lernhistorie(int anzahl) {
        when(prognoseRepository.findGtiJeIntervall(any(), any())).thenReturn(gtiPunkte(anzahl));
    }

    /** Zeilen wie {@code findGtiJeIntervall} sie liefert: {@code [zeit (Beginn), gti]}. */
    private List<Object[]> gtiPunkte(int anzahl) {
        List<Object[]> zeilen = new ArrayList<>();
        for (int i = 0; i < anzahl; i++) {
            zeilen.add(new Object[]{HELL_START.plusMinutes(15L * i), new BigDecimal("100.00")});
        }
        return zeilen;
    }

    /** Intervall<b>ende</b> zum i-ten hellen Intervall — so, wie {@code messwerte.zeit} es traegt. */
    private LocalDateTime ende(int i) {
        return HELL_START.plusMinutes(15L * i).plusMinutes(15);
    }

    /** Zeile wie {@code sumBilanzKomponentenPerZeitBetween}: {@code [zeit (Ende), produktion, ...]}. */
    private Object[] messwert(LocalDateTime zeit, String produktion) {
        return new Object[]{zeit, new BigDecimal(produktion),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
    }

    private void messwerte(Object[]... zeilen) {
        when(messwerteRepository.sumBilanzKomponentenPerZeitBetween(any(), any()))
                .thenReturn(Arrays.asList(zeilen));
    }

    /** Zeilen wie {@code sumLadungEntladungPerZeitBetween}: {@code [zeit (Ende), ladung, entladung]}. */
    private void speicherfluss(Object[]... zeilen) {
        when(messwerteRepository.sumLadungEntladungPerZeitBetween(
                eq(EinheitTyp.SPEICHER), any(), any())).thenReturn(Arrays.asList(zeilen));
    }

    private Einstrahlungsprognose prognosepunkt(LocalDateTime zeit, String gti) {
        Einstrahlungsprognose wert = new Einstrahlungsprognose();
        wert.setOrgId(ORG_ID);
        wert.setZeit(zeit);
        wert.setGti(new BigDecimal(gti));
        wert.setAbgerufenAm(LocalDateTime.of(2026, 9, 25, 6, 0));
        return wert;
    }
}
