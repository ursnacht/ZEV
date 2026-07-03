package ch.nacht.controller;

import ch.nacht.config.SecurityConfig;
import ch.nacht.service.EinstellungenService;
import ch.nacht.service.FeatureFlagService;
import ch.nacht.service.OrganisationService;
import ch.nacht.service.OrganizationContextService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Autorisierungs-Slice-Test mit aktivierter Spring-Security (echter {@link SecurityConfig}).
 * <p>
 * Verifiziert die permission-basierte Absicherung ({@code hasAuthority('<permission>')}) end-to-end
 * über den Security-Filter: korrekte Permission → Zugriff, fehlende Permission → 403, fehlende
 * Authentifizierung → 401. Deckt insbesondere die Abgrenzung {@code org_admin} (darf Einstellungen,
 * aber keine Feature-Flags) ab.
 */
@WebMvcTest({EinstellungenController.class, FeatureFlagController.class})
@Import(SecurityConfig.class)
@TestPropertySource(properties = "app.cors.allowed-origins=http://localhost:4200")
class ControllerAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EinstellungenService einstellungenService;

    @MockitoBean
    private FeatureFlagService featureFlagService;

    @MockitoBean
    private OrganizationContextService organizationContextService;

    @MockitoBean
    private OrganisationService organisationService;

    // Erforderlich, damit der oauth2ResourceServer-Filterchain-Bean gebaut werden kann.
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        // OrganizationInterceptor läuft vor @PreAuthorize und würde ohne Organisation 403 werfen;
        // hier vorhandene Organisation simulieren, damit der Test isoliert die Permission prüft.
        when(organizationContextService.hasOrganization()).thenReturn(true);
    }

    // ==================== Einstellungen: einstellungen:write ====================

    @Test
    void getEinstellungen_withEinstellungenWrite_reachesController() throws Exception {
        when(einstellungenService.getEinstellungen()).thenReturn(null); // -> 204 No Content

        mockMvc.perform(get("/api/einstellungen")
                        .with(jwt().authorities(new SimpleGrantedAuthority("einstellungen:write"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void getEinstellungen_withoutPermission_forbidden() throws Exception {
        mockMvc.perform(get("/api/einstellungen")
                        .with(jwt().authorities(new SimpleGrantedAuthority("messwerte:read"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getEinstellungen_unauthenticated_unauthorized() throws Exception {
        mockMvc.perform(get("/api/einstellungen"))
                .andExpect(status().isUnauthorized());
    }

    // ==================== Feature-Flags: read vs. manage ====================

    @Test
    void getEffectiveFlags_withFeatureflagsRead_ok() throws Exception {
        when(featureFlagService.getCurrentOrgId()).thenReturn(1L);
        when(featureFlagService.getEffectiveFlags(any())).thenReturn(Map.of());

        mockMvc.perform(get("/api/feature-flags")
                        .with(jwt().authorities(new SimpleGrantedAuthority("featureflags:read"))))
                .andExpect(status().isOk());
    }

    @Test
    void setFlag_withFeatureflagsManage_reachesController() throws Exception {
        when(featureFlagService.getCurrentOrgId()).thenReturn(1L);

        mockMvc.perform(put("/api/feature-flags/MESSWERTE_UPLOAD")
                        .with(jwt().authorities(new SimpleGrantedAuthority("featureflags:manage")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void setFlag_withOrgAdminPermissionOnly_forbidden() throws Exception {
        // org_admin besitzt einstellungen:write, aber NICHT featureflags:manage
        mockMvc.perform(put("/api/feature-flags/MESSWERTE_UPLOAD")
                        .with(jwt().authorities(new SimpleGrantedAuthority("einstellungen:write")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isForbidden());
    }
}
