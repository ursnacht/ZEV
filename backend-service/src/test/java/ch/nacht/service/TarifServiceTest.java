package ch.nacht.service;

import ch.nacht.entity.Mengeneinheit;
import ch.nacht.entity.Tarif;
import ch.nacht.entity.TarifTyp;
import ch.nacht.exception.TarifLueckenException;
import ch.nacht.repository.TarifRepository;
import ch.nacht.repository.TarifpositionRepository;
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TarifServiceTest {

    @Mock
    private TarifRepository tarifRepository;

    @Mock
    private TarifpositionRepository tarifpositionRepository;

    @Mock
    private OrganizationContextService organizationContextService;

    @Mock
    private HibernateFilterService hibernateFilterService;

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
        // In der Reihenfolge der Rechnung (Specs/Tarifverwaltung.md, FR-4)
        when(tarifRepository.findAllByOrderByReihenfolgeAscIdAsc())
            .thenReturn(Arrays.asList(vnbTarif2024, zevTarif2024));

        List<Tarif> result = tarifService.getAllTarife();

        assertNotNull(result);
        assertEquals(2, result.size());
        verify(tarifRepository).findAllByOrderByReihenfolgeAscIdAsc();
    }

    @Test
    void getTarifById_Found_ReturnsTarif() {
        when(tarifRepository.findFirstById(1L)).thenReturn(Optional.of(zevTarif2024));

        Optional<Tarif> result = tarifService.getTarifById(1L);

        assertTrue(result.isPresent());
        assertEquals("ZEV Tarif 2024", result.get().getBezeichnung());
    }

    @Test
    void getTarifById_NotFound_ReturnsEmpty() {
        when(tarifRepository.findFirstById(999L)).thenReturn(Optional.empty());

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

        when(tarifRepository.existsOverlappingTarifMitBezeichnung(
            eq(TarifTyp.ZEV), eq("ZEV Tarif 2025"), any(), any(), eq(-1L)
        )).thenReturn(false);
        when(tarifRepository.save(newTarif)).thenReturn(newTarif);

        Tarif result = tarifService.saveTarif(newTarif);

        assertNotNull(result);
        verify(tarifRepository).save(newTarif);
    }

    @Test
    void saveTarif_OverlappingTarifGleicheBezeichnung_ThrowsException() {
        // Ein zweiter Tarif mit gleicher Bezeichnung im selben Zeitraum stuende doppelt auf jeder
        // Rechnung - das bleibt verboten (Specs/Tarifverwaltung.md, FR-2).
        Tarif overlappingTarif = new Tarif(
            "ZEV Tarif 2024 Alt",
            TarifTyp.ZEV,
            new BigDecimal("0.19000"),
            LocalDate.of(2024, 6, 1),
            LocalDate.of(2024, 12, 31)
        );

        when(tarifRepository.existsOverlappingTarifMitBezeichnung(
            eq(TarifTyp.ZEV), eq("ZEV Tarif 2024 Alt"), any(), any(), eq(-1L)
        )).thenReturn(true);

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> tarifService.saveTarif(overlappingTarif)
        );

        assertTrue(exception.getMessage().contains("gleicher Bezeichnung"));
        verify(tarifRepository, never()).save(any());
    }

    @Test
    void saveTarif_VnbNetznutzungNebenEnergielieferung_SavesSuccessfully() {
        // Energielieferung und Netznutzung gelten gleichzeitig: Geprueft wird nur gegen Tarife
        // derselben Bezeichnung, die typweite Pruefung entfaellt fuer VNB.
        Tarif netznutzung = new Tarif("Netznutzung", TarifTyp.VNB, new BigDecimal("0.10500"),
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        when(tarifRepository.existsOverlappingTarifMitBezeichnung(
            eq(TarifTyp.VNB), eq("Netznutzung"), any(), any(), eq(-1L))).thenReturn(false);
        when(tarifRepository.save(netznutzung)).thenReturn(netznutzung);

        assertNotNull(tarifService.saveTarif(netznutzung));

        verify(tarifRepository, never()).existsOverlappingTarif(any(), any(), any(), anyLong());
        verify(tarifRepository).save(netznutzung);
    }

    @Test
    void saveTarif_DreiGrundgebuehren_PruefungJeBezeichnung() {
        // Energielieferung, Netznutzung und Messtarif sind je ein Grundgebuehr-Tarif.
        for (String bezeichnung : List.of("Grundtarif Energielieferung", "Grundtarif Netznutzung", "Messtarif")) {
            Tarif grundgebuehr = new Tarif(bezeichnung, TarifTyp.GRUNDGEBUEHR, new BigDecimal("5.00000"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
            when(tarifRepository.existsOverlappingTarifMitBezeichnung(
                eq(TarifTyp.GRUNDGEBUEHR), eq(bezeichnung), any(), any(), eq(-1L))).thenReturn(false);
            when(tarifRepository.save(grundgebuehr)).thenReturn(grundgebuehr);

            assertNotNull(tarifService.saveTarif(grundgebuehr));
        }

        verify(tarifRepository, never()).existsOverlappingTarif(any(), any(), any(), anyLong());
        verify(tarifRepository, times(3)).save(any(Tarif.class));
    }

    @Test
    void saveTarif_GrundgebuehrOhneMengeneinheit_GiltProMonat() {
        // Ohne Angabe bleibt das bisherige Verhalten: volle Monate (Specs/Tarifverwaltung.md, FR-3)
        Tarif grundgebuehr = new Tarif("Messtarif", TarifTyp.GRUNDGEBUEHR, new BigDecimal("5.00000"),
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        when(tarifRepository.save(any(Tarif.class))).thenAnswer(i -> i.getArgument(0));

        Tarif gespeichert = tarifService.saveTarif(grundgebuehr);

        assertEquals(Mengeneinheit.MONAT, gespeichert.getMengeneinheit());
    }

    @Test
    void saveTarif_GrundgebuehrProTag_BleibtTag() {
        Tarif grundgebuehr = new Tarif("Messtarif", TarifTyp.GRUNDGEBUEHR, new BigDecimal("0.16438"),
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        grundgebuehr.setMengeneinheit(Mengeneinheit.TAG);
        when(tarifRepository.save(any(Tarif.class))).thenAnswer(i -> i.getArgument(0));

        Tarif gespeichert = tarifService.saveTarif(grundgebuehr);

        assertEquals(Mengeneinheit.TAG, gespeichert.getMengeneinheit());
        assertEquals("TAG", gespeichert.effektiveMengeneinheit());
    }

    @Test
    void saveTarif_GrundgebuehrMitStueck_ThrowsException() {
        Tarif grundgebuehr = new Tarif("Messtarif", TarifTyp.GRUNDGEBUEHR, new BigDecimal("5.00000"),
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        grundgebuehr.setMengeneinheit(Mengeneinheit.STUECK);

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> tarifService.saveTarif(grundgebuehr)
        );

        assertTrue(exception.getMessage().contains("MONAT oder TAG"));
        verify(tarifRepository, never()).save(any());
    }

    @Test
    void saveTarif_LadestromUeberschneidung_ThrowsException() {
        // LADESTROM bleibt je Typ eindeutig: Die Position waehlt den Tarif, ein zweiter hiesse,
        // dieselben kWh zweimal zu erfassen.
        Tarif ladestrom = new Tarif("Ladestrom Netznutzung", TarifTyp.LADESTROM, new BigDecimal("0.10000"),
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        when(tarifRepository.existsOverlappingTarif(
            eq(TarifTyp.LADESTROM), any(), any(), eq(-1L))).thenReturn(true);

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> tarifService.saveTarif(ladestrom)
        );

        assertTrue(exception.getMessage().contains("überschneidet"));
        verify(tarifRepository, never()).existsOverlappingTarifMitBezeichnung(any(), any(), any(), any(), anyLong());
        verify(tarifRepository, never()).save(any());
    }

    @Test
    void saveTarif_ZweiUeberlappendeZusatzTarife_SavesBoth() {
        // Sauna, Waschkueche und Gaestezimmer teilen sich den Typ ZUSATZ und muessen gleichzeitig
        // gueltig sein - die Ueberschneidungspruefung ist fuer diesen Typ ausgenommen
        // (Specs/Tarifpositionen.md FR-1.2). Mehrdeutig wird dadurch nichts, weil der Tarif an
        // der Position ausdruecklich gewaehlt wird.
        Tarif sauna = new Tarif("Sauna", TarifTyp.ZUSATZ, new BigDecimal("5.00000"),
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        sauna.setMengeneinheit(Mengeneinheit.STUECK);

        when(tarifRepository.save(sauna)).thenReturn(sauna);

        assertNotNull(tarifService.saveTarif(sauna));

        // Die Pruefung wird fuer diesen Typ gar nicht erst angefragt
        verify(tarifRepository, never()).existsOverlappingTarif(any(), any(), any(), anyLong());
        verify(tarifRepository, never()).existsOverlappingTarifMitBezeichnung(any(), any(), any(), any(), anyLong());
        verify(tarifRepository).save(sauna);
    }

    @Test
    void saveTarif_ZusatzOhneMengeneinheit_ThrowsException() {
        Tarif ohneEinheit = new Tarif("Sauna", TarifTyp.ZUSATZ, new BigDecimal("5.00000"),
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> tarifService.saveTarif(ohneEinheit)
        );

        assertTrue(exception.getMessage().contains("Mengeneinheit"));
        verify(tarifRepository, never()).save(any());
    }

    @Test
    void saveTarif_MengeneinheitBeiAnderemTyp_WirdVerworfen() {
        // Entsteht typischerweise beim Umschalten des Typs im Formular. Der Wert haette keine
        // Wirkung, bliebe aber als irrefuehrender Datenrest stehen.
        Tarif zev = new Tarif("ZEV 2026", TarifTyp.ZEV, new BigDecimal("0.20000"),
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        zev.setMengeneinheit(Mengeneinheit.STUECK);

        when(tarifRepository.existsOverlappingTarifMitBezeichnung(eq(TarifTyp.ZEV), any(), any(), any(), eq(-1L)))
            .thenReturn(false);
        when(tarifRepository.save(any(Tarif.class))).thenAnswer(i -> i.getArgument(0));

        Tarif gespeichert = tarifService.saveTarif(zev);

        assertNull(gespeichert.getMengeneinheit());
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

        when(tarifRepository.existsOverlappingTarifMitBezeichnung(
            eq(TarifTyp.ZEV), eq("ZEV Tarif 2024 Updated"), any(), any(), eq(1L)
        )).thenReturn(false);
        when(tarifRepository.save(zevTarif2024)).thenReturn(zevTarif2024);

        Tarif result = tarifService.saveTarif(zevTarif2024);

        assertNotNull(result);
        verify(tarifRepository).existsOverlappingTarifMitBezeichnung(
            eq(TarifTyp.ZEV), eq("ZEV Tarif 2024 Updated"), any(), any(), eq(1L)
        );
    }

    @Test
    void deleteTarif_MitTarifpositionen_ThrowsException() {
        // Der FK (ON DELETE RESTRICT) weist das Loeschen ebenfalls ab - aber ohne die von der
        // Spec geforderte Anzahl (Specs/Ladestromtarif.md, Edge Cases).
        when(tarifRepository.existsById(1L)).thenReturn(true);
        when(tarifpositionRepository.countByTarifId(1L)).thenReturn(3L);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> tarifService.deleteTarif(1L));

        assertTrue(ex.getMessage().contains("3"), "Die Meldung nennt die Anzahl der Positionen");
        verify(tarifRepository, never()).deleteById(any());
    }

    @Test
    void deleteTarif_Exists_ReturnsTrue() {
        when(tarifRepository.existsById(1L)).thenReturn(true);
        when(tarifpositionRepository.countByTarifId(1L)).thenReturn(0L);
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

        TarifLueckenException exception = assertThrows(
            TarifLueckenException.class,
            () -> tarifService.validateTarifAbdeckung(von, bis)
        );

        assertTrue(exception.getLuecken().stream().anyMatch(l -> l.tarifTyp().equals("ZEV")));
    }

    @Test
    void validateTarifAbdeckung_MissingVnbTarif_ThrowsException() {
        LocalDate von = LocalDate.of(2024, 1, 1);
        LocalDate bis = LocalDate.of(2024, 1, 31);

        when(tarifRepository.findByTariftypAndZeitraumOverlapping(TarifTyp.ZEV, von, bis))
            .thenReturn(Collections.singletonList(zevTarif2024));
        when(tarifRepository.findByTariftypAndZeitraumOverlapping(TarifTyp.VNB, von, bis))
            .thenReturn(Collections.emptyList());

        TarifLueckenException exception = assertThrows(
            TarifLueckenException.class,
            () -> tarifService.validateTarifAbdeckung(von, bis)
        );

        assertTrue(exception.getLuecken().stream().anyMatch(l -> l.tarifTyp().equals("VNB")));
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

        TarifLueckenException exception = assertThrows(
            TarifLueckenException.class,
            () -> tarifService.validateTarifAbdeckung(von, bis)
        );

        assertTrue(exception.getLuecken().stream().anyMatch(l -> l.tarifTyp().equals("ZEV")));
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

    // ==================== validateQuartale Tests ====================

    @Test
    void validateQuartale_NoTarife_ReturnsValidWithMessage() {
        when(tarifRepository.findAllByOrderByTariftypAscGueltigVonDesc())
            .thenReturn(Collections.emptyList());

        TarifService.ValidationResult result = tarifService.validateQuartale();

        assertTrue(result.valid());
        assertTrue(result.luecken().isEmpty());
    }

    @Test
    void validateQuartale_FullCoverage_ReturnsValid() {
        when(tarifRepository.findAllByOrderByTariftypAscGueltigVonDesc())
            .thenReturn(Arrays.asList(zevTarif2024, vnbTarif2024));

        // Mock coverage for all quarters
        when(tarifRepository.findByTariftypAndZeitraumOverlapping(eq(TarifTyp.ZEV), any(), any()))
            .thenReturn(Collections.singletonList(zevTarif2024));
        when(tarifRepository.findByTariftypAndZeitraumOverlapping(eq(TarifTyp.VNB), any(), any()))
            .thenReturn(Collections.singletonList(vnbTarif2024));

        TarifService.ValidationResult result = tarifService.validateQuartale();

        assertTrue(result.valid());
        assertTrue(result.luecken().isEmpty());
    }

    @Test
    void validateQuartale_MissingTarifInQuarter_ReturnsInvalidWithErrors() {
        // Only ZEV tarif for Q1
        Tarif zevQ1 = new Tarif(
            "ZEV Q1",
            TarifTyp.ZEV,
            new BigDecimal("0.20000"),
            LocalDate.of(2024, 1, 1),
            LocalDate.of(2024, 3, 31)
        );

        when(tarifRepository.findAllByOrderByTariftypAscGueltigVonDesc())
            .thenReturn(Collections.singletonList(zevQ1));

        // ZEV covered, VNB missing
        when(tarifRepository.findByTariftypAndZeitraumOverlapping(eq(TarifTyp.ZEV), any(), any()))
            .thenReturn(Collections.singletonList(zevQ1));
        when(tarifRepository.findByTariftypAndZeitraumOverlapping(eq(TarifTyp.VNB), any(), any()))
            .thenReturn(Collections.emptyList());

        TarifService.ValidationResult result = tarifService.validateQuartale();

        assertFalse(result.valid());
        // Nur das Quartal mit Tarif wird geprueft (Specs/TarifValidierung.md, FR-1.2)
        assertEquals(1, result.luecken().size());
        assertThat(result.luecken().get(0).periode(), containsString("Q1/2024"));
        assertTrue(result.luecken().get(0).luecken().stream().anyMatch(l -> l.tarifTyp().equals("VNB")));
    }

    /**
     * Specs/TarifValidierung.md, FR-1.5: Welche Quartale geprueft werden, bestimmen die Tarife aller
     * Typen - auf Luecken geprueft werden aber nur ZEV und VNB. Ein Quartal mit nur einer
     * Grundgebuehr meldet deshalb die fehlenden ZEV- und VNB-Tarife, nie eine fehlende Grundgebuehr.
     */
    @Test
    void validateQuartale_NurGrundgebuehrImQuartal_PrueftZevUndVnb() {
        Tarif grundgebuehr = new Tarif("Messtarif", TarifTyp.GRUNDGEBUEHR, new BigDecimal("5.00000"),
            LocalDate.of(2024, 4, 1), LocalDate.of(2024, 6, 30));
        when(tarifRepository.findAllByOrderByTariftypAscGueltigVonDesc())
            .thenReturn(Collections.singletonList(grundgebuehr));
        when(tarifRepository.findByTariftypAndZeitraumOverlapping(any(), any(), any()))
            .thenReturn(Collections.emptyList());

        TarifService.ValidationResult result = tarifService.validateQuartale();

        assertFalse(result.valid());
        assertEquals(1, result.luecken().size());
        assertThat(result.luecken().get(0).periode(), containsString("Q2/2024"));
        assertEquals(List.of("ZEV", "VNB"),
            result.luecken().get(0).luecken().stream().map(l -> l.tarifTyp()).toList());
        verify(tarifRepository, never())
            .findByTariftypAndZeitraumOverlapping(eq(TarifTyp.GRUNDGEBUEHR), any(), any());
    }

    @Test
    void validateQuartale_GapInQuarter_ReturnsInvalidWithErrors() {
        // ZEV tarif only covers first half of Q1
        Tarif zevPartial = new Tarif(
            "ZEV Partial",
            TarifTyp.ZEV,
            new BigDecimal("0.20000"),
            LocalDate.of(2024, 1, 1),
            LocalDate.of(2024, 2, 15)
        );

        when(tarifRepository.findAllByOrderByTariftypAscGueltigVonDesc())
            .thenReturn(Arrays.asList(zevPartial, vnbTarif2024));

        // ZEV has gap, VNB full coverage
        when(tarifRepository.findByTariftypAndZeitraumOverlapping(eq(TarifTyp.ZEV), any(), any()))
            .thenReturn(Collections.singletonList(zevPartial));
        when(tarifRepository.findByTariftypAndZeitraumOverlapping(eq(TarifTyp.VNB), any(), any()))
            .thenReturn(Collections.singletonList(vnbTarif2024));

        TarifService.ValidationResult result = tarifService.validateQuartale();

        assertFalse(result.valid());
        assertFalse(result.luecken().isEmpty());
        assertTrue(result.luecken().get(0).luecken().stream().anyMatch(l -> l.tarifTyp().equals("ZEV")));
    }

    // ==================== validateJahre Tests ====================

    @Test
    void validateJahre_NoTarife_ReturnsValidWithMessage() {
        when(tarifRepository.findDistinctYears())
            .thenReturn(Collections.emptyList());

        TarifService.ValidationResult result = tarifService.validateJahre();

        assertTrue(result.valid());
        assertTrue(result.luecken().isEmpty());
    }

    @Test
    void validateJahre_FullCoverage_ReturnsValid() {
        when(tarifRepository.findDistinctYears())
            .thenReturn(Collections.singletonList(2024));

        // Mock full year coverage
        when(tarifRepository.findByTariftypAndZeitraumOverlapping(
            eq(TarifTyp.ZEV),
            eq(LocalDate.of(2024, 1, 1)),
            eq(LocalDate.of(2024, 12, 31))))
            .thenReturn(Collections.singletonList(zevTarif2024));
        when(tarifRepository.findByTariftypAndZeitraumOverlapping(
            eq(TarifTyp.VNB),
            eq(LocalDate.of(2024, 1, 1)),
            eq(LocalDate.of(2024, 12, 31))))
            .thenReturn(Collections.singletonList(vnbTarif2024));

        TarifService.ValidationResult result = tarifService.validateJahre();

        assertTrue(result.valid());
        assertTrue(result.luecken().isEmpty());
    }

    @Test
    void validateJahre_MissingTarifInYear_ReturnsInvalidWithErrors() {
        when(tarifRepository.findDistinctYears())
            .thenReturn(Collections.singletonList(2024));

        // ZEV covered, VNB missing
        when(tarifRepository.findByTariftypAndZeitraumOverlapping(
            eq(TarifTyp.ZEV),
            eq(LocalDate.of(2024, 1, 1)),
            eq(LocalDate.of(2024, 12, 31))))
            .thenReturn(Collections.singletonList(zevTarif2024));
        when(tarifRepository.findByTariftypAndZeitraumOverlapping(
            eq(TarifTyp.VNB),
            eq(LocalDate.of(2024, 1, 1)),
            eq(LocalDate.of(2024, 12, 31))))
            .thenReturn(Collections.emptyList());

        TarifService.ValidationResult result = tarifService.validateJahre();

        assertFalse(result.valid());
        assertFalse(result.luecken().isEmpty());
        assertThat(result.luecken().get(0).periode(), containsString("2024"));
        assertTrue(result.luecken().get(0).luecken().stream().anyMatch(l -> l.tarifTyp().equals("VNB")));
    }

    @Test
    void validateJahre_MultipleYears_ChecksAllYears() {
        when(tarifRepository.findDistinctYears())
            .thenReturn(Arrays.asList(2024, 2025));

        // 2024 fully covered
        when(tarifRepository.findByTariftypAndZeitraumOverlapping(
            eq(TarifTyp.ZEV),
            eq(LocalDate.of(2024, 1, 1)),
            eq(LocalDate.of(2024, 12, 31))))
            .thenReturn(Collections.singletonList(zevTarif2024));
        when(tarifRepository.findByTariftypAndZeitraumOverlapping(
            eq(TarifTyp.VNB),
            eq(LocalDate.of(2024, 1, 1)),
            eq(LocalDate.of(2024, 12, 31))))
            .thenReturn(Collections.singletonList(vnbTarif2024));

        // 2025 missing both tariffs
        when(tarifRepository.findByTariftypAndZeitraumOverlapping(
            eq(TarifTyp.ZEV),
            eq(LocalDate.of(2025, 1, 1)),
            eq(LocalDate.of(2025, 12, 31))))
            .thenReturn(Collections.emptyList());
        when(tarifRepository.findByTariftypAndZeitraumOverlapping(
            eq(TarifTyp.VNB),
            eq(LocalDate.of(2025, 1, 1)),
            eq(LocalDate.of(2025, 12, 31))))
            .thenReturn(Collections.emptyList());

        TarifService.ValidationResult result = tarifService.validateJahre();

        assertFalse(result.valid());
        assertFalse(result.luecken().isEmpty());
        assertTrue(result.luecken().stream().anyMatch(p -> p.periode().equals("2025")));
    }

    @Test
    void validateJahre_DateFormatIsSwiss() {
        when(tarifRepository.findDistinctYears())
            .thenReturn(Collections.singletonList(2024));

        // ZEV missing
        when(tarifRepository.findByTariftypAndZeitraumOverlapping(
            eq(TarifTyp.ZEV),
            eq(LocalDate.of(2024, 1, 1)),
            eq(LocalDate.of(2024, 12, 31))))
            .thenReturn(Collections.emptyList());
        when(tarifRepository.findByTariftypAndZeitraumOverlapping(
            eq(TarifTyp.VNB),
            eq(LocalDate.of(2024, 1, 1)),
            eq(LocalDate.of(2024, 12, 31))))
            .thenReturn(Collections.singletonList(vnbTarif2024));

        TarifService.ValidationResult result = tarifService.validateJahre();

        assertFalse(result.valid());
        // Check date format is dd.MM.yyyy (Swiss format)
        assertThat(result.luecken().get(0).luecken().get(0).datum(), containsString("01.01.2024"));
    }

    // ==================== Reihenfolge (Specs/Tarifverwaltung.md, FR-4) ====================

    @Test
    void saveTarif_Neu_KommtHinterDenLetztenTarifDesselbenTyps() {
        Tarif neu = new Tarif("Netznutzung", TarifTyp.VNB, new BigDecimal("0.10000"),
            LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31));
        when(organizationContextService.getCurrentOrgId()).thenReturn(7L);
        when(tarifRepository.findMaxReihenfolgeByTariftyp(TarifTyp.VNB)).thenReturn(4);
        when(tarifRepository.save(any(Tarif.class))).thenAnswer(i -> i.getArgument(0));

        Tarif gespeichert = tarifService.saveTarif(neu);

        // Die Tarife hinter Platz 4 ruecken nach - nur im eigenen Mandanten
        verify(tarifRepository).verschiebeReihenfolgeAb(7L, 4);
        assertEquals(5, gespeichert.getReihenfolge());
    }

    @Test
    void saveTarif_ErsterSeinesTyps_KommtAnsEnde() {
        Tarif neu = new Tarif("Ladestrom", TarifTyp.LADESTROM, new BigDecimal("0.30000"),
            LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31));
        when(tarifRepository.findMaxReihenfolgeByTariftyp(TarifTyp.LADESTROM)).thenReturn(null);
        when(tarifRepository.findMaxReihenfolge()).thenReturn(9);
        when(tarifRepository.save(any(Tarif.class))).thenAnswer(i -> i.getArgument(0));

        Tarif gespeichert = tarifService.saveTarif(neu);

        assertEquals(10, gespeichert.getReihenfolge());
        verify(tarifRepository, never()).verschiebeReihenfolgeAb(any(), anyInt());
    }

    @Test
    void saveTarif_ErsterTarifUeberhaupt_ErhaeltPlatzEins() {
        Tarif neu = new Tarif("ZEV", TarifTyp.ZEV, new BigDecimal("0.20000"),
            LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31));
        when(tarifRepository.save(any(Tarif.class))).thenAnswer(i -> i.getArgument(0));

        assertEquals(1, tarifService.saveTarif(neu).getReihenfolge());
    }

    @Test
    void saveTarif_Update_BehaeltGespeicherteReihenfolge() {
        // Ein mitgeschickter Wert waere ein veralteter Stand der Liste
        Tarif bestehend = new Tarif("ZEV Tarif 2024", TarifTyp.ZEV, new BigDecimal("0.20000"),
            LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));
        bestehend.setId(1L);
        bestehend.setReihenfolge(3);
        zevTarif2024.setReihenfolge(99);
        when(tarifRepository.findFirstById(1L)).thenReturn(Optional.of(bestehend));
        when(tarifRepository.save(any(Tarif.class))).thenAnswer(i -> i.getArgument(0));

        Tarif gespeichert = tarifService.saveTarif(zevTarif2024);

        assertEquals(3, gespeichert.getReihenfolge());
        verify(tarifRepository, never()).verschiebeReihenfolgeAb(any(), anyInt());
    }

    @Test
    void verschiebe_NachVornVorZiel_RuecktDieDazwischenNach() {
        // Platz 7 vor Platz 2: 2..6 ruecken um eins nach hinten, der Tarif erhaelt Platz 2
        Tarif tarif = tarifMitPlatz(30L, 7);
        tarif.setOrgId(5L);
        when(tarifRepository.findFirstById(30L)).thenReturn(Optional.of(tarif));
        when(tarifRepository.findFirstById(10L)).thenReturn(Optional.of(tarifMitPlatz(10L, 2)));

        tarifService.verschiebe(30L, 10L, null);

        verify(tarifRepository).verschiebeReihenfolgeBereich(5L, 2, 6, 1);
        verify(tarifRepository).setzeReihenfolge(5L, 30L, 2);
        // Kein Speichern der Entity - sonst schluege die Bean Validation des ganzen Tarifs zu
        verify(tarifRepository, never()).save(any());
        verify(hibernateFilterService).enableOrgFilter();
    }

    @Test
    void verschiebe_NachHintenHinterZiel_RuecktDieDazwischenVor() {
        // Platz 2 hinter Platz 7: 3..7 ruecken um eins nach vorn, der Tarif erhaelt Platz 7
        Tarif tarif = tarifMitPlatz(10L, 2);
        tarif.setOrgId(5L);
        when(tarifRepository.findFirstById(10L)).thenReturn(Optional.of(tarif));
        when(tarifRepository.findFirstById(30L)).thenReturn(Optional.of(tarifMitPlatz(30L, 7)));

        tarifService.verschiebe(10L, null, 30L);

        verify(tarifRepository).verschiebeReihenfolgeBereich(5L, 3, 7, -1);
        verify(tarifRepository).setzeReihenfolge(5L, 10L, 7);
    }

    @Test
    void verschiebe_NachHintenVorZiel_EndetDavor() {
        // Platz 2 vor Platz 7: 3..6 ruecken vor, der Tarif erhaelt Platz 6
        Tarif tarif = tarifMitPlatz(10L, 2);
        tarif.setOrgId(5L);
        when(tarifRepository.findFirstById(10L)).thenReturn(Optional.of(tarif));
        when(tarifRepository.findFirstById(30L)).thenReturn(Optional.of(tarifMitPlatz(30L, 7)));

        tarifService.verschiebe(10L, 30L, null);

        verify(tarifRepository).verschiebeReihenfolgeBereich(5L, 3, 6, -1);
        verify(tarifRepository).setzeReihenfolge(5L, 10L, 6);
    }

    @Test
    void verschiebe_SchonAmZiel_AendertNurDenTarif() {
        // Platz 3 vor Platz 4: steht schon dort - kein Bereich zu verschieben
        Tarif tarif = tarifMitPlatz(10L, 3);
        when(tarifRepository.findFirstById(10L)).thenReturn(Optional.of(tarif));
        when(tarifRepository.findFirstById(20L)).thenReturn(Optional.of(tarifMitPlatz(20L, 4)));

        tarifService.verschiebe(10L, 20L, null);

        verify(tarifRepository, never()).verschiebeReihenfolgeBereich(any(), anyInt(), anyInt(), anyInt());
        verify(tarifRepository).setzeReihenfolge(tarif.getOrgId(), 10L, 3);
    }

    @Test
    void verschiebe_UnbekannterOderFremderTarif_ThrowsException() {
        // findFirstById laeuft unter dem orgFilter - ein mandantenfremder Tarif ist "unbekannt"
        when(tarifRepository.findFirstById(1L)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> tarifService.verschiebe(1L, 2L, null));

        assertTrue(ex.getMessage().contains("Unbekannter Tarif"));
        verify(tarifRepository, never()).setzeReihenfolge(any(), any(), anyInt());
    }

    @Test
    void verschiebe_OhneOderMitZweiZielen_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> tarifService.verschiebe(1L, null, null));
        assertThrows(IllegalArgumentException.class, () -> tarifService.verschiebe(1L, 2L, 3L));
        verify(tarifRepository, never()).findFirstById(any());
    }

    @Test
    void verschiebe_RelativZuSichSelbst_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> tarifService.verschiebe(1L, 1L, null));
    }

    private static Tarif tarifMitPlatz(Long id, int reihenfolge) {
        Tarif tarif = new Tarif("T" + id, TarifTyp.VNB, new BigDecimal("0.10000"),
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        tarif.setId(id);
        tarif.setReihenfolge(reihenfolge);
        return tarif;
    }

    // ==================== Jahrespreis (Specs/Tarifverwaltung.md, FR-6) ====================

    @Test
    void saveTarif_GrundgebuehrMitJahrespreis_Gespeichert() {
        Tarif tarif = new Tarif("Messtarif", TarifTyp.GRUNDGEBUEHR, new BigDecimal("120.00000"),
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        tarif.setPreisProJahr(true);
        when(tarifRepository.save(any(Tarif.class))).thenAnswer(i -> i.getArgument(0));

        assertTrue(tarifService.saveTarif(tarif).isPreisProJahr());
    }

    @Test
    void saveTarif_ZusatzProMonatMitJahrespreis_Gespeichert() {
        Tarif tarif = new Tarif("Ladestation", TarifTyp.ZUSATZ, new BigDecimal("240.00000"),
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        tarif.setMengeneinheit(Mengeneinheit.MONAT);
        tarif.setPreisProJahr(true);
        when(tarifRepository.save(any(Tarif.class))).thenAnswer(i -> i.getArgument(0));

        assertTrue(tarifService.saveTarif(tarif).isPreisProJahr());
    }

    @Test
    void saveTarif_JahrespreisBeiZusatzProStueck_ThrowsException() {
        Tarif tarif = new Tarif("Sauna", TarifTyp.ZUSATZ, new BigDecimal("5.00000"),
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        tarif.setMengeneinheit(Mengeneinheit.STUECK);
        tarif.setPreisProJahr(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> tarifService.saveTarif(tarif));

        assertTrue(ex.getMessage().contains("Jahrespreis"));
        verify(tarifRepository, never()).save(any());
    }

    @Test
    void saveTarif_JahrespreisBeiVnb_ThrowsException() {
        Tarif tarif = new Tarif("Netz", TarifTyp.VNB, new BigDecimal("0.10000"),
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        tarif.setPreisProJahr(true);

        assertThrows(IllegalArgumentException.class, () -> tarifService.saveTarif(tarif));
        verify(tarifRepository, never()).save(any());
    }
}
