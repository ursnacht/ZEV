package ch.nacht.service;

import ch.nacht.entity.Einheit;
import ch.nacht.entity.EinheitTyp;
import ch.nacht.entity.MeldungLevel;
import ch.nacht.entity.Messwerte;
import ch.nacht.entity.Quelle;
import ch.nacht.entity.ZaehlerRohdaten;
import ch.nacht.repository.EinheitRepository;
import ch.nacht.repository.MesswerteRepository;
import ch.nacht.repository.ZaehlerRohdatenRepository;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit-Tests für den Aggregations-Job (FR-6): Delta-Bildung pro Register über 15-Min-Quartale,
 * vorzeichenbehafteter {@code total = ΔBezug − ΔEinspeisung}, Reset-Guard pro Register,
 * {@code zev = 0}, {@code quelle = MQTT}, Upsert in {@code messwerte}.
 *
 * <p>Kein Request-Scope: {@code org_id} wird explizit aus der Einheit übernommen. Da der Job
 * {@code LocalDateTime.now()} verwendet, werden die frühesten Rohdaten auf das aktuelle
 * Quartalsende gelegt (genau ein abgeschlossenes Intervall wird verarbeitet). Referenz- und
 * Intervallend-Stand werden über den tatsächlich angefragten Zeitstempel aufgelöst
 * (robust gegenüber Quartalsgrenzen).
 */
@ExtendWith(MockitoExtension.class)
public class ZaehlerAggregationServiceTest {

    private static final long ORG_ID = 100L;
    private static final long EINHEIT_ID = 1L;
    private static final int INTERVALL = 15;

    @Mock
    private ZaehlerRohdatenRepository rohdatenRepository;

    @Mock
    private MesswerteRepository messwerteRepository;

    @Mock
    private EinheitRepository einheitRepository;

    @Mock
    private MesswerteService messwerteService;

    @Mock
    private MqttMetrics metrics;

    @Mock
    private SystemmeldungService systemmeldungService;

    private ZaehlerAggregationService service;

    private Einheit einheit;

    /** Intervallgrenzen, wie sie der Job im existsBy-Aufruf verwendet (start exklusiv, ende inklusiv). */
    private final LocalDateTime[] intervall = new LocalDateTime[2];

    /** Sammelt die Log-Events des Service (Zählertausch-Erkennung wird per Log protokolliert). */
    private ListAppender<ILoggingEvent> logAppender;

    private ch.qos.logback.classic.Logger serviceLogger() {
        return (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ZaehlerAggregationService.class);
    }

    @BeforeEach
    void setUp() {
        service = new ZaehlerAggregationService(rohdatenRepository, messwerteRepository, einheitRepository,
                messwerteService, metrics, systemmeldungService);

        einheit = new Einheit("Wohnung 1", EinheitTyp.CONSUMER);
        einheit.setId(EINHEIT_ID);
        einheit.setOrgId(ORG_ID);

        logAppender = new ListAppender<>();
        logAppender.start();
        serviceLogger().setLevel(Level.DEBUG); // Rollout-Marker wird auf INFO geloggt
        serviceLogger().addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        serviceLogger().detachAppender(logAppender);
        serviceLogger().setLevel(null);
        logAppender.stop();
    }

    /** true, wenn ein Log-Event des gegebenen Levels alle Fragmente in der Meldung enthält. */
    private boolean loggedContaining(Level level, String... fragmente) {
        return logAppender.list.stream()
                .filter(e -> e.getLevel() == level)
                .anyMatch(e -> {
                    String msg = e.getFormattedMessage();
                    for (String f : fragmente) {
                        if (!msg.contains(f)) {
                            return false;
                        }
                    }
                    return true;
                });
    }

    private LocalDateTime floorAufQuartal(LocalDateTime t) {
        LocalDateTime m = t.truncatedTo(ChronoUnit.MINUTES);
        return m.minusMinutes(m.getMinute() % INTERVALL);
    }

    /** Verdrahtet Catch-up für genau ein abgeschlossenes Intervall (frühester Stand = letztes Quartalsende). */
    private void stubCatchUpEinInterval() {
        LocalDateTime letztesQuartalsende = floorAufQuartal(LocalDateTime.now());
        ZaehlerRohdaten earliest = new ZaehlerRohdaten(ORG_ID, EINHEIT_ID, letztesQuartalsende,
                BigDecimal.ZERO, BigDecimal.ZERO);

        when(rohdatenRepository.findEinheitIdsWithUnverarbeitet()).thenReturn(List.of(EINHEIT_ID));
        when(einheitRepository.findById(EINHEIT_ID)).thenReturn(Optional.of(einheit));
        when(rohdatenRepository.findFirstByEinheitIdAndVerarbeitetFalseOrderByZeitAsc(EINHEIT_ID))
                .thenReturn(Optional.of(earliest));
        when(rohdatenRepository.existsByEinheitIdAndZeitGreaterThanAndZeitLessThanEqual(eq(EINHEIT_ID), any(), any()))
                .thenAnswer(inv -> {
                    intervall[0] = inv.getArgument(1); // start
                    intervall[1] = inv.getArgument(2); // ende
                    return true;
                });
    }

