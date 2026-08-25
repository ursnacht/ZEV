package ch.nacht.service;

import ch.nacht.entity.Erfassungsart;
import ch.nacht.entity.Einheit;
import ch.nacht.entity.EinheitTyp;
import ch.nacht.entity.Mengeneinheit;
import ch.nacht.entity.Tarif;
import ch.nacht.entity.TarifTyp;
import ch.nacht.entity.Tarifposition;
import ch.nacht.repository.EinheitRepository;
import ch.nacht.repository.TarifRepository;
import ch.nacht.repository.TarifpositionRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit-Tests für {@link TarifpositionService} (Specs/Ladestationen.md).
 *
 * <p>Schwerpunkte: Auflösung und Validierung von Einheit/Tarif, die Regel „höchstens eine Position
 * je Einheit, Quartal und Tariftyp", die Beschränkung auf manuell erfasste Tariftypen, die
 * Mandanten-Zuordnung beim Anlegen bzw. Beibehalten beim Ändern sowie die Quartals-Hilfsmethoden.
 */
@ExtendWith(MockitoExtension.class)
public class TarifpositionServiceTest {

    @Mock
    private TarifpositionRepository tarifpositionRepository;

    @Mock
    private EinheitRepository einheitRepository;

    @Mock
    private TarifRepository tarifRepository;

    // IMMER mocken - Multi-Tenancy Dependencies
    @Mock
    private OrganizationContextService organizationContextService;

    @Mock
    private HibernateFilterService hibernateFilterService;

    @InjectMocks
    private TarifpositionService tarifpositionService;

    private Long testOrgId;
    private Einheit testEinheit;
    private Tarif ladestromTarif;
    /** Grundgebuehr ist NICHT erfassbar - Begruendung siehe TarifTyp.GRUNDGEBUEHR. */
    private Tarif grundgebuehrTarif;
    /** Frei konfigurierbarer Typ: auch an Wohnungen erfassbar, Eindeutigkeit je Tarif. */
    private Tarif zusatzTarif;
    private Tarifposition testPosition1;
    private Tarifposition testPosition2;

    @BeforeEach
    void setUp() {
        testOrgId = 1L;

        testEinheit = new Einheit("Ladestation 1", EinheitTyp.LADESTATION);
        testEinheit.setId(1L);
        testEinheit.setMesspunkt("RFID-001");
        testEinheit.setOrgId(testOrgId);

        ladestromTarif = new Tarif(
                "Ladestrom",
                TarifTyp.LADESTROM,
                new BigDecimal("0.35000"),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31));
        ladestromTarif.setId(10L);
        ladestromTarif.setOrgId(testOrgId);

