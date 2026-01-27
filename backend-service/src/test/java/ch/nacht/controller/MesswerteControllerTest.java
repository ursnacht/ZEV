package ch.nacht.controller;

import ch.nacht.entity.Einheit;
import ch.nacht.entity.EinheitTyp;
import ch.nacht.service.EinheitService;
import ch.nacht.service.MesswerteService;
import ch.nacht.service.MetricsService;
import ch.nacht.service.OrganizationContextService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MesswerteController.class)
@AutoConfigureMockMvc(addFilters = false)
public class MesswerteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MesswerteService messwerteService;

    @MockBean
    private MetricsService metricsService;

    @MockBean
    private EinheitService einheitService;

    @MockBean
    private OrganizationContextService organizationContextService;

    private Einheit testEinheit;

    @BeforeEach
    void setUp() {
        testEinheit = new Einheit("Wohnung A", EinheitTyp.CONSUMER);
        testEinheit.setId(1L);
    }

    // ==================== Upload Endpoint Tests ====================

    @Test
    void uploadCsv_ValidFile_ReturnsSuccess() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "test.csv", "text/csv", "Time,Total,ZEV\n00:00,1.5,0.8\n".getBytes());

        Map<String, Object> serviceResult = Map.of(
            "status", "success",
            "count", 1,
            "einheitId", 1L,
            "einheitName", "Wohnung A"
        );

        when(messwerteService.processCsvUpload(any(), eq(1L), eq("2024-01-15")))
            .thenReturn(serviceResult);
        when(einheitService.getEinheitById(1L)).thenReturn(Optional.of(testEinheit));

        mockMvc.perform(multipart("/api/messwerte/upload")
                .file(file)
                .param("date", "2024-01-15")
                .param("einheitId", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("success")))
            .andExpect(jsonPath("$.count", is(1)))
            .andExpect(jsonPath("$.einheitName", is("Wohnung A")));

        verify(metricsService).recordMessdatenUpload("Wohnung A");
    }

    @Test
    void uploadCsv_ServiceThrowsException_ReturnsBadRequest() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "test.csv", "text/csv", "invalid".getBytes());

        when(messwerteService.processCsvUpload(any(), eq(1L), eq("2024-01-15")))
            .thenThrow(new RuntimeException("CSV parsing failed"));

        mockMvc.perform(multipart("/api/messwerte/upload")
                .file(file)
                .param("date", "2024-01-15")
                .param("einheitId", "1"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status", is("error")))
            .andExpect(jsonPath("$.message", is("CSV parsing failed")));
    }

    @Test
    void uploadCsv_EinheitNotFound_MetricsRecordedWithUnbekannt() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "test.csv", "text/csv", "Time,Total,ZEV\n00:00,1.5,0.8\n".getBytes());

        Map<String, Object> serviceResult = Map.of(
            "status", "success",
            "count", 1,
            "einheitId", 1L,
            "einheitName", "Wohnung A"
        );

        when(messwerteService.processCsvUpload(any(), eq(1L), eq("2024-01-15")))
            .thenReturn(serviceResult);
        when(einheitService.getEinheitById(1L)).thenReturn(Optional.empty());

        mockMvc.perform(multipart("/api/messwerte/upload")
                .file(file)
                .param("date", "2024-01-15")
                .param("einheitId", "1"))
            .andExpect(status().isOk());

        verify(metricsService).recordMessdatenUpload("unbekannt");
    }

    // ==================== Calculate Distribution Endpoint Tests ====================

    @Test
    void calculateDistribution_ValidRequest_ReturnsSuccess() throws Exception {
        MesswerteService.CalculationResult calcResult = new MesswerteService.CalculationResult(
            10, 50,
            LocalDateTime.of(2024, 1, 1, 0, 0),
            LocalDateTime.of(2024, 1, 31, 23, 59, 59),
            100.5, 95.2
        );

        when(messwerteService.calculateSolarDistribution(any(), any(), eq("EQUAL_SHARE")))
            .thenReturn(calcResult);

        mockMvc.perform(post("/api/messwerte/calculate-distribution")
                .param("dateFrom", "2024-01-01")
                .param("dateTo", "2024-01-31")
                .param("algorithm", "EQUAL_SHARE"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("success")))
            .andExpect(jsonPath("$.algorithm", is("EQUAL_SHARE")))
            .andExpect(jsonPath("$.processedTimestamps", is(10)))
            .andExpect(jsonPath("$.processedRecords", is(50)))
            .andExpect(jsonPath("$.totalSolarProduced", is(100.5)))
            .andExpect(jsonPath("$.totalDistributed", is(95.2)));

        verify(metricsService).recordSolarverteilungBerechnung();
    }

    @Test
    void calculateDistribution_DefaultAlgorithm_UsesEqualShare() throws Exception {
        MesswerteService.CalculationResult calcResult = new MesswerteService.CalculationResult(
            0, 0,
            LocalDateTime.of(2024, 1, 1, 0, 0),
            LocalDateTime.of(2024, 1, 31, 23, 59, 59),
            0.0, 0.0
        );

        when(messwerteService.calculateSolarDistribution(any(), any(), eq("EQUAL_SHARE")))
            .thenReturn(calcResult);

        mockMvc.perform(post("/api/messwerte/calculate-distribution")
                .param("dateFrom", "2024-01-01")
                .param("dateTo", "2024-01-31"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("success")));

        verify(messwerteService).calculateSolarDistribution(any(), any(), eq("EQUAL_SHARE"));
    }

    @Test
    void calculateDistribution_ProportionalAlgorithm_PassesAlgorithm() throws Exception {
        MesswerteService.CalculationResult calcResult = new MesswerteService.CalculationResult(
            5, 20,
            LocalDateTime.of(2024, 1, 1, 0, 0),
            LocalDateTime.of(2024, 1, 31, 23, 59, 59),
            50.0, 48.0
        );

        when(messwerteService.calculateSolarDistribution(any(), any(), eq("PROPORTIONAL")))
            .thenReturn(calcResult);

        mockMvc.perform(post("/api/messwerte/calculate-distribution")
                .param("dateFrom", "2024-01-01")
                .param("dateTo", "2024-01-31")
                .param("algorithm", "PROPORTIONAL"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.algorithm", is("PROPORTIONAL")));
    }

    @Test
    void calculateDistribution_ServiceThrowsException_ReturnsBadRequest() throws Exception {
        when(messwerteService.calculateSolarDistribution(any(), any(), anyString()))
            .thenThrow(new RuntimeException("Calculation failed"));

        mockMvc.perform(post("/api/messwerte/calculate-distribution")
                .param("dateFrom", "2024-01-01")
                .param("dateTo", "2024-01-31"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status", is("error")))
            .andExpect(jsonPath("$.message", is("Calculation failed")));
    }

    @Test
    void calculateDistribution_InvalidDateFormat_ReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/messwerte/calculate-distribution")
                .param("dateFrom", "invalid-date")
                .param("dateTo", "2024-01-31"))
            .andExpect(status().isBadRequest());
    }

    // ==================== Get Messwerte by Einheit Tests ====================

    @Test
    void getMesswerteByEinheit_ValidRequest_ReturnsList() throws Exception {
        List<Map<String, Object>> serviceResult = Arrays.asList(
            Map.of("zeit", "2024-01-15T00:00", "total", 5.0, "zevCalculated", 1.8),
            Map.of("zeit", "2024-01-15T00:15", "total", 3.0, "zevCalculated", 1.2)
        );

        when(messwerteService.getMesswerteByEinheit(
            eq(1L), eq(LocalDate.of(2024, 1, 1)), eq(LocalDate.of(2024, 1, 31))))
            .thenReturn(serviceResult);

        mockMvc.perform(get("/api/messwerte/by-einheit")
                .param("einheitId", "1")
                .param("dateFrom", "2024-01-01")
                .param("dateTo", "2024-01-31"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].zeit", is("2024-01-15T00:00")))
            .andExpect(jsonPath("$[0].total", is(5.0)))
            .andExpect(jsonPath("$[0].zevCalculated", is(1.8)));
    }

    @Test
    void getMesswerteByEinheit_EmptyResult_ReturnsEmptyList() throws Exception {
        when(messwerteService.getMesswerteByEinheit(eq(1L), any(), any()))
            .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/messwerte/by-einheit")
                .param("einheitId", "1")
                .param("dateFrom", "2024-01-01")
                .param("dateTo", "2024-01-31"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void getMesswerteByEinheit_ServiceThrowsException_ReturnsBadRequest() throws Exception {
        when(messwerteService.getMesswerteByEinheit(eq(999L), any(), any()))
            .thenThrow(new RuntimeException("Einheit not found"));

        mockMvc.perform(get("/api/messwerte/by-einheit")
                .param("einheitId", "999")
                .param("dateFrom", "2024-01-01")
                .param("dateTo", "2024-01-31"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void getMesswerteByEinheit_InvalidDateFormat_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/messwerte/by-einheit")
                .param("einheitId", "1")
                .param("dateFrom", "not-a-date")
                .param("dateTo", "2024-01-31"))
            .andExpect(status().isBadRequest());
    }
}
