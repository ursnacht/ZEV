package ch.nacht.controller;

import ch.nacht.dto.EinheitMatchRequestDTO;
import ch.nacht.dto.EinheitMatchResponseDTO;
import ch.nacht.entity.Einheit;
import ch.nacht.entity.EinheitTyp;
import ch.nacht.service.EinheitMatchingService;
import ch.nacht.service.EinheitService;
import ch.nacht.service.OrganizationContextService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EinheitController.class)
@AutoConfigureMockMvc(addFilters = false)
public class EinheitControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EinheitService einheitService;

    @MockBean
    private EinheitMatchingService einheitMatchingService;

    @MockBean
    private OrganizationContextService organizationContextService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void createEinheit_ValidInput_ReturnsCreated() throws Exception {
        Einheit einheit = new Einheit("Valid Name", EinheitTyp.CONSUMER);
        when(einheitService.createEinheit(any(Einheit.class))).thenReturn(einheit);

        mockMvc.perform(post("/api/einheit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(einheit)))
                .andExpect(status().isCreated());
    }

    @Test
    public void createEinheit_InvalidName_ReturnsBadRequest() throws Exception {
        Einheit einheit = new Einheit("", EinheitTyp.CONSUMER); // Empty name

        mockMvc.perform(post("/api/einheit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(einheit)))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void createEinheit_NullTyp_ReturnsBadRequest() throws Exception {
        Einheit einheit = new Einheit("Valid Name", null); // Null typ

        mockMvc.perform(post("/api/einheit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(einheit)))
                .andExpect(status().isBadRequest());
    }

    // ========== Match Endpoint Tests ==========

    @Test
    public void matchEinheit_ValidFilename_ReturnsMatch() throws Exception {
        EinheitMatchResponseDTO response = EinheitMatchResponseDTO.builder()
                .matched(true)
                .einheitId(1L)
                .einheitName("Allgemein")
                .confidence(0.9)
                .build();

        when(einheitMatchingService.matchEinheitByFilename(anyString())).thenReturn(response);

        EinheitMatchRequestDTO request = new EinheitMatchRequestDTO("2025-07-allg.csv");

        mockMvc.perform(post("/api/einheit/match")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matched", is(true)))
                .andExpect(jsonPath("$.einheitId", is(1)))
                .andExpect(jsonPath("$.einheitName", is("Allgemein")))
                .andExpect(jsonPath("$.confidence", is(0.9)));
    }

    @Test
    public void matchEinheit_NoMatch_ReturnsNotMatched() throws Exception {
        EinheitMatchResponseDTO response = EinheitMatchResponseDTO.builder()
                .matched(false)
                .message("Keine passende Einheit gefunden")
                .confidence(0.0)
                .build();

        when(einheitMatchingService.matchEinheitByFilename(anyString())).thenReturn(response);

        EinheitMatchRequestDTO request = new EinheitMatchRequestDTO("2025-07-unknown.csv");

        mockMvc.perform(post("/api/einheit/match")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matched", is(false)))
                .andExpect(jsonPath("$.message", is("Keine passende Einheit gefunden")));
    }

    @Test
    public void matchEinheit_ServiceError_ReturnsErrorResponse() throws Exception {
        EinheitMatchResponseDTO response = EinheitMatchResponseDTO.builder()
                .matched(false)
                .message("KI-Service nicht verfügbar")
                .confidence(0.0)
                .build();

        when(einheitMatchingService.matchEinheitByFilename(anyString())).thenReturn(response);

        EinheitMatchRequestDTO request = new EinheitMatchRequestDTO("2025-07-test.csv");

        mockMvc.perform(post("/api/einheit/match")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matched", is(false)))
                .andExpect(jsonPath("$.message", is("KI-Service nicht verfügbar")));
    }

    @Test
    public void matchEinheit_ServiceThrowsException_ReturnsErrorResponse() throws Exception {
        when(einheitMatchingService.matchEinheitByFilename(anyString()))
                .thenThrow(new RuntimeException("Unexpected error"));

        EinheitMatchRequestDTO request = new EinheitMatchRequestDTO("2025-07-test.csv");

        mockMvc.perform(post("/api/einheit/match")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matched", is(false)))
                .andExpect(jsonPath("$.message", is("KI-Service nicht verfügbar")));
    }

    @Test
    public void matchEinheit_EmptyFilename_ReturnsBadRequest() throws Exception {
        String json = "{\"filename\": \"\"}";

        mockMvc.perform(post("/api/einheit/match")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void matchEinheit_NullFilename_ReturnsBadRequest() throws Exception {
        String json = "{\"filename\": null}";

        mockMvc.perform(post("/api/einheit/match")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void matchEinheit_MissingFilename_ReturnsBadRequest() throws Exception {
        String json = "{}";

        mockMvc.perform(post("/api/einheit/match")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isBadRequest());
    }
}
