package ch.nacht.service;

import ch.nacht.entity.Erfassungsart;
import ch.nacht.entity.Mieter;
import ch.nacht.entity.Tarif;
import ch.nacht.entity.TarifTyp;
import ch.nacht.entity.Tarifposition;
import ch.nacht.repository.MieterRepository;
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
 * Unit-Tests für {@link TarifpositionService} (Spec Ladestromtarif.md).
 *
 * <p>Schwerpunkte: Auflösung und Validierung von Mieter/Tarif, die Regel „höchstens eine Position
 * je Mieter, Quartal und Tariftyp", die Beschränkung auf manuell erfasste Tariftypen, die
 * Mandanten-Zuordnung beim Anlegen bzw. Beibehalten beim Ändern sowie die Quartals-Hilfsmethoden.
 */
@ExtendWith(MockitoExtension.class)
public class TarifpositionServiceTest {

    @Mock
    private TarifpositionRepository tarifpositionRepository;

    @Mock
    private MieterRepository mieterRepository;

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
    private Mieter testMieter;
    private Tarif ladestromTarif;
    private Tarifposition testPosition1;
    private Tarifposition testPosition2;

    @BeforeEach
    void setUp() {
        testOrgId = 1L;

        testMieter = new Mieter("Max Muster", LocalDate.of(2026, 1, 1), 1L);
        testMieter.setId(1L);
        testMieter.setOrgId(testOrgId);

        ladestromTarif = new Tarif(
                "Ladestrom",
                TarifTyp.LADESTROM,
                new BigDecimal("0.35000"),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31));
        ladestromTarif.setId(10L);
        ladestromTarif.setOrgId(testOrgId);

        testPosition1 = new Tarifposition(testMieter, ladestromTarif, 2026, 3, new BigDecimal("120.500"));
        testPosition1.setId(1L);
        testPosition1.setOrgId(testOrgId);
        testPosition1.setQuellReferenz("LP-01");

