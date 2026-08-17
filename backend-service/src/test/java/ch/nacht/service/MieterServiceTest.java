package ch.nacht.service;

import ch.nacht.entity.Mieter;
import ch.nacht.repository.MieterRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
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
        when(mieterRepository.findAllByOrderByEinheitIdAscMietbeginnDesc())
                .thenReturn(Arrays.asList(testMieter, mieter2));

        List<Mieter> result = mieterService.getAllMieter();

        assertEquals(2, result.size());
        assertEquals("Max Muster", result.get(0).getName());
        verify(hibernateFilterService).enableOrgFilter();
        verify(mieterRepository).findAllByOrderByEinheitIdAscMietbeginnDesc();
    }

    @Test
    void getAllMieter_EmptyList_ReturnsEmptyList() {
        when(mieterRepository.findAllByOrderByEinheitIdAscMietbeginnDesc())
                .thenReturn(Collections.emptyList());

        List<Mieter> result = mieterService.getAllMieter();

        assertTrue(result.isEmpty());
    }

    // ==================== getMieterById Tests ====================

    @Test
    void getMieterById_Found_ReturnsMieter() {
        when(mieterRepository.findById(1L)).thenReturn(Optional.of(testMieter));

        Optional<Mieter> result = mieterService.getMieterById(1L);

        assertTrue(result.isPresent());
        assertEquals("Max Muster", result.get().getName());
        verify(hibernateFilterService).enableOrgFilter();
    }

    @Test
    void getMieterById_NotFound_ReturnsEmpty() {
        when(mieterRepository.findById(99L)).thenReturn(Optional.empty());

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
