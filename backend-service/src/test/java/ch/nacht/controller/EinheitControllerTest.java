package ch.nacht.controller;

import ch.nacht.dto.EinheitMatchRequestDTO;
import ch.nacht.dto.EinheitMatchResponseDTO;
import ch.nacht.entity.Einheit;
import ch.nacht.entity.EinheitTyp;
import ch.nacht.service.EinheitMatchingService;
import ch.nacht.service.EinheitService;
import ch.nacht.service.OrganisationService;
import ch.nacht.service.OrganizationContextService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EinheitController.class)
@AutoConfigureMockMvc(addFilters = false)
public class EinheitControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EinheitService einheitService;

    @MockitoBean
    private EinheitMatchingService einheitMatchingService;

    @MockitoBean
    private OrganizationContextService organizationContextService;

    @MockitoBean
    private OrganisationService organisationService;

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
    public void createEinheit_ZweiterBilanzTyp_Returns400MitFehlerKey() throws Exception {
        Einheit einheit = new Einheit("Netzanschluss", EinheitTyp.BEZUG);
        when(einheitService.createEinheit(any(Einheit.class)))
                .thenThrow(new IllegalStateException("EINHEIT_BILANZ_TYP_EXISTIERT"));

        mockMvc.perform(post("/api/einheit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(einheit)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("EINHEIT_BILANZ_TYP_EXISTIERT")));
    }

    // ========== Ladestationen (Specs/Ladestationen.md) ==========

    /** Ladestation mit RFID im Feld messpunkt. */
    private Einheit ladestation(Long id, String rfid) {
        Einheit einheit = new Einheit("Ladestation 1", EinheitTyp.LADESTATION);
        einheit.setId(id);
        einheit.setMesspunkt(rfid);
        return einheit;
    }

    @Test
    public void createEinheit_Ladestation_ReturnsCreated() throws Exception {
        Einheit einheit = ladestation(1L, "RFID-001");
        when(einheitService.createEinheit(any(Einheit.class))).thenReturn(einheit);

        mockMvc.perform(post("/api/einheit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(einheit)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.typ", is("LADESTATION")))
                .andExpect(jsonPath("$.messpunkt", is("RFID-001")));
    }

    @Test
    public void createEinheit_LadestationMitVergebenerRfid_Returns400MitFehlerKey() throws Exception {
        Einheit einheit = ladestation(null, "RFID-001");
        when(einheitService.createEinheit(any(Einheit.class)))
                .thenThrow(new IllegalStateException("EINHEIT_MESSPUNKT_EXISTIERT"));

        mockMvc.perform(post("/api/einheit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(einheit)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("EINHEIT_MESSPUNKT_EXISTIERT")));
    }

    @Test
    public void updateEinheit_LadestationMitVergebenerRfid_Returns400MitFehlerKey() throws Exception {
        Einheit einheit = ladestation(1L, "RFID-001");
        when(einheitService.updateEinheit(eq(1L), any(Einheit.class)))
                .thenThrow(new IllegalStateException("EINHEIT_MESSPUNKT_EXISTIERT"));

        mockMvc.perform(put("/api/einheit/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(einheit)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("EINHEIT_MESSPUNKT_EXISTIERT")));
    }

    @Test
    public void updateEinheit_Ladestation_ReturnsOk() throws Exception {
        Einheit einheit = ladestation(1L, "RFID-002");
        when(einheitService.updateEinheit(eq(1L), any(Einheit.class))).thenReturn(Optional.of(einheit));

        mockMvc.perform(put("/api/einheit/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(einheit)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messpunkt", is("RFID-002")));
    }

    @Test
    public void updateEinheit_NotFound_Returns404() throws Exception {
        Einheit einheit = ladestation(99L, "RFID-099");
        when(einheitService.updateEinheit(eq(99L), any(Einheit.class))).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/einheit/99")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(einheit)))
                .andExpect(status().isNotFound());
    }

    @Test
    public void deleteEinheit_Exists_ReturnsNoContent() throws Exception {
        when(einheitService.deleteEinheit(1L)).thenReturn(true);

        mockMvc.perform(delete("/api/einheit/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    public void deleteEinheit_NotFound_Returns404() throws Exception {
        when(einheitService.deleteEinheit(99L)).thenReturn(false);

        mockMvc.perform(delete("/api/einheit/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    public void getAllEinheiten_ReturnsListIncludingLadestation() throws Exception {
        // Ladestationen werden in den Auswahllisten nicht ausgeblendet (FR-3)
        Einheit wohnung = new Einheit("Wohnung A", EinheitTyp.CONSUMER);
        when(einheitService.getAllEinheiten()).thenReturn(List.of(wohnung, ladestation(2L, "RFID-002")));

        mockMvc.perform(get("/api/einheit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[1].typ", is("LADESTATION")));
    }

    @Test
    public void createEinheit_MesspunktZuLang_ReturnsBadRequest() throws Exception {
        // messpunkt ist VARCHAR(50) - laengere RFIDs werden abgewiesen (FR-2)
        Einheit einheit = ladestation(null, "R".repeat(51));

        mockMvc.perform(post("/api/einheit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(einheit)))
                .andExpect(status().isBadRequest());
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
