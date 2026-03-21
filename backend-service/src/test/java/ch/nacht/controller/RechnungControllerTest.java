package ch.nacht.controller;

import ch.nacht.dto.RechnungDTO;
import ch.nacht.service.OrganisationService;
import ch.nacht.service.OrganizationContextService;
import ch.nacht.service.RechnungPdfService;
import ch.nacht.service.RechnungService;
import ch.nacht.service.RechnungStorageService;
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
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RechnungController.class)
@AutoConfigureMockMvc(addFilters = false)
public class RechnungControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RechnungService rechnungService;

    @MockitoBean
    private RechnungPdfService rechnungPdfService;

    @MockitoBean
    private RechnungStorageService rechnungStorageService;

    @MockitoBean
    private OrganizationContextService organizationContextService;

    @MockitoBean
    private OrganisationService organisationService;

    private ObjectMapper objectMapper;

    private RechnungDTO testRechnung;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        testRechnung = new RechnungDTO();
        testRechnung.setEinheitId(1L);
        testRechnung.setEinheitName("Wohnung 1");
        testRechnung.setMieterId(10L);
        testRechnung.setMieterName("Max Muster");
        testRechnung.setVon(LocalDate.of(2024, 1, 1));
        testRechnung.setBis(LocalDate.of(2024, 3, 31));
        testRechnung.setEndBetrag(125.50);
    }

    // ==================== POST /api/rechnungen/generate ====================

    @Test
    void generateRechnungen_ValidRequest_ReturnsOkWithList() throws Exception {
        when(rechnungService.berechneRechnungen(anyList(), any(), any()))
            .thenReturn(List.of(testRechnung));
        when(rechnungPdfService.generatePdf(any(RechnungDTO.class), anyString()))
            .thenReturn(new byte[]{1, 2, 3});
        when(rechnungStorageService.sanitizeKey(anyString())).thenReturn("Wohnung_1_10");
        when(rechnungStorageService.getFilename("Wohnung_1_10")).thenReturn("Wohnung_1_10.pdf");

        String request = """
            {
                "von": "2024-01-01",
                "bis": "2024-03-31",
                "einheitIds": [1],
                "sprache": "de"
            }
            """;

        mockMvc.perform(post("/api/rechnungen/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count", is(1)))
            .andExpect(jsonPath("$.rechnungen", hasSize(1)))
            .andExpect(jsonPath("$.rechnungen[0].einheitName", is("Wohnung 1")))
            .andExpect(jsonPath("$.rechnungen[0].mieterName", is("Max Muster")));

        verify(rechnungStorageService).clearAll();
        verify(rechnungService).berechneRechnungen(List.of(1L),
            LocalDate.of(2024, 1, 1), LocalDate.of(2024, 3, 31));
    }

    @Test
    void generateRechnungen_NullVon_ReturnsBadRequest() throws Exception {
        String request = """
            {
                "von": null,
                "bis": "2024-03-31",
                "einheitIds": [1]
            }
            """;

        mockMvc.perform(post("/api/rechnungen/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").exists());

        verifyNoInteractions(rechnungService);
    }

    @Test
    void generateRechnungen_NullBis_ReturnsBadRequest() throws Exception {
        String request = """
            {
                "von": "2024-01-01",
                "bis": null,
                "einheitIds": [1]
            }
            """;

        mockMvc.perform(post("/api/rechnungen/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").exists());

        verifyNoInteractions(rechnungService);
    }

    @Test
    void generateRechnungen_VonAfterBis_ReturnsBadRequest() throws Exception {
        String request = """
            {
                "von": "2024-04-01",
                "bis": "2024-03-31",
                "einheitIds": [1]
            }
            """;

        mockMvc.perform(post("/api/rechnungen/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").exists());

        verifyNoInteractions(rechnungService);
    }

    @Test
    void generateRechnungen_EmptyEinheitIds_ReturnsBadRequest() throws Exception {
        String request = """
            {
                "von": "2024-01-01",
                "bis": "2024-03-31",
                "einheitIds": []
            }
            """;

        mockMvc.perform(post("/api/rechnungen/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").exists());

        verifyNoInteractions(rechnungService);
    }

    @Test
    void generateRechnungen_TarifValidationFails_ReturnsBadRequest() throws Exception {
        when(rechnungService.berechneRechnungen(anyList(), any(), any()))
            .thenThrow(new IllegalStateException("Kein Tarif für Zeitraum 2024-01-01 bis 2024-03-31"));

        String request = """
            {
                "von": "2024-01-01",
                "bis": "2024-03-31",
                "einheitIds": [1]
            }
            """;

        mockMvc.perform(post("/api/rechnungen/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void generateRechnungen_DefaultSprache_UsesDe() throws Exception {
        when(rechnungService.berechneRechnungen(anyList(), any(), any()))
            .thenReturn(List.of(testRechnung));
        when(rechnungPdfService.generatePdf(any(RechnungDTO.class), eq("de")))
            .thenReturn(new byte[]{1, 2, 3});
        when(rechnungStorageService.sanitizeKey(anyString())).thenReturn("Wohnung_1_10");
        when(rechnungStorageService.getFilename(anyString())).thenReturn("Wohnung_1_10.pdf");

        String request = """
            {
                "von": "2024-01-01",
                "bis": "2024-03-31",
                "einheitIds": [1]
            }
            """;

        mockMvc.perform(post("/api/rechnungen/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
            .andExpect(status().isOk());

        verify(rechnungPdfService).generatePdf(any(RechnungDTO.class), eq("de"));
    }

    @Test
    void generateRechnungen_KeyWithoutMieter_UsesEinheitNameOnly() throws Exception {
        RechnungDTO rechnungOhneMieter = new RechnungDTO();
        rechnungOhneMieter.setEinheitId(2L);
        rechnungOhneMieter.setEinheitName("Gewerbe EG");
        rechnungOhneMieter.setMieterId(null);
        rechnungOhneMieter.setEndBetrag(80.00);

        when(rechnungService.berechneRechnungen(anyList(), any(), any()))
            .thenReturn(List.of(rechnungOhneMieter));
        when(rechnungPdfService.generatePdf(any(RechnungDTO.class), anyString()))
            .thenReturn(new byte[]{1, 2, 3});
        when(rechnungStorageService.sanitizeKey("Gewerbe EG")).thenReturn("Gewerbe_EG");
        when(rechnungStorageService.getFilename("Gewerbe_EG")).thenReturn("Gewerbe_EG.pdf");

        String request = """
            {
                "von": "2024-01-01",
                "bis": "2024-03-31",
                "einheitIds": [2]
            }
            """;

        mockMvc.perform(post("/api/rechnungen/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count", is(1)));

        verify(rechnungStorageService).sanitizeKey("Gewerbe EG");
    }

    // ==================== GET /api/rechnungen/download/{key} ====================

    @Test
    void downloadRechnung_Found_ReturnsPdfAttachment() throws Exception {
        byte[] pdfBytes = new byte[]{37, 80, 68, 70}; // %PDF
        when(rechnungStorageService.get("Wohnung_1")).thenReturn(Optional.of(pdfBytes));
        when(rechnungStorageService.getFilename("Wohnung_1")).thenReturn("Wohnung_1.pdf");

        mockMvc.perform(get("/api/rechnungen/download/Wohnung_1"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"Wohnung_1.pdf\""))
            .andExpect(content().contentType(MediaType.APPLICATION_PDF))
            .andExpect(content().bytes(pdfBytes));
    }

    @Test
    void downloadRechnung_NotFound_Returns404() throws Exception {
        when(rechnungStorageService.get("unbekannt")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/rechnungen/download/unbekannt"))
            .andExpect(status().isNotFound());
    }
}
