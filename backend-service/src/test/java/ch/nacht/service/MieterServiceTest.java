package ch.nacht.service;

import ch.nacht.entity.Mieter;
import ch.nacht.entity.MieterEinheit;
import ch.nacht.repository.MieterEinheitRepository;
import ch.nacht.repository.MieterRepository;
import ch.nacht.repository.NkAkontoRepository;
import ch.nacht.repository.NkPersonRepository;
import ch.nacht.repository.NkVerbrauchRepository;
import ch.nacht.repository.NkZusatzRepository;
import ch.nacht.repository.TarifpositionRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;


import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MieterServiceTest {

    @Mock
    private MieterRepository mieterRepository;

    @Mock
    private MieterEinheitRepository mieterEinheitRepository;

    @Mock
    private TarifpositionRepository tarifpositionRepository;

    @Mock
    private NkVerbrauchRepository nkVerbrauchRepository;

    @Mock
    private NkZusatzRepository nkZusatzRepository;

    @Mock
    private NkAkontoRepository nkAkontoRepository;

    @Mock
    private NkPersonRepository nkPersonRepository;

    @Mock
    private OrganizationContextService organizationContextService;

    @Mock
    private HibernateFilterService hibernateFilterService;

    @InjectMocks
    private MieterService mieterService;

    private Mieter testMieter;
    private Long testOrgId;

    @BeforeEach
    void setUp() {
        testOrgId = 1L;

        testMieter = new Mieter("Max Muster", LocalDate.of(2024, 1, 1), 1L);
        testMieter.setId(1L);
        testMieter.setOrgId(testOrgId);
        testMieter.setStrasse("Teststrasse 1");
        testMieter.setPlz("3000");
        testMieter.setOrt("Bern");
        testMieter.setMietende(LocalDate.of(2024, 12, 31));
    }

    // ==================== getAllMieter Tests ====================

    @Test
    void getAllMieter_ReturnsSortedList() {
        Mieter mieter2 = new Mieter("Anna Beispiel", LocalDate.of(2024, 4, 1), 2L);
        mieter2.setId(2L);
        when(mieterRepository.findAllByOrderByNameAscMietbeginnDesc())
                .thenReturn(Arrays.asList(testMieter, mieter2));

        List<Mieter> result = mieterService.getAllMieter();

        assertEquals(2, result.size());
        assertEquals("Max Muster", result.get(0).getName());
        verify(hibernateFilterService).enableOrgFilter();
        verify(mieterRepository).findAllByOrderByNameAscMietbeginnDesc();
    }

    @Test
    void getAllMieter_EmptyList_ReturnsEmptyList() {
        when(mieterRepository.findAllByOrderByNameAscMietbeginnDesc())
                .thenReturn(Collections.emptyList());

        List<Mieter> result = mieterService.getAllMieter();

        assertTrue(result.isEmpty());
    }

    // ==================== getMieterById Tests ====================

    @Test
    void getMieterById_Found_ReturnsMieter() {
        when(mieterRepository.findFirstById(1L)).thenReturn(Optional.of(testMieter));

        Optional<Mieter> result = mieterService.getMieterById(1L);

        assertTrue(result.isPresent());
        assertEquals("Max Muster", result.get().getName());
        verify(hibernateFilterService).enableOrgFilter();
    }

    @Test
    void getMieterById_NotFound_ReturnsEmpty() {
        when(mieterRepository.findFirstById(99L)).thenReturn(Optional.empty());

        Optional<Mieter> result = mieterService.getMieterById(99L);

        assertFalse(result.isPresent());
    }

    // ==================== saveMieter Tests ====================

    @Test
    void saveMieter_ValidNewMieter_SavesSuccessfully() {
        Mieter newMieter = new Mieter("Neue Mieterin", LocalDate.of(2025, 1, 1), 2L);
        newMieter.setStrasse("Neue Strasse 1");
        newMieter.setPlz("8000");
        newMieter.setOrt("Zürich");
        newMieter.setMietende(LocalDate.of(2025, 12, 31));

        when(organizationContextService.getCurrentOrgId()).thenReturn(testOrgId);
        when(mieterRepository.existsOverlappingMieterBounded(eq(2L), any(), any(), eq(-1L)))
                .thenReturn(false);
        when(mieterRepository.save(any(Mieter.class))).thenAnswer(invocation -> {
            Mieter saved = invocation.getArgument(0);
            saved.setId(2L);
            return saved;
        });

        Mieter result = mieterService.saveMieter(newMieter);

        assertNotNull(result);
        assertEquals(2L, result.getId());
        assertEquals(testOrgId, result.getOrgId());
        verify(mieterRepository).save(newMieter);
    }

    @Test
    void saveMieter_ValidNewMieterOpenEnded_SavesSuccessfully() {
        Mieter newMieter = new Mieter("Aktuelle Mieterin", LocalDate.of(2025, 1, 1), 3L);
        newMieter.setStrasse("Strasse 1");
        newMieter.setPlz("3000");
        newMieter.setOrt("Bern");
        // No mietende - open-ended lease

        when(organizationContextService.getCurrentOrgId()).thenReturn(testOrgId);
        when(mieterRepository.existsOverlappingMieterOpenEnded(eq(3L), any(), eq(-1L)))
                .thenReturn(false);
        when(mieterRepository.existsOtherMieterWithoutMietende(eq(3L), eq(-1L)))
                .thenReturn(false);
        when(mieterRepository.save(any(Mieter.class))).thenAnswer(invocation -> {
            Mieter saved = invocation.getArgument(0);
            saved.setId(3L);
            return saved;
        });

        Mieter result = mieterService.saveMieter(newMieter);

        assertNotNull(result);
        assertEquals(3L, result.getId());
    }

    @Test
    void saveMieter_ExistingMieter_DoesNotOverwriteOrgId() {
        testMieter.setMietende(LocalDate.of(2025, 6, 30));

        when(mieterRepository.existsOverlappingMieterBounded(eq(1L), any(), any(), eq(1L)))
                .thenReturn(false);
        when(mieterRepository.save(any(Mieter.class))).thenReturn(testMieter);

        Mieter result = mieterService.saveMieter(testMieter);

        assertNotNull(result);
        // Should NOT call getCurrentOrgId for existing mieter (id != null)
        verify(organizationContextService, never()).getCurrentOrgId();
    }

    @Test
    void saveMieter_MietendeBeforeMietbeginn_ThrowsException() {
        Mieter invalidMieter = new Mieter("Invalid", LocalDate.of(2024, 6, 1), 1L);
        invalidMieter.setStrasse("Strasse");
        invalidMieter.setPlz("3000");
        invalidMieter.setOrt("Bern");
        invalidMieter.setMietende(LocalDate.of(2024, 1, 1)); // Before mietbeginn

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> mieterService.saveMieter(invalidMieter)
        );

        assertThat(exception.getMessage(), containsString("Mietende muss nach Mietbeginn liegen"));
        verify(mieterRepository, never()).save(any());
    }

    @Test
    void saveMieter_MietendeEqualsMietbeginn_ThrowsException() {
        LocalDate sameDate = LocalDate.of(2024, 6, 1);
        Mieter invalidMieter = new Mieter("Invalid", sameDate, 1L);
        invalidMieter.setStrasse("Strasse");
        invalidMieter.setPlz("3000");
        invalidMieter.setOrt("Bern");
        invalidMieter.setMietende(sameDate); // Same as mietbeginn

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> mieterService.saveMieter(invalidMieter)
        );

        assertThat(exception.getMessage(), containsString("Mietende muss nach Mietbeginn liegen"));
    }

    @Test
    void saveMieter_OverlappingBounded_ThrowsException() {
        Mieter newMieter = new Mieter("Overlap", LocalDate.of(2024, 6, 1), 1L);
        newMieter.setStrasse("Strasse");
        newMieter.setPlz("3000");
        newMieter.setOrt("Bern");
        newMieter.setMietende(LocalDate.of(2024, 12, 31));

        when(mieterRepository.existsOverlappingMieterBounded(eq(1L), any(), any(), eq(-1L)))
                .thenReturn(true);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> mieterService.saveMieter(newMieter)
        );

        assertThat(exception.getMessage(), containsString("überschneidet sich"));
        verify(mieterRepository, never()).save(any());
    }

    @Test
    void saveMieter_OverlappingOpenEnded_ThrowsException() {
        Mieter newMieter = new Mieter("Overlap Open", LocalDate.of(2024, 6, 1), 1L);
        newMieter.setStrasse("Strasse");
        newMieter.setPlz("3000");
        newMieter.setOrt("Bern");
        // No mietende

        when(mieterRepository.existsOverlappingMieterOpenEnded(eq(1L), any(), eq(-1L)))
                .thenReturn(true);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> mieterService.saveMieter(newMieter)
        );

        assertThat(exception.getMessage(), containsString("überschneidet sich"));
    }

    @Test
    void saveMieter_AnotherMieterWithoutMietende_ThrowsException() {
        Mieter newMieter = new Mieter("Zweiter Aktueller", LocalDate.of(2025, 1, 1), 1L);
        newMieter.setStrasse("Strasse");
        newMieter.setPlz("3000");
        newMieter.setOrt("Bern");
        // No mietende - trying to be a second current tenant

        when(mieterRepository.existsOverlappingMieterOpenEnded(eq(1L), any(), eq(-1L)))
                .thenReturn(false);
        when(mieterRepository.existsOtherMieterWithoutMietende(eq(1L), eq(-1L)))
                .thenReturn(true);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> mieterService.saveMieter(newMieter)
        );

        assertThat(exception.getMessage(), containsString("aktueller Mieter ohne Mietende"));
    }

    @Test
    void saveMieter_WithMietende_SkipsOpenEndedCheck() {
        Mieter newMieter = new Mieter("Mit Ende", LocalDate.of(2025, 1, 1), 4L);
        newMieter.setStrasse("Strasse");
        newMieter.setPlz("3000");
        newMieter.setOrt("Bern");
        newMieter.setMietende(LocalDate.of(2025, 6, 30));

        when(organizationContextService.getCurrentOrgId()).thenReturn(testOrgId);
        when(mieterRepository.existsOverlappingMieterBounded(eq(4L), any(), any(), eq(-1L)))
                .thenReturn(false);
        when(mieterRepository.save(any(Mieter.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mieterService.saveMieter(newMieter);

        // Should NOT check existsOtherMieterWithoutMietende when mieter has mietende
        verify(mieterRepository, never()).existsOtherMieterWithoutMietende(anyLong(), anyLong());
    }

    // ==================== deleteMieter Tests ====================

    @Test
    void deleteMieter_Exists_ReturnsTrue() {
        when(mieterRepository.existsById(1L)).thenReturn(true);

        boolean result = mieterService.deleteMieter(1L);

        assertTrue(result);
        verify(mieterRepository).deleteById(1L);
        verify(hibernateFilterService).enableOrgFilter();
    }

    @Test
    void deleteMieter_NotExists_ReturnsFalse() {
        when(mieterRepository.existsById(99L)).thenReturn(false);

        boolean result = mieterService.deleteMieter(99L);

        assertFalse(result);
        verify(mieterRepository, never()).deleteById(anyLong());
    }

    // ==================== getMieterForQuartal Tests ====================

    @Test
    void getMieterForQuartal_ReturnsActiveMieter() {
        LocalDate quartalBeginn = LocalDate.of(2024, 1, 1);
        LocalDate quartalEnde = LocalDate.of(2024, 3, 31);

        when(mieterRepository.findByEinheitIdAndQuartal(1L, quartalBeginn, quartalEnde))
                .thenReturn(List.of(testMieter));

        List<Mieter> result = mieterService.getMieterForQuartal(1L, quartalBeginn, quartalEnde);

        assertEquals(1, result.size());
        assertEquals("Max Muster", result.get(0).getName());
        verify(hibernateFilterService).enableOrgFilter();
    }

    @Test
    void getMieterForQuartal_NoActiveMieter_ReturnsEmptyList() {
        LocalDate quartalBeginn = LocalDate.of(2026, 1, 1);
        LocalDate quartalEnde = LocalDate.of(2026, 3, 31);

        when(mieterRepository.findByEinheitIdAndQuartal(1L, quartalBeginn, quartalEnde))
                .thenReturn(Collections.emptyList());

        List<Mieter> result = mieterService.getMieterForQuartal(1L, quartalBeginn, quartalEnde);

        assertTrue(result.isEmpty());
    }

    // ==================== Zuordnung Mieter <-> Einheiten (Specs/Ladestationen.md) ====================

    /** Mieter mit Adresse und befristetem Mietzeitraum, dem die angegebenen Einheiten zugeordnet sind. */
    private Mieter mieterMitEinheiten(String name, Long... einheitIds) {
        Mieter mieter = new Mieter();
        mieter.setName(name);
        mieter.setStrasse("Strasse 1");
        mieter.setPlz("3000");
        mieter.setOrt("Bern");
        mieter.setMietbeginn(LocalDate.of(2026, 1, 1));
        mieter.setMietende(LocalDate.of(2026, 12, 31));
        mieter.setEinheitIds(new ArrayList<>(Arrays.asList(einheitIds)));
        return mieter;
    }

    @Test
    void saveMieter_MehrereEinheiten_SpeichertAlleZuordnungen() {
        // Wohnung (1) + zwei Ladestationen (2, 3) - alles auf einer Rechnung
        Mieter neu = mieterMitEinheiten("Mit Wohnung und Ladestationen", 1L, 2L, 3L);

        when(organizationContextService.getCurrentOrgId()).thenReturn(testOrgId);
        when(mieterRepository.existsOverlappingMieterBounded(anyLong(), any(), any(), eq(-1L)))
                .thenReturn(false);
        when(mieterRepository.save(any(Mieter.class))).thenAnswer(invocation -> {
            Mieter saved = invocation.getArgument(0);
            saved.setId(7L);
            return saved;
        });

        Mieter result = mieterService.saveMieter(neu);

        assertEquals(List.of(1L, 2L, 3L), result.getEinheitIds());
        // Zuordnungen werden komplett neu geschrieben, nicht Zeile fuer Zeile abgeglichen
        verify(mieterEinheitRepository).deleteByMieterId(7L);
        verify(mieterEinheitRepository).flush();

        ArgumentCaptor<MieterEinheit> captor = ArgumentCaptor.forClass(MieterEinheit.class);
        verify(mieterEinheitRepository, times(3)).save(captor.capture());
        assertEquals(List.of(1L, 2L, 3L),
                captor.getAllValues().stream().map(MieterEinheit::getEinheitId).toList());
        // org_id serverseitig aus dem Mieter uebernommen
        assertTrue(captor.getAllValues().stream().allMatch(z -> testOrgId.equals(z.getOrgId())));
        assertTrue(captor.getAllValues().stream().allMatch(z -> Long.valueOf(7L).equals(z.getMieterId())));
    }

    @Test
    void saveMieter_MehrereEinheiten_PruefungFuerJedeEinheit() {
        Mieter neu = mieterMitEinheiten("Zwei Einheiten", 1L, 2L);

        when(organizationContextService.getCurrentOrgId()).thenReturn(testOrgId);
        when(mieterRepository.existsOverlappingMieterBounded(anyLong(), any(), any(), eq(-1L)))
                .thenReturn(false);
        when(mieterRepository.save(any(Mieter.class))).thenAnswer(invocation -> {
            Mieter saved = invocation.getArgument(0);
            saved.setId(8L);
            return saved;
        });

        mieterService.saveMieter(neu);

        verify(mieterRepository).existsOverlappingMieterBounded(eq(1L), any(), any(), eq(-1L));
        verify(mieterRepository).existsOverlappingMieterBounded(eq(2L), any(), any(), eq(-1L));
    }

    @Test
    void saveMieter_UeberschneidungInZweiterEinheit_ThrowsException() {
        // Eine Ueberschneidung in EINER zugeordneten Einheit reicht zur Ablehnung
        Mieter neu = mieterMitEinheiten("Kollision", 1L, 2L);

        when(mieterRepository.existsOverlappingMieterBounded(eq(1L), any(), any(), eq(-1L)))
                .thenReturn(false);
        when(mieterRepository.existsOverlappingMieterBounded(eq(2L), any(), any(), eq(-1L)))
                .thenReturn(true);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> mieterService.saveMieter(neu)
        );

        assertThat(exception.getMessage(), containsString("überschneidet sich"));
        verify(mieterRepository, never()).save(any());
        verify(mieterEinheitRepository, never()).save(any());
    }

    @Test
    void saveMieter_ZweiterMieterOhneMietendeAnZweiterEinheit_ThrowsException() {
        // Regel "hoechstens ein Mieter ohne Mietende" gilt je zugeordneter Einheit
        Mieter neu = mieterMitEinheiten("Offenes Ende", 1L, 2L);
        neu.setMietende(null);

        when(mieterRepository.existsOverlappingMieterOpenEnded(anyLong(), any(), eq(-1L)))
                .thenReturn(false);
        when(mieterRepository.existsOtherMieterWithoutMietende(eq(1L), eq(-1L))).thenReturn(false);
        when(mieterRepository.existsOtherMieterWithoutMietende(eq(2L), eq(-1L))).thenReturn(true);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> mieterService.saveMieter(neu)
        );

        assertThat(exception.getMessage(), containsString("aktueller Mieter ohne Mietende"));
        verify(mieterRepository, never()).save(any());
    }

    @Test
    void saveMieter_NurLadestation_SavesSuccessfully() {
        // Nutzer ohne Wohnung: nur eine LADESTATION-Einheit zugeordnet
        Mieter neu = mieterMitEinheiten("Nutzer ohne Wohnung", 42L);

        when(organizationContextService.getCurrentOrgId()).thenReturn(testOrgId);
        when(mieterRepository.existsOverlappingMieterBounded(eq(42L), any(), any(), eq(-1L)))
                .thenReturn(false);
        when(mieterRepository.save(any(Mieter.class))).thenAnswer(invocation -> {
            Mieter saved = invocation.getArgument(0);
            saved.setId(9L);
            return saved;
        });

        Mieter result = mieterService.saveMieter(neu);

        assertEquals(List.of(42L), result.getEinheitIds());
        verify(mieterEinheitRepository).save(any(MieterEinheit.class));
    }

    @Test
    void saveMieter_OhneEinheit_ThrowsException() {
        Mieter neu = mieterMitEinheiten("Ohne Einheit");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> mieterService.saveMieter(neu)
        );

        assertThat(exception.getMessage(), containsString("Mindestens eine Einheit"));
        verify(mieterRepository, never()).save(any());
        verify(mieterEinheitRepository, never()).deleteByMieterId(anyLong());
    }

    @Test
    void saveMieter_DoppelteEinheitId_SpeichertZuordnungNurEinmal() {
        Mieter neu = mieterMitEinheiten("Doppelt", 1L, 1L, 2L);

        when(organizationContextService.getCurrentOrgId()).thenReturn(testOrgId);
        when(mieterRepository.existsOverlappingMieterBounded(anyLong(), any(), any(), eq(-1L)))
                .thenReturn(false);
        when(mieterRepository.save(any(Mieter.class))).thenAnswer(invocation -> {
            Mieter saved = invocation.getArgument(0);
            saved.setId(10L);
            return saved;
        });

        Mieter result = mieterService.saveMieter(neu);

        assertEquals(List.of(1L, 2L), result.getEinheitIds());
        verify(mieterEinheitRepository, times(2)).save(any(MieterEinheit.class));
    }

    @Test
    void saveMieter_Update_SchreibtZuordnungenNeu() {
        // Beim Bearbeiten wird die Zuordnung komplett ersetzt (Ladestation ergaenzt)
        testMieter.setEinheitIds(new ArrayList<>(List.of(1L, 2L)));

        when(mieterRepository.existsOverlappingMieterBounded(anyLong(), any(), any(), eq(1L)))
                .thenReturn(false);
        when(mieterRepository.save(any(Mieter.class))).thenReturn(testMieter);

        Mieter result = mieterService.saveMieter(testMieter);

        assertEquals(List.of(1L, 2L), result.getEinheitIds());
        verify(mieterEinheitRepository).deleteByMieterId(1L);
        verify(mieterEinheitRepository, times(2)).save(any(MieterEinheit.class));
        verify(organizationContextService, never()).getCurrentOrgId();
    }

    @Test
    void getAllMieter_FuelltEinheitIdsJeMieter() {
        Mieter mieter2 = new Mieter("Anna Beispiel", LocalDate.of(2024, 4, 1), 2L);
        mieter2.setId(2L);

        when(mieterRepository.findAllByOrderByNameAscMietbeginnDesc())
                .thenReturn(Arrays.asList(testMieter, mieter2));
        // Eine einzige Abfrage fuer die ganze Liste
        when(mieterEinheitRepository.findByMieterIdIn(List.of(1L, 2L))).thenReturn(List.of(
                new MieterEinheit(testOrgId, 1L, 10L),
                new MieterEinheit(testOrgId, 1L, 11L),
                new MieterEinheit(testOrgId, 2L, 20L)
        ));

        List<Mieter> result = mieterService.getAllMieter();

        assertEquals(List.of(10L, 11L), result.get(0).getEinheitIds());
        assertEquals(List.of(20L), result.get(1).getEinheitIds());
        verify(mieterEinheitRepository).findByMieterIdIn(List.of(1L, 2L));
    }

    @Test
    void getAllMieter_MieterOhneZuordnung_HatLeereEinheitListe() {
        when(mieterRepository.findAllByOrderByNameAscMietbeginnDesc()).thenReturn(List.of(testMieter));
        when(mieterEinheitRepository.findByMieterIdIn(List.of(1L))).thenReturn(Collections.emptyList());

        List<Mieter> result = mieterService.getAllMieter();

        assertTrue(result.get(0).getEinheitIds().isEmpty());
    }

    @Test
    void getAllMieter_EmptyList_QueriesNoZuordnungen() {
        when(mieterRepository.findAllByOrderByNameAscMietbeginnDesc()).thenReturn(Collections.emptyList());

        mieterService.getAllMieter();

        verify(mieterEinheitRepository, never()).findByMieterIdIn(any());
    }

    @Test
    void getMieterById_FuelltEinheitIds() {
        when(mieterRepository.findFirstById(1L)).thenReturn(Optional.of(testMieter));
        when(mieterEinheitRepository.findEinheitIdsByMieterId(1L)).thenReturn(List.of(10L, 11L));

        Optional<Mieter> result = mieterService.getMieterById(1L);

        assertTrue(result.isPresent());
        assertEquals(List.of(10L, 11L), result.get().getEinheitIds());
    }

    @Test
    void getMieterForQuartal_FuelltEinheitIds() {
        LocalDate quartalBeginn = LocalDate.of(2024, 1, 1);
        LocalDate quartalEnde = LocalDate.of(2024, 3, 31);

        when(mieterRepository.findByEinheitIdAndQuartal(1L, quartalBeginn, quartalEnde))
                .thenReturn(List.of(testMieter));
        when(mieterEinheitRepository.findByMieterIdIn(List.of(1L)))
                .thenReturn(List.of(new MieterEinheit(testOrgId, 1L, 10L)));

        List<Mieter> result = mieterService.getMieterForQuartal(1L, quartalBeginn, quartalEnde);

        assertEquals(List.of(10L), result.get(0).getEinheitIds());
    }

    @Test
    void getEinheitIds_ReturnsZugeordneteEinheiten() {
        when(mieterEinheitRepository.findEinheitIdsByMieterId(1L)).thenReturn(List.of(10L, 11L));

        List<Long> result = mieterService.getEinheitIds(1L);

        assertEquals(List.of(10L, 11L), result);
        verify(hibernateFilterService).enableOrgFilter();
    }

    // ==================== Loeschschutz: Positionen an zugeordneten Einheiten ====================

    @Test
    void deleteMieter_MitTarifpositionenAnZugeordneterEinheit_ThrowsException() {
        when(mieterRepository.existsById(1L)).thenReturn(true);
        when(mieterEinheitRepository.findEinheitIdsByMieterId(1L)).thenReturn(List.of(10L, 11L));
        when(tarifpositionRepository.countByEinheitId(10L)).thenReturn(0L);
        when(tarifpositionRepository.countByEinheitId(11L)).thenReturn(3L);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> mieterService.deleteMieter(1L)
        );

        // Meldung nennt die Anzahl betroffener Positionen (Specs/Ladestationen.md §5)
        assertThat(exception.getMessage(), containsString("3 Tarifposition(en)"));
        verify(mieterRepository, never()).deleteById(anyLong());
    }

    @Test
    void deleteMieter_SummiertPositionenUeberAlleEinheiten() {
        when(mieterRepository.existsById(1L)).thenReturn(true);
        when(mieterEinheitRepository.findEinheitIdsByMieterId(1L)).thenReturn(List.of(10L, 11L));
        when(tarifpositionRepository.countByEinheitId(10L)).thenReturn(2L);
        when(tarifpositionRepository.countByEinheitId(11L)).thenReturn(5L);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> mieterService.deleteMieter(1L)
        );

        assertThat(exception.getMessage(), containsString("7 Tarifposition(en)"));
    }

    @Test
    void deleteMieter_OhneTarifpositionen_ReturnsTrue() {
        when(mieterRepository.existsById(1L)).thenReturn(true);
        when(mieterEinheitRepository.findEinheitIdsByMieterId(1L)).thenReturn(List.of(10L, 11L));
        when(tarifpositionRepository.countByEinheitId(10L)).thenReturn(0L);
        when(tarifpositionRepository.countByEinheitId(11L)).thenReturn(0L);

        boolean result = mieterService.deleteMieter(1L);

        assertTrue(result);
        verify(mieterRepository).deleteById(1L);
    }

    @Test
    void deleteMieter_NotExists_PruefungEntfaellt() {
        when(mieterRepository.existsById(99L)).thenReturn(false);

        assertFalse(mieterService.deleteMieter(99L));

        verify(tarifpositionRepository, never()).countByEinheitId(anyLong());
    }

    // ==================== Loeschschutz: Mieter in einer Nebenkostenabrechnung ====================
    // Specs/Nebenkosten/Abrechnung.md FR-5: Die Fremdschluessel stehen auf ON DELETE RESTRICT.
    // Ohne die Pruefung hier saehe der Benutzer eine DataIntegrityViolationException statt einer
    // verstaendlichen Meldung.

    @Test
    void deleteMieter_MitVerbrauchsmengeInAbrechnung_ThrowsException() {
        when(mieterRepository.existsById(1L)).thenReturn(true);
        when(mieterEinheitRepository.findEinheitIdsByMieterId(1L)).thenReturn(List.of());
        when(nkVerbrauchRepository.countByMieterId(1L)).thenReturn(2L);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> mieterService.deleteMieter(1L)
        );

        assertThat(exception.getMessage(), containsString("Nebenkostenabrechnung"));
        verify(mieterRepository, never()).deleteById(anyLong());
    }

    @Test
    void deleteMieter_MitZusatzpositionInAbrechnung_ThrowsException() {
        when(mieterRepository.existsById(1L)).thenReturn(true);
        when(mieterEinheitRepository.findEinheitIdsByMieterId(1L)).thenReturn(List.of());
        when(nkVerbrauchRepository.countByMieterId(1L)).thenReturn(0L);
        when(nkZusatzRepository.countByMieterId(1L)).thenReturn(1L);

        assertThrows(IllegalArgumentException.class, () -> mieterService.deleteMieter(1L));

        verify(mieterRepository, never()).deleteById(anyLong());
    }

    @Test
    void deleteMieter_MitAkontoInAbrechnung_ThrowsException() {
        when(mieterRepository.existsById(1L)).thenReturn(true);
        when(mieterEinheitRepository.findEinheitIdsByMieterId(1L)).thenReturn(List.of());
        when(nkVerbrauchRepository.countByMieterId(1L)).thenReturn(0L);
        when(nkZusatzRepository.countByMieterId(1L)).thenReturn(0L);
        when(nkAkontoRepository.countByMieterId(1L)).thenReturn(1L);

        assertThrows(IllegalArgumentException.class, () -> mieterService.deleteMieter(1L));

        verify(mieterRepository, never()).deleteById(anyLong());
    }

    @Test
    void deleteMieter_OhneNebenkostenBezug_ReturnsTrue() {
        when(mieterRepository.existsById(1L)).thenReturn(true);
        when(mieterEinheitRepository.findEinheitIdsByMieterId(1L)).thenReturn(List.of());
        when(nkVerbrauchRepository.countByMieterId(1L)).thenReturn(0L);
        when(nkZusatzRepository.countByMieterId(1L)).thenReturn(0L);
        when(nkAkontoRepository.countByMieterId(1L)).thenReturn(0L);

        assertTrue(mieterService.deleteMieter(1L));

        verify(mieterRepository).deleteById(1L);
    }

    @Test
    void deleteMieter_NotExists_NebenkostenPruefungEntfaellt() {
        when(mieterRepository.existsById(99L)).thenReturn(false);

        assertFalse(mieterService.deleteMieter(99L));

        verify(nkVerbrauchRepository, never()).countByMieterId(anyLong());
        verify(nkZusatzRepository, never()).countByMieterId(anyLong());
        verify(nkAkontoRepository, never()).countByMieterId(anyLong());
    }

    @Test
    void getMieterForQuartal_MultipleMieterInQuartal_ReturnsAll() {
        LocalDate quartalBeginn = LocalDate.of(2024, 4, 1);
        LocalDate quartalEnde = LocalDate.of(2024, 6, 30);

        Mieter mieter1 = new Mieter("Erster", LocalDate.of(2024, 1, 1), 1L);
        mieter1.setMietende(LocalDate.of(2024, 5, 15));
        Mieter mieter2 = new Mieter("Zweiter", LocalDate.of(2024, 5, 16), 1L);

        when(mieterRepository.findByEinheitIdAndQuartal(1L, quartalBeginn, quartalEnde))
                .thenReturn(Arrays.asList(mieter1, mieter2));

        List<Mieter> result = mieterService.getMieterForQuartal(1L, quartalBeginn, quartalEnde);

        assertEquals(2, result.size());
    }
}
