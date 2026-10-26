package ch.nacht.controller;

import ch.nacht.service.OrganisationService;
import ch.nacht.service.OrganizationContextService;
import ch.nacht.service.VersionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Antwort von {@code GET /api/version} (Specs/Startseite.md, FR-4). Die Berechtigung prüft
 * {@code ControllerAuthorizationTest} mit echter Security.
 */
@WebMvcTest(VersionController.class)
@AutoConfigureMockMvc(addFilters = false)
public class VersionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VersionService versionService;

    @MockitoBean
    private OrganizationContextService organizationContextService;

    @MockitoBean
    private OrganisationService organisationService;

    @Test
    void getVersion_AlleWerte_ReturnsSchemaBuildUndSpringBoot() throws Exception {
        when(versionService.getSchemaVersion()).thenReturn("188");
        when(versionService.getBuildTime()).thenReturn("26.10.2026 10:09");
        when(versionService.getSpringBootVersion()).thenReturn("4.1.2");

        mockMvc.perform(get("/api/version"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemaVersion", is("188")))
                .andExpect(jsonPath("$.buildTime", is("26.10.2026 10:09")))
                .andExpect(jsonPath("$.springBootVersion", is("4.1.2")));
    }

    @Test
    void getVersion_FehlendeWerte_AlsNull() throws Exception {
        when(versionService.getSchemaVersion()).thenReturn(null);
        when(versionService.getBuildTime()).thenReturn(null);
        when(versionService.getSpringBootVersion()).thenReturn(null);

        mockMvc.perform(get("/api/version"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemaVersion", nullValue()))
                .andExpect(jsonPath("$.buildTime", nullValue()))
                .andExpect(jsonPath("$.springBootVersion", nullValue()));
    }
}
