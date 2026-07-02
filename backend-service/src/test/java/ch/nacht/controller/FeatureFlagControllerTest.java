package ch.nacht.controller;

import ch.nacht.dto.FeatureFlagDTO;
import ch.nacht.entity.FeatureFlag;
import ch.nacht.service.FeatureFlagService;
import ch.nacht.service.OrganisationService;
import ch.nacht.service.OrganizationContextService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FeatureFlagController.class)
@AutoConfigureMockMvc(addFilters = false)
public class FeatureFlagControllerTest {

    private static final Long ORG_ID = 1L;
    private static final String FLAG_KEY = FeatureFlag.MESSWERTE_UPLOAD.name();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FeatureFlagService featureFlagService;

    @MockitoBean
    private OrganizationContextService organizationContextService;

    @MockitoBean
    private OrganisationService organisationService;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        when(featureFlagService.getCurrentOrgId()).thenReturn(ORG_ID);
    }

    // ==================== GET /api/feature-flags ====================

    @Test
    void getEffectiveFlags_ReturnsMap() throws Exception {
        when(featureFlagService.getEffectiveFlags(ORG_ID))
                .thenReturn(Map.of(FLAG_KEY, true));

        mockMvc.perform(get("/api/feature-flags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$." + FLAG_KEY, is(true)));

        verify(featureFlagService).getEffectiveFlags(ORG_ID);
    }

    // ==================== GET /api/feature-flags/admin ====================

    @Test
    void getAdminFlags_ReturnsListOfDtos() throws Exception {
        FeatureFlagDTO dto = new FeatureFlagDTO(
                FLAG_KEY, "FEATURE_FLAG_MESSWERTE_UPLOAD", true, false, FeatureFlagDTO.Quelle.OVERRIDE);
        when(featureFlagService.getAdminFlags(ORG_ID)).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/feature-flags/admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].key", is(FLAG_KEY)))
                .andExpect(jsonPath("$[0].beschreibungKey", is("FEATURE_FLAG_MESSWERTE_UPLOAD")))
                .andExpect(jsonPath("$[0].defaultWert", is(true)))
                .andExpect(jsonPath("$[0].effektiv", is(false)))
                .andExpect(jsonPath("$[0].quelle", is("OVERRIDE")));

        verify(featureFlagService).getAdminFlags(ORG_ID);
    }

    // ==================== PUT /api/feature-flags/{key} ====================

    @Test
    void setOverride_ValidInput_ReturnsNoContent() throws Exception {
        String body = "{\"enabled\": false}";

        mockMvc.perform(put("/api/feature-flags/" + FLAG_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNoContent());

        verify(featureFlagService).setOverride(ORG_ID, FLAG_KEY, false);
    }

    @Test
    void setOverride_UnknownKey_ReturnsBadRequest() throws Exception {
        String body = "{\"enabled\": true}";
        doThrow(new IllegalArgumentException("Unbekanntes Feature-Flag: UNKNOWN_FLAG"))
                .when(featureFlagService).setOverride(eq(ORG_ID), eq("UNKNOWN_FLAG"), eq(true));

        mockMvc.perform(put("/api/feature-flags/UNKNOWN_FLAG")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void setOverride_MissingEnabled_ReturnsBadRequest() throws Exception {
        String body = "{}";

        mockMvc.perform(put("/api/feature-flags/" + FLAG_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verify(featureFlagService, never()).setOverride(anyLong(), anyString(), anyBoolean());
    }

    // ==================== DELETE /api/feature-flags/{key} ====================

    @Test
    void removeOverride_ValidKey_ReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/feature-flags/" + FLAG_KEY))
                .andExpect(status().isNoContent());

        verify(featureFlagService).removeOverride(ORG_ID, FLAG_KEY);
    }

    @Test
    void removeOverride_UnknownKey_ReturnsBadRequest() throws Exception {
        doThrow(new IllegalArgumentException("Unbekanntes Feature-Flag: UNKNOWN_FLAG"))
                .when(featureFlagService).removeOverride(eq(ORG_ID), eq("UNKNOWN_FLAG"));

        mockMvc.perform(delete("/api/feature-flags/UNKNOWN_FLAG"))
                .andExpect(status().isBadRequest());
    }
}