    /** Löst Referenz-Stand (bei intervall-start) bzw. Intervallend-Stand (bei intervall-ende) auf. */
    private void stubStaende(ZaehlerRohdaten referenz, ZaehlerRohdaten letzter) {
        when(rohdatenRepository.findFirstByEinheitIdAndZeitLessThanEqualOrderByZeitDesc(eq(EINHEIT_ID), any()))
                .thenAnswer(inv -> {
                    LocalDateTime bis = inv.getArgument(1);
                    if (bis.equals(intervall[0])) {
                        return Optional.ofNullable(referenz);
                    }
                    return Optional.ofNullable(letzter);
                });
    }

    private ZaehlerRohdaten rohdaten(String bezug, String einspeisung) {
        return new ZaehlerRohdaten(ORG_ID, EINHEIT_ID, LocalDateTime.now(),
                new BigDecimal(bezug), new BigDecimal(einspeisung));
    }

    /** Stand inkl. Zähler-Seriennummer (Zählertausch-Erkennung); {@code null} = nicht gemeldet. */
    private ZaehlerRohdaten rohdaten(String bezug, String einspeisung, String seriennummer) {
        ZaehlerRohdaten r = rohdaten(bezug, einspeisung);
        r.setSeriennummer(seriennummer);
        return r;
    }

    /** Stand zu einem konkreten Zeitpunkt (für Zeitreihen über mehrere Intervalle). */
    private ZaehlerRohdaten rohdaten(LocalDateTime zeit, String bezug, String einspeisung, String seriennummer) {
        ZaehlerRohdaten r = new ZaehlerRohdaten(ORG_ID, EINHEIT_ID, zeit,
                new BigDecimal(bezug), new BigDecimal(einspeisung));
        r.setSeriennummer(seriennummer);
        return r;
    }

    /**
     * Variante von {@link #stubCatchUpEinInterval()} für Szenarien über mehrere Intervalle
     * (Rücktausch, Offline-Lücke): der früheste Stand der Zeitreihe startet den Catch-up, ein
     * Intervall gilt als belegt, wenn ein Stand in {@code (start, ende]} liegt, und Referenz-/
     * Endstand werden – wie im Repository – als jüngster Stand {@code <= angefragte Zeit} aufgelöst.
     */
    private void stubCatchUpZeitreihe(ZaehlerRohdaten... staende) {
        NavigableMap<LocalDateTime, ZaehlerRohdaten> reihe = new TreeMap<>();
        for (ZaehlerRohdaten r : staende) {
            reihe.put(r.getZeit(), r);
        }

        when(rohdatenRepository.findEinheitIdsWithUnverarbeitet()).thenReturn(List.of(EINHEIT_ID));
        when(einheitRepository.findById(EINHEIT_ID)).thenReturn(Optional.of(einheit));
        when(rohdatenRepository.findFirstByEinheitIdAndVerarbeitetFalseOrderByZeitAsc(EINHEIT_ID))
                .thenReturn(Optional.of(reihe.firstEntry().getValue()));
        when(rohdatenRepository.existsByEinheitIdAndZeitGreaterThanAndZeitLessThanEqual(eq(EINHEIT_ID), any(), any()))
                .thenAnswer(inv -> !reihe.subMap(inv.getArgument(1), false, inv.getArgument(2), true).isEmpty());
        when(rohdatenRepository.findFirstByEinheitIdAndZeitLessThanEqualOrderByZeitDesc(eq(EINHEIT_ID), any()))
                .thenAnswer(inv -> Optional.ofNullable(reihe.floorEntry(inv.getArgument(1)))
                        .map(Map.Entry::getValue));
    }

