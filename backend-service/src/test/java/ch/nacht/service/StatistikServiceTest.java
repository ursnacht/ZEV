package ch.nacht.service;

import ch.nacht.dto.EinheitSummenDTO;
import ch.nacht.dto.MonatsStatistikDTO;
import ch.nacht.dto.StatistikDTO;
import ch.nacht.entity.Einheit;
import ch.nacht.entity.EinheitTyp;
import ch.nacht.repository.EinheitRepository;
import ch.nacht.repository.MesswerteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class StatistikServiceTest {

    @Mock
    private MesswerteRepository messwerteRepository;

    @Mock
    private EinheitRepository einheitRepository;

    @Mock
    private HibernateFilterService hibernateFilterService;

    @InjectMocks
    private StatistikService statistikService;

    private Einheit producer;
    private Einheit consumer1;
    private Einheit consumer2;

    @BeforeEach
    void setUp() {
        producer = new Einheit("Solaranlage", EinheitTyp.PRODUCER);
        producer.setId(1L);

        consumer1 = new Einheit("Wohnung A", EinheitTyp.CONSUMER);
        consumer1.setId(2L);

        consumer2 = new Einheit("Wohnung B", EinheitTyp.CONSUMER);
        consumer2.setId(3L);
    }

    @Test
    void getStatistik_SingleMonth_ReturnsCorrectData() {
        LocalDate von = LocalDate.of(2024, 1, 1);
        LocalDate bis = LocalDate.of(2024, 1, 31);
        LocalDateTime vonDateTime = von.atStartOfDay();
        LocalDateTime bisDateTime = bis.plusDays(1).atStartOfDay();

        // Mock letztes Messdatum
        when(messwerteRepository.findMaxZeit()).thenReturn(Optional.of(LocalDateTime.of(2024, 1, 31, 23, 45)));

        // Mock Einheiten
        when(einheitRepository.findAll()).thenReturn(Arrays.asList(producer, consumer1, consumer2));
        when(messwerteRepository.findDistinctEinheitenInRange(any(), any()))
                .thenReturn(Arrays.asList(producer, consumer1, consumer2));

        // Mock fehlende Tage (keine fehlenden)
        List<LocalDate> alleDaten = von.datesUntil(bis.plusDays(1)).toList();
        when(messwerteRepository.findDistinctDatesInRange(any(), any())).thenReturn(alleDaten);

        // Mock Summen nach Typ
        when(messwerteRepository.sumTotalByEinheitTypAndZeitBetween(eq(EinheitTyp.PRODUCER), any(), any()))
                .thenReturn(-1000.0); // Producer values are negative
        when(messwerteRepository.sumTotalByEinheitTypAndZeitBetween(eq(EinheitTyp.CONSUMER), any(), any()))
                .thenReturn(800.0);
        when(messwerteRepository.sumZevByEinheitTypAndZeitBetween(eq(EinheitTyp.PRODUCER), any(), any()))
                .thenReturn(-600.0);
        when(messwerteRepository.sumZevByEinheitTypAndZeitBetween(eq(EinheitTyp.CONSUMER), any(), any()))
                .thenReturn(600.0);
        when(messwerteRepository.sumZevCalculatedByEinheitTypAndZeitBetween(eq(EinheitTyp.CONSUMER), any(), any()))
                .thenReturn(600.0);

        // Mock Summen pro Einheit
        when(messwerteRepository.sumTotalByEinheitAndZeitBetween(eq(producer), any(), any())).thenReturn(-1000.0);
        when(messwerteRepository.sumZevByEinheitAndZeitBetween(eq(producer), any(), any())).thenReturn(-600.0);
        when(messwerteRepository.sumZevCalculatedByEinheitAndZeitBetween(eq(producer), any(), any())).thenReturn(0.0);

        when(messwerteRepository.sumTotalByEinheitAndZeitBetween(eq(consumer1), any(), any())).thenReturn(500.0);
        when(messwerteRepository.sumZevByEinheitAndZeitBetween(eq(consumer1), any(), any())).thenReturn(350.0);
        when(messwerteRepository.sumZevCalculatedByEinheitAndZeitBetween(eq(consumer1), any(), any())).thenReturn(350.0);

        when(messwerteRepository.sumTotalByEinheitAndZeitBetween(eq(consumer2), any(), any())).thenReturn(300.0);
        when(messwerteRepository.sumZevByEinheitAndZeitBetween(eq(consumer2), any(), any())).thenReturn(250.0);
        when(messwerteRepository.sumZevCalculatedByEinheitAndZeitBetween(eq(consumer2), any(), any())).thenReturn(250.0);

        StatistikDTO result = statistikService.getStatistik(von, bis);

        assertNotNull(result);
        assertEquals(LocalDate.of(2024, 1, 31), result.getMesswerteBisDate());
        assertTrue(result.isDatenVollstaendig());
        assertEquals(1, result.getMonate().size());

        MonatsStatistikDTO monat = result.getMonate().get(0);
        assertEquals(2024, monat.getJahr());
        assertEquals(1, monat.getMonat());
        assertEquals(1000.0, monat.getSummeProducerTotal()); // Absolute value
        assertEquals(800.0, monat.getSummeConsumerTotal());
        assertEquals(600.0, monat.getSummeProducerZev()); // Absolute value
        assertEquals(600.0, monat.getSummeConsumerZev());
        assertEquals(600.0, monat.getSummeConsumerZevCalculated());
    }

    @Test
    void getStatistik_MultipleMonths_ReturnsAllMonths() {
        LocalDate von = LocalDate.of(2024, 1, 1);
        LocalDate bis = LocalDate.of(2024, 3, 31);

        when(messwerteRepository.findMaxZeit()).thenReturn(Optional.of(LocalDateTime.of(2024, 3, 31, 23, 45)));
        when(einheitRepository.findAll()).thenReturn(Arrays.asList(producer, consumer1));
        when(messwerteRepository.findDistinctEinheitenInRange(any(), any()))
                .thenReturn(Arrays.asList(producer, consumer1));
        when(messwerteRepository.findDistinctDatesInRange(any(), any())).thenReturn(Collections.emptyList());

        // Mock alle Summen mit Standardwerten
        when(messwerteRepository.sumTotalByEinheitTypAndZeitBetween(any(), any(), any())).thenReturn(100.0);
        when(messwerteRepository.sumZevByEinheitTypAndZeitBetween(any(), any(), any())).thenReturn(50.0);
        when(messwerteRepository.sumZevCalculatedByEinheitTypAndZeitBetween(any(), any(), any())).thenReturn(50.0);
        when(messwerteRepository.sumTotalByEinheitAndZeitBetween(any(), any(), any())).thenReturn(100.0);
        when(messwerteRepository.sumZevByEinheitAndZeitBetween(any(), any(), any())).thenReturn(50.0);
        when(messwerteRepository.sumZevCalculatedByEinheitAndZeitBetween(any(), any(), any())).thenReturn(50.0);

        StatistikDTO result = statistikService.getStatistik(von, bis);

        assertNotNull(result);
        assertEquals(3, result.getMonate().size());
        assertEquals(1, result.getMonate().get(0).getMonat());
        assertEquals(2, result.getMonate().get(1).getMonat());
        assertEquals(3, result.getMonate().get(2).getMonat());
    }

    @Test
    void getStatistik_MissingEinheiten_MarksDataIncomplete() {
        LocalDate von = LocalDate.of(2024, 1, 1);
        LocalDate bis = LocalDate.of(2024, 1, 31);

        when(messwerteRepository.findMaxZeit()).thenReturn(Optional.of(LocalDateTime.of(2024, 1, 31, 23, 45)));
        when(einheitRepository.findAll()).thenReturn(Arrays.asList(producer, consumer1, consumer2));
        // Nur producer und consumer1 haben Daten
        when(messwerteRepository.findDistinctEinheitenInRange(any(), any()))
                .thenReturn(Arrays.asList(producer, consumer1));
        when(messwerteRepository.findDistinctDatesInRange(any(), any()))
                .thenReturn(von.datesUntil(bis.plusDays(1)).toList());

        when(messwerteRepository.sumTotalByEinheitTypAndZeitBetween(any(), any(), any())).thenReturn(100.0);
        when(messwerteRepository.sumZevByEinheitTypAndZeitBetween(any(), any(), any())).thenReturn(50.0);
        when(messwerteRepository.sumZevCalculatedByEinheitTypAndZeitBetween(any(), any(), any())).thenReturn(50.0);
        when(messwerteRepository.sumTotalByEinheitAndZeitBetween(any(), any(), any())).thenReturn(100.0);
        when(messwerteRepository.sumZevByEinheitAndZeitBetween(any(), any(), any())).thenReturn(50.0);
        when(messwerteRepository.sumZevCalculatedByEinheitAndZeitBetween(any(), any(), any())).thenReturn(50.0);

        StatistikDTO result = statistikService.getStatistik(von, bis);

        assertFalse(result.isDatenVollstaendig());
        assertTrue(result.getFehlendeEinheiten().contains("Wohnung B"));
    }

    @Test
    void getStatistik_MissingDays_MarksDataIncomplete() {
        LocalDate von = LocalDate.of(2024, 1, 1);
        LocalDate bis = LocalDate.of(2024, 1, 31);

        when(messwerteRepository.findMaxZeit()).thenReturn(Optional.of(LocalDateTime.of(2024, 1, 31, 23, 45)));
        when(einheitRepository.findAll()).thenReturn(Arrays.asList(producer, consumer1));
        when(messwerteRepository.findDistinctEinheitenInRange(any(), any()))
                .thenReturn(Arrays.asList(producer, consumer1));
        // Nur erste 15 Tage haben Daten
        when(messwerteRepository.findDistinctDatesInRange(any(), any()))
                .thenReturn(von.datesUntil(LocalDate.of(2024, 1, 16)).toList());

        when(messwerteRepository.sumTotalByEinheitTypAndZeitBetween(any(), any(), any())).thenReturn(100.0);
        when(messwerteRepository.sumZevByEinheitTypAndZeitBetween(any(), any(), any())).thenReturn(50.0);
        when(messwerteRepository.sumZevCalculatedByEinheitTypAndZeitBetween(any(), any(), any())).thenReturn(50.0);
        when(messwerteRepository.sumTotalByEinheitAndZeitBetween(any(), any(), any())).thenReturn(100.0);
        when(messwerteRepository.sumZevByEinheitAndZeitBetween(any(), any(), any())).thenReturn(50.0);
        when(messwerteRepository.sumZevCalculatedByEinheitAndZeitBetween(any(), any(), any())).thenReturn(50.0);

        StatistikDTO result = statistikService.getStatistik(von, bis);

        assertFalse(result.isDatenVollstaendig());
        assertEquals(16, result.getFehlendeTage().size()); // 16.-31. Januar fehlen
    }

    @Test
    void getStatistik_SummenVergleich_DetectsDiscrepancy() {
        LocalDate von = LocalDate.of(2024, 1, 1);
        LocalDate bis = LocalDate.of(2024, 1, 31);

        when(messwerteRepository.findMaxZeit()).thenReturn(Optional.of(LocalDateTime.of(2024, 1, 31, 23, 45)));
        when(einheitRepository.findAll()).thenReturn(Arrays.asList(producer, consumer1));
        when(messwerteRepository.findDistinctEinheitenInRange(any(), any()))
                .thenReturn(Arrays.asList(producer, consumer1));
        when(messwerteRepository.findDistinctDatesInRange(any(), any()))
                .thenReturn(von.datesUntil(bis.plusDays(1)).toList());

        when(messwerteRepository.sumTotalByEinheitTypAndZeitBetween(any(), any(), any())).thenReturn(100.0);
        // Producer ZEV und Consumer ZEV sind unterschiedlich (Abweichung)
        when(messwerteRepository.sumZevByEinheitTypAndZeitBetween(eq(EinheitTyp.PRODUCER), any(), any()))
                .thenReturn(-100.0);
        when(messwerteRepository.sumZevByEinheitTypAndZeitBetween(eq(EinheitTyp.CONSUMER), any(), any()))
                .thenReturn(95.0); // 5 kWh Differenz
        when(messwerteRepository.sumZevCalculatedByEinheitTypAndZeitBetween(any(), any(), any()))
                .thenReturn(95.0);

        when(messwerteRepository.sumTotalByEinheitAndZeitBetween(any(), any(), any())).thenReturn(100.0);
        when(messwerteRepository.sumZevByEinheitAndZeitBetween(any(), any(), any())).thenReturn(50.0);
        when(messwerteRepository.sumZevCalculatedByEinheitAndZeitBetween(any(), any(), any())).thenReturn(50.0);

        StatistikDTO result = statistikService.getStatistik(von, bis);

        MonatsStatistikDTO monat = result.getMonate().get(0);
        assertFalse(monat.isSummenCDGleich()); // C (Producer ZEV) != D (Consumer ZEV)
        assertEquals(5.0, monat.getDifferenzCD(), 0.001);
    }

    @Test
    void getStatistik_SummenVergleich_WithinTolerance() {
        LocalDate von = LocalDate.of(2024, 1, 1);
        LocalDate bis = LocalDate.of(2024, 1, 31);

        when(messwerteRepository.findMaxZeit()).thenReturn(Optional.of(LocalDateTime.of(2024, 1, 31, 23, 45)));
        when(einheitRepository.findAll()).thenReturn(Arrays.asList(producer, consumer1));
        when(messwerteRepository.findDistinctEinheitenInRange(any(), any()))
                .thenReturn(Arrays.asList(producer, consumer1));
        when(messwerteRepository.findDistinctDatesInRange(any(), any()))
                .thenReturn(von.datesUntil(bis.plusDays(1)).toList());

        when(messwerteRepository.sumTotalByEinheitTypAndZeitBetween(any(), any(), any())).thenReturn(100.0);
        // Differenz ist innerhalb der Toleranz (0.1 kWh)
        when(messwerteRepository.sumZevByEinheitTypAndZeitBetween(eq(EinheitTyp.PRODUCER), any(), any()))
                .thenReturn(-100.0);
        when(messwerteRepository.sumZevByEinheitTypAndZeitBetween(eq(EinheitTyp.CONSUMER), any(), any()))
                .thenReturn(99.95); // 0.05 kWh Differenz - innerhalb Toleranz
        when(messwerteRepository.sumZevCalculatedByEinheitTypAndZeitBetween(any(), any(), any()))
                .thenReturn(99.95);

        when(messwerteRepository.sumTotalByEinheitAndZeitBetween(any(), any(), any())).thenReturn(100.0);
        when(messwerteRepository.sumZevByEinheitAndZeitBetween(any(), any(), any())).thenReturn(50.0);
        when(messwerteRepository.sumZevCalculatedByEinheitAndZeitBetween(any(), any(), any())).thenReturn(50.0);

        StatistikDTO result = statistikService.getStatistik(von, bis);

        MonatsStatistikDTO monat = result.getMonate().get(0);
        assertTrue(monat.isSummenCDGleich()); // Innerhalb Toleranz
    }

    @Test
    void getStatistik_EinheitSummen_CorrectlyCalculated() {
        LocalDate von = LocalDate.of(2024, 1, 1);
        LocalDate bis = LocalDate.of(2024, 1, 31);

        when(messwerteRepository.findMaxZeit()).thenReturn(Optional.of(LocalDateTime.of(2024, 1, 31, 23, 45)));
        when(einheitRepository.findAll()).thenReturn(Arrays.asList(producer, consumer1, consumer2));
        when(messwerteRepository.findDistinctEinheitenInRange(any(), any()))
                .thenReturn(Arrays.asList(producer, consumer1, consumer2));
        when(messwerteRepository.findDistinctDatesInRange(any(), any()))
                .thenReturn(von.datesUntil(bis.plusDays(1)).toList());

        when(messwerteRepository.sumTotalByEinheitTypAndZeitBetween(any(), any(), any())).thenReturn(100.0);
        when(messwerteRepository.sumZevByEinheitTypAndZeitBetween(any(), any(), any())).thenReturn(50.0);
        when(messwerteRepository.sumZevCalculatedByEinheitTypAndZeitBetween(any(), any(), any())).thenReturn(50.0);

        // Producer - negative Werte
        when(messwerteRepository.sumTotalByEinheitAndZeitBetween(eq(producer), any(), any())).thenReturn(-1000.0);
        when(messwerteRepository.sumZevByEinheitAndZeitBetween(eq(producer), any(), any())).thenReturn(-800.0);
        when(messwerteRepository.sumZevCalculatedByEinheitAndZeitBetween(eq(producer), any(), any())).thenReturn(0.0);

        // Consumer 1
        when(messwerteRepository.sumTotalByEinheitAndZeitBetween(eq(consumer1), any(), any())).thenReturn(500.0);
        when(messwerteRepository.sumZevByEinheitAndZeitBetween(eq(consumer1), any(), any())).thenReturn(400.0);
        when(messwerteRepository.sumZevCalculatedByEinheitAndZeitBetween(eq(consumer1), any(), any())).thenReturn(400.0);

        // Consumer 2
        when(messwerteRepository.sumTotalByEinheitAndZeitBetween(eq(consumer2), any(), any())).thenReturn(300.0);
        when(messwerteRepository.sumZevByEinheitAndZeitBetween(eq(consumer2), any(), any())).thenReturn(200.0);
        when(messwerteRepository.sumZevCalculatedByEinheitAndZeitBetween(eq(consumer2), any(), any())).thenReturn(200.0);

        StatistikDTO result = statistikService.getStatistik(von, bis);

        MonatsStatistikDTO monat = result.getMonate().get(0);
        List<EinheitSummenDTO> einheitSummen = monat.getEinheitSummen();

        assertNotNull(einheitSummen);
        assertEquals(3, einheitSummen.size());

        // Producer sollte zuerst kommen (sortiert nach Typ, dann Name)
        EinheitSummenDTO producerSummen = einheitSummen.get(0);
        assertEquals("Solaranlage", producerSummen.getEinheitName());
        assertEquals(EinheitTyp.PRODUCER, producerSummen.getEinheitTyp());
        assertEquals(1000.0, producerSummen.getSummeTotal()); // Absolute value
        assertEquals(800.0, producerSummen.getSummeZev()); // Absolute value

        // Consumer 1 (alphabetisch: Wohnung A vor Wohnung B)
        EinheitSummenDTO consumer1Summen = einheitSummen.get(1);
        assertEquals("Wohnung A", consumer1Summen.getEinheitName());
        assertEquals(EinheitTyp.CONSUMER, consumer1Summen.getEinheitTyp());
        assertEquals(500.0, consumer1Summen.getSummeTotal());
        assertEquals(400.0, consumer1Summen.getSummeZev());
        assertEquals(400.0, consumer1Summen.getSummeZevCalculated());

        // Consumer 2
        EinheitSummenDTO consumer2Summen = einheitSummen.get(2);
        assertEquals("Wohnung B", consumer2Summen.getEinheitName());
        assertEquals(300.0, consumer2Summen.getSummeTotal());
    }

    @Test
    void getStatistik_ProducerNegativeValues_ConvertedToAbsolute() {
        LocalDate von = LocalDate.of(2024, 1, 1);
        LocalDate bis = LocalDate.of(2024, 1, 31);

        when(messwerteRepository.findMaxZeit()).thenReturn(Optional.of(LocalDateTime.of(2024, 1, 31, 23, 45)));
        when(einheitRepository.findAll()).thenReturn(Arrays.asList(producer));
        when(messwerteRepository.findDistinctEinheitenInRange(any(), any())).thenReturn(Arrays.asList(producer));
        when(messwerteRepository.findDistinctDatesInRange(any(), any()))
                .thenReturn(von.datesUntil(bis.plusDays(1)).toList());

        // Producer Werte sind in der DB negativ
        when(messwerteRepository.sumTotalByEinheitTypAndZeitBetween(eq(EinheitTyp.PRODUCER), any(), any()))
                .thenReturn(-1500.0);
        when(messwerteRepository.sumZevByEinheitTypAndZeitBetween(eq(EinheitTyp.PRODUCER), any(), any()))
                .thenReturn(-1200.0);
        when(messwerteRepository.sumTotalByEinheitTypAndZeitBetween(eq(EinheitTyp.CONSUMER), any(), any()))
                .thenReturn(0.0);
        when(messwerteRepository.sumZevByEinheitTypAndZeitBetween(eq(EinheitTyp.CONSUMER), any(), any()))
                .thenReturn(0.0);
        when(messwerteRepository.sumZevCalculatedByEinheitTypAndZeitBetween(any(), any(), any())).thenReturn(0.0);

        when(messwerteRepository.sumTotalByEinheitAndZeitBetween(eq(producer), any(), any())).thenReturn(-1500.0);
        when(messwerteRepository.sumZevByEinheitAndZeitBetween(eq(producer), any(), any())).thenReturn(-1200.0);
        when(messwerteRepository.sumZevCalculatedByEinheitAndZeitBetween(eq(producer), any(), any())).thenReturn(0.0);

        StatistikDTO result = statistikService.getStatistik(von, bis);

        MonatsStatistikDTO monat = result.getMonate().get(0);
        // Alle Producer-Werte sollten als positive Absolutwerte angezeigt werden
        assertEquals(1500.0, monat.getSummeProducerTotal());
        assertEquals(1200.0, monat.getSummeProducerZev());

        EinheitSummenDTO producerSummen = monat.getEinheitSummen().get(0);
        assertEquals(1500.0, producerSummen.getSummeTotal());
        assertEquals(1200.0, producerSummen.getSummeZev());
    }

    @Test
    void getStatistik_NullValues_HandledGracefully() {
        LocalDate von = LocalDate.of(2024, 1, 1);
        LocalDate bis = LocalDate.of(2024, 1, 31);

        when(messwerteRepository.findMaxZeit()).thenReturn(Optional.empty());
        when(einheitRepository.findAll()).thenReturn(Arrays.asList(producer));
        when(messwerteRepository.findDistinctEinheitenInRange(any(), any())).thenReturn(Collections.emptyList());
        when(messwerteRepository.findDistinctDatesInRange(any(), any())).thenReturn(Collections.emptyList());

        // Alle Summen sind null (keine Daten)
        when(messwerteRepository.sumTotalByEinheitTypAndZeitBetween(any(), any(), any())).thenReturn(null);
        when(messwerteRepository.sumZevByEinheitTypAndZeitBetween(any(), any(), any())).thenReturn(null);
        when(messwerteRepository.sumZevCalculatedByEinheitTypAndZeitBetween(any(), any(), any())).thenReturn(null);
        when(messwerteRepository.sumTotalByEinheitAndZeitBetween(any(), any(), any())).thenReturn(null);
        when(messwerteRepository.sumZevByEinheitAndZeitBetween(any(), any(), any())).thenReturn(null);
        when(messwerteRepository.sumZevCalculatedByEinheitAndZeitBetween(any(), any(), any())).thenReturn(null);

        StatistikDTO result = statistikService.getStatistik(von, bis);

        assertNotNull(result);
        assertNull(result.getMesswerteBisDate());
        MonatsStatistikDTO monat = result.getMonate().get(0);
        assertEquals(0.0, monat.getSummeProducerTotal());
        assertEquals(0.0, monat.getSummeConsumerTotal());
    }

    @Test
    void ermittleLetztesMessdatum_WithData_ReturnsDate() {
        LocalDateTime expectedDate = LocalDateTime.of(2024, 6, 15, 14, 30);
        when(messwerteRepository.findMaxZeit()).thenReturn(Optional.of(expectedDate));

        LocalDate result = statistikService.ermittleLetztesMessdatum();

        assertEquals(LocalDate.of(2024, 6, 15), result);
    }

    @Test
    void ermittleLetztesMessdatum_NoData_ReturnsNull() {
        when(messwerteRepository.findMaxZeit()).thenReturn(Optional.empty());

        LocalDate result = statistikService.ermittleLetztesMessdatum();

        assertNull(result);
    }

    @Test
    void getStatistik_PartialMonth_CalculatesCorrectDateRange() {
        // Test mit Zeitraum der nicht am Monatsanfang beginnt
        LocalDate von = LocalDate.of(2024, 1, 15);
        LocalDate bis = LocalDate.of(2024, 1, 31);

        when(messwerteRepository.findMaxZeit()).thenReturn(Optional.of(LocalDateTime.of(2024, 1, 31, 23, 45)));
        when(einheitRepository.findAll()).thenReturn(Arrays.asList(producer));
        when(messwerteRepository.findDistinctEinheitenInRange(any(), any())).thenReturn(Arrays.asList(producer));
        when(messwerteRepository.findDistinctDatesInRange(any(), any()))
                .thenReturn(von.datesUntil(bis.plusDays(1)).toList());

        when(messwerteRepository.sumTotalByEinheitTypAndZeitBetween(any(), any(), any())).thenReturn(100.0);
        when(messwerteRepository.sumZevByEinheitTypAndZeitBetween(any(), any(), any())).thenReturn(50.0);
        when(messwerteRepository.sumZevCalculatedByEinheitTypAndZeitBetween(any(), any(), any())).thenReturn(50.0);
        when(messwerteRepository.sumTotalByEinheitAndZeitBetween(any(), any(), any())).thenReturn(100.0);
        when(messwerteRepository.sumZevByEinheitAndZeitBetween(any(), any(), any())).thenReturn(50.0);
        when(messwerteRepository.sumZevCalculatedByEinheitAndZeitBetween(any(), any(), any())).thenReturn(50.0);

        StatistikDTO result = statistikService.getStatistik(von, bis);

        assertEquals(1, result.getMonate().size());
        MonatsStatistikDTO monat = result.getMonate().get(0);
        assertEquals(LocalDate.of(2024, 1, 15), monat.getVon());
        assertEquals(LocalDate.of(2024, 1, 31), monat.getBis());
    }
}
