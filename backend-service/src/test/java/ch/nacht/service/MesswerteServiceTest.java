package ch.nacht.service;

import ch.nacht.entity.Einheit;
import ch.nacht.entity.EinheitTyp;
import ch.nacht.entity.Messwerte;
import ch.nacht.entity.Quelle;
import ch.nacht.entity.Verteilmodus;
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

    @Mock
    private CalculationProgressService calculationProgressService;

    @Mock
    private EinstellungenService einstellungenService;

    @InjectMocks
    private MesswerteService messwerteService;

    private Einheit consumerEinheit;
    private Einheit producerEinheit;
    private Einheit bezugEinheit;
    private Einheit ruecklieferungEinheit;
    private Long testOrgId;

    @BeforeEach
    void setUp() {
        testOrgId = 1L;

        consumerEinheit = new Einheit("Wohnung A", EinheitTyp.CONSUMER);
        consumerEinheit.setId(1L);
        consumerEinheit.setOrgId(testOrgId);

        producerEinheit = new Einheit("Solaranlage", EinheitTyp.PRODUCER);
        producerEinheit.setId(2L);
        producerEinheit.setOrgId(testOrgId);

        bezugEinheit = new Einheit("Bezug", EinheitTyp.BEZUG);
        bezugEinheit.setId(10L);
        bezugEinheit.setOrgId(testOrgId);

        ruecklieferungEinheit = new Einheit("Rücklieferung", EinheitTyp.RUECKLIEFERUNG);
        ruecklieferungEinheit.setId(11L);
        ruecklieferungEinheit.setOrgId(testOrgId);

        // Default-Verteilmodus (heutiges Verhalten) für die bestehenden Verteilungs-Tests;
        // lenient, da nicht jeder Test die Verteilung auslöst.
        lenient().when(einstellungenService.getVerteilmodus(any()))
            .thenReturn(Verteilmodus.PRODUCER_MESSUNG);
    }

    // ==================== processBilanzCsvUpload Tests ====================

    /** Gültige Bilanz-Kopfzeile (Bezug-/Rücklieferung-Spaltentitel, positionsbasiert). */
    private static final String BILANZ_HEADER =
        "category;vZEV Bezug von VNB (Total 315.54 kWh);vZEV Rücklieferung an VNB (Total -454.06 kWh)";

    /** date-Parameter des Bilanz-Uploads (Startzeitpunkt, wie beim Consumer-Upload). */
    private static final String BILANZ_DATE = "2026-06-01";

    private void stubBilanzEinheiten() {
        when(einheitRepository.findFirstByTyp(EinheitTyp.BEZUG)).thenReturn(Optional.of(bezugEinheit));
        when(einheitRepository.findFirstByTyp(EinheitTyp.RUECKLIEFERUNG))
            .thenReturn(Optional.of(ruecklieferungEinheit));
    }

    private MockMultipartFile bilanzFile(String body) {
        return new MockMultipartFile("file", "2026-06-Bilanz.csv", "text/csv",
            (BILANZ_HEADER + "\n" + body).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void processBilanzCsvUpload_ValidCsv_SavesBezugAndRuecklieferung() throws Exception {
        // Eine Bezug-Zeile (positiv), eine Rücklieferung-Zeile (negativ)
        MockMultipartFile file = bilanzFile(
            "Mon Jun 01 2026;1.5;\n" +
            "Mon Jun 01 2026;;-2.0\n");

        stubBilanzEinheiten();
        when(organizationContextService.getCurrentOrgId()).thenReturn(testOrgId);
        when(messwerteRepository.findByEinheitAndZeitBetween(any(), any(), any()))
            .thenReturn(Collections.emptyList());

        Map<String, Object> result = messwerteService.processBilanzCsvUpload(file, BILANZ_DATE);

        assertEquals("success", result.get("status"));
        assertEquals(2, result.get("count"));
        assertEquals("Bezug", result.get("bezugEinheit"));
        assertEquals("Rücklieferung", result.get("ruecklieferungEinheit"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Messwerte>> captor = ArgumentCaptor.forClass(List.class);
        verify(messwerteRepository).saveAll(captor.capture());
        List<Messwerte> saved = captor.getValue();
        assertEquals(2, saved.size());

        Messwerte bezug = saved.get(0);
        assertEquals(bezugEinheit, bezug.getEinheit());
        assertEquals(1.5, bezug.getTotal(), 1e-9);
        assertEquals(0.0, bezug.getZev(), 1e-9);
        assertEquals(Quelle.CSV, bezug.getQuelle());

        Messwerte rueck = saved.get(1);
        assertEquals(ruecklieferungEinheit, rueck.getEinheit());
        assertEquals(-2.0, rueck.getTotal(), 1e-9);
        assertEquals(0.0, rueck.getZev(), 1e-9);
        assertEquals(Quelle.CSV, rueck.getQuelle());

        verify(hibernateFilterService).enableOrgFilter();
    }

    @Test
    void processBilanzCsvUpload_TimestampFromDateParamAndSlot() throws Exception {
        // Zeitstempel = date-Parameter (00:00) + fortlaufend +15 min je Zeile (wie Consumer-Upload)
        MockMultipartFile file = bilanzFile(
            "Mon Jun 01 2026;1.0;\n" +
            "Mon Jun 01 2026;2.0;\n" +
            "Mon Jun 01 2026;;-3.0\n");

        stubBilanzEinheiten();
        when(organizationContextService.getCurrentOrgId()).thenReturn(testOrgId);
        when(messwerteRepository.findByEinheitAndZeitBetween(any(), any(), any()))
            .thenReturn(Collections.emptyList());

        messwerteService.processBilanzCsvUpload(file, BILANZ_DATE);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Messwerte>> captor = ArgumentCaptor.forClass(List.class);
        verify(messwerteRepository).saveAll(captor.capture());
        List<Messwerte> saved = captor.getValue();

        assertEquals(3, saved.size());
        assertEquals(LocalDateTime.of(2026, 6, 1, 0, 0), saved.get(0).getZeit());
        assertEquals(LocalDateTime.of(2026, 6, 1, 0, 15), saved.get(1).getZeit());
        assertEquals(LocalDateTime.of(2026, 6, 1, 0, 30), saved.get(2).getZeit());
    }

    @Test
    void processBilanzCsvUpload_IgnoresCategoryDate_ContinuousSlots() throws Exception {
        // Die category-Spalte (auch ein Tageswechsel) wird ignoriert; der Slot läuft
        // fortlaufend ab dem date-Parameter weiter (identisch zum Consumer-Upload).
        MockMultipartFile file = bilanzFile(
            "Mon Jun 01 2026;1.0;\n" +
            "Mon Jun 01 2026;2.0;\n" +
            "Tue Jun 02 2026;3.0;\n");

        stubBilanzEinheiten();
        when(organizationContextService.getCurrentOrgId()).thenReturn(testOrgId);
        when(messwerteRepository.findByEinheitAndZeitBetween(any(), any(), any()))
            .thenReturn(Collections.emptyList());

        messwerteService.processBilanzCsvUpload(file, BILANZ_DATE);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Messwerte>> captor = ArgumentCaptor.forClass(List.class);
        verify(messwerteRepository).saveAll(captor.capture());
        List<Messwerte> saved = captor.getValue();

        assertEquals(LocalDateTime.of(2026, 6, 1, 0, 0), saved.get(0).getZeit());
        assertEquals(LocalDateTime.of(2026, 6, 1, 0, 15), saved.get(1).getZeit());
        assertEquals(LocalDateTime.of(2026, 6, 1, 0, 30), saved.get(2).getZeit());
    }

    @Test
    void processBilanzCsvUpload_ExistingData_DeletesBothEinheitenForMonth() throws Exception {
        MockMultipartFile file = bilanzFile("Mon Jun 01 2026;1.5;\n");

        Messwerte existingBezug = new Messwerte(
            LocalDateTime.of(2026, 6, 5, 0, 0), 1.0, 0.0, bezugEinheit);
        Messwerte existingRueck = new Messwerte(
            LocalDateTime.of(2026, 6, 5, 0, 0), -1.0, 0.0, ruecklieferungEinheit);

        stubBilanzEinheiten();
        when(organizationContextService.getCurrentOrgId()).thenReturn(testOrgId);
        when(messwerteRepository.findByEinheitAndZeitBetween(eq(bezugEinheit), any(), any()))
            .thenReturn(List.of(existingBezug));
        when(messwerteRepository.findByEinheitAndZeitBetween(eq(ruecklieferungEinheit), any(), any()))
            .thenReturn(List.of(existingRueck));

        messwerteService.processBilanzCsvUpload(file, BILANZ_DATE);

        verify(messwerteRepository).deleteAll(List.of(existingBezug));
        verify(messwerteRepository).deleteAll(List.of(existingRueck));
        verify(messwerteRepository).saveAll(anyList());

        // Overwrite-Fenster ist der ganze Monat der ersten Datenzeile (Juni 2026)
        ArgumentCaptor<LocalDateTime> vonCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> bisCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(messwerteRepository, times(2))
            .findByEinheitAndZeitBetween(any(), vonCaptor.capture(), bisCaptor.capture());
        assertEquals(LocalDateTime.of(2026, 6, 1, 0, 0), vonCaptor.getAllValues().get(0));
        assertEquals(LocalDateTime.of(2026, 6, 30, 23, 59, 59), bisCaptor.getAllValues().get(0));
    }

    @Test
    void processBilanzCsvUpload_MissingBezugEinheit_ThrowsBilanzEinheitFehlt() {
        MockMultipartFile file = bilanzFile("Mon Jun 01 2026;1.5;\n");

        when(einheitRepository.findFirstByTyp(EinheitTyp.BEZUG)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> messwerteService.processBilanzCsvUpload(file, BILANZ_DATE));
        assertEquals("BILANZ_EINHEIT_FEHLT", ex.getMessage());
        verify(messwerteRepository, never()).saveAll(anyList());
    }

    @Test
    void processBilanzCsvUpload_MissingRuecklieferungEinheit_ThrowsBilanzEinheitFehlt() {
        MockMultipartFile file = bilanzFile("Mon Jun 01 2026;1.5;\n");

        when(einheitRepository.findFirstByTyp(EinheitTyp.BEZUG)).thenReturn(Optional.of(bezugEinheit));
        when(einheitRepository.findFirstByTyp(EinheitTyp.RUECKLIEFERUNG)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> messwerteService.processBilanzCsvUpload(file, BILANZ_DATE));
        assertEquals("BILANZ_EINHEIT_FEHLT", ex.getMessage());
        verify(messwerteRepository, never()).saveAll(anyList());
    }

    @Test
    void processBilanzCsvUpload_HeaderTitleMismatch_ThrowsBilanzCsvUngueltig() {
        // Spaltentitel passen nicht zur Position (Bezug/Rücklieferung vertauscht/fehlend)
        MockMultipartFile file = new MockMultipartFile("file", "2026-06-Bilanz.csv", "text/csv",
            ("category;Spalte A;Spalte B\nMon Jun 01 2026;1.5;\n")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));

        stubBilanzEinheiten();
        when(organizationContextService.getCurrentOrgId()).thenReturn(testOrgId);

        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> messwerteService.processBilanzCsvUpload(file, BILANZ_DATE));
        assertEquals("BILANZ_CSV_UNGUELTIG", ex.getMessage());
        verify(messwerteRepository, never()).saveAll(anyList());
    }

    @Test
    void processBilanzCsvUpload_EmptyFile_ThrowsBilanzCsvUngueltig() {
        MockMultipartFile file = new MockMultipartFile("file", "2026-06-Bilanz.csv", "text/csv",
            new byte[0]);

        stubBilanzEinheiten();
        when(organizationContextService.getCurrentOrgId()).thenReturn(testOrgId);

        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> messwerteService.processBilanzCsvUpload(file, BILANZ_DATE));
        assertEquals("BILANZ_CSV_UNGUELTIG", ex.getMessage());
    }

    @Test
    void processBilanzCsvUpload_OnlyHeader_ThrowsBilanzCsvUngueltig() {
        MockMultipartFile file = bilanzFile("");

        stubBilanzEinheiten();
        when(organizationContextService.getCurrentOrgId()).thenReturn(testOrgId);

        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> messwerteService.processBilanzCsvUpload(file, BILANZ_DATE));
        assertEquals("BILANZ_CSV_UNGUELTIG", ex.getMessage());
        verify(messwerteRepository, never()).saveAll(anyList());
    }

    @Test
    void processBilanzCsvUpload_BothColumnsFilled_SkipsLine() throws Exception {
        // Zeile mit beiden Spalten gefüllt wird übersprungen; nur die valide Zeile bleibt
        MockMultipartFile file = bilanzFile(
            "Mon Jun 01 2026;1.5;-2.0\n" +
            "Mon Jun 01 2026;3.0;\n");

        stubBilanzEinheiten();
        when(organizationContextService.getCurrentOrgId()).thenReturn(testOrgId);
        when(messwerteRepository.findByEinheitAndZeitBetween(any(), any(), any()))
            .thenReturn(Collections.emptyList());

        Map<String, Object> result = messwerteService.processBilanzCsvUpload(file, BILANZ_DATE);

        assertEquals(1, result.get("count"));
    }

    @Test
    void processBilanzCsvUpload_NoColumnFilled_SkipsLine() throws Exception {
        MockMultipartFile file = bilanzFile(
            "Mon Jun 01 2026;;\n" +
            "Mon Jun 01 2026;3.0;\n");

        stubBilanzEinheiten();
        when(organizationContextService.getCurrentOrgId()).thenReturn(testOrgId);
        when(messwerteRepository.findByEinheitAndZeitBetween(any(), any(), any()))
            .thenReturn(Collections.emptyList());

        Map<String, Object> result = messwerteService.processBilanzCsvUpload(file, BILANZ_DATE);

        assertEquals(1, result.get("count"));
    }

    @Test
    void processBilanzCsvUpload_NonNumericValue_SkipsLine() throws Exception {
        MockMultipartFile file = bilanzFile(
            "Mon Jun 01 2026;abc;\n" +
            "Mon Jun 01 2026;3.0;\n");

        stubBilanzEinheiten();
        when(organizationContextService.getCurrentOrgId()).thenReturn(testOrgId);
        when(messwerteRepository.findByEinheitAndZeitBetween(any(), any(), any()))
            .thenReturn(Collections.emptyList());

        Map<String, Object> result = messwerteService.processBilanzCsvUpload(file, BILANZ_DATE);

        assertEquals(1, result.get("count"));
    }

    @Test
    void processBilanzCsvUpload_UnparsableCategory_StillSaved() throws Exception {
        // Die category-Spalte wird backendseitig nicht ausgewertet -> auch eine Zeile mit
        // unparsbarem "Datum" wird gespeichert (Zeitstempel kommt aus dem date-Parameter).
        MockMultipartFile file = bilanzFile(
            "not-a-date;1.5;\n" +
            "Mon Jun 01 2026;3.0;\n");

        stubBilanzEinheiten();
        when(organizationContextService.getCurrentOrgId()).thenReturn(testOrgId);
        when(messwerteRepository.findByEinheitAndZeitBetween(any(), any(), any()))
            .thenReturn(Collections.emptyList());

        Map<String, Object> result = messwerteService.processBilanzCsvUpload(file, BILANZ_DATE);

        assertEquals(2, result.get("count"));
    }

    @Test
    void processBilanzCsvUpload_InvalidDateParam_Throws() {
        // Ungültiger date-Parameter -> LocalDate.parse wirft (wie beim Consumer-Upload);
        // der Controller mappt das auf HTTP 400.
        MockMultipartFile file = bilanzFile("Mon Jun 01 2026;1.5;\n");

        stubBilanzEinheiten();

        assertThrows(Exception.class,
            () -> messwerteService.processBilanzCsvUpload(file, "kein-datum"));
        verify(messwerteRepository, never()).saveAll(anyList());
    }

    @Test
    void processBilanzCsvUpload_SetsOrgIdOnMesswerte() throws Exception {
        MockMultipartFile file = bilanzFile("Mon Jun 01 2026;1.5;\n");

        stubBilanzEinheiten();
        when(organizationContextService.getCurrentOrgId()).thenReturn(testOrgId);
        when(messwerteRepository.findByEinheitAndZeitBetween(any(), any(), any()))
            .thenReturn(Collections.emptyList());

        messwerteService.processBilanzCsvUpload(file, BILANZ_DATE);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Messwerte>> captor = ArgumentCaptor.forClass(List.class);
        verify(messwerteRepository).saveAll(captor.capture());
        assertEquals(testOrgId, captor.getValue().get(0).getOrgId());
    }

    @Test
    void processBilanzCsvUpload_CommaDelimiterAndHeader_ParsesCorrectly() throws Exception {
        // Trennzeichen Komma statt Semikolon
        MockMultipartFile file = new MockMultipartFile("file", "2026-06-Bilanz.csv", "text/csv",
            ("category,Bezug von VNB,Rücklieferung an VNB\nMon Jun 01 2026,1.5,\n")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));

        stubBilanzEinheiten();
        when(organizationContextService.getCurrentOrgId()).thenReturn(testOrgId);
        when(messwerteRepository.findByEinheitAndZeitBetween(any(), any(), any()))
            .thenReturn(Collections.emptyList());

        Map<String, Object> result = messwerteService.processBilanzCsvUpload(file, BILANZ_DATE);

        assertEquals(1, result.get("count"));
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

        when(einheitRepository.findById(1L)).thenReturn(Optional.of(consumerEinheit));
        when(messwerteRepository.findByEinheitAndZeitBetween(eq(consumerEinheit), any(), any()))
            .thenReturn(List.of(messwert));

        List<Map<String, Object>> result = messwerteService.getMesswerteByEinheit(1L, dateFrom, dateTo);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(zeit.toString(), result.get(0).get("zeit"));
        assertEquals(5.0, result.get(0).get("total"));
        assertEquals(2.5, result.get(0).get("zev"));
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

        when(einheitRepository.findById(1L)).thenReturn(Optional.of(consumerEinheit));
        when(messwerteRepository.findByEinheitAndZeitBetween(eq(consumerEinheit), any(), any()))
            .thenReturn(List.of(messwert));

        List<Map<String, Object>> result = messwerteService.getMesswerteByEinheit(1L, dateFrom, dateTo);

        assertEquals(0.0, result.get(0).get("total"));
        assertEquals(0.0, result.get(0).get("zev"));
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

    // ==================== Producer-zev (im ZEV konsumierte Produktion) ====================

    @Test
    void calculateSolarDistribution_MqttProducer_ZevIstVerteilteMenge() {
        LocalDateTime dateFrom = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime dateTo = LocalDateTime.of(2024, 1, 31, 23, 59, 59);
        LocalDateTime zeit = LocalDateTime.of(2024, 1, 15, 12, 0);

        // Produktion (10) übersteigt den Verbrauch (5): verteilt wird nur 5,
        // der Producer-zev muss auf −5 korrigiert werden (Rest = Rücklieferung).
        Messwerte producer = new Messwerte(zeit, -10.0, -10.0, producerEinheit);
        producer.setQuelle(Quelle.MQTT);
        Messwerte consumer = new Messwerte(zeit, 5.0, 0.0, consumerEinheit);

        when(messwerteRepository.findDistinctZeitBetween(dateFrom, dateTo))
            .thenReturn(List.of(zeit));
        when(messwerteRepository.findByZeitAndEinheitTyp(zeit, EinheitTyp.PRODUCER))
            .thenReturn(List.of(producer));
        when(messwerteRepository.findByZeitAndEinheitTyp(zeit, EinheitTyp.CONSUMER))
            .thenReturn(List.of(consumer));
        when(messwerteRepository.save(any(Messwerte.class))).thenAnswer(inv -> inv.getArgument(0));

        messwerteService.calculateSolarDistribution(dateFrom, dateTo, "PROPORTIONAL");

        assertEquals(-5.0, producer.getZev(), 1e-9);
        verify(messwerteRepository).save(producer);
    }

    @Test
    void calculateSolarDistribution_CsvProducer_ZevBleibtGemessenerWert() {
        LocalDateTime dateFrom = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime dateTo = LocalDateTime.of(2024, 1, 31, 23, 59, 59);
        LocalDateTime zeit = LocalDateTime.of(2024, 1, 15, 12, 0);

        // CSV: zev ist der vom Messdienstleister gemessene ZEV-Anteil – bleibt unangetastet.
        Messwerte producer = new Messwerte(zeit, -10.0, -7.0, producerEinheit);
        Messwerte consumer = new Messwerte(zeit, 5.0, 0.0, consumerEinheit);

        when(messwerteRepository.findDistinctZeitBetween(dateFrom, dateTo))
            .thenReturn(List.of(zeit));
        when(messwerteRepository.findByZeitAndEinheitTyp(zeit, EinheitTyp.PRODUCER))
            .thenReturn(List.of(producer));
        when(messwerteRepository.findByZeitAndEinheitTyp(zeit, EinheitTyp.CONSUMER))
            .thenReturn(List.of(consumer));
        when(messwerteRepository.save(any(Messwerte.class))).thenAnswer(inv -> inv.getArgument(0));

        messwerteService.calculateSolarDistribution(dateFrom, dateTo, "PROPORTIONAL");

        assertEquals(-7.0, producer.getZev(), 1e-9);
        verify(messwerteRepository, never()).save(producer);
    }

    @Test
    void calculateSolarDistribution_MqttProducerOhneConsumer_ZevWirdNull() {
        LocalDateTime dateFrom = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime dateTo = LocalDateTime.of(2024, 1, 31, 23, 59, 59);
        LocalDateTime zeit = LocalDateTime.of(2024, 1, 15, 12, 0);

        Messwerte producer = new Messwerte(zeit, -10.0, -10.0, producerEinheit);
        producer.setQuelle(Quelle.MQTT);

        when(messwerteRepository.findDistinctZeitBetween(dateFrom, dateTo))
            .thenReturn(List.of(zeit));
        when(messwerteRepository.findByZeitAndEinheitTyp(zeit, EinheitTyp.PRODUCER))
            .thenReturn(List.of(producer));
        when(messwerteRepository.findByZeitAndEinheitTyp(zeit, EinheitTyp.CONSUMER))
            .thenReturn(Collections.emptyList());
        when(messwerteRepository.save(any(Messwerte.class))).thenAnswer(inv -> inv.getArgument(0));

        messwerteService.calculateSolarDistribution(dateFrom, dateTo, "PROPORTIONAL");

        // Nichts im ZEV konsumiert: alles Rücklieferung
        assertEquals(0.0, producer.getZev(), 1e-9);
        verify(messwerteRepository).save(producer);
    }

    @Test
    void calculateSolarDistribution_MehrereMqttProducer_ProportionaleAufteilung() {
        LocalDateTime dateFrom = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime dateTo = LocalDateTime.of(2024, 1, 31, 23, 59, 59);
        LocalDateTime zeit = LocalDateTime.of(2024, 1, 15, 12, 0);

        Einheit producerEinheit2 = new Einheit("Solaranlage 2", EinheitTyp.PRODUCER);
        producerEinheit2.setId(3L);
        producerEinheit2.setOrgId(testOrgId);

        // Produktion 6 + 4 = 10, Verbrauch 5 → verteilt 5, aufgeteilt 60%/40% → −3 / −2
        Messwerte producer1 = new Messwerte(zeit, -6.0, -6.0, producerEinheit);
        producer1.setQuelle(Quelle.MQTT);
        Messwerte producer2 = new Messwerte(zeit, -4.0, -4.0, producerEinheit2);
        producer2.setQuelle(Quelle.MQTT);
        Messwerte consumer = new Messwerte(zeit, 5.0, 0.0, consumerEinheit);

        when(messwerteRepository.findDistinctZeitBetween(dateFrom, dateTo))
            .thenReturn(List.of(zeit));
        when(messwerteRepository.findByZeitAndEinheitTyp(zeit, EinheitTyp.PRODUCER))
            .thenReturn(List.of(producer1, producer2));
        when(messwerteRepository.findByZeitAndEinheitTyp(zeit, EinheitTyp.CONSUMER))
            .thenReturn(List.of(consumer));
        when(messwerteRepository.save(any(Messwerte.class))).thenAnswer(inv -> inv.getArgument(0));

        messwerteService.calculateSolarDistribution(dateFrom, dateTo, "PROPORTIONAL");

        assertEquals(-3.0, producer1.getZev(), 1e-9);
        assertEquals(-2.0, producer2.getZev(), 1e-9);
    }

    @Test
    void calculateSolarDistribution_MqttSteuergeraet_ZevNull() {
        LocalDateTime dateFrom = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime dateTo = LocalDateTime.of(2024, 1, 31, 23, 59, 59);
        LocalDateTime zeit = LocalDateTime.of(2024, 1, 15, 12, 0);

        Einheit steuergeraetEinheit = new Einheit("Steuergerät", EinheitTyp.PRODUCER);
        steuergeraetEinheit.setId(3L);
        steuergeraetEinheit.setOrgId(testOrgId);

        // Positiver total (Verbrauch des Steuergeräts) produziert nichts → zev = 0
        Messwerte producer = new Messwerte(zeit, -10.0, -10.0, producerEinheit);
        producer.setQuelle(Quelle.MQTT);
        Messwerte steuergeraet = new Messwerte(zeit, 2.0, 2.0, steuergeraetEinheit);
        steuergeraet.setQuelle(Quelle.MQTT);
        Messwerte consumer = new Messwerte(zeit, 5.0, 0.0, consumerEinheit);

        when(messwerteRepository.findDistinctZeitBetween(dateFrom, dateTo))
            .thenReturn(List.of(zeit));
        when(messwerteRepository.findByZeitAndEinheitTyp(zeit, EinheitTyp.PRODUCER))
            .thenReturn(List.of(producer, steuergeraet));
        when(messwerteRepository.findByZeitAndEinheitTyp(zeit, EinheitTyp.CONSUMER))
            .thenReturn(List.of(consumer));
        when(messwerteRepository.save(any(Messwerte.class))).thenAnswer(inv -> inv.getArgument(0));

        messwerteService.calculateSolarDistribution(dateFrom, dateTo, "PROPORTIONAL");

        assertEquals(0.0, steuergeraet.getZev(), 1e-9);
        // Netto-Produktion 10 − 2 = 8 ≥ Verbrauch 5 → verteilte Menge 5 komplett dem Produzenten
        assertEquals(-5.0, producer.getZev(), 1e-9);
    }

    // ==================== Bilanzmodus (Verteilmodus.BILANZ) ====================

    @Test
    void calculateSolarDistribution_Bilanz_VerteiltSMaxConsumerMinusBezug() {
        LocalDateTime dateFrom = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime dateTo = LocalDateTime.of(2024, 1, 31, 23, 59, 59);
        LocalDateTime zeit = LocalDateTime.of(2024, 1, 15, 12, 0);

        when(einstellungenService.getVerteilmodus(any())).thenReturn(Verteilmodus.BILANZ);
        when(einheitRepository.existsByTyp(EinheitTyp.BEZUG)).thenReturn(true);
        when(messwerteRepository.findDistinctZeitBetween(dateFrom, dateTo)).thenReturn(List.of(zeit));

        // ConsumerTotal=10, Bezug=4 → S = max(0, 10 - 4) = 6 (Spec FR-2.1);
        // die zentrale Invariante ist Netz-Anteil-Summe = Bezug (FR-3.3).
        Messwerte consumer = new Messwerte(zeit, 10.0, 0.0, consumerEinheit);
        Messwerte bezug = new Messwerte(zeit, 4.0, 0.0, bezugEinheit);
        when(messwerteRepository.findByZeitAndEinheitTyp(zeit, EinheitTyp.PRODUCER)).thenReturn(Collections.emptyList());
        when(messwerteRepository.findByZeitAndEinheitTyp(zeit, EinheitTyp.CONSUMER)).thenReturn(List.of(consumer));
        when(messwerteRepository.findByZeitAndEinheitTyp(zeit, EinheitTyp.BEZUG)).thenReturn(List.of(bezug));
        when(messwerteRepository.save(any(Messwerte.class))).thenAnswer(inv -> inv.getArgument(0));

        MesswerteService.CalculationResult result = messwerteService.calculateSolarDistribution(
            dateFrom, dateTo, "EQUAL_SHARE");

        // S = 6, dem einzigen Consumer zugeteilt
        assertEquals(6.0, consumer.getZev(), 1e-9);
        assertEquals(6.0, consumer.getZevCalculated(), 1e-9);
        // Netz-Anteil = total - zev = 10 - 6 = 4 = Bezug
        assertEquals(4.0, consumer.getTotal() - consumer.getZev(), 1e-9);
        assertEquals(1, result.getProcessedTimestamps());
        assertEquals(6.0, result.getTotalSolarProduced(), 1e-9);
    }

    @Test
    void calculateSolarDistribution_Bilanz_ConsumerZevSummeUndNetzAnteilGleichBezug() {
        LocalDateTime dateFrom = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime dateTo = LocalDateTime.of(2024, 1, 31, 23, 59, 59);
        LocalDateTime zeit = LocalDateTime.of(2024, 1, 15, 12, 0);

        Einheit consumerEinheit2 = new Einheit("Wohnung B", EinheitTyp.CONSUMER);
        consumerEinheit2.setId(3L);
        consumerEinheit2.setOrgId(testOrgId);

        when(einstellungenService.getVerteilmodus(any())).thenReturn(Verteilmodus.BILANZ);
        when(einheitRepository.existsByTyp(EinheitTyp.BEZUG)).thenReturn(true);
        when(messwerteRepository.findDistinctZeitBetween(dateFrom, dateTo)).thenReturn(List.of(zeit));

        // ConsumerTotal = 5 + 5 = 10, Bezug = 4 → S = 6, EQUAL_SHARE → je 3
        Messwerte c1 = new Messwerte(zeit, 5.0, 0.0, consumerEinheit);
        Messwerte c2 = new Messwerte(zeit, 5.0, 0.0, consumerEinheit2);
        Messwerte bezug = new Messwerte(zeit, 4.0, 0.0, bezugEinheit);
        when(messwerteRepository.findByZeitAndEinheitTyp(zeit, EinheitTyp.PRODUCER)).thenReturn(Collections.emptyList());
        when(messwerteRepository.findByZeitAndEinheitTyp(zeit, EinheitTyp.CONSUMER)).thenReturn(List.of(c1, c2));
        when(messwerteRepository.findByZeitAndEinheitTyp(zeit, EinheitTyp.BEZUG)).thenReturn(List.of(bezug));
        when(messwerteRepository.save(any(Messwerte.class))).thenAnswer(inv -> inv.getArgument(0));

        MesswerteService.CalculationResult result = messwerteService.calculateSolarDistribution(
            dateFrom, dateTo, "EQUAL_SHARE");

        assertEquals(3.0, c1.getZev(), 1e-9);
        assertEquals(3.0, c2.getZev(), 1e-9);
        // Consumer-zev-Summe = 6 = S
        assertEquals(6.0, c1.getZev() + c2.getZev(), 1e-9);
        // Netz-Anteil-Summe = (5-3)+(5-3) = 4 = Bezug (FR-3.3 verrechnungstreu)
        assertEquals(4.0, (c1.getTotal() - c1.getZev()) + (c2.getTotal() - c2.getZev()), 1e-9);
        assertEquals(2, result.getProcessedRecords());
    }

    @Test
    void calculateSolarDistribution_Bilanz_BezugGroesserConsumerTotal_SIstNull() {
        LocalDateTime dateFrom = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime dateTo = LocalDateTime.of(2024, 1, 31, 23, 59, 59);
        LocalDateTime zeit = LocalDateTime.of(2024, 1, 15, 12, 0);

        when(einstellungenService.getVerteilmodus(any())).thenReturn(Verteilmodus.BILANZ);
        when(einheitRepository.existsByTyp(EinheitTyp.BEZUG)).thenReturn(true);
        when(messwerteRepository.findDistinctZeitBetween(dateFrom, dateTo)).thenReturn(List.of(zeit));

        // Bezug (15) > ConsumerTotal (10), z.B. Batterie lädt aus dem Netz → S = 0
        Messwerte consumer = new Messwerte(zeit, 10.0, 0.0, consumerEinheit);
        Messwerte bezug = new Messwerte(zeit, 15.0, 0.0, bezugEinheit);
        when(messwerteRepository.findByZeitAndEinheitTyp(zeit, EinheitTyp.PRODUCER)).thenReturn(Collections.emptyList());
        when(messwerteRepository.findByZeitAndEinheitTyp(zeit, EinheitTyp.CONSUMER)).thenReturn(List.of(consumer));
        when(messwerteRepository.findByZeitAndEinheitTyp(zeit, EinheitTyp.BEZUG)).thenReturn(List.of(bezug));
        when(messwerteRepository.save(any(Messwerte.class))).thenAnswer(inv -> inv.getArgument(0));

        MesswerteService.CalculationResult result = messwerteService.calculateSolarDistribution(
            dateFrom, dateTo, "EQUAL_SHARE");

        assertEquals(0.0, consumer.getZev(), 1e-9);
        assertEquals(0.0, consumer.getZevCalculated(), 1e-9);
        assertEquals(0.0, result.getTotalSolarProduced(), 1e-9);
        assertEquals(1, result.getProcessedTimestamps());
    }

    @Test
    void calculateSolarDistribution_Bilanz_OhneProducer_VerteiltTrotzdem() {
        LocalDateTime dateFrom = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime dateTo = LocalDateTime.of(2024, 1, 31, 23, 59, 59);
        LocalDateTime zeit = LocalDateTime.of(2024, 1, 15, 12, 0);

        when(einstellungenService.getVerteilmodus(any())).thenReturn(Verteilmodus.BILANZ);
        when(einheitRepository.existsByTyp(EinheitTyp.BEZUG)).thenReturn(true);
        when(messwerteRepository.findDistinctZeitBetween(dateFrom, dateTo)).thenReturn(List.of(zeit));

        // Keine Producer-Messwerte in diesem Intervall (findByZeitAndEinheitTyp(PRODUCER) = leer);
        // im BILANZ-Zweig darf der producer-gesteuerte Skip NICHT greifen.
        Messwerte consumer = new Messwerte(zeit, 8.0, 0.0, consumerEinheit);
        Messwerte bezug = new Messwerte(zeit, 3.0, 0.0, bezugEinheit);
        when(messwerteRepository.findByZeitAndEinheitTyp(zeit, EinheitTyp.PRODUCER)).thenReturn(Collections.emptyList());
        when(messwerteRepository.findByZeitAndEinheitTyp(zeit, EinheitTyp.CONSUMER)).thenReturn(List.of(consumer));
        when(messwerteRepository.findByZeitAndEinheitTyp(zeit, EinheitTyp.BEZUG)).thenReturn(List.of(bezug));
        when(messwerteRepository.save(any(Messwerte.class))).thenAnswer(inv -> inv.getArgument(0));

        MesswerteService.CalculationResult result = messwerteService.calculateSolarDistribution(
            dateFrom, dateTo, "EQUAL_SHARE");

        // S = max(0, 8 - 3) = 5 wird trotz fehlender Producer verteilt (kein Skip)
        assertEquals(1, result.getProcessedTimestamps());
        assertEquals(5.0, consumer.getZev(), 1e-9);
    }

    @Test
    void calculateSolarDistribution_Bilanz_KeineBezugEinheit_ThrowsIllegalState() {
        LocalDateTime dateFrom = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime dateTo = LocalDateTime.of(2024, 1, 31, 23, 59, 59);

        when(einstellungenService.getVerteilmodus(any())).thenReturn(Verteilmodus.BILANZ);
        when(einheitRepository.existsByTyp(EinheitTyp.BEZUG)).thenReturn(false);

        IllegalStateException ex = assertThrows(
            IllegalStateException.class,
            () -> messwerteService.calculateSolarDistribution(dateFrom, dateTo, "EQUAL_SHARE"));

        assertTrue(ex.getMessage().contains("BILANZMODELL_KEINE_BILANZDATEN"));
        // Rollback-Garantie: keine zev-Werte geschrieben
        verify(messwerteRepository, never()).save(any());
    }

    @Test
    void calculateSolarDistribution_Bilanz_FehlenderBezugMesswertImIntervall_ThrowsIllegalStateMitIntervall() {
        LocalDateTime dateFrom = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime dateTo = LocalDateTime.of(2024, 1, 31, 23, 59, 59);
        LocalDateTime zeit = LocalDateTime.of(2024, 1, 15, 12, 0);

        when(einstellungenService.getVerteilmodus(any())).thenReturn(Verteilmodus.BILANZ);
        when(einheitRepository.existsByTyp(EinheitTyp.BEZUG)).thenReturn(true);
        when(messwerteRepository.findDistinctZeitBetween(dateFrom, dateTo)).thenReturn(List.of(zeit));

        // Consumer vorhanden, aber KEIN BEZUG-Messwert im Intervall (findByZeitAndEinheitTyp(BEZUG) = leer)
        Messwerte consumer = new Messwerte(zeit, 10.0, 0.0, consumerEinheit);
        when(messwerteRepository.findByZeitAndEinheitTyp(zeit, EinheitTyp.PRODUCER)).thenReturn(Collections.emptyList());
        when(messwerteRepository.findByZeitAndEinheitTyp(zeit, EinheitTyp.CONSUMER)).thenReturn(List.of(consumer));
        when(messwerteRepository.findByZeitAndEinheitTyp(zeit, EinheitTyp.BEZUG)).thenReturn(Collections.emptyList());

        IllegalStateException ex = assertThrows(
            IllegalStateException.class,
            () -> messwerteService.calculateSolarDistribution(dateFrom, dateTo, "EQUAL_SHARE"));

        assertTrue(ex.getMessage().contains("BILANZMODELL_KEINE_BILANZDATEN"),
            "Message soll den Key tragen: " + ex.getMessage());
        // Intervall-Angabe (Tag + Zeit)
        assertTrue(ex.getMessage().contains("2024-01-15"), "Message soll den Tag tragen: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("12:00"), "Message soll die Zeit tragen: " + ex.getMessage());
        // keine Teilwerte geschrieben
        verify(messwerteRepository, never()).save(any());
    }

    @Test
    void calculateSolarDistribution_Bilanz_KeineRuecklieferung_KeinAbbruch_ConsumerVerteilt() {
        LocalDateTime dateFrom = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime dateTo = LocalDateTime.of(2024, 1, 31, 23, 59, 59);
        LocalDateTime zeit = LocalDateTime.of(2024, 1, 15, 12, 0);

        when(einstellungenService.getVerteilmodus(any())).thenReturn(Verteilmodus.BILANZ);
        when(einheitRepository.existsByTyp(EinheitTyp.BEZUG)).thenReturn(true);
        // Keine RUECKLIEFERUNG-Einheit vorhanden → Producer-zev = 0 (Statistik unvollständig, FR-2.4)
        when(einheitRepository.existsByTyp(EinheitTyp.RUECKLIEFERUNG)).thenReturn(false);
        when(messwerteRepository.findDistinctZeitBetween(dateFrom, dateTo)).thenReturn(List.of(zeit));

        Messwerte producer = new Messwerte(zeit, -10.0, -10.0, producerEinheit);
        producer.setQuelle(Quelle.MQTT);
        Messwerte consumer = new Messwerte(zeit, 10.0, 0.0, consumerEinheit);
        Messwerte bezug = new Messwerte(zeit, 6.0, 0.0, bezugEinheit);
        when(messwerteRepository.findByZeitAndEinheitTyp(zeit, EinheitTyp.PRODUCER)).thenReturn(List.of(producer));
        when(messwerteRepository.findByZeitAndEinheitTyp(zeit, EinheitTyp.CONSUMER)).thenReturn(List.of(consumer));
        when(messwerteRepository.findByZeitAndEinheitTyp(zeit, EinheitTyp.BEZUG)).thenReturn(List.of(bezug));
        when(messwerteRepository.save(any(Messwerte.class))).thenAnswer(inv -> inv.getArgument(0));

        // Kein Abbruch: fehlende Rücklieferung ist nicht abrechnungskritisch (FR-2.4)
        MesswerteService.CalculationResult result = messwerteService.calculateSolarDistribution(
            dateFrom, dateTo, "EQUAL_SHARE");

        // Consumer-Abrechnung unberührt: S = max(0, 10 - 6) = 4
        assertEquals(4.0, consumer.getZev(), 1e-9);
        // Fehlende RUECKLIEFERUNG-Einheit → Producer-zev = 0 (MQTT-Producer, nur Statistik)
        assertEquals(0.0, producer.getZev(), 1e-9);
        assertEquals(1, result.getProcessedTimestamps());
    }

    @Test
    void calculateSolarDistribution_Bilanz_CsvProducer_ZevBleibtGemessen() {
        LocalDateTime dateFrom = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime dateTo = LocalDateTime.of(2024, 1, 31, 23, 59, 59);
        LocalDateTime zeit = LocalDateTime.of(2024, 1, 15, 12, 0);

        when(einstellungenService.getVerteilmodus(any())).thenReturn(Verteilmodus.BILANZ);
        when(einheitRepository.existsByTyp(EinheitTyp.BEZUG)).thenReturn(true);
        when(messwerteRepository.findDistinctZeitBetween(dateFrom, dateTo)).thenReturn(List.of(zeit));

        when(einheitRepository.existsByTyp(EinheitTyp.RUECKLIEFERUNG)).thenReturn(true);

        // CSV-Producer: gemessener zev (-7) bleibt unangetastet (Guard quelle == MQTT)
        Messwerte producer = new Messwerte(zeit, -10.0, -7.0, producerEinheit); // default Quelle = CSV
        Messwerte rueck = new Messwerte(zeit, -3.0, 0.0, ruecklieferungEinheit);
        Messwerte consumer = new Messwerte(zeit, 10.0, 0.0, consumerEinheit);
        Messwerte bezug = new Messwerte(zeit, 6.0, 0.0, bezugEinheit);
        when(messwerteRepository.findByZeitAndEinheitTyp(zeit, EinheitTyp.PRODUCER)).thenReturn(List.of(producer));
        when(messwerteRepository.findByZeitAndEinheitTyp(zeit, EinheitTyp.RUECKLIEFERUNG)).thenReturn(List.of(rueck));
        when(messwerteRepository.findByZeitAndEinheitTyp(zeit, EinheitTyp.CONSUMER)).thenReturn(List.of(consumer));
        when(messwerteRepository.findByZeitAndEinheitTyp(zeit, EinheitTyp.BEZUG)).thenReturn(List.of(bezug));
        when(messwerteRepository.save(any(Messwerte.class))).thenAnswer(inv -> inv.getArgument(0));

        messwerteService.calculateSolarDistribution(dateFrom, dateTo, "EQUAL_SHARE");

        assertEquals(-7.0, producer.getZev(), 1e-9);
        verify(messwerteRepository, never()).save(producer);
        // Consumer regulär verteilt: S = 10 - 6 = 4
        assertEquals(4.0, consumer.getZev(), 1e-9);
    }

    @Test
    void calculateSolarDistribution_Bilanz_MqttProducer_ProducerZevAusProduktionMinusRuecklieferung() {
        LocalDateTime dateFrom = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime dateTo = LocalDateTime.of(2024, 1, 31, 23, 59, 59);
        LocalDateTime zeit = LocalDateTime.of(2024, 1, 15, 12, 0);

        when(einstellungenService.getVerteilmodus(any())).thenReturn(Verteilmodus.BILANZ);
        when(einheitRepository.existsByTyp(EinheitTyp.BEZUG)).thenReturn(true);
        when(einheitRepository.existsByTyp(EinheitTyp.RUECKLIEFERUNG)).thenReturn(true);
        when(messwerteRepository.findDistinctZeitBetween(dateFrom, dateTo)).thenReturn(List.of(zeit));

        // MQTT-Producer: zev = |Produktion(10)| - |Rücklieferung(3)| = 7 (negativ gespeichert → -7)
        Messwerte producer = new Messwerte(zeit, -10.0, -10.0, producerEinheit);
        producer.setQuelle(Quelle.MQTT);
        Messwerte rueck = new Messwerte(zeit, -3.0, 0.0, ruecklieferungEinheit);
        Messwerte consumer = new Messwerte(zeit, 10.0, 0.0, consumerEinheit);
        Messwerte bezug = new Messwerte(zeit, 6.0, 0.0, bezugEinheit);
        when(messwerteRepository.findByZeitAndEinheitTyp(zeit, EinheitTyp.PRODUCER)).thenReturn(List.of(producer));
        when(messwerteRepository.findByZeitAndEinheitTyp(zeit, EinheitTyp.RUECKLIEFERUNG)).thenReturn(List.of(rueck));
        when(messwerteRepository.findByZeitAndEinheitTyp(zeit, EinheitTyp.CONSUMER)).thenReturn(List.of(consumer));
        when(messwerteRepository.findByZeitAndEinheitTyp(zeit, EinheitTyp.BEZUG)).thenReturn(List.of(bezug));
        when(messwerteRepository.save(any(Messwerte.class))).thenAnswer(inv -> inv.getArgument(0));

        messwerteService.calculateSolarDistribution(dateFrom, dateTo, "EQUAL_SHARE");

        assertEquals(-7.0, producer.getZev(), 1e-9);
        verify(messwerteRepository).save(producer);
        assertEquals(4.0, consumer.getZev(), 1e-9);
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
