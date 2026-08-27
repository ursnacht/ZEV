package ch.nacht.controller;

import ch.nacht.config.SecurityConfig;
import ch.nacht.dto.NkRechnungLaufDTO;
import ch.nacht.entity.Einheit;
import ch.nacht.entity.EinheitTyp;
import ch.nacht.service.EinheitMatchingService;
import ch.nacht.service.EinheitService;
import ch.nacht.service.EinstellungenService;
import ch.nacht.service.FeatureFlagService;
import ch.nacht.service.MieterService;
import ch.nacht.service.NkAbrechnungService;
import ch.nacht.service.NkRechnungService;
import ch.nacht.service.SystemmeldungService;
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

import org.springframework.data.domain.SliceImpl;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
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
        EinheitController.class, MieterController.class, NkAbrechnungController.class,
        NkRechnungController.class, SystemmeldungController.class})
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
    private NkRechnungService nkRechnungService;

    @MockitoBean
    private TarifpositionService tarifpositionService;

    @MockitoBean
    private EinheitService einheitService;

    @MockitoBean
    private EinheitMatchingService einheitMatchingService;

    @MockitoBean
    private MieterService mieterService;

    @MockitoBean
    private NkAbrechnungService nkAbrechnungService;

    @MockitoBean
    private SystemmeldungService systemmeldungService;

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

    // ==================== Nebenkostenabrechnung: nebenkosten:manage ====================
    // Specs/Nebenkosten/Abrechnung.md NFR-2: @PreAuthorize auf Klassenebene - die Permission gilt
    // fuer jeden Endpunkt des Controllers, lesend wie schreibend.

    @Test
    void getAbrechnungen_withNebenkostenManage_reachesController() throws Exception {
        when(nkAbrechnungService.getAllAbrechnungen()).thenReturn(List.of());

        mockMvc.perform(get("/api/nebenkosten/abrechnungen")
                        .with(jwt().authorities(new SimpleGrantedAuthority("nebenkosten:manage"))))
                .andExpect(status().isOk());
    }

    @Test
    void getAbrechnungen_withoutPermission_forbidden() throws Exception {
        // Lesen ist hier nicht getrennt: Wer die Abrechnung nicht verwalten darf, sieht sie nicht.
        mockMvc.perform(get("/api/nebenkosten/abrechnungen")
                        .with(jwt().authorities(new SimpleGrantedAuthority("mieter:read"))))
                .andExpect(status().isForbidden());

        verify(nkAbrechnungService, never()).getAllAbrechnungen();
    }

    @Test
    void getAbrechnungen_unauthenticated_unauthorized() throws Exception {
        mockMvc.perform(get("/api/nebenkosten/abrechnungen"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createAbrechnung_withoutPermission_forbidden() throws Exception {
        mockMvc.perform(post("/api/nebenkosten/abrechnungen")
                        .with(jwt().authorities(new SimpleGrantedAuthority("einstellungen:write")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bezeichnung":"NK 2026","datumVon":"2026-01-01",
                                 "datumBis":"2026-12-31","anzahlWohnungen":9}
                                """))
                .andExpect(status().isForbidden());

        verify(nkAbrechnungService, never()).createAbrechnung(any());
    }

    @Test
    void deleteAbrechnung_withoutPermission_forbidden() throws Exception {
        mockMvc.perform(delete("/api/nebenkosten/abrechnungen/1")
                        .with(jwt().authorities(new SimpleGrantedAuthority("mieter:manage")))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verify(nkAbrechnungService, never()).deleteAbrechnung(any());
    }

    // ==================== Systemmeldungen: read gegen manage ====================
    // Specs/Systemmeldungen.md FR-1.7: Wer nur systemmeldungen:read besitzt, sieht die Liste,
    // darf aber nichts umschalten und nichts loeschen.

    @Test
    void getSystemmeldungen_withSystemmeldungenRead_reachesController() throws Exception {
        when(systemmeldungService.getSeite(any(), any(), any(), anyInt(), anyInt(), any(), any()))
                .thenReturn(new SliceImpl<>(List.of()));

        mockMvc.perform(get("/api/systemmeldungen")
                        .with(jwt().authorities(new SimpleGrantedAuthority("systemmeldungen:read"))))
                .andExpect(status().isOk());
    }

    @Test
    void getSystemmeldungen_withoutPermission_forbidden() throws Exception {
        mockMvc.perform(get("/api/systemmeldungen")
                        .with(jwt().authorities(new SimpleGrantedAuthority("mieter:read"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getSystemmeldungen_withoutAuthentication_unauthorized() throws Exception {
        mockMvc.perform(get("/api/systemmeldungen"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void setErledigt_withReadOnly_forbidden() throws Exception {
        // Der Kern von FR-1.7: Lesen genuegt zum Umschalten nicht.
        mockMvc.perform(put("/api/systemmeldungen/1/erledigt")
                        .param("erledigt", "true")
                        .with(jwt().authorities(new SimpleGrantedAuthority("systemmeldungen:read")))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verify(systemmeldungService, never()).setErledigt(any(), anyBoolean());
    }

    @Test
    void setErledigt_withSystemmeldungenManage_reachesController() throws Exception {
        when(systemmeldungService.setErledigt(1L, true))
                .thenThrow(new IllegalArgumentException("SYSTEMMELDUNG_NICHT_GEFUNDEN"));

        // 404 belegt, dass der Controller erreicht wurde - die Autorisierung hat also gegriffen.
        mockMvc.perform(put("/api/systemmeldungen/1/erledigt")
                        .param("erledigt", "true")
                        .with(jwt().authorities(new SimpleGrantedAuthority("systemmeldungen:manage")))
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteSystemmeldung_withReadOnly_forbidden() throws Exception {
        mockMvc.perform(delete("/api/systemmeldungen/1")
                        .with(jwt().authorities(new SimpleGrantedAuthority("systemmeldungen:read")))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verify(systemmeldungService, never()).delete(any());
    }

    @Test
    void deleteSystemmeldung_withSystemmeldungenManage_reachesController() throws Exception {
        when(systemmeldungService.delete(1L)).thenReturn(true);

        mockMvc.perform(delete("/api/systemmeldungen/1")
                        .with(jwt().authorities(new SimpleGrantedAuthority("systemmeldungen:manage")))
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    /**
     * Das Aufraeumen loescht alle erledigten Meldungen des Mandanten auf einen Schlag und ist
     * nicht umkehrbar - {@code systemmeldungen:read} darf das keinesfalls koennen.
     */
    @Test
    void deleteErledigte_withReadOnly_forbidden() throws Exception {
        mockMvc.perform(delete("/api/systemmeldungen/erledigt")
                        .with(jwt().authorities(new SimpleGrantedAuthority("systemmeldungen:read")))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verify(systemmeldungService, never()).loescheAlleErledigten();
    }

    @Test
    void deleteErledigte_withSystemmeldungenManage_reachesController() throws Exception {
        when(systemmeldungService.loescheAlleErledigten()).thenReturn(3);

        mockMvc.perform(delete("/api/systemmeldungen/erledigt")
                        .with(jwt().authorities(new SimpleGrantedAuthority("systemmeldungen:manage")))
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    // ==================== NK-Rechnungen: nebenkosten:manage UND rechnungen:manage ====================
    // Specs/Nebenkosten/RechnungenGenerieren.md NFR-2: Es ist eine NK-Aktion, aber sie stellt
    // Rechnungen und bucht Forderungen. Heute halten alle drei Fachrollen beide Permissions - die
    // Forderung ist also keine Einschraenkung, bleibt aber richtig, wenn die Rollen auseinanderlaufen.

    @Test
    void erzeugeRechnungen_withBothPermissions_reachesController() throws Exception {
        when(nkRechnungService.erzeugeRechnungen(anyLong(), any())).thenReturn(new NkRechnungLaufDTO());

        mockMvc.perform(post("/api/nebenkosten/abrechnungen/12/rechnungen")
                        .with(jwt().authorities(
                                new SimpleGrantedAuthority("nebenkosten:manage"),
                                new SimpleGrantedAuthority("rechnungen:manage")))
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void erzeugeRechnungen_onlyNebenkostenManage_forbidden() throws Exception {
        mockMvc.perform(post("/api/nebenkosten/abrechnungen/12/rechnungen")
                        .with(jwt().authorities(new SimpleGrantedAuthority("nebenkosten:manage")))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verify(nkRechnungService, never()).erzeugeRechnungen(anyLong(), any());
    }

    @Test
    void erzeugeRechnungen_onlyRechnungenManage_forbidden() throws Exception {
        mockMvc.perform(post("/api/nebenkosten/abrechnungen/12/rechnungen")
                        .with(jwt().authorities(new SimpleGrantedAuthority("rechnungen:manage")))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verify(nkRechnungService, never()).erzeugeRechnungen(anyLong(), any());
    }

    @Test
    void erzeugeRechnungen_unauthenticated_unauthorized() throws Exception {
        mockMvc.perform(post("/api/nebenkosten/abrechnungen/12/rechnungen").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    /** Der Download haengt am selben Controller und damit an denselben beiden Permissions. */
    @Test
    void ladeRechnungPdf_withBothPermissions_reachesController() throws Exception {
        when(nkRechnungService.ladePdf(anyLong(), anyLong())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/nebenkosten/abrechnungen/12/rechnungen/45/pdf")
                        .with(jwt().authorities(
                                new SimpleGrantedAuthority("nebenkosten:manage"),
                                new SimpleGrantedAuthority("rechnungen:manage"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void ladeRechnungPdf_withoutPermission_forbidden() throws Exception {
        mockMvc.perform(get("/api/nebenkosten/abrechnungen/12/rechnungen/45/pdf")
                        .with(jwt().authorities(new SimpleGrantedAuthority("mieter:read"))))
                .andExpect(status().isForbidden());

        verify(nkRechnungService, never()).ladePdf(anyLong(), anyLong());
    }
}
