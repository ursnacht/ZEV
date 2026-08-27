package ch.nacht.controller;

import ch.nacht.dto.PreiszeitreiheDownloadDTO;
import ch.nacht.dto.PreiszeitreihePunktDTO;
import ch.nacht.exception.FeatureDisabledException;
import ch.nacht.exception.PreiszeitreiheQuelleException;
import ch.nacht.service.OrganisationService;
import ch.nacht.service.OrganizationContextService;
import ch.nacht.service.PreiszeitreiheService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests der REST-Endpunkte der Preiszeitreihe (Specs/Preiszeitreihe.md, FR-4).
 *
 * <p>Schwerpunkt sind die <b>Statuscodes und die Rumpfform</b>: {@code 502} für einen Fehler der
 * Quelle (dort liegt der Fehler, nicht beim Aufrufer), {@code 400} für lokale Validierung und
 * Fehlkonfiguration — und in beiden Fällen <b>Klartext</b>. Ein Objekt-Rumpf erscheint in der Maske
 * als {@code [object Object]}; das ist in diesem Projekt schon zweimal passiert.
 *
 * <p>Die Permissions prüft {@code ControllerAuthorizationTest} mit echtem Security-Filter — hier
 * läuft er bewusst nicht ({@code addFilters = false}).
 */
@WebMvcTest(PreiszeitreiheController.class)
@AutoConfigureMockMvc(addFilters = false)
class PreiszeitreiheControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PreiszeitreiheService preiszeitreiheService;

    @MockitoBean
    private OrganizationContextService organizationContextService;

    // Gebraucht vom OrganizationInterceptor, den der WebMvc-Slice mitlaedt.
    @MockitoBean
    private OrganisationService organisationService;

    // ==================== GET ====================

    @Test
    void getPunkte_MitWerten_ReturnsListe() throws Exception {
        when(preiszeitreiheService.getPunkte(LocalDate.of(2026, 1, 15), LocalDate.of(2026, 1, 15)))
                .thenReturn(List.of(
                        new PreiszeitreihePunktDTO(LocalDateTime.of(2026, 1, 15, 11, 0),
                                new BigDecimal("0.13800")),
                        new PreiszeitreihePunktDTO(LocalDateTime.of(2026, 1, 15, 11, 15),
                                new BigDecimal("0.14200"))));

        mockMvc.perform(get("/api/preiszeitreihe?von=2026-01-15&bis=2026-01-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].zeit", is("2026-01-15T11:00:00")))
                .andExpect(jsonPath("$[0].preis", is(0.138)));
    }

    @Test
    void getPunkte_KeineWerte_ReturnsLeereListeUndNicht404() throws Exception {
        when(preiszeitreiheService.getPunkte(any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/preiszeitreihe?von=2026-01-15&bis=2026-01-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void getPunkte_UngueltigeSpanne_ReturnsBadRequestMitKlartext() throws Exception {
        when(preiszeitreiheService.getPunkte(any(), any()))
                .thenThrow(new IllegalArgumentException("Datum von muss vor oder gleich Datum bis liegen"));

        mockMvc.perform(get("/api/preiszeitreihe?von=2026-01-20&bis=2026-01-15"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Datum von")));
    }

    @Test
    void getPunkte_FlagAus_ReturnsForbidden() throws Exception {
        when(preiszeitreiheService.getPunkte(any(), any()))
                .thenThrow(new FeatureDisabledException("FEATURE_FLAG_DEAKTIVIERT"));

        mockMvc.perform(get("/api/preiszeitreihe?von=2026-01-15&bis=2026-01-15"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getPunkte_FehlenderParameter_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/preiszeitreihe?von=2026-01-15"))
                .andExpect(status().isBadRequest());

        verify(preiszeitreiheService, never()).getPunkte(any(), any());
    }

    @Test
    void getPunkte_UnlesbaresDatum_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/preiszeitreihe?von=15.01.2026&bis=2026-01-15"))
                .andExpect(status().isBadRequest());

        verify(preiszeitreiheService, never()).getPunkte(any(), any());
    }

    // ==================== POST /download ====================

    @Test
    void download_Erfolgreich_ReturnsZaehlwerte() throws Exception {
        when(preiszeitreiheService.download()).thenReturn(new PreiszeitreiheDownloadDTO(
                96, 90, 6, 0, LocalDateTime.of(2026, 8, 27, 15, 50)));

        mockMvc.perform(post("/api/preiszeitreihe/download"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.abgerufen", is(96)))
                .andExpect(jsonPath("$.neu", is(90)))
                .andExpect(jsonPath("$.aktualisiert", is(6)))
                .andExpect(jsonPath("$.uebersprungen", is(0)))
                .andExpect(jsonPath("$.publikation", is("2026-08-27T15:50:00")));
    }

    @Test
    void download_OhnePublikation_ReturnsNull() throws Exception {
        when(preiszeitreiheService.download())
                .thenReturn(new PreiszeitreiheDownloadDTO(96, 96, 0, 0, null));

        mockMvc.perform(post("/api/preiszeitreihe/download"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publikation", is(nullValue())));
    }

    /**
     * {@code 502} und nicht {@code 400}: Der Aufrufer hat alles richtig gemacht, die Quelle nicht.
     * Der Rumpf ist Klartext, damit die Maske ihn direkt anzeigen kann.
     */
    @Test
    void download_QuelleVersagt_ReturnsBadGatewayMitKlartext() throws Exception {
        when(preiszeitreiheService.download()).thenThrow(new PreiszeitreiheQuelleException(
                "Die Quelle der Einspeisepreise ist nicht erreichbar: timeout"));

        mockMvc.perform(post("/api/preiszeitreihe/download"))
                .andExpect(status().isBadGateway())
                .andExpect(content().string(containsString("nicht erreichbar")));
    }

    @Test
    void download_UrlNichtKonfiguriert_ReturnsBadRequestMitKlartext() throws Exception {
        when(preiszeitreiheService.download()).thenThrow(
                new IllegalArgumentException("Die Quelle der Einspeisepreise ist nicht konfiguriert"));

        mockMvc.perform(post("/api/preiszeitreihe/download"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("nicht konfiguriert")));
    }

    @Test
    void download_FlagAus_ReturnsForbidden() throws Exception {
        when(preiszeitreiheService.download())
                .thenThrow(new FeatureDisabledException("FEATURE_FLAG_DEAKTIVIERT"));

        mockMvc.perform(post("/api/preiszeitreihe/download"))
                .andExpect(status().isForbidden());
    }
}