        testPosition2 = new Tarifposition(testMieter, ladestromTarif, 2026, 4, new BigDecimal("80.000"));
        testPosition2.setId(2L);
        testPosition2.setOrgId(testOrgId);
    }

    // ==================== getByMieter ====================

    @Test
    void getByMieter_ReturnsList() {
        when(tarifpositionRepository.findByMieterId(1L))
                .thenReturn(Arrays.asList(testPosition1, testPosition2));

        List<Tarifposition> result = tarifpositionService.getByMieter(1L);

        assertEquals(2, result.size());
        verify(hibernateFilterService).enableOrgFilter();
        verify(tarifpositionRepository).findByMieterId(1L);
    }

    @Test
    void getByMieter_NoPositions_ReturnsEmptyList() {
        when(tarifpositionRepository.findByMieterId(99L)).thenReturn(Collections.emptyList());

        List<Tarifposition> result = tarifpositionService.getByMieter(99L);

        assertTrue(result.isEmpty());
        verify(hibernateFilterService).enableOrgFilter();
    }

    // ==================== getTarifpositionById ====================

    @Test
    void getTarifpositionById_Found_ReturnsTarifposition() {
        when(tarifpositionRepository.findById(1L)).thenReturn(Optional.of(testPosition1));

        Optional<Tarifposition> result = tarifpositionService.getTarifpositionById(1L);

        assertTrue(result.isPresent());
        assertEquals(2026, result.get().getJahr());
        verify(hibernateFilterService).enableOrgFilter();
    }

    @Test
    void getTarifpositionById_NotFound_ReturnsEmpty() {
        when(tarifpositionRepository.findById(99L)).thenReturn(Optional.empty());

        Optional<Tarifposition> result = tarifpositionService.getTarifpositionById(99L);

        assertTrue(result.isEmpty());
    }

    // ==================== getFuerRechnung ====================

    @Test
    void getFuerRechnung_TranslatesPeriodIntoQuarterBounds() {
        LocalDate von = LocalDate.of(2026, 7, 1);
        LocalDate bis = LocalDate.of(2026, 9, 30);
        when(tarifpositionRepository.findByMieterIdAndQuartalOverlapping(1L, 2026, 3, 2026, 3))
                .thenReturn(List.of(testPosition1));

        List<Tarifposition> result = tarifpositionService.getFuerRechnung(1L, von, bis);

        assertEquals(1, result.size());
        verify(hibernateFilterService).enableOrgFilter();
        verify(tarifpositionRepository).findByMieterIdAndQuartalOverlapping(1L, 2026, 3, 2026, 3);
    }

    @Test
    void getFuerRechnung_PeriodAcrossYearBoundary_UsesBothQuarters() {
        // Halbjahresrechnung Q4/2026 - Q1/2027
        LocalDate von = LocalDate.of(2026, 10, 1);
        LocalDate bis = LocalDate.of(2027, 3, 31);
        when(tarifpositionRepository.findByMieterIdAndQuartalOverlapping(1L, 2026, 4, 2027, 1))
                .thenReturn(Arrays.asList(testPosition2, testPosition1));

        List<Tarifposition> result = tarifpositionService.getFuerRechnung(1L, von, bis);

        assertEquals(2, result.size());
        verify(tarifpositionRepository).findByMieterIdAndQuartalOverlapping(1L, 2026, 4, 2027, 1);
    }

    @Test
    void getFuerRechnung_PartialQuarter_StillQueriesTheOverlappingQuarter() {
        // Mieterwechsel: Rechnung deckt nur die zweite Haelfte von Q1 ab
        LocalDate von = LocalDate.of(2026, 3, 1);
        LocalDate bis = LocalDate.of(2026, 3, 31);
        when(tarifpositionRepository.findByMieterIdAndQuartalOverlapping(1L, 2026, 1, 2026, 1))
                .thenReturn(List.of(testPosition1));

        List<Tarifposition> result = tarifpositionService.getFuerRechnung(1L, von, bis);

        assertEquals(1, result.size());
        verify(tarifpositionRepository).findByMieterIdAndQuartalOverlapping(1L, 2026, 1, 2026, 1);
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
        Tarifposition neu = new Tarifposition(testMieter, ladestromTarif, 2026, 2, new BigDecimal("42.000"));

        when(mieterRepository.findById(1L)).thenReturn(Optional.of(testMieter));
        when(tarifRepository.findById(10L)).thenReturn(Optional.of(ladestromTarif));
        when(tarifpositionRepository.existsByMieterAndQuartalAndTariftyp(
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
    void saveTarifposition_NewPosition_ChecksOnlyManuellErfasstTypes() {
        Tarifposition neu = new Tarifposition(testMieter, ladestromTarif, 2026, 2, new BigDecimal("42.000"));

        when(mieterRepository.findById(1L)).thenReturn(Optional.of(testMieter));
        when(tarifRepository.findById(10L)).thenReturn(Optional.of(ladestromTarif));
        when(tarifpositionRepository.existsByMieterAndQuartalAndTariftyp(
                anyLong(), anyInt(), anyInt(), anySet(), anyLong())).thenReturn(false);
        when(organizationContextService.getCurrentOrgId()).thenReturn(testOrgId);
        when(tarifpositionRepository.save(any(Tarifposition.class))).thenAnswer(i -> i.getArgument(0));

        tarifpositionService.saveTarifposition(neu);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<TarifTyp>> typenCaptor = ArgumentCaptor.forClass(Set.class);
        verify(tarifpositionRepository).existsByMieterAndQuartalAndTariftyp(
                eq(1L), eq(2026), eq(2), typenCaptor.capture(), eq(-1L));
        assertEquals(TarifTyp.MANUELL_ERFASST, typenCaptor.getValue());
        assertTrue(typenCaptor.getValue().contains(TarifTyp.LADESTROM));
    }

    @Test
    void saveTarifposition_MengeZero_SavesSuccessfully() {
        // Menge = 0 ist speicherbar (erzeugt spaeter keine Rechnungszeile)
        Tarifposition neu = new Tarifposition(testMieter, ladestromTarif, 2026, 2, BigDecimal.ZERO);

        when(mieterRepository.findById(1L)).thenReturn(Optional.of(testMieter));
        when(tarifRepository.findById(10L)).thenReturn(Optional.of(ladestromTarif));
        when(tarifpositionRepository.existsByMieterAndQuartalAndTariftyp(
                anyLong(), anyInt(), anyInt(), anySet(), anyLong())).thenReturn(false);
        when(organizationContextService.getCurrentOrgId()).thenReturn(testOrgId);
        when(tarifpositionRepository.save(any(Tarifposition.class))).thenAnswer(i -> i.getArgument(0));

        Tarifposition result = tarifpositionService.saveTarifposition(neu);

        assertEquals(0, BigDecimal.ZERO.compareTo(result.getMenge()));
        verify(tarifpositionRepository).save(neu);
    }

    @Test
    void saveTarifposition_NoErfassungsart_DefaultsToManuell() {
        Tarifposition neu = new Tarifposition(testMieter, ladestromTarif, 2026, 2, new BigDecimal("10.000"));
        neu.setErfassungsart(null);

        when(mieterRepository.findById(1L)).thenReturn(Optional.of(testMieter));
        when(tarifRepository.findById(10L)).thenReturn(Optional.of(ladestromTarif));
        when(tarifpositionRepository.existsByMieterAndQuartalAndTariftyp(
                anyLong(), anyInt(), anyInt(), anySet(), anyLong())).thenReturn(false);
        when(organizationContextService.getCurrentOrgId()).thenReturn(testOrgId);
        when(tarifpositionRepository.save(any(Tarifposition.class))).thenAnswer(i -> i.getArgument(0));

        Tarifposition result = tarifpositionService.saveTarifposition(neu);

        assertEquals(Erfassungsart.MANUELL, result.getErfassungsart());
    }

    @Test
    void saveTarifposition_ImportErfassungsart_IsPreserved() {
        Tarifposition neu = new Tarifposition(testMieter, ladestromTarif, 2026, 2, new BigDecimal("10.000"));
        neu.setErfassungsart(Erfassungsart.IMPORT);
        neu.setQuellReferenz("LP-01");

        when(mieterRepository.findById(1L)).thenReturn(Optional.of(testMieter));
        when(tarifRepository.findById(10L)).thenReturn(Optional.of(ladestromTarif));
        when(tarifpositionRepository.existsByMieterAndQuartalAndTariftyp(
                anyLong(), anyInt(), anyInt(), anySet(), anyLong())).thenReturn(false);
        when(organizationContextService.getCurrentOrgId()).thenReturn(testOrgId);
        when(tarifpositionRepository.save(any(Tarifposition.class))).thenAnswer(i -> i.getArgument(0));

        Tarifposition result = tarifpositionService.saveTarifposition(neu);

        assertEquals(Erfassungsart.IMPORT, result.getErfassungsart());
        assertEquals("LP-01", result.getQuellReferenz());
    }

    @Test
    void saveTarifposition_ExistingPosition_KeepsOrgIdFromDatabase() {
        Tarifposition geaendert = new Tarifposition(testMieter, ladestromTarif, 2026, 3, new BigDecimal("150.000"));
        geaendert.setId(1L);
        // orgId bewusst nicht gesetzt - das DTO traegt sie nicht

        when(mieterRepository.findById(1L)).thenReturn(Optional.of(testMieter));
        when(tarifRepository.findById(10L)).thenReturn(Optional.of(ladestromTarif));
        when(tarifpositionRepository.existsByMieterAndQuartalAndTariftyp(
                eq(1L), eq(2026), eq(3), anySet(), eq(1L))).thenReturn(false);
        when(tarifpositionRepository.findById(1L)).thenReturn(Optional.of(testPosition1));
        when(tarifpositionRepository.save(any(Tarifposition.class))).thenAnswer(i -> i.getArgument(0));

        Tarifposition result = tarifpositionService.saveTarifposition(geaendert);

        assertEquals(testOrgId, result.getOrgId());
        // Bei bestehenden Positionen darf die orgId NICHT aus dem Kontext kommen
        verify(organizationContextService, never()).getCurrentOrgId();
    }

    @Test
    void saveTarifposition_ExistingPosition_ExcludesItselfFromDuplicateCheck() {
        Tarifposition geaendert = new Tarifposition(testMieter, ladestromTarif, 2026, 3, new BigDecimal("150.000"));
        geaendert.setId(1L);

        when(mieterRepository.findById(1L)).thenReturn(Optional.of(testMieter));
        when(tarifRepository.findById(10L)).thenReturn(Optional.of(ladestromTarif));
        when(tarifpositionRepository.existsByMieterAndQuartalAndTariftyp(
                anyLong(), anyInt(), anyInt(), anySet(), anyLong())).thenReturn(false);
        when(tarifpositionRepository.findById(1L)).thenReturn(Optional.of(testPosition1));
        when(tarifpositionRepository.save(any(Tarifposition.class))).thenAnswer(i -> i.getArgument(0));

        tarifpositionService.saveTarifposition(geaendert);

        verify(tarifpositionRepository).existsByMieterAndQuartalAndTariftyp(
                eq(1L), eq(2026), eq(3), anySet(), eq(1L));
    }

    @Test
    void saveTarifposition_ExistingPositionNotFound_ThrowsException() {
        Tarifposition geaendert = new Tarifposition(testMieter, ladestromTarif, 2026, 3, new BigDecimal("150.000"));
        geaendert.setId(99L);

        when(mieterRepository.findById(1L)).thenReturn(Optional.of(testMieter));
        when(tarifRepository.findById(10L)).thenReturn(Optional.of(ladestromTarif));
        when(tarifpositionRepository.existsByMieterAndQuartalAndTariftyp(
                anyLong(), anyInt(), anyInt(), anySet(), anyLong())).thenReturn(false);
        when(tarifpositionRepository.findById(99L)).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> tarifpositionService.saveTarifposition(geaendert)
        );

        assertThat(exception.getMessage(), containsString("Tarifposition nicht gefunden"));
        verify(tarifpositionRepository, never()).save(any());
    }

    @Test
    void saveTarifposition_DuplicateForSameQuartalAndTariftyp_ThrowsException() {
        Tarifposition duplikat = new Tarifposition(testMieter, ladestromTarif, 2026, 3, new BigDecimal("50.000"));

        when(mieterRepository.findById(1L)).thenReturn(Optional.of(testMieter));
        when(tarifRepository.findById(10L)).thenReturn(Optional.of(ladestromTarif));
        when(tarifpositionRepository.existsByMieterAndQuartalAndTariftyp(
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

        Tarifposition duplikat = new Tarifposition(testMieter, andererLadestromTarif, 2026, 3, new BigDecimal("50.000"));

        when(mieterRepository.findById(1L)).thenReturn(Optional.of(testMieter));
        when(tarifRepository.findById(11L)).thenReturn(Optional.of(andererLadestromTarif));
        when(tarifpositionRepository.existsByMieterAndQuartalAndTariftyp(
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

        Tarifposition neu = new Tarifposition(testMieter, zevTarif, 2026, 1, new BigDecimal("10.000"));

        when(mieterRepository.findById(1L)).thenReturn(Optional.of(testMieter));
        when(tarifRepository.findById(20L)).thenReturn(Optional.of(zevTarif));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> tarifpositionService.saveTarifposition(neu)
        );

        assertThat(exception.getMessage(), containsString("ZEV"));
        verify(tarifpositionRepository, never()).save(any());
    }

    @Test
    void saveTarifposition_GrundgebuehrTariftyp_ThrowsException() {
        Tarif grundgebuehr = new Tarif(
                "Grundgebühr 2026",
                TarifTyp.GRUNDGEBUEHR,
                new BigDecimal("5.00000"),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31));
        grundgebuehr.setId(21L);

        Tarifposition neu = new Tarifposition(testMieter, grundgebuehr, 2026, 1, new BigDecimal("10.000"));

        when(mieterRepository.findById(1L)).thenReturn(Optional.of(testMieter));
        when(tarifRepository.findById(21L)).thenReturn(Optional.of(grundgebuehr));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> tarifpositionService.saveTarifposition(neu)
        );

        assertThat(exception.getMessage(), containsString("GRUNDGEBUEHR"));
        verify(tarifpositionRepository, never()).save(any());
    }

    @Test
    void saveTarifposition_MieterMissing_ThrowsException() {
        Tarifposition ohneMieter = new Tarifposition(null, ladestromTarif, 2026, 1, new BigDecimal("10.000"));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> tarifpositionService.saveTarifposition(ohneMieter)
        );

        assertThat(exception.getMessage(), containsString("Mieter ist erforderlich"));
        verify(tarifpositionRepository, never()).save(any());
    }

    @Test
    void saveTarifposition_MieterNotFound_ThrowsException() {
        Mieter unbekannt = new Mieter("Unbekannt", LocalDate.of(2026, 1, 1), 1L);
        unbekannt.setId(99L);
        Tarifposition neu = new Tarifposition(unbekannt, ladestromTarif, 2026, 1, new BigDecimal("10.000"));

        when(mieterRepository.findById(99L)).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> tarifpositionService.saveTarifposition(neu)
        );

        assertThat(exception.getMessage(), containsString("Mieter nicht gefunden"));
        verify(tarifpositionRepository, never()).save(any());
    }

    @Test
    void saveTarifposition_TarifMissing_ThrowsException() {
        Tarifposition ohneTarif = new Tarifposition(testMieter, null, 2026, 1, new BigDecimal("10.000"));

        when(mieterRepository.findById(1L)).thenReturn(Optional.of(testMieter));

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
        Tarifposition neu = new Tarifposition(testMieter, unbekannt, 2026, 1, new BigDecimal("10.000"));

        when(mieterRepository.findById(1L)).thenReturn(Optional.of(testMieter));
        when(tarifRepository.findById(99L)).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> tarifpositionService.saveTarifposition(neu)
        );

        assertThat(exception.getMessage(), containsString("Tarif nicht gefunden"));
        verify(tarifpositionRepository, never()).save(any());
    }

    @Test
    void saveTarifposition_ResolvesMieterAndTarifFromDatabase() {
        // Controller liefert nur die IDs - der Service muss die vollstaendigen Entities einsetzen
        Mieter nurId = new Mieter();
        nurId.setId(1L);
        Tarif tarifNurId = new Tarif();
        tarifNurId.setId(10L);
        Tarifposition neu = new Tarifposition(nurId, tarifNurId, 2026, 2, new BigDecimal("10.000"));

        when(mieterRepository.findById(1L)).thenReturn(Optional.of(testMieter));
        when(tarifRepository.findById(10L)).thenReturn(Optional.of(ladestromTarif));
        when(tarifpositionRepository.existsByMieterAndQuartalAndTariftyp(
                anyLong(), anyInt(), anyInt(), anySet(), anyLong())).thenReturn(false);
        when(organizationContextService.getCurrentOrgId()).thenReturn(testOrgId);
        when(tarifpositionRepository.save(any(Tarifposition.class))).thenAnswer(i -> i.getArgument(0));

        Tarifposition result = tarifpositionService.saveTarifposition(neu);

        assertSame(testMieter, result.getMieter());
        assertSame(ladestromTarif, result.getTarif());
        assertEquals("Max Muster", result.getMieter().getName());
    }

    // ==================== deleteTarifposition ====================

    @Test
    void deleteTarifposition_Exists_ReturnsTrue() {
        when(tarifpositionRepository.findById(1L)).thenReturn(Optional.of(testPosition1));

        boolean result = tarifpositionService.deleteTarifposition(1L);

        assertTrue(result);
        verify(tarifpositionRepository).delete(testPosition1);
        verify(hibernateFilterService).enableOrgFilter();
    }

    @Test
    void deleteTarifposition_NotExists_ReturnsFalse() {
        when(tarifpositionRepository.findById(99L)).thenReturn(Optional.empty());

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
}
