package ch.nacht.service;

import ch.nacht.config.RechnungConfig;
import ch.nacht.dto.RechnungDTO;
import ch.nacht.dto.TarifZeileDTO;
import ch.nacht.entity.Einheit;
import ch.nacht.entity.EinheitTyp;
import ch.nacht.entity.Tarif;
import ch.nacht.entity.TarifTyp;
import ch.nacht.repository.EinheitRepository;
import ch.nacht.repository.MesswerteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RechnungServiceTest {

    @Mock
    private EinheitRepository einheitRepository;

    @Mock
    private MesswerteRepository messwerteRepository;

    @Mock
    private RechnungConfig rechnungConfig;

    @Mock
    private TarifService tarifService;

    @InjectMocks
    private RechnungService rechnungService;

    private Einheit consumer;
    private Tarif zevTarif2024;
    private Tarif vnbTarif2024;

    @BeforeEach
    void setUp() {
        consumer = new Einheit("Wohnung A", EinheitTyp.CONSUMER);
        consumer.setId(1L);
        consumer.setMietername("Max Muster");
        consumer.setMesspunkt("CH123456789");

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
            new BigDecimal("0.34000"),
            LocalDate.of(2024, 1, 1),
            LocalDate.of(2024, 12, 31)
        );
        vnbTarif2024.setId(2L);

        // Setup RechnungConfig mock (all lenient as not all tests use them)
        RechnungConfig.Steller steller = mock(RechnungConfig.Steller.class);
        lenient().when(steller.getName()).thenReturn("Test AG");
        lenient().when(steller.getStrasse()).thenReturn("Teststrasse 1");
        lenient().when(steller.getPlz()).thenReturn("3000");
        lenient().when(steller.getOrt()).thenReturn("Bern");

        RechnungConfig.Adresse adresse = mock(RechnungConfig.Adresse.class);
        lenient().when(adresse.getStrasse()).thenReturn("Musterweg 5");
        lenient().when(adresse.getPlz()).thenReturn("3001");
        lenient().when(adresse.getOrt()).thenReturn("Bern");

        lenient().when(rechnungConfig.getZahlungsfrist()).thenReturn("30 Tage");
        lenient().when(rechnungConfig.getIban()).thenReturn("CH12 3456 7890 1234");
        lenient().when(rechnungConfig.getSteller()).thenReturn(steller);
        lenient().when(rechnungConfig.getAdresse()).thenReturn(adresse);
    }

    @Test
    void berechneRechnung_SingleTarifPerType_CalculatesCorrectly() {
        LocalDate von = LocalDate.of(2024, 1, 1);
        LocalDate bis = LocalDate.of(2024, 1, 31);

        when(tarifService.getTarifeForZeitraum(TarifTyp.ZEV, von, bis))
            .thenReturn(Collections.singletonList(zevTarif2024));
        when(tarifService.getTarifeForZeitraum(TarifTyp.VNB, von, bis))
            .thenReturn(Collections.singletonList(vnbTarif2024));

        // Mock measurements: 100 kWh ZEV, 150 kWh total -> 50 kWh VNB
        when(messwerteRepository.sumZevCalculatedByEinheitAndZeitBetween(
            eq(consumer), any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(100.0);
        when(messwerteRepository.sumTotalByEinheitAndZeitBetween(
            eq(consumer), any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(150.0);

        RechnungDTO rechnung = rechnungService.berechneRechnung(consumer, von, bis);

        assertNotNull(rechnung);
        assertEquals("Wohnung A", rechnung.getEinheitName());
        assertEquals("Max Muster", rechnung.getMietername());

        List<TarifZeileDTO> zeilen = rechnung.getTarifZeilen();
        assertEquals(2, zeilen.size());

        // ZEV line: 100 kWh * 0.20 = 20.00
        TarifZeileDTO zevZeile = zeilen.stream()
            .filter(z -> z.getTyp() == TarifTyp.ZEV)
            .findFirst().orElseThrow();
        assertEquals(100.0, zevZeile.getMenge());
        assertEquals(0.20000, zevZeile.getPreis(), 0.00001);
        assertEquals(20.0, zevZeile.getBetrag(), 0.01);

        // VNB line: 50 kWh * 0.34 = 17.00
        TarifZeileDTO vnbZeile = zeilen.stream()
            .filter(z -> z.getTyp() == TarifTyp.VNB)
            .findFirst().orElseThrow();
        assertEquals(50.0, vnbZeile.getMenge());
        assertEquals(0.34000, vnbZeile.getPreis(), 0.00001);
        assertEquals(17.0, vnbZeile.getBetrag(), 0.01);

        // Total: 20 + 17 = 37.00
        assertEquals(37.0, rechnung.getTotalBetrag(), 0.01);
        assertEquals(37.0, rechnung.getEndBetrag(), 0.01);
    }

    @Test
    void berechneRechnung_MultipleTarifsPerType_CreatesMultipleLines() {
        LocalDate von = LocalDate.of(2024, 1, 1);
        LocalDate bis = LocalDate.of(2024, 6, 30);

        // Two ZEV tariffs: H1 and H2
        Tarif zevH1 = new Tarif(
            "ZEV H1", TarifTyp.ZEV, new BigDecimal("0.19"),
            LocalDate.of(2024, 1, 1), LocalDate.of(2024, 3, 31)
        );
        Tarif zevH2 = new Tarif(
            "ZEV H2", TarifTyp.ZEV, new BigDecimal("0.21"),
            LocalDate.of(2024, 4, 1), LocalDate.of(2024, 6, 30)
        );

        when(tarifService.getTarifeForZeitraum(TarifTyp.ZEV, von, bis))
            .thenReturn(Arrays.asList(zevH1, zevH2));
        when(tarifService.getTarifeForZeitraum(TarifTyp.VNB, von, bis))
            .thenReturn(Collections.singletonList(vnbTarif2024));

        // H1: 50 kWh ZEV
        when(messwerteRepository.sumZevCalculatedByEinheitAndZeitBetween(
            eq(consumer),
            eq(LocalDate.of(2024, 1, 1).atStartOfDay()),
            eq(LocalDate.of(2024, 4, 1).atStartOfDay())))
            .thenReturn(50.0);

        // H2: 60 kWh ZEV
        when(messwerteRepository.sumZevCalculatedByEinheitAndZeitBetween(
            eq(consumer),
            eq(LocalDate.of(2024, 4, 1).atStartOfDay()),
            eq(LocalDate.of(2024, 7, 1).atStartOfDay())))
            .thenReturn(60.0);

        // VNB full period: 200 kWh total
        when(messwerteRepository.sumTotalByEinheitAndZeitBetween(
            eq(consumer), any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(200.0);
        when(messwerteRepository.sumZevCalculatedByEinheitAndZeitBetween(
            eq(consumer),
            eq(LocalDate.of(2024, 1, 1).atStartOfDay()),
            eq(LocalDate.of(2024, 7, 1).atStartOfDay())))
            .thenReturn(110.0);

        RechnungDTO rechnung = rechnungService.berechneRechnung(consumer, von, bis);

        // Should have 3 lines: 2 ZEV + 1 VNB
        List<TarifZeileDTO> zevZeilen = rechnung.getTarifZeilen().stream()
            .filter(z -> z.getTyp() == TarifTyp.ZEV)
            .toList();
        assertEquals(2, zevZeilen.size());
    }

    @Test
    void berechneRechnung_RoundsTo5Rappen() {
        LocalDate von = LocalDate.of(2024, 1, 1);
        LocalDate bis = LocalDate.of(2024, 1, 31);

        when(tarifService.getTarifeForZeitraum(TarifTyp.ZEV, von, bis))
            .thenReturn(Collections.singletonList(zevTarif2024));
        when(tarifService.getTarifeForZeitraum(TarifTyp.VNB, von, bis))
            .thenReturn(Collections.singletonList(vnbTarif2024));

        // 123 kWh * 0.20 = 24.60, 77 kWh * 0.34 = 26.18 => Total 50.78 -> rounds to 50.80
        when(messwerteRepository.sumZevCalculatedByEinheitAndZeitBetween(
            eq(consumer), any(), any()))
            .thenReturn(123.0);
        when(messwerteRepository.sumTotalByEinheitAndZeitBetween(
            eq(consumer), any(), any()))
            .thenReturn(200.0);

        RechnungDTO rechnung = rechnungService.berechneRechnung(consumer, von, bis);

        // 123 * 0.20 = 24.60, 77 * 0.34 = 26.18, Total = 50.78, rounded to 50.80
        assertEquals(50.80, rechnung.getEndBetrag(), 0.001);
        assertEquals(0.02, rechnung.getRundung(), 0.001);
    }

    @Test
    void berechneRechnung_NoMeasurements_ReturnsZeroAmounts() {
        LocalDate von = LocalDate.of(2024, 1, 1);
        LocalDate bis = LocalDate.of(2024, 1, 31);

        when(tarifService.getTarifeForZeitraum(TarifTyp.ZEV, von, bis))
            .thenReturn(Collections.singletonList(zevTarif2024));
        when(tarifService.getTarifeForZeitraum(TarifTyp.VNB, von, bis))
            .thenReturn(Collections.singletonList(vnbTarif2024));

        when(messwerteRepository.sumZevCalculatedByEinheitAndZeitBetween(
            eq(consumer), any(), any()))
            .thenReturn(null);
        when(messwerteRepository.sumTotalByEinheitAndZeitBetween(
            eq(consumer), any(), any()))
            .thenReturn(null);

        RechnungDTO rechnung = rechnungService.berechneRechnung(consumer, von, bis);

        assertEquals(0.0, rechnung.getTotalBetrag());
        assertEquals(0.0, rechnung.getEndBetrag());
    }

    @Test
    void berechneRechnungen_ValidatesTarifAbdeckung() {
        LocalDate von = LocalDate.of(2024, 1, 1);
        LocalDate bis = LocalDate.of(2024, 1, 31);

        doThrow(new IllegalStateException("ZEV-Tarif fehlt"))
            .when(tarifService).validateTarifAbdeckung(von, bis);

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> rechnungService.berechneRechnungen(List.of(1L), von, bis)
        );

        assertTrue(exception.getMessage().contains("ZEV-Tarif"));
        verify(tarifService).validateTarifAbdeckung(von, bis);
    }

    @Test
    void berechneRechnungen_SkipsNonConsumers() {
        LocalDate von = LocalDate.of(2024, 1, 1);
        LocalDate bis = LocalDate.of(2024, 1, 31);

        Einheit producer = new Einheit("Solaranlage", EinheitTyp.PRODUCER);
        producer.setId(2L);

        doNothing().when(tarifService).validateTarifAbdeckung(von, bis);
        when(einheitRepository.findById(1L)).thenReturn(Optional.of(consumer));
        when(einheitRepository.findById(2L)).thenReturn(Optional.of(producer));

        when(tarifService.getTarifeForZeitraum(any(), any(), any()))
            .thenReturn(Collections.singletonList(zevTarif2024));
        when(messwerteRepository.sumZevCalculatedByEinheitAndZeitBetween(any(), any(), any()))
            .thenReturn(100.0);
        when(messwerteRepository.sumTotalByEinheitAndZeitBetween(any(), any(), any()))
            .thenReturn(150.0);

        List<RechnungDTO> rechnungen = rechnungService.berechneRechnungen(
            List.of(1L, 2L), von, bis);

        // Only consumer should have invoice
        assertEquals(1, rechnungen.size());
        assertEquals("Wohnung A", rechnungen.get(0).getEinheitName());
    }

    @Test
    void berechneRechnung_TarifZeileDatesMatchTarifPeriod() {
        LocalDate von = LocalDate.of(2024, 1, 1);
        LocalDate bis = LocalDate.of(2024, 3, 31);

        // Tariff only covers February
        Tarif zevFeb = new Tarif(
            "ZEV Feb", TarifTyp.ZEV, new BigDecimal("0.20"),
            LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 29)
        );

        when(tarifService.getTarifeForZeitraum(TarifTyp.ZEV, von, bis))
            .thenReturn(Collections.singletonList(zevFeb));
        when(tarifService.getTarifeForZeitraum(TarifTyp.VNB, von, bis))
            .thenReturn(Collections.singletonList(vnbTarif2024));

        when(messwerteRepository.sumZevCalculatedByEinheitAndZeitBetween(
            eq(consumer),
            eq(LocalDate.of(2024, 2, 1).atStartOfDay()),
            eq(LocalDate.of(2024, 3, 1).atStartOfDay())))
            .thenReturn(50.0);
        when(messwerteRepository.sumTotalByEinheitAndZeitBetween(any(), any(), any()))
            .thenReturn(100.0);
        when(messwerteRepository.sumZevCalculatedByEinheitAndZeitBetween(
            eq(consumer),
            eq(LocalDate.of(2024, 1, 1).atStartOfDay()),
            eq(LocalDate.of(2024, 4, 1).atStartOfDay())))
            .thenReturn(50.0);

        RechnungDTO rechnung = rechnungService.berechneRechnung(consumer, von, bis);

        TarifZeileDTO zevZeile = rechnung.getTarifZeilen().stream()
            .filter(z -> z.getTyp() == TarifTyp.ZEV)
            .findFirst().orElseThrow();

        // Should use effective dates (intersection of invoice and tariff period)
        assertEquals(LocalDate.of(2024, 2, 1), zevZeile.getVon());
        assertEquals(LocalDate.of(2024, 2, 29), zevZeile.getBis());
    }

    @Test
    void roundTo5Rappen_RoundsCorrectly() {
        assertEquals(10.00, RechnungService.roundTo5Rappen(10.00));
        assertEquals(10.00, RechnungService.roundTo5Rappen(10.02));
        assertEquals(10.05, RechnungService.roundTo5Rappen(10.03));
        assertEquals(10.05, RechnungService.roundTo5Rappen(10.05));
        assertEquals(10.05, RechnungService.roundTo5Rappen(10.07));
        assertEquals(10.10, RechnungService.roundTo5Rappen(10.08));
        assertEquals(10.10, RechnungService.roundTo5Rappen(10.10));
    }
}