    private Messwerte captureSavedMesswert() {
        ArgumentCaptor<Messwerte> captor = ArgumentCaptor.forClass(Messwerte.class);
        verify(messwerteRepository, atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }

    // --- Vorzeichenbehafteter total ----------------------------------------

    @Test
    void aggregiere_BezugUeberwiegt_TotalPositiv() {
        stubCatchUpEinInterval();
        stubStaende(rohdaten("100.0", "50.0"), rohdaten("110.0", "52.0")); // ΔBezug=10, ΔEinsp=2
        when(messwerteRepository.findByEinheitAndZeit(eq(einheit), any())).thenReturn(Optional.empty());
        when(messwerteRepository.save(any(Messwerte.class))).thenAnswer(inv -> inv.getArgument(0));

        service.aggregiere();

        Messwerte m = captureSavedMesswert();
        assertEquals(8.0, m.getTotal(), 1e-9);
        assertEquals(0.0, m.getZev(), 1e-9);
        assertNull(m.getZevCalculated());
        assertEquals(Quelle.MQTT, m.getQuelle());
        assertEquals(ORG_ID, m.getOrgId());
        assertSame(einheit, m.getEinheit());
        verify(metrics).recordAggregationRun();
    }

    @Test
    void aggregiere_EinspeisungUeberwiegt_TotalNegativ() {
        stubCatchUpEinInterval();
        stubStaende(rohdaten("100.0", "50.0"), rohdaten("101.0", "70.0")); // ΔBezug=1, ΔEinsp=20
        when(messwerteRepository.findByEinheitAndZeit(eq(einheit), any())).thenReturn(Optional.empty());
        when(messwerteRepository.save(any(Messwerte.class))).thenAnswer(inv -> inv.getArgument(0));

        service.aggregiere();

        Messwerte m = captureSavedMesswert();
        assertEquals(-19.0, m.getTotal(), 1e-9);
        assertEquals(Quelle.MQTT, m.getQuelle());
    }

    // --- Producer: zev = total ---------------------------------------------

    @Test
    void aggregiere_Producer_ZevGleichTotal() {
        einheit = new Einheit("PV-Anlage", EinheitTyp.PRODUCER);
        einheit.setId(EINHEIT_ID);
        einheit.setOrgId(ORG_ID);
        stubCatchUpEinInterval();
        stubStaende(rohdaten("100.0", "50.0"), rohdaten("101.0", "70.0")); // ΔBezug=1, ΔEinsp=20 → total=-19
        when(messwerteRepository.findByEinheitAndZeit(eq(einheit), any())).thenReturn(Optional.empty());
        when(messwerteRepository.save(any(Messwerte.class))).thenAnswer(inv -> inv.getArgument(0));

        service.aggregiere();

        Messwerte m = captureSavedMesswert();
        assertEquals(-19.0, m.getTotal(), 1e-9);
        assertEquals(-19.0, m.getZev(), 1e-9); // Producer: zev = total (nicht Sentinel 0)
        assertNull(m.getZevCalculated());
        assertEquals(Quelle.MQTT, m.getQuelle());
    }

    // --- Wiederaufnahme nach längerem Unterbruch (ggf. mehrtägig) ----------

    @Test
    void aggregiere_WiederaufnahmeNachUnterbruch_GesamterVerbrauchImErstenIntervall() {
        // Referenz = letzter Vor-Unterbruch-Stand (Tage alt, i.d.R. bereits verarbeitet);
        // letzter = erster Stand nach Wiederaufnahme. Die Differenz überbrückt den gesamten
        // Unterbruch, weil die Referenz-Abfrage (findFirst...ZeitLessThanEqual) NICHT auf
        // `verarbeitet` filtert. Kein Datenverlust: der komplette Verbrauch fällt gebündelt in
        // das erste Intervall mit Meldung nach Wiederaufnahme.
        stubCatchUpEinInterval();
        stubStaende(rohdaten("1000.0", "200.0"), rohdaten("1250.0", "200.0")); // ΔBezug=250 über mehrere Tage
        when(messwerteRepository.findByEinheitAndZeit(eq(einheit), any())).thenReturn(Optional.empty());
        when(messwerteRepository.save(any(Messwerte.class))).thenAnswer(inv -> inv.getArgument(0));

        service.aggregiere();

        Messwerte m = captureSavedMesswert();
        assertEquals(250.0, m.getTotal(), 1e-9); // gesamter Unterbruch-Verbrauch, nichts verloren
        assertEquals(Quelle.MQTT, m.getQuelle());
    }

    // --- Reset-Guard pro Register ------------------------------------------

    @Test
    void aggregiere_RegisterRuecksprung_DeltaAufNull() {
        stubCatchUpEinInterval();
        // Bezug springt zurück (Reset) → ΔBezug auf 0; Einspeisung normal ΔEinsp=10
        stubStaende(rohdaten("100.0", "50.0"), rohdaten("30.0", "60.0"));
        when(messwerteRepository.findByEinheitAndZeit(eq(einheit), any())).thenReturn(Optional.empty());
        when(messwerteRepository.save(any(Messwerte.class))).thenAnswer(inv -> inv.getArgument(0));

        service.aggregiere();

        Messwerte m = captureSavedMesswert();
        // total = 0 (Bezug geguardet) − 10 (Einspeisung) = -10
        assertEquals(-10.0, m.getTotal(), 1e-9);
    }

    // --- Fehlende Referenz / leeres Intervall ------------------------------

    @Test
    void aggregiere_KeineReferenz_KeinMesswert() {
        stubCatchUpEinInterval();
        stubStaende(null, rohdaten("110.0", "52.0")); // erste Messung: keine Referenz

        service.aggregiere();

        verify(messwerteRepository, never()).save(any());
    }

    @Test
    void aggregiere_LeeresIntervall_KeinEintrag() {
        LocalDateTime letztesQuartalsende = floorAufQuartal(LocalDateTime.now());
        ZaehlerRohdaten earliest = new ZaehlerRohdaten(ORG_ID, EINHEIT_ID, letztesQuartalsende,
                BigDecimal.ZERO, BigDecimal.ZERO);
        when(rohdatenRepository.findEinheitIdsWithUnverarbeitet()).thenReturn(List.of(EINHEIT_ID));
        when(einheitRepository.findById(EINHEIT_ID)).thenReturn(Optional.of(einheit));
        when(rohdatenRepository.findFirstByEinheitIdAndVerarbeitetFalseOrderByZeitAsc(EINHEIT_ID))
                .thenReturn(Optional.of(earliest));
        when(rohdatenRepository.existsByEinheitIdAndZeitGreaterThanAndZeitLessThanEqual(eq(EINHEIT_ID), any(), any()))
                .thenReturn(false); // kein neuer Messwert im Intervall

        service.aggregiere();

        verify(messwerteRepository, never()).save(any());
        verify(rohdatenRepository, never()).markVerarbeitet(anyLong(), any(), any());
        verify(metrics).recordAggregationRun();
    }

    // --- Upsert -------------------------------------------------------------

    @Test
    void aggregiere_BestehenderMesswert_WirdAktualisiert() {
        stubCatchUpEinInterval();
        stubStaende(rohdaten("100.0", "50.0"), rohdaten("110.0", "52.0")); // total=8
        Messwerte existing = new Messwerte();
        existing.setEinheit(einheit);
        existing.setOrgId(ORG_ID);
        existing.setTotal(-999.0);
        existing.setQuelle(Quelle.MQTT);
        when(messwerteRepository.findByEinheitAndZeit(eq(einheit), any())).thenReturn(Optional.of(existing));
        when(messwerteRepository.save(any(Messwerte.class))).thenAnswer(inv -> inv.getArgument(0));

        service.aggregiere();

        ArgumentCaptor<Messwerte> captor = ArgumentCaptor.forClass(Messwerte.class);
        verify(messwerteRepository, atLeastOnce()).save(captor.capture());
        assertSame(existing, captor.getValue());
        assertEquals(8.0, captor.getValue().getTotal(), 1e-9);
    }

    // --- Marker / Housekeeping ---------------------------------------------

    @Test
    void aggregiere_ProcessedInterval_MarkiertRohdatenVerarbeitet() {
        stubCatchUpEinInterval();
        stubStaende(rohdaten("100.0", "50.0"), rohdaten("110.0", "52.0"));
        when(messwerteRepository.findByEinheitAndZeit(eq(einheit), any())).thenReturn(Optional.empty());
        when(messwerteRepository.save(any(Messwerte.class))).thenAnswer(inv -> inv.getArgument(0));

        service.aggregiere();

        verify(rohdatenRepository, atLeastOnce()).markVerarbeitet(eq(EINHEIT_ID), any(), any());
    }

    // --- Solarverteilung nach Aggregation (FR-6.7) -------------------------

    @Test
    void aggregiere_RuftSolarverteilungFuerBehandeltenZeitraum() {
        stubCatchUpEinInterval();
        stubStaende(rohdaten("100.0", "50.0"), rohdaten("110.0", "52.0"));
        when(messwerteRepository.findByEinheitAndZeit(eq(einheit), any())).thenReturn(Optional.empty());
        when(messwerteRepository.save(any(Messwerte.class))).thenAnswer(inv -> inv.getArgument(0));

        service.aggregiere();

        // Ein Intervall verarbeitet → Verteilung für ORG_ID über [start, ende] mit PROPORTIONAL,
        // ohne Fortschritts-Tracking (showProgress = false)
        verify(messwerteService).calculateSolarDistributionForOrg(
                eq(ORG_ID), eq(intervall[0]), eq(intervall[1]), eq("PROPORTIONAL"), eq(false));
    }

    @Test
    void aggregiere_KeinMesswert_KeineSolarverteilung() {
        stubCatchUpEinInterval();
        stubStaende(null, rohdaten("110.0", "52.0")); // keine Referenz → kein Messwert

        service.aggregiere();

        verify(messwerteService, never()).calculateSolarDistributionForOrg(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void aggregiere_EinheitNichtGefunden_Uebersprungen() {
        when(rohdatenRepository.findEinheitIdsWithUnverarbeitet()).thenReturn(List.of(EINHEIT_ID));
        when(einheitRepository.findById(EINHEIT_ID)).thenReturn(Optional.empty());

        service.aggregiere();

        verify(messwerteRepository, never()).save(any());
        verify(metrics).recordAggregationRun();
    }

    @Test
    void aggregiere_KeineUnverarbeiteten_NoOp() {
        when(rohdatenRepository.findEinheitIdsWithUnverarbeitet()).thenReturn(List.of());

        service.aggregiere();

        verify(messwerteRepository, never()).save(any());
        verify(einheitRepository, never()).findById(anyLong());
        verify(metrics).recordAggregationRun();
    }

    // --- Zählertausch-Erkennung (Spec Zaehlertausch-Erkennung.md, FR-3/FR-4) -----------------

    @Test
    void aggregiere_SerienWechselNeuerZaehlerHoeher_KeinMesswert() {
        // Arrange – Regressionstest zum dokumentierten Blindspot: der neue Zähler startet HÖHER
        // (10 000 → 50 000). Ohne Serien-Vergleich entstünde ein positiver Bogus-Wert von 40 000.
        stubCatchUpEinInterval();
        stubStaende(rohdaten("10000.0", "0.0", "SN-ALT"), rohdaten("50000.0", "0.0", "SN-NEU"));

        // Act
        service.aggregiere();

        // Assert – kein Messwert, keine Verteilung; die Rohdaten werden dennoch als verarbeitet markiert
        verify(messwerteRepository, never()).save(any());
        verify(messwerteRepository, never()).findByEinheitAndZeit(any(), any());
        verify(messwerteService, never()).calculateSolarDistributionForOrg(any(), any(), any(), any(), anyBoolean());
        verify(rohdatenRepository, atLeastOnce()).markVerarbeitet(eq(EINHEIT_ID), any(), any());
    }

    @Test
    void aggregiere_SerienWechselNeuerZaehlerNiedriger_KeinMesswert() {
        // Arrange – niedriger startender neuer Zähler: bisher hätte der Reset-Guard das Delta
        // genullt und trotzdem einen Messwert geschrieben.
        stubCatchUpEinInterval();
        stubStaende(rohdaten("10000.0", "500.0", "SN-ALT"), rohdaten("12.0", "0.0", "SN-NEU"));

        // Act
        service.aggregiere();

        // Assert
        verify(messwerteRepository, never()).save(any());
        verify(messwerteService, never()).calculateSolarDistributionForOrg(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void aggregiere_SerienWechsel_LoggtEinheitIntervallUndBeideSerien() {
        // Arrange
        stubCatchUpEinInterval();
        stubStaende(rohdaten("10000.0", "0.0", "SN-ALT"), rohdaten("50000.0", "0.0", "SN-NEU"));

        // Act
        service.aggregiere();

        // Assert – Nachvollziehbarkeit (FR-3.4)
        assertTrue(loggedContaining(Level.WARN, "Zählerwechsel erkannt",
                        String.valueOf(EINHEIT_ID), intervall[0].toString(), intervall[1].toString(),
                        "SN-ALT", "SN-NEU"),
                "WARN mit Einheit, Intervall und alter/neuer Serie erwartet, war: " + logAppender.list);
    }

    @Test
    void aggregiere_SerienWechsel_ErfasstInfoSystemmeldung() {
        // Arrange
        stubCatchUpEinInterval();
        stubStaende(rohdaten("10000.0", "0.0", "SN-ALT"), rohdaten("50000.0", "0.0", "SN-NEU"));

        // Act
        service.aggregiere();

        // Assert – Audit-Eintrag (ein Eintrag je Ereignis, kein Dedup)
        ArgumentCaptor<String> parameter = ArgumentCaptor.forClass(String.class);
        verify(systemmeldungService).erfasseAudit(eq(ORG_ID), eq(MeldungLevel.INFO),
                eq(SystemmeldungService.KATEGORIE_MQTT), eq(SystemmeldungService.KEY_ZAEHLERTAUSCH),
                parameter.capture());
        assertTrue(parameter.getValue().contains("Wohnung 1"), parameter.getValue());
        assertTrue(parameter.getValue().contains("SN-ALT"), parameter.getValue());
        assertTrue(parameter.getValue().contains("SN-NEU"), parameter.getValue());
        // Nicht deduplizierend erfassen – sonst überschreibt ein zweiter Tausch den ersten.
        verify(systemmeldungService, never()).erfasse(any(), any(),
                eq(SystemmeldungService.KATEGORIE_MQTT), eq(SystemmeldungService.KEY_ZAEHLERTAUSCH),
                anyString());
    }

    @Test
    void aggregiere_GleicheSeriennummer_KeineZaehlertauschMeldung() {
        // Arrange – kein Wechsel
        stubCatchUpEinInterval();
        stubStaende(rohdaten("100.0", "50.0", "SN-1"), rohdaten("110.0", "52.0", "SN-1"));
        when(messwerteRepository.findByEinheitAndZeit(eq(einheit), any())).thenReturn(Optional.empty());
        when(messwerteRepository.save(any(Messwerte.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        service.aggregiere();

        // Assert
        verify(systemmeldungService, never()).erfasseAudit(any(), any(), anyString(), anyString(),
                anyString());
    }

    @Test
    void aggregiere_SerienWechselMeldungSchlaegtFehl_AggregationLaeuftWeiter() {
        // Arrange – das Erfassen der Meldung darf die Aggregation nicht abbrechen
        stubCatchUpEinInterval();
        stubStaende(rohdaten("10000.0", "0.0", "SN-ALT"), rohdaten("50000.0", "0.0", "SN-NEU"));
        doThrow(new RuntimeException("DB weg")).when(systemmeldungService)
                .erfasseAudit(any(), any(), anyString(), anyString(), anyString());

        // Act + Assert – keine Exception nach aussen
        service.aggregiere();
        verify(messwerteRepository, never()).save(any());
        assertTrue(loggedContaining(Level.WARN, "Zählerwechsel erkannt"));
    }

    @Test
    void aggregiere_SeriennummerNurCaseUnterschied_KeinMesswert() {
        // Arrange – der Vergleich ist case-sensitive: "ABC123" != "abc123"
        stubCatchUpEinInterval();
        stubStaende(rohdaten("100.0", "50.0", "ABC123"), rohdaten("110.0", "52.0", "abc123"));

        // Act
        service.aggregiere();

        // Assert
        verify(messwerteRepository, never()).save(any());
    }

    @Test
    void aggregiere_SerienWechselMitBestehendemMesswert_MesswertUnveraendert() {
        // Arrange – für das Übergangsintervall existiert bereits ein (Alt-)Messwert
        stubCatchUpEinInterval();
        stubStaende(rohdaten("10000.0", "0.0", "SN-ALT"), rohdaten("50000.0", "0.0", "SN-NEU"));
        Messwerte bestehend = new Messwerte();
        bestehend.setEinheit(einheit);
        bestehend.setOrgId(ORG_ID);
        bestehend.setTotal(40000.0); // vor Einführung der Erkennung geschriebener Bogus-Wert
        bestehend.setZev(0.0);
        bestehend.setQuelle(Quelle.MQTT);
        lenient().when(messwerteRepository.findByEinheitAndZeit(eq(einheit), any()))
                .thenReturn(Optional.of(bestehend));

        // Act
        service.aggregiere();

        // Assert – nicht überschrieben, nicht genullt, nicht gelöscht
        verify(messwerteRepository, never()).save(any());
        verify(messwerteRepository, never()).delete(any());
        assertEquals(40000.0, bestehend.getTotal(), 1e-9);
        assertEquals(Quelle.MQTT, bestehend.getQuelle());
    }

    @Test
    void aggregiere_GleicheSeriennummer_MesswertMitDelta() {
        // Arrange – kein Wechsel: normaler Delta-Pfad
        stubCatchUpEinInterval();
        stubStaende(rohdaten("100.0", "50.0", "SN-1"), rohdaten("110.0", "52.0", "SN-1"));
        when(messwerteRepository.findByEinheitAndZeit(eq(einheit), any())).thenReturn(Optional.empty());
        when(messwerteRepository.save(any(Messwerte.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        service.aggregiere();

        // Assert
        assertEquals(8.0, captureSavedMesswert().getTotal(), 1e-9);
    }

    @Test
    void aggregiere_GleicheSerieTrotzRuecksprung_ResetGuardWieBisher() {
        // Arrange – physischer Tausch ohne Pi-Config-Update (dokumentiertes Restrisiko):
        // die Serie bleibt gleich → keine Erkennung, es greift wie bisher der Reset-Guard.
        stubCatchUpEinInterval();
        stubStaende(rohdaten("100.0", "50.0", "SN-1"), rohdaten("30.0", "60.0", "SN-1"));
        when(messwerteRepository.findByEinheitAndZeit(eq(einheit), any())).thenReturn(Optional.empty());
        when(messwerteRepository.save(any(Messwerte.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        service.aggregiere();

        // Assert – ΔBezug geguardet auf 0, ΔEinspeisung 10 → total = -10
        assertEquals(-10.0, captureSavedMesswert().getTotal(), 1e-9);
    }

    @Test
    void aggregiere_ReferenzSerieNull_FallbackMitMesswertUndRolloutMarker() {
        // Arrange – gemischter Rollout: Referenz ohne, Endstand mit Serie
        stubCatchUpEinInterval();
        stubStaende(rohdaten("100.0", "50.0", null), rohdaten("110.0", "52.0", "SN-NEU"));
        when(messwerteRepository.findByEinheitAndZeit(eq(einheit), any())).thenReturn(Optional.empty());
        when(messwerteRepository.save(any(Messwerte.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        service.aggregiere();

        // Assert – kein Baseline-Reset (Delta wie bisher) + Log-Hinweis (FR-4.2)
        assertEquals(8.0, captureSavedMesswert().getTotal(), 1e-9);
        assertTrue(loggedContaining(Level.INFO, "Seriennummer erstmals vorhanden",
                        String.valueOf(EINHEIT_ID), intervall[0].toString(), intervall[1].toString(), "SN-NEU"),
                "INFO-Rollout-Marker erwartet, war: " + logAppender.list);
    }

    @Test
    void aggregiere_EndSerieNull_FallbackMitMesswertOhneRolloutMarker() {
        // Arrange – Serie fällt weg (z. B. Pi-Downgrade): kein sicherer Vergleich möglich
        stubCatchUpEinInterval();
        stubStaende(rohdaten("100.0", "50.0", "SN-ALT"), rohdaten("110.0", "52.0", null));
        when(messwerteRepository.findByEinheitAndZeit(eq(einheit), any())).thenReturn(Optional.empty());
        when(messwerteRepository.save(any(Messwerte.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        service.aggregiere();

        // Assert
        assertEquals(8.0, captureSavedMesswert().getTotal(), 1e-9);
        assertFalse(loggedContaining(Level.WARN, "Zählerwechsel erkannt"));
        assertFalse(loggedContaining(Level.INFO, "Seriennummer erstmals vorhanden"));
    }

    @Test
    void aggregiere_BeideSeriennummernNull_FallbackWieBisher() {
        // Arrange – Bestandsdaten / Mandant ohne Pi-Update
        stubCatchUpEinInterval();
        stubStaende(rohdaten("100.0", "50.0", null), rohdaten("110.0", "52.0", null));
        when(messwerteRepository.findByEinheitAndZeit(eq(einheit), any())).thenReturn(Optional.empty());
        when(messwerteRepository.save(any(Messwerte.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        service.aggregiere();

        // Assert
        assertEquals(8.0, captureSavedMesswert().getTotal(), 1e-9);
        assertFalse(loggedContaining(Level.WARN, "Zählerwechsel erkannt"));
    }

    @Test
    void aggregiere_OfflineLueckeUeberTausch_NurUebergangsintervallOhneMesswert() {
        // Arrange – letzter Stand des alten Zählers (Serie A) bei T0, erster Stand des neuen
        // Zählers (Serie B) erst 5 Intervalle später; dazwischen keine Daten.
        LocalDateTime q = floorAufQuartal(LocalDateTime.now());
        stubCatchUpZeitreihe(
                rohdaten(q.minusMinutes(90), "1000.0", "0.0", "SN-A"),  // T0 (letzter alter Stand)
                rohdaten(q.minusMinutes(15), "50.0", "0.0", "SN-B"),    // T5 (erster neuer Stand)
                rohdaten(q, "60.0", "0.0", "SN-B"));                    // T6
        when(messwerteRepository.findByEinheitAndZeit(eq(einheit), any())).thenReturn(Optional.empty());
        when(messwerteRepository.save(any(Messwerte.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        service.aggregiere();

        // Assert – genau ein Messwert: das Übergangsintervall (T5) bleibt leer, die datenlosen
        // Intervalle davor ebenfalls; das erste vollständig im neuen Zähler liegende Intervall rechnet.
        ArgumentCaptor<Messwerte> captor = ArgumentCaptor.forClass(Messwerte.class);
        verify(messwerteRepository, times(1)).save(captor.capture());
        assertEquals(10.0, captor.getValue().getTotal(), 1e-9);
        assertEquals(q, captor.getValue().getZeit());
        assertTrue(loggedContaining(Level.WARN, "Zählerwechsel erkannt", "SN-A", "SN-B"));
    }

    @Test
    void aggregiere_RuecktauschAufAltenZaehler_ZweiUebergangsintervalleOhneMesswert() {
        // Arrange – A → B → A (Rücktausch): jeder Wechsel wird eigenständig behandelt
        LocalDateTime q = floorAufQuartal(LocalDateTime.now());
        stubCatchUpZeitreihe(
                rohdaten(q.minusMinutes(60), "100.0", "0.0", "SN-A"),
                rohdaten(q.minusMinutes(45), "110.0", "0.0", "SN-A"),  // A→A: Delta 10
                rohdaten(q.minusMinutes(30), "5.0", "0.0", "SN-B"),    // A→B: Übergang
                rohdaten(q.minusMinutes(15), "12.0", "0.0", "SN-B"),   // B→B: Delta 7
                rohdaten(q, "120.0", "0.0", "SN-A"));                  // B→A: Übergang
        when(messwerteRepository.findByEinheitAndZeit(eq(einheit), any())).thenReturn(Optional.empty());
        when(messwerteRepository.save(any(Messwerte.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        service.aggregiere();

        // Assert – zwei Übergangsintervalle ohne Messwert, die Intervalle dazwischen rechnen korrekt
        ArgumentCaptor<Messwerte> captor = ArgumentCaptor.forClass(Messwerte.class);
        verify(messwerteRepository, times(2)).save(captor.capture());
        assertEquals(10.0, captor.getAllValues().get(0).getTotal(), 1e-9);
        assertEquals(7.0, captor.getAllValues().get(1).getTotal(), 1e-9);
        assertTrue(loggedContaining(Level.WARN, "Zählerwechsel erkannt", "SN-A -> SN-B"));
        assertTrue(loggedContaining(Level.WARN, "Zählerwechsel erkannt", "SN-B -> SN-A"));
    }

    // --- Monitoring von Datenlücken (Zähler-Ausfall) ------------------------

    @Test
    void aggregiere_LueckenlosOhneAusfall_KeineSystemmeldung() {
        // Referenz liegt im unmittelbar vorangehenden Intervall → keine Lücke.
        LocalDateTime q = floorAufQuartal(LocalDateTime.now());
        stubCatchUpZeitreihe(
                rohdaten(q.minusMinutes(15), "100.0", "0.0", null),
                rohdaten(q, "110.0", "0.0", null));
        when(messwerteRepository.findByEinheitAndZeit(eq(einheit), any())).thenReturn(Optional.empty());
        when(messwerteRepository.save(any(Messwerte.class))).thenAnswer(inv -> inv.getArgument(0));

        service.aggregiere();

        verify(systemmeldungService, never()).erfasse(any(), any(), any(), any(), any());
    }

    @Test
    void aggregiere_EinIntervallOhneDaten_ErfasstInfoMeldung() {
        // Lücke von genau einem Intervall: Stand bei q-30 fehlt.
        LocalDateTime q = floorAufQuartal(LocalDateTime.now());
        stubCatchUpZeitreihe(
                rohdaten(q.minusMinutes(30), "100.0", "0.0", null),
                rohdaten(q, "130.0", "0.0", null));
        when(messwerteRepository.findByEinheitAndZeit(eq(einheit), any())).thenReturn(Optional.empty());
        when(messwerteRepository.save(any(Messwerte.class))).thenAnswer(inv -> inv.getArgument(0));

        service.aggregiere();

        verify(systemmeldungService).erfasse(eq(ORG_ID), eq(MeldungLevel.INFO),
                eq(SystemmeldungService.KATEGORIE_MQTT), eq(SystemmeldungService.KEY_ZAEHLER_LUECKE),
                contains("Wohnung 1"));
        // Der Verbrauch geht nicht verloren – er fällt gebündelt in das Folgeintervall.
        assertEquals(30.0, captureSavedMesswert().getTotal(), 1e-9);
    }

    @Test
    void aggregiere_MehrereIntervalleOhneDaten_ErfasstWarnMeldung() {
        // Lücke über drei Intervalle (q-60 → q).
        LocalDateTime q = floorAufQuartal(LocalDateTime.now());
        stubCatchUpZeitreihe(
                rohdaten(q.minusMinutes(60), "100.0", "0.0", null),
                rohdaten(q, "190.0", "0.0", null));
        when(messwerteRepository.findByEinheitAndZeit(eq(einheit), any())).thenReturn(Optional.empty());
        when(messwerteRepository.save(any(Messwerte.class))).thenAnswer(inv -> inv.getArgument(0));

        service.aggregiere();

        verify(systemmeldungService).erfasse(eq(ORG_ID), eq(MeldungLevel.WARN),
                eq(SystemmeldungService.KATEGORIE_MQTT), eq(SystemmeldungService.KEY_ZAEHLER_AUSFALL),
                contains("3 Intervalle"));
        verify(systemmeldungService, never()).erfasse(any(), eq(MeldungLevel.INFO), any(), any(), any());
    }
}
