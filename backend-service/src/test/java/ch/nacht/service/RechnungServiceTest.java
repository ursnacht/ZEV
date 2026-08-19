package ch.nacht.service;

import ch.nacht.dto.EinstellungenDTO;
import ch.nacht.dto.RechnungDTO;
import ch.nacht.dto.RechnungKonfigurationDTO;
import ch.nacht.dto.TarifZeileDTO;
import ch.nacht.entity.Einheit;
import ch.nacht.entity.EinheitTyp;
import ch.nacht.entity.Mieter;
import ch.nacht.entity.Tarif;
import ch.nacht.entity.TarifTyp;
import ch.nacht.entity.Tarifposition;
import ch.nacht.exception.TarifLuecke;
import ch.nacht.exception.TarifLueckenException;
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
    private EinstellungenService einstellungenService;

    @Mock
    private TarifService tarifService;

    @Mock
    private MieterService mieterService;

    @Mock
    private TarifpositionService tarifpositionService;

    @Mock
    private HibernateFilterService hibernateFilterService;

    @InjectMocks
    private RechnungService rechnungService;

    private Einheit consumer;
    private Tarif zevTarif2024;
    private Tarif vnbTarif2024;

    @BeforeEach
    void setUp() {
        consumer = new Einheit("Wohnung A", EinheitTyp.CONSUMER);
        consumer.setId(1L);
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

        // Setup EinstellungenService mock (lenient as not all tests use them)
        RechnungKonfigurationDTO.StellerDTO steller = new RechnungKonfigurationDTO.StellerDTO(
            "Test AG", "Teststrasse 1", "3000", "Bern"
        );
        RechnungKonfigurationDTO rechnungKonfig = new RechnungKonfigurationDTO(
            "30 Tage", "CH12 3456 7890 1234", steller
        );
        EinstellungenDTO einstellungen = new EinstellungenDTO(1L, rechnungKonfig);
        lenient().when(einstellungenService.getEinstellungenOrThrow()).thenReturn(einstellungen);
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

        RechnungDTO rechnung = rechnungService.berechneRechnung(consumer, null, von, bis);

        assertNotNull(rechnung);
        assertEquals("Wohnung A", rechnung.getEinheitName());
        // No tenant provided
        assertNull(rechnung.getMieterName());

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
    void berechneRechnung_WithTenant_IncludesTenantData() {
        LocalDate von = LocalDate.of(2024, 1, 1);
        LocalDate bis = LocalDate.of(2024, 1, 31);

        Mieter mieter = new Mieter("Max Muster", LocalDate.of(2023, 1, 1), 1L);
        mieter.setId(1L);
        mieter.setStrasse("Musterweg 5");
        mieter.setPlz("3000");
        mieter.setOrt("Bern");

        when(tarifService.getTarifeForZeitraum(TarifTyp.ZEV, von, bis))
            .thenReturn(Collections.singletonList(zevTarif2024));
        when(tarifService.getTarifeForZeitraum(TarifTyp.VNB, von, bis))
            .thenReturn(Collections.singletonList(vnbTarif2024));

        when(messwerteRepository.sumZevCalculatedByEinheitAndZeitBetween(
            eq(consumer), any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(100.0);
        when(messwerteRepository.sumTotalByEinheitAndZeitBetween(
            eq(consumer), any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(150.0);

        RechnungDTO rechnung = rechnungService.berechneRechnung(consumer, mieter, von, bis);

        assertNotNull(rechnung);
        assertEquals("Max Muster", rechnung.getMieterName());
        assertEquals("Musterweg 5", rechnung.getMieterStrasse());
        assertEquals("3000 Bern", rechnung.getMieterPlzOrt());
        assertEquals(1L, rechnung.getMieterId());
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

        RechnungDTO rechnung = rechnungService.berechneRechnung(consumer, null, von, bis);

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

        RechnungDTO rechnung = rechnungService.berechneRechnung(consumer, null, von, bis);

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

        RechnungDTO rechnung = rechnungService.berechneRechnung(consumer, null, von, bis);

        assertEquals(0.0, rechnung.getTotalBetrag());
        assertEquals(0.0, rechnung.getEndBetrag());
    }

    @Test
    void berechneRechnungen_ValidatesTarifAbdeckung() {
        LocalDate von = LocalDate.of(2024, 1, 1);
        LocalDate bis = LocalDate.of(2024, 1, 31);

        when(einheitRepository.findById(1L)).thenReturn(Optional.of(consumer));
        doThrow(new TarifLueckenException(List.of(new TarifLuecke("ZEV", "01.01.2024", false))))
            .when(tarifService).validateTarifAbdeckung(von, bis);

        TarifLueckenException exception = assertThrows(
            TarifLueckenException.class,
            () -> rechnungService.berechneRechnungen(List.of(1L), von, bis)
        );

        assertTrue(exception.getLuecken().stream().anyMatch(l -> l.tarifTyp().equals("ZEV")));
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
        when(mieterService.getMieterForQuartal(eq(1L), any(), any())).thenReturn(Collections.emptyList());

        when(tarifService.getTarifeForZeitraum(eq(TarifTyp.ZEV), any(), any()))
            .thenReturn(Collections.singletonList(zevTarif2024));
        when(tarifService.getTarifeForZeitraum(eq(TarifTyp.VNB), any(), any()))
            .thenReturn(Collections.singletonList(vnbTarif2024));
        when(tarifService.getTarifeForZeitraum(eq(TarifTyp.GRUNDGEBUEHR), any(), any()))
            .thenReturn(Collections.emptyList());
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
    void berechneRechnungen_CreatesSeparateInvoicesPerTenant() {
        LocalDate von = LocalDate.of(2024, 1, 1);
        LocalDate bis = LocalDate.of(2024, 3, 31);

        Mieter mieter1 = new Mieter("Mieter A", LocalDate.of(2023, 1, 1), 1L);
        mieter1.setId(1L);
        mieter1.setMietende(LocalDate.of(2024, 2, 15));

        Mieter mieter2 = new Mieter("Mieter B", LocalDate.of(2024, 2, 16), 1L);
        mieter2.setId(2L);

        doNothing().when(tarifService).validateTarifAbdeckung(von, bis);
        when(einheitRepository.findById(1L)).thenReturn(Optional.of(consumer));
        when(mieterService.getMieterForQuartal(eq(1L), any(), any()))
            .thenReturn(Arrays.asList(mieter1, mieter2));

        when(tarifService.getTarifeForZeitraum(any(), any(), any()))
            .thenReturn(Collections.singletonList(zevTarif2024));
        when(messwerteRepository.sumZevCalculatedByEinheitAndZeitBetween(any(), any(), any()))
            .thenReturn(100.0);
        when(messwerteRepository.sumTotalByEinheitAndZeitBetween(any(), any(), any()))
            .thenReturn(150.0);

        List<RechnungDTO> rechnungen = rechnungService.berechneRechnungen(List.of(1L), von, bis);

        // Should create 2 invoices - one per tenant
        assertEquals(2, rechnungen.size());
        assertEquals("Mieter A", rechnungen.get(0).getMieterName());
        assertEquals("Mieter B", rechnungen.get(1).getMieterName());

        // First invoice: from period start to tenant end
        assertEquals(von, rechnungen.get(0).getVon());
        assertEquals(LocalDate.of(2024, 2, 15), rechnungen.get(0).getBis());

        // Second invoice: from tenant start to period end
        assertEquals(LocalDate.of(2024, 2, 16), rechnungen.get(1).getVon());
        assertEquals(bis, rechnungen.get(1).getBis());
    }

    @Test
    void berechneRechnungen_NoTenants_CreatesInvoiceWithoutTenantData() {
        LocalDate von = LocalDate.of(2024, 1, 1);
        LocalDate bis = LocalDate.of(2024, 1, 31);

        doNothing().when(tarifService).validateTarifAbdeckung(von, bis);
        when(einheitRepository.findById(1L)).thenReturn(Optional.of(consumer));
        when(mieterService.getMieterForQuartal(eq(1L), any(), any())).thenReturn(Collections.emptyList());

        when(tarifService.getTarifeForZeitraum(any(), any(), any()))
            .thenReturn(Collections.singletonList(zevTarif2024));
        when(messwerteRepository.sumZevCalculatedByEinheitAndZeitBetween(any(), any(), any()))
            .thenReturn(100.0);
        when(messwerteRepository.sumTotalByEinheitAndZeitBetween(any(), any(), any()))
            .thenReturn(150.0);

        List<RechnungDTO> rechnungen = rechnungService.berechneRechnungen(List.of(1L), von, bis);

        assertEquals(1, rechnungen.size());
        assertNull(rechnungen.get(0).getMieterName());
        assertEquals(von, rechnungen.get(0).getVon());
        assertEquals(bis, rechnungen.get(0).getBis());
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

        RechnungDTO rechnung = rechnungService.berechneRechnung(consumer, null, von, bis);

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

    // ─── GRUNDGEBUEHR: zaehleVolleMonate (indirekt via berechneRechnung) ─────────

    @Test
    void berechneRechnung_GrundgebuehrFullQuarter_ThreeMonths() {
        LocalDate von = LocalDate.of(2024, 1, 1);
        LocalDate bis = LocalDate.of(2024, 3, 31);

        Tarif grundgebuehr = new Tarif(
            "Grundgebühr 2024", TarifTyp.GRUNDGEBUEHR, new BigDecimal("5.00000"),
            LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)
        );

        when(tarifService.getTarifeForZeitraum(TarifTyp.ZEV, von, bis))
            .thenReturn(Collections.singletonList(zevTarif2024));
        when(tarifService.getTarifeForZeitraum(TarifTyp.VNB, von, bis))
            .thenReturn(Collections.singletonList(vnbTarif2024));
        when(tarifService.getTarifeForZeitraum(TarifTyp.GRUNDGEBUEHR, von, bis))
            .thenReturn(Collections.singletonList(grundgebuehr));
        when(messwerteRepository.sumZevCalculatedByEinheitAndZeitBetween(eq(consumer), any(), any()))
            .thenReturn(100.0);
        when(messwerteRepository.sumTotalByEinheitAndZeitBetween(eq(consumer), any(), any()))
            .thenReturn(150.0);

        RechnungDTO rechnung = rechnungService.berechneRechnung(consumer, null, von, bis);

        TarifZeileDTO grundgebuehrZeile = rechnung.getTarifZeilen().stream()
            .filter(z -> z.getTyp() == TarifTyp.GRUNDGEBUEHR)
            .findFirst().orElseThrow();

        assertEquals(3.0, grundgebuehrZeile.getMenge(), 0.001); // 3 volle Monate: Jan, Feb, Mär
        assertEquals(5.0, grundgebuehrZeile.getPreis(), 0.00001);
        assertEquals(15.0, grundgebuehrZeile.getBetrag(), 0.01);
    }

    @Test
    void berechneRechnung_GrundgebuehrPartialStartMonth_ExcludesIncompleteMonth() {
        // Rechnungsperiode beginnt am 15. Januar → Januar wird nicht gezählt
        LocalDate von = LocalDate.of(2024, 1, 15);
        LocalDate bis = LocalDate.of(2024, 3, 31);

        Tarif grundgebuehr = new Tarif(
            "Grundgebühr 2024", TarifTyp.GRUNDGEBUEHR, new BigDecimal("5.00000"),
            LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)
        );

        when(tarifService.getTarifeForZeitraum(TarifTyp.ZEV, von, bis))
            .thenReturn(Collections.singletonList(zevTarif2024));
        when(tarifService.getTarifeForZeitraum(TarifTyp.VNB, von, bis))
            .thenReturn(Collections.singletonList(vnbTarif2024));
        when(tarifService.getTarifeForZeitraum(TarifTyp.GRUNDGEBUEHR, von, bis))
            .thenReturn(Collections.singletonList(grundgebuehr));
        when(messwerteRepository.sumZevCalculatedByEinheitAndZeitBetween(eq(consumer), any(), any()))
            .thenReturn(100.0);
        when(messwerteRepository.sumTotalByEinheitAndZeitBetween(eq(consumer), any(), any()))
            .thenReturn(150.0);

        RechnungDTO rechnung = rechnungService.berechneRechnung(consumer, null, von, bis);

        TarifZeileDTO grundgebuehrZeile = rechnung.getTarifZeilen().stream()
            .filter(z -> z.getTyp() == TarifTyp.GRUNDGEBUEHR)
            .findFirst().orElseThrow();

        assertEquals(2.0, grundgebuehrZeile.getMenge(), 0.001); // Nur Feb + Mär
        assertEquals(10.0, grundgebuehrZeile.getBetrag(), 0.01);
    }

    @Test
    void berechneRechnung_GrundgebuehrPartialEndMonth_ExcludesIncompleteMonth() {
        // Rechnungsperiode endet am 15. März → März wird nicht gezählt
        LocalDate von = LocalDate.of(2024, 1, 1);
        LocalDate bis = LocalDate.of(2024, 3, 15);

        Tarif grundgebuehr = new Tarif(
            "Grundgebühr 2024", TarifTyp.GRUNDGEBUEHR, new BigDecimal("5.00000"),
            LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)
        );

        when(tarifService.getTarifeForZeitraum(TarifTyp.ZEV, von, bis))
            .thenReturn(Collections.singletonList(zevTarif2024));
        when(tarifService.getTarifeForZeitraum(TarifTyp.VNB, von, bis))
            .thenReturn(Collections.singletonList(vnbTarif2024));
        when(tarifService.getTarifeForZeitraum(TarifTyp.GRUNDGEBUEHR, von, bis))
            .thenReturn(Collections.singletonList(grundgebuehr));
        when(messwerteRepository.sumZevCalculatedByEinheitAndZeitBetween(eq(consumer), any(), any()))
            .thenReturn(100.0);
        when(messwerteRepository.sumTotalByEinheitAndZeitBetween(eq(consumer), any(), any()))
            .thenReturn(150.0);

        RechnungDTO rechnung = rechnungService.berechneRechnung(consumer, null, von, bis);

        TarifZeileDTO grundgebuehrZeile = rechnung.getTarifZeilen().stream()
            .filter(z -> z.getTyp() == TarifTyp.GRUNDGEBUEHR)
            .findFirst().orElseThrow();

        assertEquals(2.0, grundgebuehrZeile.getMenge(), 0.001); // Nur Jan + Feb
    }

    @Test
    void berechneRechnung_GrundgebuehrLessThanOneMonth_NoLineAdded() {
        // Rechnungsperiode weniger als 1 Monat → 0 volle Monate → keine Zeile
        LocalDate von = LocalDate.of(2024, 1, 15);
        LocalDate bis = LocalDate.of(2024, 1, 28);

        Tarif grundgebuehr = new Tarif(
            "Grundgebühr 2024", TarifTyp.GRUNDGEBUEHR, new BigDecimal("5.00000"),
            LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)
        );

        when(tarifService.getTarifeForZeitraum(TarifTyp.ZEV, von, bis))
            .thenReturn(Collections.singletonList(zevTarif2024));
        when(tarifService.getTarifeForZeitraum(TarifTyp.VNB, von, bis))
            .thenReturn(Collections.singletonList(vnbTarif2024));
        when(tarifService.getTarifeForZeitraum(TarifTyp.GRUNDGEBUEHR, von, bis))
            .thenReturn(Collections.singletonList(grundgebuehr));
        when(messwerteRepository.sumZevCalculatedByEinheitAndZeitBetween(eq(consumer), any(), any()))
            .thenReturn(100.0);
        when(messwerteRepository.sumTotalByEinheitAndZeitBetween(eq(consumer), any(), any()))
            .thenReturn(150.0);

        RechnungDTO rechnung = rechnungService.berechneRechnung(consumer, null, von, bis);

        long grundgebuehrZeilen = rechnung.getTarifZeilen().stream()
            .filter(z -> z.getTyp() == TarifTyp.GRUNDGEBUEHR)
            .count();

        assertEquals(0, grundgebuehrZeilen);
    }

    @Test
    void berechneRechnung_GrundgebuehrOptional_NoErrorWhenNoTarif() {
        LocalDate von = LocalDate.of(2024, 1, 1);
        LocalDate bis = LocalDate.of(2024, 1, 31);

        when(tarifService.getTarifeForZeitraum(TarifTyp.ZEV, von, bis))
            .thenReturn(Collections.singletonList(zevTarif2024));
        when(tarifService.getTarifeForZeitraum(TarifTyp.VNB, von, bis))
            .thenReturn(Collections.singletonList(vnbTarif2024));
        when(tarifService.getTarifeForZeitraum(TarifTyp.GRUNDGEBUEHR, von, bis))
            .thenReturn(Collections.emptyList());
        when(messwerteRepository.sumZevCalculatedByEinheitAndZeitBetween(eq(consumer), any(), any()))
            .thenReturn(100.0);
        when(messwerteRepository.sumTotalByEinheitAndZeitBetween(eq(consumer), any(), any()))
            .thenReturn(150.0);

        RechnungDTO rechnung = rechnungService.berechneRechnung(consumer, null, von, bis);

        // Kein Fehler, keine GRUNDGEBUEHR-Zeile, nur ZEV + VNB
        assertEquals(2, rechnung.getTarifZeilen().size());
        assertTrue(rechnung.getTarifZeilen().stream().noneMatch(z -> z.getTyp() == TarifTyp.GRUNDGEBUEHR));
    }

    @Test
    void berechneRechnung_GrundgebuehrIncludedInTotal() {
        LocalDate von = LocalDate.of(2024, 1, 1);
        LocalDate bis = LocalDate.of(2024, 1, 31);

        Tarif grundgebuehr = new Tarif(
            "Grundgebühr", TarifTyp.GRUNDGEBUEHR, new BigDecimal("10.00000"),
            LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)
        );

        when(tarifService.getTarifeForZeitraum(TarifTyp.ZEV, von, bis))
            .thenReturn(Collections.singletonList(zevTarif2024));
        when(tarifService.getTarifeForZeitraum(TarifTyp.VNB, von, bis))
            .thenReturn(Collections.singletonList(vnbTarif2024));
        when(tarifService.getTarifeForZeitraum(TarifTyp.GRUNDGEBUEHR, von, bis))
            .thenReturn(Collections.singletonList(grundgebuehr));
        // 100 kWh ZEV * 0.20 = 20.00, 50 kWh VNB * 0.34 = 17.00, Grundgebühr 1 * 10 = 10.00 → Total 47.00
        when(messwerteRepository.sumZevCalculatedByEinheitAndZeitBetween(eq(consumer), any(), any()))
            .thenReturn(100.0);
        when(messwerteRepository.sumTotalByEinheitAndZeitBetween(eq(consumer), any(), any()))
            .thenReturn(150.0);

        RechnungDTO rechnung = rechnungService.berechneRechnung(consumer, null, von, bis);

        assertEquals(47.0, rechnung.getTotalBetrag(), 0.01);
        assertEquals(47.0, rechnung.getEndBetrag(), 0.01);
        assertEquals(3, rechnung.getTarifZeilen().size());
    }

    // ─── GRUNDGEBUEHR: mengeneinheit ─────────────────────────────────────────────

    @Test
    void berechneRechnung_ZevVnbZeilen_HaveKwhMengeneinheit() {
        LocalDate von = LocalDate.of(2024, 1, 1);
        LocalDate bis = LocalDate.of(2024, 1, 31);

        when(tarifService.getTarifeForZeitraum(TarifTyp.ZEV, von, bis))
            .thenReturn(Collections.singletonList(zevTarif2024));
        when(tarifService.getTarifeForZeitraum(TarifTyp.VNB, von, bis))
            .thenReturn(Collections.singletonList(vnbTarif2024));
        when(messwerteRepository.sumZevCalculatedByEinheitAndZeitBetween(eq(consumer), any(), any()))
            .thenReturn(100.0);
        when(messwerteRepository.sumTotalByEinheitAndZeitBetween(eq(consumer), any(), any()))
            .thenReturn(150.0);

        RechnungDTO rechnung = rechnungService.berechneRechnung(consumer, null, von, bis);

        rechnung.getTarifZeilen().forEach(z ->
            assertEquals("KWH", z.getMengeneinheit(),
                "Erwartet KWH für Typ " + z.getTyp())
        );
    }

    @Test
    void berechneRechnung_GrundgebuehrZeile_HasMonatMengeneinheit() {
        LocalDate von = LocalDate.of(2024, 1, 1);
        LocalDate bis = LocalDate.of(2024, 1, 31);

        Tarif grundgebuehr = new Tarif(
            "Grundgebühr", TarifTyp.GRUNDGEBUEHR, new BigDecimal("5.00000"),
            LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)
        );

        when(tarifService.getTarifeForZeitraum(TarifTyp.ZEV, von, bis))
            .thenReturn(Collections.singletonList(zevTarif2024));
        when(tarifService.getTarifeForZeitraum(TarifTyp.VNB, von, bis))
            .thenReturn(Collections.singletonList(vnbTarif2024));
        when(tarifService.getTarifeForZeitraum(TarifTyp.GRUNDGEBUEHR, von, bis))
            .thenReturn(Collections.singletonList(grundgebuehr));
        when(messwerteRepository.sumZevCalculatedByEinheitAndZeitBetween(eq(consumer), any(), any()))
            .thenReturn(100.0);
        when(messwerteRepository.sumTotalByEinheitAndZeitBetween(eq(consumer), any(), any()))
            .thenReturn(150.0);

        RechnungDTO rechnung = rechnungService.berechneRechnung(consumer, null, von, bis);

        TarifZeileDTO grundgebuehrZeile = rechnung.getTarifZeilen().stream()
            .filter(z -> z.getTyp() == TarifTyp.GRUNDGEBUEHR)
            .findFirst().orElseThrow();

        assertEquals("MONAT", grundgebuehrZeile.getMengeneinheit());
    }

    // ─── GRUNDGEBUEHR: Produzenten-Rechnungen ────────────────────────────────────

    @Test
    void berechneRechnungen_ProducerWithGrundgebuehr_GetsInvoice() {
        LocalDate von = LocalDate.of(2024, 1, 1);
        LocalDate bis = LocalDate.of(2024, 3, 31);

        Einheit producer = new Einheit("Solaranlage", EinheitTyp.PRODUCER);
        producer.setId(2L);

        Tarif grundgebuehr = new Tarif(
            "Grundgebühr 2024", TarifTyp.GRUNDGEBUEHR, new BigDecimal("5.00000"),
            LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)
        );
        grundgebuehr.setProduzentVerrechnen(true);

        when(einheitRepository.findById(2L)).thenReturn(Optional.of(producer));
        when(tarifService.getTarifeForZeitraum(TarifTyp.GRUNDGEBUEHR, von, bis))
            .thenReturn(Collections.singletonList(grundgebuehr));

        List<RechnungDTO> rechnungen = rechnungService.berechneRechnungen(List.of(2L), von, bis);

        assertEquals(1, rechnungen.size());
        RechnungDTO rechnung = rechnungen.get(0);
        assertEquals("Solaranlage", rechnung.getEinheitName());
        assertNull(rechnung.getMieterName());

        // 3 Monate * 5.00 = 15.00
        assertEquals(1, rechnung.getTarifZeilen().size());
        assertEquals(TarifTyp.GRUNDGEBUEHR, rechnung.getTarifZeilen().get(0).getTyp());
        assertEquals(3.0, rechnung.getTarifZeilen().get(0).getMenge(), 0.001);
        assertEquals(15.0, rechnung.getEndBetrag(), 0.01);
    }

    @Test
    void berechneRechnungen_ProducerWithoutGrundgebuehr_NotIncluded() {
        LocalDate von = LocalDate.of(2024, 1, 1);
        LocalDate bis = LocalDate.of(2024, 3, 31);

        Einheit producer = new Einheit("Solaranlage", EinheitTyp.PRODUCER);
        producer.setId(2L);

        when(einheitRepository.findById(2L)).thenReturn(Optional.of(producer));
        when(tarifService.getTarifeForZeitraum(TarifTyp.GRUNDGEBUEHR, von, bis))
            .thenReturn(Collections.emptyList());

        List<RechnungDTO> rechnungen = rechnungService.berechneRechnungen(List.of(2L), von, bis);

        // Kein GRUNDGEBUEHR-Tarif → keine Rechnung für Produzenten
        assertEquals(0, rechnungen.size());
    }

    @Test
    void berechneRechnungen_ProducerOnly_SkipsZevVnbValidation() {
        LocalDate von = LocalDate.of(2024, 1, 1);
        LocalDate bis = LocalDate.of(2024, 3, 31);

        Einheit producer = new Einheit("Solaranlage", EinheitTyp.PRODUCER);
        producer.setId(2L);

        when(einheitRepository.findById(2L)).thenReturn(Optional.of(producer));
        when(tarifService.getTarifeForZeitraum(TarifTyp.GRUNDGEBUEHR, von, bis))
            .thenReturn(Collections.emptyList());

        rechnungService.berechneRechnungen(List.of(2L), von, bis);

        // validateTarifAbdeckung darf bei reinen Produzenten NICHT aufgerufen werden
        verify(tarifService, never()).validateTarifAbdeckung(any(), any());
    }

    @Test
    void berechneRechnungen_ProducerHasNoZevVnbLines() {
        LocalDate von = LocalDate.of(2024, 1, 1);
        LocalDate bis = LocalDate.of(2024, 1, 31);

        Einheit producer = new Einheit("Solaranlage", EinheitTyp.PRODUCER);
        producer.setId(2L);

        Tarif grundgebuehr = new Tarif(
            "Grundgebühr", TarifTyp.GRUNDGEBUEHR, new BigDecimal("5.00000"),
            LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)
        );
        grundgebuehr.setProduzentVerrechnen(true);

        when(einheitRepository.findById(2L)).thenReturn(Optional.of(producer));
        when(tarifService.getTarifeForZeitraum(TarifTyp.GRUNDGEBUEHR, von, bis))
            .thenReturn(Collections.singletonList(grundgebuehr));

        List<RechnungDTO> rechnungen = rechnungService.berechneRechnungen(List.of(2L), von, bis);

        assertEquals(1, rechnungen.size());
        // Keine ZEV- oder VNB-Zeilen auf der Produzenten-Rechnung
        assertTrue(rechnungen.get(0).getTarifZeilen().stream()
            .noneMatch(z -> z.getTyp() == TarifTyp.ZEV || z.getTyp() == TarifTyp.VNB));
    }

    @Test
    void berechneRechnungen_ProducerWithGrundgebuehrNotFlagged_NotIncluded() {
        LocalDate von = LocalDate.of(2024, 1, 1);
        LocalDate bis = LocalDate.of(2024, 3, 31);

        Einheit producer = new Einheit("Solaranlage", EinheitTyp.PRODUCER);
        producer.setId(2L);

        Tarif grundgebuehr = new Tarif(
            "Grundgebühr 2024", TarifTyp.GRUNDGEBUEHR, new BigDecimal("5.00000"),
            LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)
        );
        // produzentVerrechnen defaults to false → producer must NOT be charged

        when(einheitRepository.findById(2L)).thenReturn(Optional.of(producer));
        when(tarifService.getTarifeForZeitraum(TarifTyp.GRUNDGEBUEHR, von, bis))
            .thenReturn(Collections.singletonList(grundgebuehr));

        List<RechnungDTO> rechnungen = rechnungService.berechneRechnungen(List.of(2L), von, bis);

        // GRUNDGEBUEHR not flagged for producers → no invoice
        assertEquals(0, rechnungen.size());
    }

    // ─── Tarifpositionen (Ladestrom, Spec Ladestromtarif.md) ────────────────────

    /** Tarif vom Typ LADESTROM, gültig für das ganze Jahr 2024. */
    private Tarif ladestromTarif(String bezeichnung, String preis) {
        Tarif tarif = new Tarif(
            bezeichnung, TarifTyp.LADESTROM, new BigDecimal(preis),
            LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)
        );
        tarif.setId(99L);
        return tarif;
    }

    /** Ladestations-Einheit, an der die Tarifpositionen haengen (Specs/Ladestationen.md). */
    private Einheit ladestation() {
        Einheit einheit = new Einheit("Ladestation", EinheitTyp.LADESTATION);
        einheit.setId(900L);
        einheit.setMesspunkt("RFID-900");
        return einheit;
    }

    private Tarifposition tarifposition(Tarif tarif, Einheit einheit, int jahr, int quartal, String menge) {
        Tarifposition position = new Tarifposition(einheit, tarif, jahr, quartal, new BigDecimal(menge));
        position.setId(1L);
        return position;
    }

    /** Standard-Mieter mit ZEV/VNB-Messwerten für die Rechnungstests. */
    private Mieter mieterMitMesswerten(LocalDate von, LocalDate bis) {
        Mieter mieter = new Mieter("Max Muster", LocalDate.of(2023, 1, 1), 1L);
        mieter.setId(1L);

        when(tarifService.getTarifeForZeitraum(TarifTyp.ZEV, von, bis))
            .thenReturn(Collections.singletonList(zevTarif2024));
        when(tarifService.getTarifeForZeitraum(TarifTyp.VNB, von, bis))
            .thenReturn(Collections.singletonList(vnbTarif2024));
        when(messwerteRepository.sumZevCalculatedByEinheitAndZeitBetween(eq(consumer), any(), any()))
            .thenReturn(100.0);
        when(messwerteRepository.sumTotalByEinheitAndZeitBetween(eq(consumer), any(), any()))
            .thenReturn(150.0);

        return mieter;
    }

    @Test
    void berechneRechnung_TarifpositionMitMenge_AddsLadestromZeile() {
        LocalDate von = LocalDate.of(2024, 1, 1);
        LocalDate bis = LocalDate.of(2024, 3, 31);

        Mieter mieter = mieterMitMesswerten(von, bis);
        Tarif ladestrom = ladestromTarif("Ladestrom", "0.35000");
        when(tarifpositionService.getFuerRechnung(anyCollection(), eq(von), eq(bis)))
            .thenReturn(List.of(tarifposition(ladestrom, ladestation(), 2024, 1, "120.000")));

        RechnungDTO rechnung = rechnungService.berechneRechnung(consumer, mieter, von, bis);

        TarifZeileDTO zeile = rechnung.getTarifZeilen().stream()
            .filter(z -> z.getTyp() == TarifTyp.LADESTROM)
            .findFirst().orElseThrow();

        assertEquals("Ladestrom", zeile.getBezeichnung());
        // Quartalsgrenzen als Von/Bis, nicht der Rechnungszeitraum
        assertEquals(LocalDate.of(2024, 1, 1), zeile.getVon());
        assertEquals(LocalDate.of(2024, 3, 31), zeile.getBis());
        assertEquals(120.0, zeile.getMenge(), 0.001);
        assertEquals(0.35, zeile.getPreis(), 0.00001);
        assertEquals(42.0, zeile.getBetrag(), 0.01); // 120 * 0.35
        assertEquals("KWH", zeile.getMengeneinheit());
    }

    @Test
    void berechneRechnung_TarifpositionQ3_UsesQuartalBoundsAsPeriod() {
        LocalDate von = LocalDate.of(2024, 7, 1);
        LocalDate bis = LocalDate.of(2024, 9, 30);

        Mieter mieter = mieterMitMesswerten(von, bis);
        Tarif ladestrom = ladestromTarif("Ladestrom", "0.35000");
        when(tarifpositionService.getFuerRechnung(anyCollection(), eq(von), eq(bis)))
            .thenReturn(List.of(tarifposition(ladestrom, ladestation(), 2024, 3, "10.000")));

        RechnungDTO rechnung = rechnungService.berechneRechnung(consumer, mieter, von, bis);

        TarifZeileDTO zeile = rechnung.getTarifZeilen().stream()
            .filter(z -> z.getTyp() == TarifTyp.LADESTROM)
            .findFirst().orElseThrow();

        assertEquals(LocalDate.of(2024, 7, 1), zeile.getVon());
        assertEquals(LocalDate.of(2024, 9, 30), zeile.getBis());
    }

    @Test
    void berechneRechnung_TarifpositionMenge_RoundedLikeZevVnbLines() {
        LocalDate von = LocalDate.of(2024, 1, 1);
        LocalDate bis = LocalDate.of(2024, 3, 31);

        Mieter mieter = mieterMitMesswerten(von, bis);
        Tarif ladestrom = ladestromTarif("Ladestrom", "0.35000");
        // Gespeichert mit 3 NKS, dargestellt/verrechnet gerundet wie ZEV/VNB
        when(tarifpositionService.getFuerRechnung(anyCollection(), eq(von), eq(bis)))
            .thenReturn(List.of(tarifposition(ladestrom, ladestation(), 2024, 1, "120.567")));

        RechnungDTO rechnung = rechnungService.berechneRechnung(consumer, mieter, von, bis);

        TarifZeileDTO zeile = rechnung.getTarifZeilen().stream()
            .filter(z -> z.getTyp() == TarifTyp.LADESTROM)
            .findFirst().orElseThrow();

        assertEquals(121.0, zeile.getMenge(), 0.001);
        assertEquals(42.35, zeile.getBetrag(), 0.01);
    }

    @Test
    void berechneRechnung_TarifpositionBetrag_FlowsIntoTotalRundungAndEndbetrag() {
        LocalDate von = LocalDate.of(2024, 1, 1);
        LocalDate bis = LocalDate.of(2024, 3, 31);

        Mieter mieter = mieterMitMesswerten(von, bis);
        Tarif ladestrom = ladestromTarif("Ladestrom", "0.33000");
        when(tarifpositionService.getFuerRechnung(anyCollection(), eq(von), eq(bis)))
            .thenReturn(List.of(tarifposition(ladestrom, ladestation(), 2024, 1, "7.000")));

        RechnungDTO rechnung = rechnungService.berechneRechnung(consumer, mieter, von, bis);

        // ZEV 100 * 0.20 = 20.00, VNB 50 * 0.34 = 17.00, Ladestrom 7 * 0.33 = 2.31 → 39.31
        assertEquals(39.31, rechnung.getTotalBetrag(), 0.001);
        assertEquals(39.30, rechnung.getEndBetrag(), 0.001);
        assertEquals(-0.01, rechnung.getRundung(), 0.001);
    }

    @Test
    void berechneRechnung_TarifpositionMitQuellReferenz_AppendsItToBezeichnung() {
        LocalDate von = LocalDate.of(2024, 1, 1);
        LocalDate bis = LocalDate.of(2024, 3, 31);

        Mieter mieter = mieterMitMesswerten(von, bis);
        Tarif ladestrom = ladestromTarif("Ladestrom", "0.35000");
        Tarifposition position = tarifposition(ladestrom, ladestation(), 2024, 1, "10.000");
        position.setQuellReferenz("LP-01");
        when(tarifpositionService.getFuerRechnung(anyCollection(), eq(von), eq(bis))).thenReturn(List.of(position));

        RechnungDTO rechnung = rechnungService.berechneRechnung(consumer, mieter, von, bis);

        TarifZeileDTO zeile = rechnung.getTarifZeilen().stream()
            .filter(z -> z.getTyp() == TarifTyp.LADESTROM)
            .findFirst().orElseThrow();

        assertEquals("Ladestrom (LP-01)", zeile.getBezeichnung());
    }

    @Test
    void berechneRechnung_TarifpositionOhneQuellReferenz_KeepsBezeichnung() {
        LocalDate von = LocalDate.of(2024, 1, 1);
        LocalDate bis = LocalDate.of(2024, 3, 31);

        Mieter mieter = mieterMitMesswerten(von, bis);
        Tarif ladestrom = ladestromTarif("Ladestrom", "0.35000");
        when(tarifpositionService.getFuerRechnung(anyCollection(), eq(von), eq(bis)))
            .thenReturn(List.of(tarifposition(ladestrom, ladestation(), 2024, 1, "10.000")));

        RechnungDTO rechnung = rechnungService.berechneRechnung(consumer, mieter, von, bis);

        TarifZeileDTO zeile = rechnung.getTarifZeilen().stream()
            .filter(z -> z.getTyp() == TarifTyp.LADESTROM)
            .findFirst().orElseThrow();

        assertEquals("Ladestrom", zeile.getBezeichnung());
    }

    @Test
    void berechneRechnung_TarifpositionMitLeererQuellReferenz_KeepsBezeichnung() {
        LocalDate von = LocalDate.of(2024, 1, 1);
        LocalDate bis = LocalDate.of(2024, 3, 31);

        Mieter mieter = mieterMitMesswerten(von, bis);
        Tarif ladestrom = ladestromTarif("Ladestrom", "0.35000");
        Tarifposition position = tarifposition(ladestrom, ladestation(), 2024, 1, "10.000");
        position.setQuellReferenz("   ");
        when(tarifpositionService.getFuerRechnung(anyCollection(), eq(von), eq(bis))).thenReturn(List.of(position));

        RechnungDTO rechnung = rechnungService.berechneRechnung(consumer, mieter, von, bis);

        TarifZeileDTO zeile = rechnung.getTarifZeilen().stream()
            .filter(z -> z.getTyp() == TarifTyp.LADESTROM)
            .findFirst().orElseThrow();

        assertEquals("Ladestrom", zeile.getBezeichnung());
    }

    @Test
    void berechneRechnung_ZweiQuartaleImZeitraum_CreatesTwoLadestromZeilen() {
        LocalDate von = LocalDate.of(2024, 1, 1);
        LocalDate bis = LocalDate.of(2024, 6, 30);

        Mieter mieter = mieterMitMesswerten(von, bis);
        Tarif ladestrom = ladestromTarif("Ladestrom", "0.35000");
        when(tarifpositionService.getFuerRechnung(anyCollection(), eq(von), eq(bis))).thenReturn(List.of(
            tarifposition(ladestrom, ladestation(), 2024, 1, "100.000"),
            tarifposition(ladestrom, ladestation(), 2024, 2, "200.000")
        ));

        RechnungDTO rechnung = rechnungService.berechneRechnung(consumer, mieter, von, bis);

        List<TarifZeileDTO> ladestromZeilen = rechnung.getTarifZeilen().stream()
            .filter(z -> z.getTyp() == TarifTyp.LADESTROM)
            .toList();

        assertEquals(2, ladestromZeilen.size());
        assertEquals(LocalDate.of(2024, 1, 1), ladestromZeilen.get(0).getVon());
        assertEquals(LocalDate.of(2024, 3, 31), ladestromZeilen.get(0).getBis());
        assertEquals(LocalDate.of(2024, 4, 1), ladestromZeilen.get(1).getVon());
        assertEquals(LocalDate.of(2024, 6, 30), ladestromZeilen.get(1).getBis());
        // 100 * 0.35 + 200 * 0.35 = 105.00, dazu ZEV 20.00 + VNB 17.00
        assertEquals(142.0, rechnung.getTotalBetrag(), 0.01);
    }

    @Test
    void berechneRechnung_KeineTarifpositionen_RechnungUnveraendert() {
        LocalDate von = LocalDate.of(2024, 1, 1);
        LocalDate bis = LocalDate.of(2024, 1, 31);

        Mieter mieter = mieterMitMesswerten(von, bis);
        when(tarifpositionService.getFuerRechnung(anyCollection(), eq(von), eq(bis))).thenReturn(Collections.emptyList());

        RechnungDTO rechnung = rechnungService.berechneRechnung(consumer, mieter, von, bis);

        // Nur ZEV + VNB wie bisher - keine leere Zeile, kein Fehler
        assertEquals(2, rechnung.getTarifZeilen().size());
        assertEquals(37.0, rechnung.getTotalBetrag(), 0.01);
    }

    @Test
    void berechneRechnung_OhneMieter_QueriesNoTarifpositionen() {
        LocalDate von = LocalDate.of(2024, 1, 1);
        LocalDate bis = LocalDate.of(2024, 1, 31);

        when(tarifService.getTarifeForZeitraum(TarifTyp.ZEV, von, bis))
            .thenReturn(Collections.singletonList(zevTarif2024));
        when(tarifService.getTarifeForZeitraum(TarifTyp.VNB, von, bis))
            .thenReturn(Collections.singletonList(vnbTarif2024));
        when(messwerteRepository.sumZevCalculatedByEinheitAndZeitBetween(eq(consumer), any(), any()))
            .thenReturn(100.0);
        when(messwerteRepository.sumTotalByEinheitAndZeitBetween(eq(consumer), any(), any()))
            .thenReturn(150.0);

        RechnungDTO rechnung = rechnungService.berechneRechnung(consumer, null, von, bis);

        assertEquals(2, rechnung.getTarifZeilen().size());
        verify(tarifpositionService, never()).getFuerRechnung(any(), any(), any());
    }

    @Test
    void berechneRechnung_LadestromZeilen_AfterZevVnbAndBeforeGrundgebuehr() {
        LocalDate von = LocalDate.of(2024, 1, 1);
        LocalDate bis = LocalDate.of(2024, 3, 31);

        Mieter mieter = mieterMitMesswerten(von, bis);
        Tarif ladestrom = ladestromTarif("Ladestrom", "0.35000");
        when(tarifpositionService.getFuerRechnung(anyCollection(), eq(von), eq(bis)))
            .thenReturn(List.of(tarifposition(ladestrom, ladestation(), 2024, 1, "10.000")));

        Tarif grundgebuehr = new Tarif(
            "Grundgebühr 2024", TarifTyp.GRUNDGEBUEHR, new BigDecimal("5.00000"),
            LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)
        );
        when(tarifService.getTarifeForZeitraum(TarifTyp.GRUNDGEBUEHR, von, bis))
            .thenReturn(Collections.singletonList(grundgebuehr));

        RechnungDTO rechnung = rechnungService.berechneRechnung(consumer, mieter, von, bis);

        List<TarifTyp> reihenfolge = rechnung.getTarifZeilen().stream()
            .map(TarifZeileDTO::getTyp)
            .toList();

        assertEquals(List.of(TarifTyp.ZEV, TarifTyp.VNB, TarifTyp.LADESTROM, TarifTyp.GRUNDGEBUEHR),
            reihenfolge);
    }

    @Test
    void berechneRechnungen_MieterwechselImQuartal_EachInvoiceOnlyOwnPosition() {
        LocalDate von = LocalDate.of(2024, 1, 1);
        LocalDate bis = LocalDate.of(2024, 3, 31);

        Mieter mieterA = new Mieter("Mieter A", LocalDate.of(2023, 1, 1), 1L);
        mieterA.setId(1L);
        mieterA.setMietende(LocalDate.of(2024, 2, 29));

        Mieter mieterB = new Mieter("Mieter B", LocalDate.of(2024, 3, 1), 1L);
        mieterB.setId(2L);

        Tarif ladestrom = ladestromTarif("Ladestrom", "0.35000");

        doNothing().when(tarifService).validateTarifAbdeckung(von, bis);
        when(einheitRepository.findById(1L)).thenReturn(Optional.of(consumer));
        when(mieterService.getMieterForQuartal(eq(1L), any(), any()))
            .thenReturn(Arrays.asList(mieterA, mieterB));
        when(tarifService.getTarifeForZeitraum(any(), any(), any()))
            .thenReturn(Collections.singletonList(zevTarif2024));
        when(messwerteRepository.sumZevCalculatedByEinheitAndZeitBetween(any(), any(), any()))
            .thenReturn(100.0);
        when(messwerteRepository.sumTotalByEinheitAndZeitBetween(any(), any(), any()))
            .thenReturn(150.0);

        // Jeder Mieter traegt seine eigene Q1-Position - obwohl beide Rechnungen nur einen
        // Teil von Q1 abdecken (Ueberschneidungsregel FR-1.5).
        when(mieterService.getEinheitIds(mieterA.getId())).thenReturn(List.of(1L));
        when(mieterService.getEinheitIds(mieterB.getId())).thenReturn(List.of(2L));
        when(tarifpositionService.getFuerRechnung(eq(List.of(1L)), any(), any()))
            .thenReturn(List.of(tarifposition(ladestrom, ladestation(), 2024, 1, "100.000")));
        when(tarifpositionService.getFuerRechnung(eq(List.of(2L)), any(), any()))
            .thenReturn(List.of(tarifposition(ladestrom, ladestation(), 2024, 1, "200.000")));

        List<RechnungDTO> rechnungen = rechnungService.berechneRechnungen(List.of(1L), von, bis);

        assertEquals(2, rechnungen.size());

        TarifZeileDTO zeileA = rechnungen.get(0).getTarifZeilen().stream()
            .filter(z -> z.getTyp() == TarifTyp.LADESTROM)
            .findFirst().orElseThrow();
        TarifZeileDTO zeileB = rechnungen.get(1).getTarifZeilen().stream()
            .filter(z -> z.getTyp() == TarifTyp.LADESTROM)
            .findFirst().orElseThrow();

        assertEquals(100.0, zeileA.getMenge(), 0.001);
        assertEquals(200.0, zeileB.getMenge(), 0.001);
        // Je Rechnung genau eine Ladestrom-Zeile - keine fremde Position
        assertEquals(1, rechnungen.get(0).getTarifZeilen().stream()
            .filter(z -> z.getTyp() == TarifTyp.LADESTROM).count());
        assertEquals(1, rechnungen.get(1).getTarifZeilen().stream()
            .filter(z -> z.getTyp() == TarifTyp.LADESTROM).count());
    }

    @Test
    void berechneRechnungen_Produzent_HasNoTarifpositionsZeilen() {
        LocalDate von = LocalDate.of(2024, 1, 1);
        LocalDate bis = LocalDate.of(2024, 3, 31);

        Einheit producer = new Einheit("Solaranlage", EinheitTyp.PRODUCER);
        producer.setId(2L);

        Tarif grundgebuehr = new Tarif(
            "Grundgebühr 2024", TarifTyp.GRUNDGEBUEHR, new BigDecimal("5.00000"),
            LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)
        );
        grundgebuehr.setProduzentVerrechnen(true);

        when(einheitRepository.findById(2L)).thenReturn(Optional.of(producer));
        when(tarifService.getTarifeForZeitraum(TarifTyp.GRUNDGEBUEHR, von, bis))
            .thenReturn(Collections.singletonList(grundgebuehr));

        List<RechnungDTO> rechnungen = rechnungService.berechneRechnungen(List.of(2L), von, bis);

        assertEquals(1, rechnungen.size());
        assertTrue(rechnungen.get(0).getTarifZeilen().stream()
            .noneMatch(z -> z.getTyp() == TarifTyp.LADESTROM));
        verify(tarifpositionService, never()).getFuerRechnung(any(), any(), any());
    }

    @Test
    void berechneRechnungen_ConsumerGrundgebuehr_IgnoresProduzentFlag() {
        LocalDate von = LocalDate.of(2024, 1, 1);
        LocalDate bis = LocalDate.of(2024, 3, 31);

        doNothing().when(tarifService).validateTarifAbdeckung(von, bis);
        when(einheitRepository.findById(1L)).thenReturn(Optional.of(consumer));
        when(mieterService.getMieterForQuartal(eq(1L), any(), any())).thenReturn(Collections.emptyList());

        when(tarifService.getTarifeForZeitraum(eq(TarifTyp.ZEV), any(), any()))
            .thenReturn(Collections.singletonList(zevTarif2024));
        when(tarifService.getTarifeForZeitraum(eq(TarifTyp.VNB), any(), any()))
            .thenReturn(Collections.singletonList(vnbTarif2024));
        when(messwerteRepository.sumZevCalculatedByEinheitAndZeitBetween(any(), any(), any()))
            .thenReturn(100.0);
        when(messwerteRepository.sumTotalByEinheitAndZeitBetween(any(), any(), any()))
            .thenReturn(150.0);

        Tarif grundgebuehr = new Tarif(
            "Grundgebühr 2024", TarifTyp.GRUNDGEBUEHR, new BigDecimal("5.00000"),
            LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)
        );
        // Flag is false, but consumers are always charged GRUNDGEBUEHR
        when(tarifService.getTarifeForZeitraum(TarifTyp.GRUNDGEBUEHR, von, bis))
            .thenReturn(Collections.singletonList(grundgebuehr));

        List<RechnungDTO> rechnungen = rechnungService.berechneRechnungen(List.of(1L), von, bis);

        assertEquals(1, rechnungen.size());
        // Consumer invoice contains the GRUNDGEBUEHR line regardless of the producer flag
        assertTrue(rechnungen.get(0).getTarifZeilen().stream()
            .anyMatch(z -> z.getTyp() == TarifTyp.GRUNDGEBUEHR));
    }

    // ─── Dritter Zweig: eigene Rechnung fuer Ladestationen (Specs/Ladestationen.md FR-1.5) ───
    // Eine LADESTATION erzeugt nur dann eine eigene Rechnung, wenn ihrem Mieter keine CONSUMER-
    // Einheit zugeordnet ist ("Nutzer ohne Wohnung"). Hat er eine, erscheinen die Positionen auf
    // deren Rechnung - sonst bekaeme er zwei Rechnungen mit derselben Zeile.

    /** Mieter mit Adresse, unbefristet ab 2023. */
    private Mieter mieter(Long id, String name) {
        Mieter mieter = new Mieter(name, LocalDate.of(2023, 1, 1), 900L);
        mieter.setId(id);
        mieter.setStrasse("Ladeweg 7");
        mieter.setPlz("3000");
        mieter.setOrt("Bern");
        return mieter;
    }

    /** Zweite Ladestation desselben Mieters (eigene RFID, eigene Einheit). */
    private Einheit ladestationZwei() {
        Einheit einheit = new Einheit("Ladestation 2", EinheitTyp.LADESTATION);
        einheit.setId(901L);
        einheit.setMesspunkt("RFID-901");
        return einheit;
    }

    @Test
    void berechneRechnungen_OhneWohnungZweiLadestationen_ErzeugtNurEineRechnung() {
        // Regression: Zuvor entstand je gewaehlter Ladestation eine Rechnung - und weil die
        // Positionen ALLER Einheiten des Mieters gesammelt werden, trug jede davon saemtliche
        // Zeilen. Der Nutzer waere doppelt belastet worden (Specs/Ladestationen.md: "genau eine
        // Rechnung").
        LocalDate von = LocalDate.of(2024, 1, 1);
        LocalDate bis = LocalDate.of(2024, 3, 31);

        Einheit erste = ladestation();
        Einheit zweite = ladestationZwei();
        Mieter nutzer = mieter(52L, "Nutzer mit zwei Ladestationen");
        Tarif ladestrom = ladestromTarif("Ladestrom", "0.35000");

        when(einheitRepository.findById(900L)).thenReturn(Optional.of(erste));
        when(einheitRepository.findById(901L)).thenReturn(Optional.of(zweite));
        when(mieterService.getMieterForQuartal(eq(900L), any(), any())).thenReturn(List.of(nutzer));
        when(mieterService.getMieterForQuartal(eq(901L), any(), any())).thenReturn(List.of(nutzer));
        // Beide Ladestationen gehoeren demselben Mieter, eine Wohnung hat er nicht
        when(mieterService.getEinheitIds(52L)).thenReturn(List.of(900L, 901L));
        when(tarifpositionService.getFuerRechnung(eq(List.of(900L, 901L)), any(), any()))
            .thenReturn(List.of(
                tarifposition(ladestrom, erste, 2024, 1, "100.000"),
                tarifposition(ladestrom, zweite, 2024, 1, "50.000")));

        List<RechnungDTO> rechnungen = rechnungService.berechneRechnungen(List.of(900L, 901L), von, bis);

        // Genau EINE Rechnung - und darauf beide Ladestrom-Zeilen
        assertEquals(1, rechnungen.size());
        RechnungDTO rechnung = rechnungen.get(0);
        assertEquals(52L, rechnung.getMieterId());
        assertEquals(2, rechnung.getTarifZeilen().size());
        // 100 kWh + 50 kWh zu 0.35 = 52.50
        assertEquals(52.50, rechnung.getTotalBetrag(), 0.001);
    }

    @Test
    void berechneRechnungen_LadestationOhneWohnung_ErzeugtEigeneRechnung() {
        LocalDate von = LocalDate.of(2024, 1, 1);
        LocalDate bis = LocalDate.of(2024, 3, 31);

        Einheit ladestation = ladestation();
        Mieter nutzer = mieter(50L, "Nutzer ohne Wohnung");
        Tarif ladestrom = ladestromTarif("Ladestrom", "0.35000");

        when(einheitRepository.findById(900L)).thenReturn(Optional.of(ladestation));
        when(mieterService.getMieterForQuartal(eq(900L), any(), any())).thenReturn(List.of(nutzer));
        // Nur die Ladestation ist zugeordnet -> keine Wohnung
        when(mieterService.getEinheitIds(50L)).thenReturn(List.of(900L));
        when(tarifpositionService.getFuerRechnung(eq(List.of(900L)), eq(von), eq(bis)))
            .thenReturn(List.of(tarifposition(ladestrom, ladestation, 2024, 1, "120.000")));

        List<RechnungDTO> rechnungen = rechnungService.berechneRechnungen(List.of(900L), von, bis);

        assertEquals(1, rechnungen.size());
        RechnungDTO rechnung = rechnungen.get(0);
        assertEquals(900L, rechnung.getEinheitId());
        assertEquals("Ladestation", rechnung.getEinheitName());
        assertEquals("RFID-900", rechnung.getMesspunkt());
        assertEquals(50L, rechnung.getMieterId());
        assertEquals("Nutzer ohne Wohnung", rechnung.getMieterName());
        assertEquals("Ladeweg 7", rechnung.getMieterStrasse());
        assertEquals("3000 Bern", rechnung.getMieterPlzOrt());
        assertEquals(von, rechnung.getVon());
        assertEquals(bis, rechnung.getBis());

        assertEquals(1, rechnung.getTarifZeilen().size());
        TarifZeileDTO zeile = rechnung.getTarifZeilen().get(0);
        assertEquals(TarifTyp.LADESTROM, zeile.getTyp());
        assertEquals(120.0, zeile.getMenge(), 0.001);
        assertEquals(42.0, zeile.getBetrag(), 0.01);
        assertEquals(42.0, rechnung.getTotalBetrag(), 0.01);
        assertEquals(42.0, rechnung.getEndBetrag(), 0.01);
    }

    @Test
    void berechneRechnungen_LadestationOhneWohnung_HatKeineZevVnbUndGrundgebuehrZeilen() {
        LocalDate von = LocalDate.of(2024, 1, 1);
        LocalDate bis = LocalDate.of(2024, 3, 31);

        Einheit ladestation = ladestation();
        Mieter nutzer = mieter(50L, "Nutzer ohne Wohnung");
        Tarif ladestrom = ladestromTarif("Ladestrom", "0.35000");

        when(einheitRepository.findById(900L)).thenReturn(Optional.of(ladestation));
        when(mieterService.getMieterForQuartal(eq(900L), any(), any())).thenReturn(List.of(nutzer));
        when(mieterService.getEinheitIds(50L)).thenReturn(List.of(900L));
        when(tarifpositionService.getFuerRechnung(anyCollection(), eq(von), eq(bis)))
            .thenReturn(List.of(tarifposition(ladestrom, ladestation, 2024, 1, "10.000")));

        List<RechnungDTO> rechnungen = rechnungService.berechneRechnungen(List.of(900L), von, bis);

        // Ladestationen haben keine Messwerte (kein ZEV/VNB) und tragen keine Grundgebuehr
        assertTrue(rechnungen.get(0).getTarifZeilen().stream()
            .allMatch(z -> z.getTyp() == TarifTyp.LADESTROM));
        verify(tarifService, never()).getTarifeForZeitraum(any(), any(), any());
        verify(messwerteRepository, never()).sumZevCalculatedByEinheitAndZeitBetween(any(), any(), any());
        verify(messwerteRepository, never()).sumTotalByEinheitAndZeitBetween(any(), any(), any());
        // Ohne CONSUMER-Einheit im Lauf entfaellt die Tarifabdeckungspruefung
        verify(tarifService, never()).validateTarifAbdeckung(any(), any());
    }

    @Test
    void berechneRechnungen_LadestationMitWohnung_ErzeugtKeineEigeneRechnung() {
        LocalDate von = LocalDate.of(2024, 1, 1);
        LocalDate bis = LocalDate.of(2024, 3, 31);

        Einheit ladestation = ladestation();
        Mieter mitWohnung = mieter(51L, "Mieter mit Wohnung");

        when(einheitRepository.findById(900L)).thenReturn(Optional.of(ladestation));
        when(einheitRepository.findById(1L)).thenReturn(Optional.of(consumer));
        when(mieterService.getMieterForQuartal(eq(900L), any(), any())).thenReturn(List.of(mitWohnung));
        // Wohnung + Ladestation -> die Positionen erscheinen auf der Wohnungsrechnung
        when(mieterService.getEinheitIds(51L)).thenReturn(List.of(1L, 900L));

        List<RechnungDTO> rechnungen = rechnungService.berechneRechnungen(List.of(900L), von, bis);

        assertTrue(rechnungen.isEmpty());
        verify(tarifpositionService, never()).getFuerRechnung(any(), any(), any());
    }

    @Test
    void berechneRechnungen_WohnungUndLadestationAusgewaehlt_ErzeugtGenauEineRechnung() {
        LocalDate von = LocalDate.of(2024, 1, 1);
        LocalDate bis = LocalDate.of(2024, 3, 31);

        Einheit ladestation = ladestation();
        Mieter mitWohnung = mieter(51L, "Mieter mit Wohnung");
        Tarif ladestrom = ladestromTarif("Ladestrom", "0.35000");

        doNothing().when(tarifService).validateTarifAbdeckung(von, bis);
        when(einheitRepository.findById(1L)).thenReturn(Optional.of(consumer));
        when(einheitRepository.findById(900L)).thenReturn(Optional.of(ladestation));
        when(mieterService.getMieterForQuartal(eq(1L), any(), any())).thenReturn(List.of(mitWohnung));
        when(mieterService.getMieterForQuartal(eq(900L), any(), any())).thenReturn(List.of(mitWohnung));
        when(mieterService.getEinheitIds(51L)).thenReturn(List.of(1L, 900L));
        when(tarifService.getTarifeForZeitraum(eq(TarifTyp.ZEV), any(), any()))
            .thenReturn(Collections.singletonList(zevTarif2024));
        when(tarifService.getTarifeForZeitraum(eq(TarifTyp.VNB), any(), any()))
            .thenReturn(Collections.singletonList(vnbTarif2024));
        when(tarifService.getTarifeForZeitraum(eq(TarifTyp.GRUNDGEBUEHR), any(), any()))
            .thenReturn(Collections.emptyList());
        when(messwerteRepository.sumZevCalculatedByEinheitAndZeitBetween(any(), any(), any()))
            .thenReturn(100.0);
        when(messwerteRepository.sumTotalByEinheitAndZeitBetween(any(), any(), any()))
            .thenReturn(150.0);
        when(tarifpositionService.getFuerRechnung(anyCollection(), any(), any()))
            .thenReturn(List.of(tarifposition(ladestrom, ladestation, 2024, 1, "100.000")));

        List<RechnungDTO> rechnungen = rechnungService.berechneRechnungen(List.of(1L, 900L), von, bis);

        // Genau EINE Rechnung - die der Wohnung, mit der Ladestrom-Zeile darauf
        assertEquals(1, rechnungen.size());
        assertEquals("Wohnung A", rechnungen.get(0).getEinheitName());
        assertEquals(1, rechnungen.get(0).getTarifZeilen().stream()
            .filter(z -> z.getTyp() == TarifTyp.LADESTROM).count());
    }

    @Test
    void berechneRechnungen_LadestationOhnePositionen_ErzeugtKeineRechnung() {
        LocalDate von = LocalDate.of(2024, 1, 1);
        LocalDate bis = LocalDate.of(2024, 3, 31);

        Mieter nutzer = mieter(50L, "Nutzer ohne Wohnung");

        when(einheitRepository.findById(900L)).thenReturn(Optional.of(ladestation()));
        when(mieterService.getMieterForQuartal(eq(900L), any(), any())).thenReturn(List.of(nutzer));
        when(mieterService.getEinheitIds(50L)).thenReturn(List.of(900L));
        when(tarifpositionService.getFuerRechnung(anyCollection(), any(), any()))
            .thenReturn(Collections.emptyList());

        List<RechnungDTO> rechnungen = rechnungService.berechneRechnungen(List.of(900L), von, bis);

        // Keine leere Rechnung ohne Zeilen (Specs/Ladestationen.md §5)
        assertTrue(rechnungen.isEmpty());
    }

    @Test
    void berechneRechnungen_LadestationOhneMieter_ErzeugtKeineRechnung() {
        LocalDate von = LocalDate.of(2024, 1, 1);
        LocalDate bis = LocalDate.of(2024, 3, 31);

        when(einheitRepository.findById(900L)).thenReturn(Optional.of(ladestation()));
        when(mieterService.getMieterForQuartal(eq(900L), any(), any())).thenReturn(Collections.emptyList());

        List<RechnungDTO> rechnungen = rechnungService.berechneRechnungen(List.of(900L), von, bis);

        // Positionen sind erfassbar, erscheinen aber auf keiner Rechnung
        assertTrue(rechnungen.isEmpty());
        verify(tarifpositionService, never()).getFuerRechnung(any(), any(), any());
    }

    @Test
    void berechneRechnungen_LadestationMietendeImZeitraum_KuerztDenEffektivenZeitraum() {
        LocalDate von = LocalDate.of(2024, 1, 1);
        LocalDate bis = LocalDate.of(2024, 3, 31);

        Einheit ladestation = ladestation();
        Mieter nutzer = mieter(50L, "Nutzer ohne Wohnung");
        nutzer.setMietbeginn(LocalDate.of(2024, 2, 1));
        nutzer.setMietende(LocalDate.of(2024, 2, 29));
        Tarif ladestrom = ladestromTarif("Ladestrom", "0.35000");

        when(einheitRepository.findById(900L)).thenReturn(Optional.of(ladestation));
        when(mieterService.getMieterForQuartal(eq(900L), any(), any())).thenReturn(List.of(nutzer));
        when(mieterService.getEinheitIds(50L)).thenReturn(List.of(900L));
        when(tarifpositionService.getFuerRechnung(anyCollection(), any(), any()))
            .thenReturn(List.of(tarifposition(ladestrom, ladestation, 2024, 1, "10.000")));

        List<RechnungDTO> rechnungen = rechnungService.berechneRechnungen(List.of(900L), von, bis);

        assertEquals(1, rechnungen.size());
        assertEquals(LocalDate.of(2024, 2, 1), rechnungen.get(0).getVon());
        assertEquals(LocalDate.of(2024, 2, 29), rechnungen.get(0).getBis());
        // Die Position wird trotz Teilzeitraum aufgenommen (Ueberschneidungsregel)
        verify(tarifpositionService).getFuerRechnung(anyCollection(),
            eq(LocalDate.of(2024, 2, 1)), eq(LocalDate.of(2024, 2, 29)));
        // Die Quartalsgrenzen bleiben die Zeilengrenzen
        assertEquals(LocalDate.of(2024, 1, 1), rechnungen.get(0).getTarifZeilen().get(0).getVon());
        assertEquals(LocalDate.of(2024, 3, 31), rechnungen.get(0).getTarifZeilen().get(0).getBis());
    }

    @Test
    void berechneRechnungen_LadestationRechnung_RundetAuf5Rappen() {
        LocalDate von = LocalDate.of(2024, 1, 1);
        LocalDate bis = LocalDate.of(2024, 3, 31);

        Einheit ladestation = ladestation();
        Mieter nutzer = mieter(50L, "Nutzer ohne Wohnung");
        Tarif ladestrom = ladestromTarif("Ladestrom", "0.33000");

        when(einheitRepository.findById(900L)).thenReturn(Optional.of(ladestation));
        when(mieterService.getMieterForQuartal(eq(900L), any(), any())).thenReturn(List.of(nutzer));
        when(mieterService.getEinheitIds(50L)).thenReturn(List.of(900L));
        when(tarifpositionService.getFuerRechnung(anyCollection(), any(), any()))
            .thenReturn(List.of(tarifposition(ladestrom, ladestation, 2024, 1, "7.000")));

        List<RechnungDTO> rechnungen = rechnungService.berechneRechnungen(List.of(900L), von, bis);

        // 7 * 0.33 = 2.31 -> 2.30
        assertEquals(2.31, rechnungen.get(0).getTotalBetrag(), 0.001);
        assertEquals(2.30, rechnungen.get(0).getEndBetrag(), 0.001);
        assertEquals(-0.01, rechnungen.get(0).getRundung(), 0.001);
    }

    @Test
    void berechneRechnungen_LadestationRechnung_EnthaeltStellerUndZahlungsfrist() {
        LocalDate von = LocalDate.of(2024, 1, 1);
        LocalDate bis = LocalDate.of(2024, 3, 31);

        Einheit ladestation = ladestation();
        Mieter nutzer = mieter(50L, "Nutzer ohne Wohnung");
        Tarif ladestrom = ladestromTarif("Ladestrom", "0.35000");

        when(einheitRepository.findById(900L)).thenReturn(Optional.of(ladestation));
        when(mieterService.getMieterForQuartal(eq(900L), any(), any())).thenReturn(List.of(nutzer));
        when(mieterService.getEinheitIds(50L)).thenReturn(List.of(900L));
        when(tarifpositionService.getFuerRechnung(anyCollection(), any(), any()))
            .thenReturn(List.of(tarifposition(ladestrom, ladestation, 2024, 1, "10.000")));

        RechnungDTO rechnung = rechnungService.berechneRechnungen(List.of(900L), von, bis).get(0);

        assertEquals("30 Tage", rechnung.getZahlungsfrist());
        assertEquals("CH12 3456 7890 1234", rechnung.getIban());
        assertEquals("Test AG", rechnung.getStellerName());
        assertEquals("Teststrasse 1", rechnung.getStellerStrasse());
        assertEquals("3000 Bern", rechnung.getStellerPlzOrt());
        assertEquals(LocalDate.now(), rechnung.getErstellungsdatum());
    }

    @Test
    void berechneRechnungen_MieterMitZweiLadestationen_BeidePositionenAlsEigeneZeile() {
        LocalDate von = LocalDate.of(2024, 1, 1);
        LocalDate bis = LocalDate.of(2024, 3, 31);

        Mieter mitWohnung = mieter(51L, "Mieter mit zwei Ladestationen");
        Tarif ladestrom = ladestromTarif("Ladestrom", "0.35000");

        Tarifposition ersteStation = tarifposition(ladestrom, ladestation(), 2024, 1, "100.000");
        ersteStation.setQuellReferenz("RFID-900");
        Tarifposition zweiteStation = tarifposition(ladestrom, ladestationZwei(), 2024, 1, "200.000");
        zweiteStation.setQuellReferenz("RFID-901");

        doNothing().when(tarifService).validateTarifAbdeckung(von, bis);
        when(einheitRepository.findById(1L)).thenReturn(Optional.of(consumer));
        when(mieterService.getMieterForQuartal(eq(1L), any(), any())).thenReturn(List.of(mitWohnung));
        when(mieterService.getEinheitIds(51L)).thenReturn(List.of(1L, 900L, 901L));
        when(tarifService.getTarifeForZeitraum(eq(TarifTyp.ZEV), any(), any()))
            .thenReturn(Collections.singletonList(zevTarif2024));
        when(tarifService.getTarifeForZeitraum(eq(TarifTyp.VNB), any(), any()))
            .thenReturn(Collections.singletonList(vnbTarif2024));
        when(tarifService.getTarifeForZeitraum(eq(TarifTyp.GRUNDGEBUEHR), any(), any()))
            .thenReturn(Collections.emptyList());
        when(messwerteRepository.sumZevCalculatedByEinheitAndZeitBetween(any(), any(), any()))
            .thenReturn(100.0);
        when(messwerteRepository.sumTotalByEinheitAndZeitBetween(any(), any(), any()))
            .thenReturn(150.0);
        // Die Rechnung fragt die Positionen ALLER Einheiten des Mieters ab
        when(tarifpositionService.getFuerRechnung(eq(List.of(1L, 900L, 901L)), any(), any()))
            .thenReturn(List.of(ersteStation, zweiteStation));

        List<RechnungDTO> rechnungen = rechnungService.berechneRechnungen(List.of(1L), von, bis);

        assertEquals(1, rechnungen.size());
        List<TarifZeileDTO> ladestromZeilen = rechnungen.get(0).getTarifZeilen().stream()
            .filter(z -> z.getTyp() == TarifTyp.LADESTROM)
            .toList();

        assertEquals(2, ladestromZeilen.size());
        assertEquals("Ladestrom (RFID-900)", ladestromZeilen.get(0).getBezeichnung());
        assertEquals("Ladestrom (RFID-901)", ladestromZeilen.get(1).getBezeichnung());
        assertEquals(100.0, ladestromZeilen.get(0).getMenge(), 0.001);
        assertEquals(200.0, ladestromZeilen.get(1).getMenge(), 0.001);
        // ZEV 20.00 + VNB 17.00 + 35.00 + 70.00
        assertEquals(142.0, rechnungen.get(0).getTotalBetrag(), 0.01);
    }

    @Test
    void berechneRechnungen_BilanzTypen_ErzeugenKeineRechnung() {
        LocalDate von = LocalDate.of(2024, 1, 1);
        LocalDate bis = LocalDate.of(2024, 3, 31);

        Einheit bezug = new Einheit("Netzanschluss Bezug", EinheitTyp.BEZUG);
        bezug.setId(10L);
        Einheit ruecklieferung = new Einheit("Netzanschluss Ruecklieferung", EinheitTyp.RUECKLIEFERUNG);
        ruecklieferung.setId(11L);

        when(einheitRepository.findById(10L)).thenReturn(Optional.of(bezug));
        when(einheitRepository.findById(11L)).thenReturn(Optional.of(ruecklieferung));

        List<RechnungDTO> rechnungen = rechnungService.berechneRechnungen(List.of(10L, 11L), von, bis);

        assertTrue(rechnungen.isEmpty());
        verify(mieterService, never()).getMieterForQuartal(any(), any(), any());
        verify(tarifpositionService, never()).getFuerRechnung(any(), any(), any());
    }

    @Test
    void berechneRechnungen_UnbekannteEinheitId_WirdUebersprungen() {
        LocalDate von = LocalDate.of(2024, 1, 1);
        LocalDate bis = LocalDate.of(2024, 3, 31);

        when(einheitRepository.findById(999L)).thenReturn(Optional.empty());

        List<RechnungDTO> rechnungen = rechnungService.berechneRechnungen(List.of(999L), von, bis);

        assertTrue(rechnungen.isEmpty());
        verify(tarifService, never()).validateTarifAbdeckung(any(), any());
    }
}
