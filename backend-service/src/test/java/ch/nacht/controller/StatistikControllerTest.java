package ch.nacht.controller;

import ch.nacht.dto.MonatsStatistikDTO;
import ch.nacht.dto.StatistikDTO;
import ch.nacht.service.OrganisationService;
import ch.nacht.service.OrganizationContextService;
import ch.nacht.service.StatistikPdfService;
import ch.nacht.service.StatistikService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(StatistikController.class)
@AutoConfigureMockMvc(addFilters = false)
public class StatistikControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StatistikService statistikService;

    @MockitoBean
    private StatistikPdfService statistikPdfService;

    @MockitoBean
    private OrganizationContextService organizationContextService;

    @MockitoBean
    private OrganisationService organisationService;

    private ObjectMapper objectMapper;

    private StatistikDTO testStatistik;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        testStatistik = new StatistikDTO();
        testStatistik.setDatenVollstaendig(true);
        testStatistik.setMesswerteBisDate(LocalDate.of(2024, 3, 31));
        testStatistik.setToleranz(0.01);

        MonatsStatistikDTO januar = new MonatsStatistikDTO();
        januar.setJahr(2024);
        januar.setMonat(1);
        MonatsStatistikDTO februar = new MonatsStatistikDTO();
        februar.setJahr(2024);
        februar.setMonat(2);
        testStatistik.setMonate(List.of(januar, februar));
    }

    // ==================== GET /api/statistik ====================

    @Test
    void getStatistik_ValidDateRange_ReturnsStatistik() throws Exception {
        when(statistikService.getStatistik(
            LocalDate.of(2024, 1, 1),
            LocalDate.of(2024, 3, 31))
        ).thenReturn(testStatistik);

        mockMvc.perform(get("/api/statistik")
                .param("von", "2024-01-01")
                .param("bis", "2024-03-31"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.datenVollstaendig", is(true)))
            .andExpect(jsonPath("$.monate", hasSize(2)));

        verify(statistikService).getStatistik(
            LocalDate.of(2024, 1, 1),
            LocalDate.of(2024, 3, 31));
    }

    @Test
    void getStatistik_VonAfterBis_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/statistik")
                .param("von", "2024-04-01")
                .param("bis", "2024-03-31"))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(statistikService);
    }

    @Test
    void getStatistik_SameDayRange_ReturnsStatistik() throws Exception {
        StatistikDTO singleDay = new StatistikDTO();
        singleDay.setDatenVollstaendig(true);
        singleDay.setMonate(List.of());
        when(statistikService.getStatistik(
            LocalDate.of(2024, 1, 1),
            LocalDate.of(2024, 1, 1))
        ).thenReturn(singleDay);

        mockMvc.perform(get("/api/statistik")
                .param("von", "2024-01-01")
                .param("bis", "2024-01-01"))
            .andExpect(status().isOk());
    }

    @Test
    void getStatistik_ServiceThrowsException_ReturnsInternalServerError() throws Exception {
        when(statistikService.getStatistik(any(), any()))
            .thenThrow(new RuntimeException("DB Fehler"));

        mockMvc.perform(get("/api/statistik")
                .param("von", "2024-01-01")
                .param("bis", "2024-03-31"))
            .andExpect(status().isInternalServerError());
    }

    @Test
    void getStatistik_IncompleteData_ReturnsDatenVollstaendigFalse() throws Exception {
        StatistikDTO incomplete = new StatistikDTO();
        incomplete.setDatenVollstaendig(false);
        incomplete.setFehlendeEinheiten(List.of("Wohnung 3"));
        incomplete.setMonate(List.of());
        when(statistikService.getStatistik(any(), any())).thenReturn(incomplete);

        mockMvc.perform(get("/api/statistik")
                .param("von", "2024-01-01")
                .param("bis", "2024-03-31"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.datenVollstaendig", is(false)));
    }

    // ==================== GET /api/statistik/letztes-datum ====================

    @Test
    void getLetztesMessdatum_DateExists_ReturnsDate() throws Exception {
        when(statistikService.ermittleLetztesMessdatum())
            .thenReturn(LocalDate.of(2024, 3, 31));

        mockMvc.perform(get("/api/statistik/letztes-datum"))
            .andExpect(status().isOk())
            .andExpect(content().string("\"2024-03-31\""));
    }

    @Test
    void getLetztesMessdatum_NoMeasurements_ReturnsNoContent() throws Exception {
        when(statistikService.ermittleLetztesMessdatum()).thenReturn(null);

        mockMvc.perform(get("/api/statistik/letztes-datum"))
            .andExpect(status().isNoContent());
    }

    @Test
    void getLetztesMessdatum_ServiceThrowsException_ReturnsInternalServerError() throws Exception {
        when(statistikService.ermittleLetztesMessdatum())
            .thenThrow(new RuntimeException("DB Fehler"));

        mockMvc.perform(get("/api/statistik/letztes-datum"))
            .andExpect(status().isInternalServerError());
    }

    // ==================== GET /api/statistik/export/pdf ====================

    @Test
    void exportPdf_ValidRequest_ReturnsPdfAttachment() throws Exception {
        byte[] pdfBytes = new byte[]{37, 80, 68, 70}; // %PDF
        when(statistikService.getStatistik(any(), any())).thenReturn(testStatistik);
        when(statistikPdfService.generatePdf(any(StatistikDTO.class), eq("de")))
            .thenReturn(pdfBytes);

        mockMvc.perform(get("/api/statistik/export/pdf")
                .param("von", "2024-01-01")
                .param("bis", "2024-03-31")
                .param("sprache", "de"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition",
                "attachment; filename=statistik_2024-01-01_2024-03-31.pdf"))
            .andExpect(content().contentType(MediaType.APPLICATION_PDF))
            .andExpect(content().bytes(pdfBytes));
    }

    @Test
    void exportPdf_DefaultSprache_UsesDe() throws Exception {
        byte[] pdfBytes = new byte[]{37, 80, 68, 70};
        when(statistikService.getStatistik(any(), any())).thenReturn(testStatistik);
        when(statistikPdfService.generatePdf(any(StatistikDTO.class), eq("de")))
            .thenReturn(pdfBytes);

        mockMvc.perform(get("/api/statistik/export/pdf")
                .param("von", "2024-01-01")
                .param("bis", "2024-03-31"))
            .andExpect(status().isOk());

        verify(statistikPdfService).generatePdf(any(StatistikDTO.class), eq("de"));
    }

    @Test
    void exportPdf_VonAfterBis_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/statistik/export/pdf")
                .param("von", "2024-04-01")
                .param("bis", "2024-03-31"))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(statistikService);
        verifyNoInteractions(statistikPdfService);
    }

    @Test
    void exportPdf_ServiceThrowsException_ReturnsInternalServerError() throws Exception {
        when(statistikService.getStatistik(any(), any()))
            .thenThrow(new RuntimeException("PDF Generierungsfehler"));

        mockMvc.perform(get("/api/statistik/export/pdf")
                .param("von", "2024-01-01")
                .param("bis", "2024-03-31"))
            .andExpect(status().isInternalServerError());
    }

    @Test
    void exportPdf_FrenchLanguage_PassesSpracheToService() throws Exception {
        byte[] pdfBytes = new byte[]{37, 80, 68, 70};
        when(statistikService.getStatistik(any(), any())).thenReturn(testStatistik);
        when(statistikPdfService.generatePdf(any(StatistikDTO.class), eq("fr")))
            .thenReturn(pdfBytes);

        mockMvc.perform(get("/api/statistik/export/pdf")
                .param("von", "2024-01-01")
                .param("bis", "2024-03-31")
                .param("sprache", "fr"))
            .andExpect(status().isOk());

        verify(statistikPdfService).generatePdf(any(StatistikDTO.class), eq("fr"));
    }
}
