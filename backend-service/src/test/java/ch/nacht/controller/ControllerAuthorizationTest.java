package ch.nacht.controller;

import ch.nacht.config.SecurityConfig;
import ch.nacht.entity.Einheit;
import ch.nacht.entity.EinheitTyp;
import ch.nacht.service.EinheitMatchingService;
import ch.nacht.service.EinheitService;
import ch.nacht.service.EinstellungenService;
import ch.nacht.service.FeatureFlagService;
import ch.nacht.service.MieterService;
import ch.nacht.service.OrganisationService;
import ch.nacht.service.OrganizationContextService;
import ch.nacht.service.TarifpositionService;
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

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
@WebMvcTest({EinstellungenController.class, FeatureFlagController.class, TarifpositionController.class,
        EinheitController.class, MieterController.class})
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
    private TarifpositionService tarifpositionService;

    @MockitoBean
    private EinheitService einheitService;

    @MockitoBean
    private EinheitMatchingService einheitMatchingService;

    @MockitoBean
    private MieterService mieterService;

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

    // ==================== Tarifpositionen: mieter:read vs. rechnungen:manage ====================
    // Spec Ladestationen.md NFR-2: Lesen über mieter:read, Schreiben über rechnungen:manage.

    @Test
    void getTarifpositionen_withMieterRead_reachesController() throws Exception {
        when(tarifpositionService.getByEinheit(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/tarifpositionen?einheitId=1")
                        .with(jwt().authorities(new SimpleGrantedAuthority("mieter:read"))))
                .andExpect(status().isOk());
    }

    @Test
    void getTarifpositionen_withoutPermission_forbidden() throws Exception {
        mockMvc.perform(get("/api/tarifpositionen?einheitId=1")
                        .with(jwt().authorities(new SimpleGrantedAuthority("messwerte:read"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getTarifpositionen_unauthenticated_unauthorized() throws Exception {
        mockMvc.perform(get("/api/tarifpositionen?einheitId=1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createTarifposition_withRechnungenManage_reachesController() throws Exception {
        // Service weist die Eingabe fachlich ab -> 400 belegt, dass der Controller erreicht wurde
        when(tarifpositionService.saveTarifposition(any()))
                .thenThrow(new IllegalArgumentException("Tarif nicht gefunden"));

        mockMvc.perform(post("/api/tarifpositionen")
                        .with(jwt().authorities(new SimpleGrantedAuthority("rechnungen:manage")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"einheitId\":1,\"tarifId\":1,\"jahr\":2026,\"quartal\":1,\"menge\":10}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTarifposition_withMieterReadOnly_forbidden() throws Exception {
        // Lesen genügt zum Erfassen nicht
        mockMvc.perform(post("/api/tarifpositionen")
                        .with(jwt().authorities(new SimpleGrantedAuthority("mieter:read")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"einheitId\":1,\"tarifId\":1,\"jahr\":2026,\"quartal\":1,\"menge\":10}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteTarifposition_withMieterReadOnly_forbidden() throws Exception {
        mockMvc.perform(delete("/api/tarifpositionen/1")
                        .with(jwt().authorities(new SimpleGrantedAuthority("mieter:read")))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verify(tarifpositionService, never()).deleteTarifposition(any());
    }

    @Test
    void deleteTarifposition_withRechnungenManage_reachesController() throws Exception {
        when(tarifpositionService.deleteTarifposition(1L)).thenReturn(true);

        mockMvc.perform(delete("/api/tarifpositionen/1")
                        .with(jwt().authorities(new SimpleGrantedAuthority("rechnungen:manage")))
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    // ==================== Einheiten (inkl. Ladestationen): einheit:write ====================
    // Specs/Ladestationen.md NFR-2: Einheiten sind Stammdaten - Typ und messpunkt (RFID)
    // aendert nur, wer einheit:write besitzt.

    private static final String LADESTATION_JSON = """
            {"name":"Ladestation 1","typ":"LADESTATION","messpunkt":"RFID-001"}
            """;

    @Test
    void createEinheit_withEinheitWrite_reachesController() throws Exception {
        when(einheitService.createEinheit(any()))
                .thenReturn(new Einheit("Ladestation 1", EinheitTyp.LADESTATION));

        mockMvc.perform(post("/api/einheit")
                        .with(jwt().authorities(new SimpleGrantedAuthority("einheit:write")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LADESTATION_JSON))
                .andExpect(status().isCreated());
    }

    @Test
    void createEinheit_withReadPermissionOnly_forbidden() throws Exception {
        // zev_user hat einheit:read, aber nicht einheit:write
        mockMvc.perform(post("/api/einheit")
                        .with(jwt().authorities(new SimpleGrantedAuthority("einheit:read")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LADESTATION_JSON))
                .andExpect(status().isForbidden());

        verify(einheitService, never()).createEinheit(any());
    }

    @Test
    void updateEinheit_withoutPermission_forbidden() throws Exception {
        mockMvc.perform(put("/api/einheit/1")
                        .with(jwt().authorities(new SimpleGrantedAuthority("mieter:read")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LADESTATION_JSON))
                .andExpect(status().isForbidden());

        verify(einheitService, never()).updateEinheit(any(), any());
    }

    @Test
    void createEinheit_unauthenticated_unauthorized() throws Exception {
        mockMvc.perform(post("/api/einheit")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LADESTATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    // ==================== Mieter-Zuordnung: mieter:manage ====================

    private static final String MIETER_JSON = """
            {"name":"Nutzer ohne Wohnung","strasse":"Ladeweg 7","plz":"3000","ort":"Bern",
             "mietbeginn":"2026-01-01","einheitIds":[42]}
            """;

    @Test
    void createMieter_withMieterManage_reachesController() throws Exception {
        // Service weist fachlich ab -> 400 belegt, dass der Controller erreicht wurde
        when(mieterService.saveMieter(any()))
                .thenThrow(new IllegalArgumentException("Mindestens eine Einheit ist erforderlich"));

        mockMvc.perform(post("/api/mieter")
                        .with(jwt().authorities(new SimpleGrantedAuthority("mieter:manage")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MIETER_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createMieter_withMieterReadOnly_forbidden() throws Exception {
        // Lesen genuegt fuer die Zuordnung Mieter <-> Einheit nicht
        mockMvc.perform(post("/api/mieter")
                        .with(jwt().authorities(new SimpleGrantedAuthority("mieter:read")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MIETER_JSON))
                .andExpect(status().isForbidden());

        verify(mieterService, never()).saveMieter(any());
    }

    @Test
    void updateMieter_withMieterReadOnly_forbidden() throws Exception {
        mockMvc.perform(put("/api/mieter/1")
                        .with(jwt().authorities(new SimpleGrantedAuthority("mieter:read")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MIETER_JSON))
                .andExpect(status().isForbidden());

        verify(mieterService, never()).saveMieter(any());
    }

    @Test
    void getAllMieter_withMieterRead_reachesController() throws Exception {
        when(mieterService.getAllMieter()).thenReturn(List.of());

        mockMvc.perform(get("/api/mieter")
                        .with(jwt().authorities(new SimpleGrantedAuthority("mieter:read"))))
                .andExpect(status().isOk());
    }

    @Test
    void getAllMieter_unauthenticated_unauthorized() throws Exception {
        mockMvc.perform(get("/api/mieter"))
                .andExpect(status().isUnauthorized());
    }
}
