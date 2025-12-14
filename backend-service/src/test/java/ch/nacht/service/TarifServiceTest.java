package ch.nacht.service;

import ch.nacht.entity.Tarif;
import ch.nacht.entity.TarifTyp;
import ch.nacht.repository.TarifRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TarifServiceTest {

    @Mock
    private TarifRepository tarifRepository;

    @InjectMocks
    private TarifService tarifService;

    private Tarif zevTarif2024;
    private Tarif vnbTarif2024;

    @BeforeEach
    void setUp() {
        zevTarif2024 = new Tarif(
            "ZEV Tarif 2024",
            TarifTyp.ZEV,
            new BigDecimal("0.20000"),
            LocalDate.of(2024, 1, 1),
            LocalDate.of(2024, 12, 31)
        );
        zevTarif2024.setId(1L);

        vnbTarif2024 = new Tarif(
            "VNB Tarif 2024",
            TarifTyp.VNB,
            new BigDecimal("0.34192"),
            LocalDate.of(2024, 1, 1),
            LocalDate.of(2024, 12, 31)
        );
        vnbTarif2024.setId(2L);
    }

    @Test
    void getAllTarife_ReturnsSortedList() {
        when(tarifRepository.findAllByOrderByTariftypAscGueltigVonDesc())
            .thenReturn(Arrays.asList(vnbTarif2024, zevTarif2024));

        List<Tarif> result = tarifService.getAllTarife();

        assertNotNull(result);
        assertEquals(2, result.size());
        verify(tarifRepository).findAllByOrderByTariftypAscGueltigVonDesc();
    }

    @Test
    void getTarifById_Found_ReturnsTarif() {
        when(tarifRepository.findById(1L)).thenReturn(Optional.of(zevTarif2024));

        Optional<Tarif> result = tarifService.getTarifById(1L);

        assertTrue(result.isPresent());
        assertEquals("ZEV Tarif 2024", result.get().getBezeichnung());
    }

    @Test
    void getTarifById_NotFound_ReturnsEmpty() {
        when(tarifRepository.findById(999L)).thenReturn(Optional.empty());

        Optional<Tarif> result = tarifService.getTarifById(999L);

        assertFalse(result.isPresent());
    }

    @Test
    void saveTarif_ValidNewTarif_SavesSuccessfully() {
        Tarif newTarif = new Tarif(
            "ZEV Tarif 2025",
            TarifTyp.ZEV,
            new BigDecimal("0.21000"),
            LocalDate.of(2025, 1, 1),
            LocalDate.of(2025, 12, 31)
        );

        when(tarifRepository.existsOverlappingTarif(
            eq(TarifTyp.ZEV), any(), any(), eq(-1L)
        )).thenReturn(false);
        when(tarifRepository.save(newTarif)).thenReturn(newTarif);

        Tarif result = tarifService.saveTarif(newTarif);

        assertNotNull(result);
        verify(tarifRepository).save(newTarif);
    }

    @Test
    void saveTarif_OverlappingTarif_ThrowsException() {
        Tarif overlappingTarif = new Tarif(
            "ZEV Tarif 2024 Alt",
            TarifTyp.ZEV,
            new BigDecimal("0.19000"),
            LocalDate.of(2024, 6, 1),
            LocalDate.of(2024, 12, 31)
        );

        when(tarifRepository.existsOverlappingTarif(
            eq(TarifTyp.ZEV), any(), any(), eq(-1L)
        )).thenReturn(true);

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> tarifService.saveTarif(overlappingTarif)
        );

        assertTrue(exception.getMessage().contains("überschneidet"));
        verify(tarifRepository, never()).save(any());
    }

    @Test
    void saveTarif_InvalidDateRange_ThrowsException() {
        Tarif invalidTarif = new Tarif(
            "Invalid Tarif",
            TarifTyp.ZEV,
            new BigDecimal("0.20000"),
            LocalDate.of(2024, 12, 31),  // von after bis
            LocalDate.of(2024, 1, 1)
        );

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> tarifService.saveTarif(invalidTarif)
        );

        assertTrue(exception.getMessage().contains("Gültig von"));
        verify(tarifRepository, never()).existsOverlappingTarif(any(), any(), any(), anyLong());
        verify(tarifRepository, never()).save(any());
    }

    @Test
    void saveTarif_UpdateExisting_ExcludesItself() {
        zevTarif2024.setBezeichnung("ZEV Tarif 2024 Updated");

        when(tarifRepository.existsOverlappingTarif(
            eq(TarifTyp.ZEV), any(), any(), eq(1L)
        )).thenReturn(false);
        when(tarifRepository.save(zevTarif2024)).thenReturn(zevTarif2024);

        Tarif result = tarifService.saveTarif(zevTarif2024);

        assertNotNull(result);
        verify(tarifRepository).existsOverlappingTarif(
            eq(TarifTyp.ZEV), any(), any(), eq(1L)
        );
    }

    @Test
    void deleteTarif_Exists_ReturnsTrue() {
        when(tarifRepository.existsById(1L)).thenReturn(true);
        doNothing().when(tarifRepository).deleteById(1L);

        boolean result = tarifService.deleteTarif(1L);

        assertTrue(result);
        verify(tarifRepository).deleteById(1L);
    }

    @Test
    void deleteTarif_NotExists_ReturnsFalse() {
        when(tarifRepository.existsById(999L)).thenReturn(false);

        boolean result = tarifService.deleteTarif(999L);

        assertFalse(result);
        verify(tarifRepository, never()).deleteById(anyLong());
    }

    @Test
    void getTarifeForZeitraum_ReturnsMatchingTarife() {
        LocalDate von = LocalDate.of(2024, 3, 1);
        LocalDate bis = LocalDate.of(2024, 3, 31);

        when(tarifRepository.findByTariftypAndZeitraumOverlapping(TarifTyp.ZEV, von, bis))
            .thenReturn(Collections.singletonList(zevTarif2024));

        List<Tarif> result = tarifService.getTarifeForZeitraum(TarifTyp.ZEV, von, bis);

        assertEquals(1, result.size());
        assertEquals("ZEV Tarif 2024", result.get(0).getBezeichnung());
    }

    @Test
    void validateTarifAbdeckung_FullCoverage_NoException() {
        LocalDate von = LocalDate.of(2024, 1, 1);
        LocalDate bis = LocalDate.of(2024, 1, 31);

        when(tarifRepository.findByTariftypAndZeitraumOverlapping(TarifTyp.ZEV, von, bis))
            .thenReturn(Collections.singletonList(zevTarif2024));
        when(tarifRepository.findByTariftypAndZeitraumOverlapping(TarifTyp.VNB, von, bis))
            .thenReturn(Collections.singletonList(vnbTarif2024));

        assertDoesNotThrow(() -> tarifService.validateTarifAbdeckung(von, bis));
    }

    @Test
    void validateTarifAbdeckung_MissingZevTarif_ThrowsException() {
        LocalDate von = LocalDate.of(2024, 1, 1);
        LocalDate bis = LocalDate.of(2024, 1, 31);

        when(tarifRepository.findByTariftypAndZeitraumOverlapping(TarifTyp.ZEV, von, bis))
            .thenReturn(Collections.emptyList());
        when(tarifRepository.findByTariftypAndZeitraumOverlapping(TarifTyp.VNB, von, bis))
            .thenReturn(Collections.singletonList(vnbTarif2024));

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> tarifService.validateTarifAbdeckung(von, bis)
        );

        assertTrue(exception.getMessage().contains("ZEV"));
    }

    @Test
    void validateTarifAbdeckung_MissingVnbTarif_ThrowsException() {
        LocalDate von = LocalDate.of(2024, 1, 1);
        LocalDate bis = LocalDate.of(2024, 1, 31);

        when(tarifRepository.findByTariftypAndZeitraumOverlapping(TarifTyp.ZEV, von, bis))
            .thenReturn(Collections.singletonList(zevTarif2024));
        when(tarifRepository.findByTariftypAndZeitraumOverlapping(TarifTyp.VNB, von, bis))
            .thenReturn(Collections.emptyList());

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> tarifService.validateTarifAbdeckung(von, bis)
        );

        assertTrue(exception.getMessage().contains("VNB"));
    }

    @Test
    void validateTarifAbdeckung_GapInCoverage_ThrowsException() {
        LocalDate von = LocalDate.of(2024, 1, 1);
        LocalDate bis = LocalDate.of(2024, 3, 31);

        // ZEV Tarif only covers January
        Tarif zevJan = new Tarif(
            "ZEV Jan",
            TarifTyp.ZEV,
            new BigDecimal("0.20000"),
            LocalDate.of(2024, 1, 1),
            LocalDate.of(2024, 1, 31)
        );
        // ZEV Tarif only covers March (gap in February)
        Tarif zevMar = new Tarif(
            "ZEV Mar",
            TarifTyp.ZEV,
            new BigDecimal("0.21000"),
            LocalDate.of(2024, 3, 1),
            LocalDate.of(2024, 3, 31)
        );

        when(tarifRepository.findByTariftypAndZeitraumOverlapping(TarifTyp.ZEV, von, bis))
            .thenReturn(Arrays.asList(zevJan, zevMar));
        when(tarifRepository.findByTariftypAndZeitraumOverlapping(TarifTyp.VNB, von, bis))
            .thenReturn(Collections.singletonList(vnbTarif2024));

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> tarifService.validateTarifAbdeckung(von, bis)
        );

        assertTrue(exception.getMessage().contains("ZEV"));
    }

    @Test
    void validateTarifAbdeckung_MultipleTarifsWithoutGap_NoException() {
        LocalDate von = LocalDate.of(2024, 1, 1);
        LocalDate bis = LocalDate.of(2024, 6, 30);

        // ZEV Tarif H1 (Jan-Jun)
        Tarif zevH1 = new Tarif(
            "ZEV H1",
            TarifTyp.ZEV,
            new BigDecimal("0.20000"),
            LocalDate.of(2024, 1, 1),
            LocalDate.of(2024, 3, 31)
        );
        // ZEV Tarif H2 (Apr-Jun) - continuous with H1
        Tarif zevH2 = new Tarif(
            "ZEV H2",
            TarifTyp.ZEV,
            new BigDecimal("0.21000"),
            LocalDate.of(2024, 4, 1),
            LocalDate.of(2024, 6, 30)
        );

        when(tarifRepository.findByTariftypAndZeitraumOverlapping(TarifTyp.ZEV, von, bis))
            .thenReturn(Arrays.asList(zevH1, zevH2));
        when(tarifRepository.findByTariftypAndZeitraumOverlapping(TarifTyp.VNB, von, bis))
            .thenReturn(Collections.singletonList(vnbTarif2024));

        assertDoesNotThrow(() -> tarifService.validateTarifAbdeckung(von, bis));
    }
}
