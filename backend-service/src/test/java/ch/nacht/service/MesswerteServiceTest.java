package ch.nacht.service;

import ch.nacht.entity.Einheit;
import ch.nacht.entity.EinheitTyp;
import ch.nacht.entity.Messwerte;
import ch.nacht.repository.EinheitRepository;
import ch.nacht.repository.MesswerteRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MesswerteServiceTest {

    @Mock
    private MesswerteRepository messwerteRepository;

    @Mock
    private EinheitRepository einheitRepository;

    @Mock
    private OrganizationContextService organizationContextService;

    @Mock
    private HibernateFilterService hibernateFilterService;

    @InjectMocks
    private MesswerteService messwerteService;

    private Einheit consumerEinheit;
    private Einheit producerEinheit;
    private UUID testOrgId;

    @BeforeEach
    void setUp() {
        testOrgId = UUID.randomUUID();

        consumerEinheit = new Einheit("Wohnung A", EinheitTyp.CONSUMER);
        consumerEinheit.setId(1L);
        consumerEinheit.setOrgId(testOrgId);

        producerEinheit = new Einheit("Solaranlage", EinheitTyp.PRODUCER);
        producerEinheit.setId(2L);
        producerEinheit.setOrgId(testOrgId);
    }

    // ==================== processCsvUpload Tests ====================

    @Test
    void processCsvUpload_ValidCsv_ReturnsSuccess() throws Exception {
        String csvContent = "Time,Total,ZEV\n00:00,1.5,0.8\n00:15,2.0,1.2\n";
        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", csvContent.getBytes());

        when(einheitRepository.findById(1L)).thenReturn(Optional.of(consumerEinheit));
        when(organizationContextService.getCurrentOrgId()).thenReturn(testOrgId);
        when(messwerteRepository.findByEinheitAndZeitBetween(any(), any(), any()))
            .thenReturn(Collections.emptyList());

        Map<String, Object> result = messwerteService.processCsvUpload(file, 1L, "2024-01-15");

        assertEquals("success", result.get("status"));
        assertEquals(2, result.get("count"));
        assertEquals(1L, result.get("einheitId"));
        assertEquals("Wohnung A", result.get("einheitName"));
        verify(messwerteRepository).saveAll(anyList());
    }

    @Test
    void processCsvUpload_EinheitNotFound_ThrowsException() {
        String csvContent = "Time,Total,ZEV\n00:00,1.5,0.8\n";
        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", csvContent.getBytes());

        when(einheitRepository.findById(999L)).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(
            RuntimeException.class,
            () -> messwerteService.processCsvUpload(file, 999L, "2024-01-15")
        );

        assertTrue(exception.getMessage().contains("Einheit not found"));
    }

    @Test
    void processCsvUpload_SemicolonDelimiter_ParsesCorrectly() throws Exception {
        String csvContent = "Time;Total;ZEV\n00:00;3.5;1.8\n";
        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", csvContent.getBytes());

        when(einheitRepository.findById(1L)).thenReturn(Optional.of(consumerEinheit));
        when(organizationContextService.getCurrentOrgId()).thenReturn(testOrgId);
        when(messwerteRepository.findByEinheitAndZeitBetween(any(), any(), any()))
            .thenReturn(Collections.emptyList());

        Map<String, Object> result = messwerteService.processCsvUpload(file, 1L, "2024-01-15");

        assertEquals("success", result.get("status"));
        assertEquals(1, result.get("count"));
    }

    @Test
    void processCsvUpload_ExistingData_DeletesBeforeSave() throws Exception {
        String csvContent = "Time,Total,ZEV\n00:00,1.5,0.8\n";
        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", csvContent.getBytes());

        Messwerte existingMesswert = new Messwerte(
            LocalDateTime.of(2024, 1, 15, 0, 0), 1.0, 0.5, consumerEinheit);
        List<Messwerte> existingData = List.of(existingMesswert);

        when(einheitRepository.findById(1L)).thenReturn(Optional.of(consumerEinheit));
        when(organizationContextService.getCurrentOrgId()).thenReturn(testOrgId);
        when(messwerteRepository.findByEinheitAndZeitBetween(any(), any(), any()))
            .thenReturn(existingData);

        messwerteService.processCsvUpload(file, 1L, "2024-01-15");

        verify(messwerteRepository).deleteAll(existingData);
        verify(messwerteRepository).saveAll(anyList());
    }

    @Test
    void processCsvUpload_SkipsInvalidLines() throws Exception {
        String csvContent = "Time,Total,ZEV\n00:00,1.5,0.8\ninvalid_line\n00:15,2.0,1.2\n";
        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", csvContent.getBytes());

        when(einheitRepository.findById(1L)).thenReturn(Optional.of(consumerEinheit));
        when(organizationContextService.getCurrentOrgId()).thenReturn(testOrgId);
        when(messwerteRepository.findByEinheitAndZeitBetween(any(), any(), any()))
            .thenReturn(Collections.emptyList());

        Map<String, Object> result = messwerteService.processCsvUpload(file, 1L, "2024-01-15");

        assertEquals(2, result.get("count"));
    }

    @Test
    void processCsvUpload_SetsOrgIdOnMesswerte() throws Exception {
        String csvContent = "Time,Total,ZEV\n00:00,1.5,0.8\n";
        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", csvContent.getBytes());

        when(einheitRepository.findById(1L)).thenReturn(Optional.of(consumerEinheit));
        when(organizationContextService.getCurrentOrgId()).thenReturn(testOrgId);
        when(messwerteRepository.findByEinheitAndZeitBetween(any(), any(), any()))
            .thenReturn(Collections.emptyList());

        messwerteService.processCsvUpload(file, 1L, "2024-01-15");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Messwerte>> captor = ArgumentCaptor.forClass(List.class);
        verify(messwerteRepository).saveAll(captor.capture());

        List<Messwerte> savedMesswerte = captor.getValue();
        assertEquals(1, savedMesswerte.size());
        assertEquals(testOrgId, savedMesswerte.get(0).getOrgId());
    }

    @Test
    void processCsvUpload_TimestampsIncrement15Min() throws Exception {
        String csvContent = "Time,Total,ZEV\n00:00,1.0,0.5\n00:15,2.0,1.0\n00:30,3.0,1.5\n";
        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", csvContent.getBytes());

        when(einheitRepository.findById(1L)).thenReturn(Optional.of(consumerEinheit));
        when(organizationContextService.getCurrentOrgId()).thenReturn(testOrgId);
        when(messwerteRepository.findByEinheitAndZeitBetween(any(), any(), any()))
            .thenReturn(Collections.emptyList());

        messwerteService.processCsvUpload(file, 1L, "2024-01-15");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Messwerte>> captor = ArgumentCaptor.forClass(List.class);
        verify(messwerteRepository).saveAll(captor.capture());

        List<Messwerte> saved = captor.getValue();
        assertEquals(3, saved.size());
        assertEquals(LocalDateTime.of(2024, 1, 15, 0, 0), saved.get(0).getZeit());
        assertEquals(LocalDateTime.of(2024, 1, 15, 0, 15), saved.get(1).getZeit());
        assertEquals(LocalDateTime.of(2024, 1, 15, 0, 30), saved.get(2).getZeit());
    }

    // ==================== getMesswerteByEinheit Tests ====================

    @Test
    void getMesswerteByEinheit_ReturnsFormattedData() {
        LocalDate dateFrom = LocalDate.of(2024, 1, 1);
        LocalDate dateTo = LocalDate.of(2024, 1, 31);
        LocalDateTime zeit = LocalDateTime.of(2024, 1, 15, 12, 0);

        Messwerte messwert = new Messwerte(zeit, 5.0, 2.5, consumerEinheit);
        messwert.setZevCalculated(1.8);

        when(einheitRepository.findById(1L)).thenReturn(Optional.of(consumerEinheit));
        when(messwerteRepository.findByEinheitAndZeitBetween(eq(consumerEinheit), any(), any()))
            .thenReturn(List.of(messwert));

        List<Map<String, Object>> result = messwerteService.getMesswerteByEinheit(1L, dateFrom, dateTo);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(zeit.toString(), result.get(0).get("zeit"));
        assertEquals(5.0, result.get(0).get("total"));
        assertEquals(1.8, result.get(0).get("zevCalculated"));
        verify(hibernateFilterService).enableOrgFilter();
    }

    @Test
    void getMesswerteByEinheit_EinheitNotFound_ThrowsException() {
        when(einheitRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(
            RuntimeException.class,
            () -> messwerteService.getMesswerteByEinheit(999L, LocalDate.now(), LocalDate.now())
        );
    }

    @Test
    void getMesswerteByEinheit_NullValues_DefaultsToZero() {
        LocalDate dateFrom = LocalDate.of(2024, 1, 1);
        LocalDate dateTo = LocalDate.of(2024, 1, 31);

        Messwerte messwert = new Messwerte(LocalDateTime.of(2024, 1, 15, 12, 0), null, null, consumerEinheit);
        messwert.setZevCalculated(null);

        when(einheitRepository.findById(1L)).thenReturn(Optional.of(consumerEinheit));
        when(messwerteRepository.findByEinheitAndZeitBetween(eq(consumerEinheit), any(), any()))
            .thenReturn(List.of(messwert));

        List<Map<String, Object>> result = messwerteService.getMesswerteByEinheit(1L, dateFrom, dateTo);

        assertEquals(0.0, result.get(0).get("total"));
        assertEquals(0.0, result.get(0).get("zevCalculated"));
    }

    @Test
    void getMesswerteByEinheit_EmptyResult_ReturnsEmptyList() {
        when(einheitRepository.findById(1L)).thenReturn(Optional.of(consumerEinheit));
        when(messwerteRepository.findByEinheitAndZeitBetween(eq(consumerEinheit), any(), any()))
            .thenReturn(Collections.emptyList());

        List<Map<String, Object>> result = messwerteService.getMesswerteByEinheit(
            1L, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31));

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ==================== calculateSolarDistribution Tests ====================

    @Test
    void calculateSolarDistribution_NoTimestamps_ReturnsZeros() {
        LocalDateTime dateFrom = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime dateTo = LocalDateTime.of(2024, 1, 31, 23, 59, 59);

        when(messwerteRepository.findDistinctZeitBetween(dateFrom, dateTo))
            .thenReturn(Collections.emptyList());

        MesswerteService.CalculationResult result = messwerteService.calculateSolarDistribution(
            dateFrom, dateTo, "EQUAL_SHARE");

        assertEquals(0, result.getProcessedTimestamps());
        assertEquals(0, result.getProcessedRecords());
        assertEquals(0.0, result.getTotalSolarProduced());
        assertEquals(0.0, result.getTotalDistributed());
    }

    @Test
    void calculateSolarDistribution_NoProducers_SkipsTimestamp() {
        LocalDateTime dateFrom = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime dateTo = LocalDateTime.of(2024, 1, 31, 23, 59, 59);
        LocalDateTime zeit = LocalDateTime.of(2024, 1, 15, 12, 0);

        when(messwerteRepository.findDistinctZeitBetween(dateFrom, dateTo))
            .thenReturn(List.of(zeit));
        when(messwerteRepository.findByZeitAndEinheitTyp(zeit, EinheitTyp.PRODUCER))
            .thenReturn(Collections.emptyList());

        MesswerteService.CalculationResult result = messwerteService.calculateSolarDistribution(
            dateFrom, dateTo, "EQUAL_SHARE");

        assertEquals(0, result.getProcessedTimestamps());
        assertEquals(0, result.getProcessedRecords());
    }

    @Test
    void calculateSolarDistribution_NoConsumers_SkipsDistribution() {
        LocalDateTime dateFrom = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime dateTo = LocalDateTime.of(2024, 1, 31, 23, 59, 59);
        LocalDateTime zeit = LocalDateTime.of(2024, 1, 15, 12, 0);

        Messwerte producer = new Messwerte(zeit, -5.0, 0.0, producerEinheit);

        when(messwerteRepository.findDistinctZeitBetween(dateFrom, dateTo))
            .thenReturn(List.of(zeit));
        when(messwerteRepository.findByZeitAndEinheitTyp(zeit, EinheitTyp.PRODUCER))
            .thenReturn(List.of(producer));
        when(messwerteRepository.findByZeitAndEinheitTyp(zeit, EinheitTyp.CONSUMER))
            .thenReturn(Collections.emptyList());

        MesswerteService.CalculationResult result = messwerteService.calculateSolarDistribution(
            dateFrom, dateTo, "EQUAL_SHARE");

        assertEquals(0, result.getProcessedTimestamps());
    }

    @Test
    void calculateSolarDistribution_WithProducersAndConsumers_DistributesSolar() {
        LocalDateTime dateFrom = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime dateTo = LocalDateTime.of(2024, 1, 31, 23, 59, 59);
        LocalDateTime zeit = LocalDateTime.of(2024, 1, 15, 12, 0);

        // Producer with negative total (production)
        Messwerte producer = new Messwerte(zeit, -10.0, 0.0, producerEinheit);

        // Consumer
        Messwerte consumer = new Messwerte(zeit, 5.0, 0.0, consumerEinheit);
        consumer.setId(1L);

        when(messwerteRepository.findDistinctZeitBetween(dateFrom, dateTo))
            .thenReturn(List.of(zeit));
        when(messwerteRepository.findByZeitAndEinheitTyp(zeit, EinheitTyp.PRODUCER))
            .thenReturn(List.of(producer));
        when(messwerteRepository.findByZeitAndEinheitTyp(zeit, EinheitTyp.CONSUMER))
            .thenReturn(List.of(consumer));
        when(messwerteRepository.save(any(Messwerte.class))).thenReturn(consumer);

        MesswerteService.CalculationResult result = messwerteService.calculateSolarDistribution(
            dateFrom, dateTo, "EQUAL_SHARE");

        assertEquals(1, result.getProcessedTimestamps());
        assertEquals(1, result.getProcessedRecords());
        assertTrue(result.getTotalSolarProduced() > 0);
        verify(messwerteRepository).save(any(Messwerte.class));
    }

    @Test
    void calculateSolarDistribution_PositiveProducer_NoDistribution() {
        LocalDateTime dateFrom = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime dateTo = LocalDateTime.of(2024, 1, 31, 23, 59, 59);
        LocalDateTime zeit = LocalDateTime.of(2024, 1, 15, 12, 0);

        // Positive total means no net production
        Messwerte producer = new Messwerte(zeit, 5.0, 0.0, producerEinheit);

        Messwerte consumer = new Messwerte(zeit, 3.0, 0.0, consumerEinheit);

        when(messwerteRepository.findDistinctZeitBetween(dateFrom, dateTo))
            .thenReturn(List.of(zeit));
        when(messwerteRepository.findByZeitAndEinheitTyp(zeit, EinheitTyp.PRODUCER))
            .thenReturn(List.of(producer));
        when(messwerteRepository.findByZeitAndEinheitTyp(zeit, EinheitTyp.CONSUMER))
            .thenReturn(List.of(consumer));
        when(messwerteRepository.save(any(Messwerte.class))).thenReturn(consumer);

        MesswerteService.CalculationResult result = messwerteService.calculateSolarDistribution(
            dateFrom, dateTo, "EQUAL_SHARE");

        assertEquals(1, result.getProcessedTimestamps());
        assertEquals(0.0, result.getTotalSolarProduced());
    }

    @Test
    void calculateSolarDistribution_ProportionalAlgorithm_UsesProportional() {
        LocalDateTime dateFrom = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime dateTo = LocalDateTime.of(2024, 1, 31, 23, 59, 59);
        LocalDateTime zeit = LocalDateTime.of(2024, 1, 15, 12, 0);

        Messwerte producer = new Messwerte(zeit, -10.0, 0.0, producerEinheit);
        Messwerte consumer = new Messwerte(zeit, 5.0, 0.0, consumerEinheit);

        when(messwerteRepository.findDistinctZeitBetween(dateFrom, dateTo))
            .thenReturn(List.of(zeit));
        when(messwerteRepository.findByZeitAndEinheitTyp(zeit, EinheitTyp.PRODUCER))
            .thenReturn(List.of(producer));
        when(messwerteRepository.findByZeitAndEinheitTyp(zeit, EinheitTyp.CONSUMER))
            .thenReturn(List.of(consumer));
        when(messwerteRepository.save(any(Messwerte.class))).thenReturn(consumer);

        MesswerteService.CalculationResult result = messwerteService.calculateSolarDistribution(
            dateFrom, dateTo, "PROPORTIONAL");

        assertEquals(1, result.getProcessedTimestamps());
        assertEquals(1, result.getProcessedRecords());
        verify(messwerteRepository).save(any(Messwerte.class));
    }

    @Test
    void calculateSolarDistribution_ReturnsCorrectDateRange() {
        LocalDateTime dateFrom = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime dateTo = LocalDateTime.of(2024, 1, 31, 23, 59, 59);

        when(messwerteRepository.findDistinctZeitBetween(dateFrom, dateTo))
            .thenReturn(Collections.emptyList());

        MesswerteService.CalculationResult result = messwerteService.calculateSolarDistribution(
            dateFrom, dateTo, "EQUAL_SHARE");

        assertEquals(dateFrom, result.getDateFrom());
        assertEquals(dateTo, result.getDateTo());
    }

    // ==================== CalculationResult Tests ====================

    @Test
    void calculationResult_ConstructorAndGetters() {
        LocalDateTime from = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2024, 1, 31, 23, 59, 59);

        MesswerteService.CalculationResult result = new MesswerteService.CalculationResult(
            10, 50, from, to, 100.5, 95.2);

        assertEquals(10, result.getProcessedTimestamps());
        assertEquals(50, result.getProcessedRecords());
        assertEquals(from, result.getDateFrom());
        assertEquals(to, result.getDateTo());
        assertEquals(100.5, result.getTotalSolarProduced());
        assertEquals(95.2, result.getTotalDistributed());
    }
}