        grundgebuehrTarif = new Tarif(
                "Grundgebühr Ladestation",
                TarifTyp.GRUNDGEBUEHR,
                new BigDecimal("5.00000"),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31));
        grundgebuehrTarif.setId(21L);
        grundgebuehrTarif.setOrgId(testOrgId);

        zusatzTarif = new Tarif(
                "Sauna",
                TarifTyp.ZUSATZ,
                new BigDecimal("5.00000"),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31));
        zusatzTarif.setId(30L);
        zusatzTarif.setOrgId(testOrgId);
        zusatzTarif.setMengeneinheit(Mengeneinheit.STUECK);

        testPosition1 = new Tarifposition(testEinheit, ladestromTarif, 2026, 3, new BigDecimal("120.500"));
        testPosition1.setId(1L);
        testPosition1.setOrgId(testOrgId);
        testPosition1.setQuellReferenz("LP-01");

        testPosition2 = new Tarifposition(testEinheit, ladestromTarif, 2026, 4, new BigDecimal("80.000"));
        testPosition2.setId(2L);
        testPosition2.setOrgId(testOrgId);
    }

    // ==================== getByMieter ====================

    @Test
    void getByMieter_ReturnsList() {
        when(tarifpositionRepository.findByEinheitId(1L))
                .thenReturn(Arrays.asList(testPosition1, testPosition2));

        List<Tarifposition> result = tarifpositionService.getByEinheit(1L);

        assertEquals(2, result.size());
        verify(hibernateFilterService).enableOrgFilter();
        verify(tarifpositionRepository).findByEinheitId(1L);
    }

    @Test
    void getByMieter_NoPositions_ReturnsEmptyList() {
        when(tarifpositionRepository.findByEinheitId(99L)).thenReturn(Collections.emptyList());

        List<Tarifposition> result = tarifpositionService.getByEinheit(99L);

        assertTrue(result.isEmpty());
        verify(hibernateFilterService).enableOrgFilter();
    }

    // ==================== getTarifpositionById ====================

    @Test
    void getTarifpositionById_Found_ReturnsTarifposition() {
        when(tarifpositionRepository.findFirstById(1L)).thenReturn(Optional.of(testPosition1));

        Optional<Tarifposition> result = tarifpositionService.getTarifpositionById(1L);

        assertTrue(result.isPresent());
        assertEquals(2026, result.get().getJahr());
        verify(hibernateFilterService).enableOrgFilter();
    }

    @Test
    void getTarifpositionById_NotFound_ReturnsEmpty() {
        when(tarifpositionRepository.findFirstById(99L)).thenReturn(Optional.empty());

        Optional<Tarifposition> result = tarifpositionService.getTarifpositionById(99L);

        assertTrue(result.isEmpty());
    }

    // ==================== getFuerRechnung ====================

    @Test
    void getFuerRechnung_TranslatesPeriodIntoQuarterBounds() {
        LocalDate von = LocalDate.of(2026, 7, 1);
        LocalDate bis = LocalDate.of(2026, 9, 30);
        when(tarifpositionRepository.findByEinheitIdsAndQuartalOverlapping(List.of(1L), 2026, 3, 2026, 3))
                .thenReturn(List.of(testPosition1));

        List<Tarifposition> result = tarifpositionService.getFuerRechnung(List.of(1L), von, bis);

        assertEquals(1, result.size());
        verify(hibernateFilterService).enableOrgFilter();
        verify(tarifpositionRepository).findByEinheitIdsAndQuartalOverlapping(List.of(1L), 2026, 3, 2026, 3);
    }

    @Test
    void getFuerRechnung_PeriodAcrossYearBoundary_UsesBothQuarters() {
        // Halbjahresrechnung Q4/2026 - Q1/2027
        LocalDate von = LocalDate.of(2026, 10, 1);
        LocalDate bis = LocalDate.of(2027, 3, 31);
        when(tarifpositionRepository.findByEinheitIdsAndQuartalOverlapping(List.of(1L), 2026, 4, 2027, 1))
                .thenReturn(Arrays.asList(testPosition2, testPosition1));

        List<Tarifposition> result = tarifpositionService.getFuerRechnung(List.of(1L), von, bis);

        assertEquals(2, result.size());
        verify(tarifpositionRepository).findByEinheitIdsAndQuartalOverlapping(List.of(1L), 2026, 4, 2027, 1);
    }

    @Test
    void getFuerRechnung_PartialQuarter_StillQueriesTheOverlappingQuarter() {
        // Mieterwechsel: Rechnung deckt nur die zweite Haelfte von Q1 ab
        LocalDate von = LocalDate.of(2026, 3, 1);
        LocalDate bis = LocalDate.of(2026, 3, 31);
        when(tarifpositionRepository.findByEinheitIdsAndQuartalOverlapping(List.of(1L), 2026, 1, 2026, 1))
                .thenReturn(List.of(testPosition1));

        List<Tarifposition> result = tarifpositionService.getFuerRechnung(List.of(1L), von, bis);

        assertEquals(1, result.size());
        verify(tarifpositionRepository).findByEinheitIdsAndQuartalOverlapping(List.of(1L), 2026, 1, 2026, 1);
    }

    // ==================== Quartals-Hilfsmethoden ====================

    @Test
    void quartalVon_ReturnsQuarterOfDate() {
        assertEquals(1, TarifpositionService.quartalVon(LocalDate.of(2026, 1, 1)));
        assertEquals(1, TarifpositionService.quartalVon(LocalDate.of(2026, 3, 31)));
        assertEquals(2, TarifpositionService.quartalVon(LocalDate.of(2026, 4, 1)));
        assertEquals(3, TarifpositionService.quartalVon(LocalDate.of(2026, 9, 30)));
        assertEquals(4, TarifpositionService.quartalVon(LocalDate.of(2026, 12, 31)));
    }

    @Test
    void quartalBeginn_ReturnsFirstDayOfQuarter() {
        assertEquals(LocalDate.of(2026, 1, 1), TarifpositionService.quartalBeginn(2026, 1));
        assertEquals(LocalDate.of(2026, 4, 1), TarifpositionService.quartalBeginn(2026, 2));
        assertEquals(LocalDate.of(2026, 7, 1), TarifpositionService.quartalBeginn(2026, 3));
        assertEquals(LocalDate.of(2026, 10, 1), TarifpositionService.quartalBeginn(2026, 4));
    }

    @Test
    void quartalEnde_ReturnsLastDayOfQuarter() {
        assertEquals(LocalDate.of(2026, 3, 31), TarifpositionService.quartalEnde(2026, 1));
        assertEquals(LocalDate.of(2026, 6, 30), TarifpositionService.quartalEnde(2026, 2));
        assertEquals(LocalDate.of(2026, 9, 30), TarifpositionService.quartalEnde(2026, 3));
        assertEquals(LocalDate.of(2026, 12, 31), TarifpositionService.quartalEnde(2026, 4));
    }

    @Test
    void quartalEnde_LeapYearQ1_ReturnsMarch31() {
        // Schaltjahr: Q1 endet trotzdem am 31.03.
        assertEquals(LocalDate.of(2024, 3, 31), TarifpositionService.quartalEnde(2024, 1));
    }

    // ==================== saveTarifposition ====================

    @Test
    void saveTarifposition_ValidNewPosition_SavesSuccessfully() {
        Tarifposition neu = new Tarifposition(testEinheit, ladestromTarif, 2026, 2, new BigDecimal("42.000"));

        when(einheitRepository.findFirstById(1L)).thenReturn(Optional.of(testEinheit));
        when(tarifRepository.findFirstById(10L)).thenReturn(Optional.of(ladestromTarif));
        when(tarifpositionRepository.existsByEinheitAndQuartalAndTariftyp(
                eq(1L), eq(2026), eq(2), anySet(), eq(-1L))).thenReturn(false);
        when(organizationContextService.getCurrentOrgId()).thenReturn(testOrgId);
        when(tarifpositionRepository.save(any(Tarifposition.class)))
                .thenAnswer(invocation -> {
                    Tarifposition saved = invocation.getArgument(0);
                    saved.setId(5L);
                    return saved;
                });

        Tarifposition result = tarifpositionService.saveTarifposition(neu);

        assertNotNull(result);
        assertEquals(5L, result.getId());
        assertEquals(testOrgId, result.getOrgId());
        verify(hibernateFilterService).enableOrgFilter();
        verify(organizationContextService).getCurrentOrgId();
        verify(tarifpositionRepository).save(neu);
    }

    @Test
    void saveTarifposition_NewPosition_ChecksOnlyOwnTariftyp() {
        // Geprueft wird gegen den Typ DIESER Position, nicht gegen alle manuell erfassbaren
        // Typen: sonst schlossen sich eine Ladestrom- und eine Grundgebuehr-Position im selben
        // Quartal gegenseitig aus (Specs/Ladestromtarif.md FR-6).
        Tarifposition neu = new Tarifposition(testEinheit, ladestromTarif, 2026, 2, new BigDecimal("42.000"));

        when(einheitRepository.findFirstById(1L)).thenReturn(Optional.of(testEinheit));
        when(tarifRepository.findFirstById(10L)).thenReturn(Optional.of(ladestromTarif));
        when(tarifpositionRepository.existsByEinheitAndQuartalAndTariftyp(
                anyLong(), anyInt(), anyInt(), anySet(), anyLong())).thenReturn(false);
        when(organizationContextService.getCurrentOrgId()).thenReturn(testOrgId);
        when(tarifpositionRepository.save(any(Tarifposition.class))).thenAnswer(i -> i.getArgument(0));

        tarifpositionService.saveTarifposition(neu);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<TarifTyp>> typenCaptor = ArgumentCaptor.forClass(Set.class);
        verify(tarifpositionRepository).existsByEinheitAndQuartalAndTariftyp(
                eq(1L), eq(2026), eq(2), typenCaptor.capture(), eq(-1L));
        assertEquals(Set.of(TarifTyp.LADESTROM), typenCaptor.getValue());
        assertFalse(typenCaptor.getValue().contains(TarifTyp.GRUNDGEBUEHR));
    }

    @Test
    void saveTarifposition_MengeZero_SavesSuccessfully() {
        // Menge = 0 ist speicherbar (erzeugt spaeter keine Rechnungszeile)
        Tarifposition neu = new Tarifposition(testEinheit, ladestromTarif, 2026, 2, BigDecimal.ZERO);

        when(einheitRepository.findFirstById(1L)).thenReturn(Optional.of(testEinheit));
        when(tarifRepository.findFirstById(10L)).thenReturn(Optional.of(ladestromTarif));
        when(tarifpositionRepository.existsByEinheitAndQuartalAndTariftyp(
                anyLong(), anyInt(), anyInt(), anySet(), anyLong())).thenReturn(false);
        when(organizationContextService.getCurrentOrgId()).thenReturn(testOrgId);
        when(tarifpositionRepository.save(any(Tarifposition.class))).thenAnswer(i -> i.getArgument(0));

        Tarifposition result = tarifpositionService.saveTarifposition(neu);

        assertEquals(0, BigDecimal.ZERO.compareTo(result.getMenge()));
        verify(tarifpositionRepository).save(neu);
    }

    @Test
    void saveTarifposition_NoErfassungsart_DefaultsToManuell() {
        Tarifposition neu = new Tarifposition(testEinheit, ladestromTarif, 2026, 2, new BigDecimal("10.000"));
        neu.setErfassungsart(null);

        when(einheitRepository.findFirstById(1L)).thenReturn(Optional.of(testEinheit));
        when(tarifRepository.findFirstById(10L)).thenReturn(Optional.of(ladestromTarif));
        when(tarifpositionRepository.existsByEinheitAndQuartalAndTariftyp(
                anyLong(), anyInt(), anyInt(), anySet(), anyLong())).thenReturn(false);
        when(organizationContextService.getCurrentOrgId()).thenReturn(testOrgId);
        when(tarifpositionRepository.save(any(Tarifposition.class))).thenAnswer(i -> i.getArgument(0));

        Tarifposition result = tarifpositionService.saveTarifposition(neu);

        assertEquals(Erfassungsart.MANUELL, result.getErfassungsart());
    }

    @Test
    void saveTarifposition_ImportErfassungsart_IsPreserved() {
        Tarifposition neu = new Tarifposition(testEinheit, ladestromTarif, 2026, 2, new BigDecimal("10.000"));
        neu.setErfassungsart(Erfassungsart.IMPORT);
        neu.setQuellReferenz("LP-01");

        when(einheitRepository.findFirstById(1L)).thenReturn(Optional.of(testEinheit));
        when(tarifRepository.findFirstById(10L)).thenReturn(Optional.of(ladestromTarif));
        when(tarifpositionRepository.existsByEinheitAndQuartalAndTariftyp(
                anyLong(), anyInt(), anyInt(), anySet(), anyLong())).thenReturn(false);
        when(organizationContextService.getCurrentOrgId()).thenReturn(testOrgId);
        when(tarifpositionRepository.save(any(Tarifposition.class))).thenAnswer(i -> i.getArgument(0));

        Tarifposition result = tarifpositionService.saveTarifposition(neu);

        assertEquals(Erfassungsart.IMPORT, result.getErfassungsart());
        assertEquals("LP-01", result.getQuellReferenz());
    }

    @Test
    void saveTarifposition_ExistingPosition_KeepsOrgIdFromDatabase() {
        Tarifposition geaendert = new Tarifposition(testEinheit, ladestromTarif, 2026, 3, new BigDecimal("150.000"));
        geaendert.setId(1L);
        // orgId bewusst nicht gesetzt - das DTO traegt sie nicht

        when(einheitRepository.findFirstById(1L)).thenReturn(Optional.of(testEinheit));
        when(tarifRepository.findFirstById(10L)).thenReturn(Optional.of(ladestromTarif));
        when(tarifpositionRepository.existsByEinheitAndQuartalAndTariftyp(
                eq(1L), eq(2026), eq(3), anySet(), eq(1L))).thenReturn(false);
        when(tarifpositionRepository.findFirstById(1L)).thenReturn(Optional.of(testPosition1));
        when(tarifpositionRepository.save(any(Tarifposition.class))).thenAnswer(i -> i.getArgument(0));

        Tarifposition result = tarifpositionService.saveTarifposition(geaendert);

        assertEquals(testOrgId, result.getOrgId());
        // Bei bestehenden Positionen darf die orgId NICHT aus dem Kontext kommen
        verify(organizationContextService, never()).getCurrentOrgId();
    }

    @Test
    void saveTarifposition_ExistingPosition_ExcludesItselfFromDuplicateCheck() {
        Tarifposition geaendert = new Tarifposition(testEinheit, ladestromTarif, 2026, 3, new BigDecimal("150.000"));
        geaendert.setId(1L);

        when(einheitRepository.findFirstById(1L)).thenReturn(Optional.of(testEinheit));
        when(tarifRepository.findFirstById(10L)).thenReturn(Optional.of(ladestromTarif));
        when(tarifpositionRepository.existsByEinheitAndQuartalAndTariftyp(
                anyLong(), anyInt(), anyInt(), anySet(), anyLong())).thenReturn(false);
        when(tarifpositionRepository.findFirstById(1L)).thenReturn(Optional.of(testPosition1));
        when(tarifpositionRepository.save(any(Tarifposition.class))).thenAnswer(i -> i.getArgument(0));

        tarifpositionService.saveTarifposition(geaendert);

        verify(tarifpositionRepository).existsByEinheitAndQuartalAndTariftyp(
                eq(1L), eq(2026), eq(3), anySet(), eq(1L));
    }

    @Test
    void saveTarifposition_ExistingPositionNotFound_ThrowsException() {
        Tarifposition geaendert = new Tarifposition(testEinheit, ladestromTarif, 2026, 3, new BigDecimal("150.000"));
        geaendert.setId(99L);

        when(einheitRepository.findFirstById(1L)).thenReturn(Optional.of(testEinheit));
        when(tarifRepository.findFirstById(10L)).thenReturn(Optional.of(ladestromTarif));
        when(tarifpositionRepository.existsByEinheitAndQuartalAndTariftyp(
                anyLong(), anyInt(), anyInt(), anySet(), anyLong())).thenReturn(false);
        when(tarifpositionRepository.findFirstById(99L)).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> tarifpositionService.saveTarifposition(geaendert)
        );

        assertThat(exception.getMessage(), containsString("Tarifposition nicht gefunden"));
        verify(tarifpositionRepository, never()).save(any());
    }

    @Test
    void saveTarifposition_DuplicateForSameQuartalAndTariftyp_ThrowsException() {
        Tarifposition duplikat = new Tarifposition(testEinheit, ladestromTarif, 2026, 3, new BigDecimal("50.000"));

        when(einheitRepository.findFirstById(1L)).thenReturn(Optional.of(testEinheit));
        when(tarifRepository.findFirstById(10L)).thenReturn(Optional.of(ladestromTarif));
        when(tarifpositionRepository.existsByEinheitAndQuartalAndTariftyp(
                eq(1L), eq(2026), eq(3), anySet(), eq(-1L))).thenReturn(true);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> tarifpositionService.saveTarifposition(duplikat)
        );

        assertThat(exception.getMessage(), containsString("bereits eine Position"));
        verify(tarifpositionRepository, never()).save(any());
        verify(organizationContextService, never()).getCurrentOrgId();
    }

    @Test
    void saveTarifposition_DuplicateWithOtherLadestromTarif_ThrowsException() {
        // Zweiter LADESTROM-Tarif, gleiches Quartal, gleicher Mieter -> ebenfalls abgewiesen,
        // weil die Regel auf dem TYP und nicht auf dem einzelnen Tarif beruht.
        Tarif andererLadestromTarif = new Tarif(
                "Ladestrom Sondertarif",
                TarifTyp.LADESTROM,
                new BigDecimal("0.40000"),
                LocalDate.of(2027, 1, 1),
                LocalDate.of(2027, 12, 31));
        andererLadestromTarif.setId(11L);

        Tarifposition duplikat = new Tarifposition(testEinheit, andererLadestromTarif, 2026, 3, new BigDecimal("50.000"));

        when(einheitRepository.findFirstById(1L)).thenReturn(Optional.of(testEinheit));
        when(tarifRepository.findFirstById(11L)).thenReturn(Optional.of(andererLadestromTarif));
        when(tarifpositionRepository.existsByEinheitAndQuartalAndTariftyp(
                eq(1L), eq(2026), eq(3), anySet(), eq(-1L))).thenReturn(true);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> tarifpositionService.saveTarifposition(duplikat)
        );

        assertThat(exception.getMessage(), containsString("bereits eine Position"));
        verify(tarifpositionRepository, never()).save(any());
    }

    @Test
    void saveTarifposition_ZevTariftyp_ThrowsException() {
        Tarif zevTarif = new Tarif(
                "ZEV 2026",
                TarifTyp.ZEV,
                new BigDecimal("0.20000"),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31));
        zevTarif.setId(20L);

        Tarifposition neu = new Tarifposition(testEinheit, zevTarif, 2026, 1, new BigDecimal("10.000"));

        when(einheitRepository.findFirstById(1L)).thenReturn(Optional.of(testEinheit));
        when(tarifRepository.findFirstById(20L)).thenReturn(Optional.of(zevTarif));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> tarifpositionService.saveTarifposition(neu)
        );

        assertThat(exception.getMessage(), containsString("ZEV"));
        verify(tarifpositionRepository, never()).save(any());
    }

    @Test
    void saveTarifposition_ZweiZusatzPositionenVerschiedenerTarife_SavesBoth() {
        // Eindeutigkeit gilt bei ZUSATZ je TARIF, nicht je Typ - sonst waere pro Quartal nur
        // eine einzige Zusatzposition moeglich, also nicht Sauna UND Waschkueche.
        Tarifposition neu = new Tarifposition(testEinheit, zusatzTarif, 2026, 1, new BigDecimal("2.000"));

        when(einheitRepository.findFirstById(1L)).thenReturn(Optional.of(testEinheit));
        when(tarifRepository.findFirstById(30L)).thenReturn(Optional.of(zusatzTarif));
        when(tarifpositionRepository.existsByEinheitAndQuartalAndTarif(
                anyLong(), anyInt(), anyInt(), anyLong(), anyLong())).thenReturn(false);
        when(organizationContextService.getCurrentOrgId()).thenReturn(testOrgId);
        when(tarifpositionRepository.save(any(Tarifposition.class))).thenAnswer(i -> i.getArgument(0));

        assertNotNull(tarifpositionService.saveTarifposition(neu));

        // Gefragt wird nach dem TARIF, nicht nach dem Typ
        verify(tarifpositionRepository).existsByEinheitAndQuartalAndTarif(
                eq(1L), eq(2026), eq(1), eq(30L), eq(-1L));
        verify(tarifpositionRepository, never()).existsByEinheitAndQuartalAndTariftyp(
                anyLong(), anyInt(), anyInt(), anySet(), anyLong());
    }

    @Test
    void saveTarifposition_ZweitePositionMitDemselbenZusatzTarif_ThrowsException() {
        Tarifposition neu = new Tarifposition(testEinheit, zusatzTarif, 2026, 1, new BigDecimal("2.000"));

        when(einheitRepository.findFirstById(1L)).thenReturn(Optional.of(testEinheit));
        when(tarifRepository.findFirstById(30L)).thenReturn(Optional.of(zusatzTarif));
        when(tarifpositionRepository.existsByEinheitAndQuartalAndTarif(
                anyLong(), anyInt(), anyInt(), anyLong(), anyLong())).thenReturn(true);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> tarifpositionService.saveTarifposition(neu)
        );

        assertThat(exception.getMessage(), containsString("bereits eine Position"));
        verify(tarifpositionRepository, never()).save(any());
    }

    @Test
    void saveTarifposition_GrundgebuehrTariftyp_ThrowsException() {
        // Grundgebuehr ist bewusst nicht erfassbar: Je Zeitraum ist nur EIN Grundgebuehr-Tarif
        // gueltig (Ueberschneidungsregel), ein eigener Tarif fuer Ladestationen also gar nicht
        // anlegbar - und jeder gueltige wird automatisch auf jede Konsumenten-Rechnung
        // geschrieben. Der Fall gehoert zu ZUSATZ mit Mengeneinheit Monat.
        Tarifposition neu = new Tarifposition(testEinheit, grundgebuehrTarif, 2026, 1, new BigDecimal("3.000"));

        when(einheitRepository.findFirstById(1L)).thenReturn(Optional.of(testEinheit));
        when(tarifRepository.findFirstById(21L)).thenReturn(Optional.of(grundgebuehrTarif));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> tarifpositionService.saveTarifposition(neu)
        );

        assertThat(exception.getMessage(), containsString("GRUNDGEBUEHR"));
        verify(tarifpositionRepository, never()).save(any());
    }

    @Test
    void saveTarifposition_EinheitMissing_ThrowsException() {
        Tarifposition ohneMieter = new Tarifposition(null, ladestromTarif, 2026, 1, new BigDecimal("10.000"));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> tarifpositionService.saveTarifposition(ohneMieter)
        );

        assertThat(exception.getMessage(), containsString("Einheit ist erforderlich"));
        verify(tarifpositionRepository, never()).save(any());
    }

    @Test
    void saveTarifposition_EinheitNotFound_ThrowsException() {
        Einheit unbekannt = new Einheit("Unbekannt", EinheitTyp.LADESTATION);
        unbekannt.setId(99L);
        Tarifposition neu = new Tarifposition(unbekannt, ladestromTarif, 2026, 1, new BigDecimal("10.000"));

        when(einheitRepository.findFirstById(99L)).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> tarifpositionService.saveTarifposition(neu)
        );

        assertThat(exception.getMessage(), containsString("Einheit nicht gefunden"));
        verify(tarifpositionRepository, never()).save(any());
    }

    @Test
    void saveTarifposition_TarifMissing_ThrowsException() {
        Tarifposition ohneTarif = new Tarifposition(testEinheit, null, 2026, 1, new BigDecimal("10.000"));

        when(einheitRepository.findFirstById(1L)).thenReturn(Optional.of(testEinheit));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> tarifpositionService.saveTarifposition(ohneTarif)
        );

        assertThat(exception.getMessage(), containsString("Tarif ist erforderlich"));
        verify(tarifpositionRepository, never()).save(any());
    }

    @Test
    void saveTarifposition_TarifNotFound_ThrowsException() {
        Tarif unbekannt = new Tarif();
        unbekannt.setId(99L);
        Tarifposition neu = new Tarifposition(testEinheit, unbekannt, 2026, 1, new BigDecimal("10.000"));

        when(einheitRepository.findFirstById(1L)).thenReturn(Optional.of(testEinheit));
        when(tarifRepository.findFirstById(99L)).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> tarifpositionService.saveTarifposition(neu)
        );

        assertThat(exception.getMessage(), containsString("Tarif nicht gefunden"));
        verify(tarifpositionRepository, never()).save(any());
    }

    @Test
    void saveTarifposition_ResolvesEinheitAndTarifFromDatabase() {
        // Controller liefert nur die IDs - der Service muss die vollstaendigen Entities einsetzen
        Einheit nurId = new Einheit();
        nurId.setId(1L);
        Tarif tarifNurId = new Tarif();
        tarifNurId.setId(10L);
        Tarifposition neu = new Tarifposition(nurId, tarifNurId, 2026, 2, new BigDecimal("10.000"));

        when(einheitRepository.findFirstById(1L)).thenReturn(Optional.of(testEinheit));
        when(tarifRepository.findFirstById(10L)).thenReturn(Optional.of(ladestromTarif));
        when(tarifpositionRepository.existsByEinheitAndQuartalAndTariftyp(
                anyLong(), anyInt(), anyInt(), anySet(), anyLong())).thenReturn(false);
        when(organizationContextService.getCurrentOrgId()).thenReturn(testOrgId);
        when(tarifpositionRepository.save(any(Tarifposition.class))).thenAnswer(i -> i.getArgument(0));

        Tarifposition result = tarifpositionService.saveTarifposition(neu);

        assertSame(testEinheit, result.getEinheit());
        assertSame(ladestromTarif, result.getTarif());
        assertEquals("Ladestation 1", result.getEinheit().getName());
    }

    // ==================== deleteTarifposition ====================

    @Test
    void deleteTarifposition_Exists_ReturnsTrue() {
        when(tarifpositionRepository.findFirstById(1L)).thenReturn(Optional.of(testPosition1));

        boolean result = tarifpositionService.deleteTarifposition(1L);

        assertTrue(result);
        verify(tarifpositionRepository).delete(testPosition1);
        verify(hibernateFilterService).enableOrgFilter();
    }

    @Test
    void deleteTarifposition_NotExists_ReturnsFalse() {
        when(tarifpositionRepository.findFirstById(99L)).thenReturn(Optional.empty());

        boolean result = tarifpositionService.deleteTarifposition(99L);

        assertFalse(result);
        verify(tarifpositionRepository, never()).delete(any());
    }

    // ==================== countByTarif ====================

    @Test
    void countByTarif_ReturnsNumberOfReferencingPositions() {
        when(tarifpositionRepository.countByTarifId(10L)).thenReturn(3L);

        long result = tarifpositionService.countByTarif(10L);

        assertEquals(3L, result);
        verify(hibernateFilterService).enableOrgFilter();
    }

    @Test
    void countByTarif_NoPositions_ReturnsZero() {
        when(tarifpositionRepository.countByTarifId(99L)).thenReturn(0L);

        assertEquals(0L, tarifpositionService.countByTarif(99L));
    }

    // ==================== Typpruefung LADESTATION (Specs/Ladestationen.md FR-1.3) ====================
    // Positionen haengen ausschliesslich an Ladestations-Einheiten. Die Pruefung im Service ist
    // die einzige Absicherung: die DB kennt nur den FK auf `einheit`, nicht deren Typ.

    /** Einheit des angegebenen Typs, wie sie das Repository zurueckgibt. */
    private Einheit einheitVomTyp(Long id, String name, EinheitTyp typ) {
        Einheit einheit = new Einheit(name, typ);
        einheit.setId(id);
        einheit.setOrgId(testOrgId);
        return einheit;
    }

    private void assertTypAbgewiesen(EinheitTyp typ) {
        Einheit einheit = einheitVomTyp(50L, "Andere Einheit", typ);
        Tarifposition neu = new Tarifposition(einheit, ladestromTarif, 2026, 1, new BigDecimal("10.000"));
        when(einheitRepository.findFirstById(50L)).thenReturn(Optional.of(einheit));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> tarifpositionService.saveTarifposition(neu)
        );

        assertThat(exception.getMessage(), containsString("nur für Einheiten vom Typ"));
        verify(tarifpositionRepository, never()).save(any());
        // Der Tarif wird nicht mehr aufgeloest - die Einheit scheitert zuerst
        verify(tarifRepository, never()).findById(any());
    }

    @Test
    void saveTarifposition_KonsumentMitZusatzTarif_SavesSuccessfully() {
        // Seit Specs/Tarifpositionen.md sind Positionen auch an Wohnungen erfassbar - aber nur
        // mit einem ZUSATZ-Tarif.
        Einheit wohnung = einheitVomTyp(50L, "Wohnung 1", EinheitTyp.CONSUMER);
        Tarifposition neu = new Tarifposition(wohnung, zusatzTarif, 2026, 1, new BigDecimal("3.000"));

        when(einheitRepository.findFirstById(50L)).thenReturn(Optional.of(wohnung));
        when(tarifRepository.findFirstById(30L)).thenReturn(Optional.of(zusatzTarif));
        when(tarifpositionRepository.existsByEinheitAndQuartalAndTarif(
                anyLong(), anyInt(), anyInt(), anyLong(), anyLong())).thenReturn(false);
        when(organizationContextService.getCurrentOrgId()).thenReturn(testOrgId);
        when(tarifpositionRepository.save(any(Tarifposition.class))).thenAnswer(i -> i.getArgument(0));

        assertNotNull(tarifpositionService.saveTarifposition(neu));
        verify(tarifpositionRepository).save(neu);
    }

    @Test
    void saveTarifposition_KonsumentMitLadestromTarif_ThrowsException() {
        // Ladestrom gehoert fachlich an eine Ladestation
        Einheit wohnung = einheitVomTyp(50L, "Wohnung 1", EinheitTyp.CONSUMER);
        Tarifposition neu = new Tarifposition(wohnung, ladestromTarif, 2026, 1, new BigDecimal("10.000"));

        when(einheitRepository.findFirstById(50L)).thenReturn(Optional.of(wohnung));
        when(tarifRepository.findFirstById(10L)).thenReturn(Optional.of(ladestromTarif));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> tarifpositionService.saveTarifposition(neu)
        );

        assertThat(exception.getMessage(), containsString("Konsumenten"));
        verify(tarifpositionRepository, never()).save(any());
    }

    @Test
    void saveTarifposition_EinheitVomTypProducer_ThrowsException() {
        assertTypAbgewiesen(EinheitTyp.PRODUCER);
    }

    @Test
    void saveTarifposition_EinheitVomTypBezug_ThrowsException() {
        assertTypAbgewiesen(EinheitTyp.BEZUG);
    }

    @Test
    void saveTarifposition_EinheitVomTypRuecklieferung_ThrowsException() {
        assertTypAbgewiesen(EinheitTyp.RUECKLIEFERUNG);
    }

    @Test
    void saveTarifposition_EinheitOhneId_ThrowsException() {
        // Referenz ohne ID ist keine Referenz - sonst wuerde findById(null) durchgereicht
        Einheit ohneId = new Einheit("Ladestation ohne ID", EinheitTyp.LADESTATION);
        Tarifposition neu = new Tarifposition(ohneId, ladestromTarif, 2026, 1, new BigDecimal("10.000"));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> tarifpositionService.saveTarifposition(neu)
        );

        assertThat(exception.getMessage(), containsString("Einheit ist erforderlich"));
        verify(einheitRepository, never()).findById(any());
    }

    // ==================== getFuerRechnung: mehrere Einheiten eines Mieters ====================

    @Test
    void getFuerRechnung_ZweiLadestationenDesselbenMieters_FragtBeideAb() {
        // Mieter mit zwei Ladestationen: beide Positionen landen auf derselben Rechnung
        LocalDate von = LocalDate.of(2026, 1, 1);
        LocalDate bis = LocalDate.of(2026, 3, 31);
        Tarifposition zweite = new Tarifposition(testEinheit, ladestromTarif, 2026, 1, new BigDecimal("50.000"));
        when(tarifpositionRepository.findByEinheitIdsAndQuartalOverlapping(
                List.of(1L, 2L), 2026, 1, 2026, 1))
                .thenReturn(List.of(testPosition1, zweite));

        List<Tarifposition> result = tarifpositionService.getFuerRechnung(List.of(1L, 2L), von, bis);

        assertEquals(2, result.size());
        verify(tarifpositionRepository).findByEinheitIdsAndQuartalOverlapping(
                List.of(1L, 2L), 2026, 1, 2026, 1);
    }

    @Test
    void getFuerRechnung_KeineEinheiten_ReturnsEmptyWithoutQuery() {
        // Mieter ohne Zuordnung: `IN ()` waere ungueltiges SQL - der Service faengt das ab
        List<Tarifposition> result = tarifpositionService.getFuerRechnung(
                List.of(), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31));

        assertTrue(result.isEmpty());
        verify(tarifpositionRepository, never()).findByEinheitIdsAndQuartalOverlapping(
                any(), anyInt(), anyInt(), anyInt(), anyInt());
    }
}
