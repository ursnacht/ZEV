package ch.nacht.service;

import ch.nacht.entity.Einheit;
import ch.nacht.entity.EinheitTyp;
import ch.nacht.entity.Messwerte;
import ch.nacht.entity.Quelle;
import ch.nacht.entity.ZaehlerRohdaten;
import ch.nacht.repository.EinheitRepository;
import ch.nacht.repository.MesswerteRepository;
import ch.nacht.repository.ZaehlerRohdatenRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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

    private ZaehlerAggregationService service;

    private Einheit einheit;

    /** Intervallgrenzen, wie sie der Job im existsBy-Aufruf verwendet (start exklusiv, ende inklusiv). */
    private final LocalDateTime[] intervall = new LocalDateTime[2];

    @BeforeEach
    void setUp() {
        service = new ZaehlerAggregationService(rohdatenRepository, messwerteRepository, einheitRepository,
                messwerteService, metrics);

        einheit = new Einheit("Wohnung 1", EinheitTyp.CONSUMER);
        einheit.setId(EINHEIT_ID);
        einheit.setOrgId(ORG_ID);
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
}
