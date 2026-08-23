package ch.nacht.controller;

import ch.nacht.dto.NkAbrechnungDetailDTO;
import ch.nacht.dto.NkPositionDTO;
import ch.nacht.entity.Mengeneinheit;
import ch.nacht.entity.NkAbrechnung;
import ch.nacht.entity.NkPositionsart;
import ch.nacht.exception.FeatureDisabledException;
import ch.nacht.service.NkAbrechnungService;
import ch.nacht.service.OrganisationService;
import ch.nacht.service.OrganizationContextService;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests der REST-Endpunkte aus {@code Specs/Nebenkosten/Abrechnung.md} FR-6.
 *
 * <p>Der Feature-Flag wird im Service geprueft; hier wird nur belegt, dass die daraus entstehende
 * {@link FeatureDisabledException} tatsaechlich als {@code 403} beim Client ankommt — der Flag
 * waere sonst reine Kosmetik.
 */
@WebMvcTest(NkAbrechnungController.class)
@AutoConfigureMockMvc(addFilters = false)
public class NkAbrechnungControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NkAbrechnungService nkAbrechnungService;

    @MockitoBean
    private OrganizationContextService organizationContextService;

    @MockitoBean
    private OrganisationService organisationService;

    private ObjectMapper objectMapper;

    private NkAbrechnung testAbrechnung1;
    private NkAbrechnung testAbrechnung2;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        testAbrechnung1 = abrechnung(1L, "Nebenkostenabrechnung 2025", LocalDate.of(2025, 1, 1));
        testAbrechnung2 = abrechnung(2L, "Nebenkostenabrechnung 2024", LocalDate.of(2024, 1, 1));
    }

    // ==================== GET /api/nebenkosten/abrechnungen ====================

    @Test
    void getAllAbrechnungen_ReturnsList() throws Exception {
        when(nkAbrechnungService.getAllAbrechnungen())
                .thenReturn(List.of(testAbrechnung1, testAbrechnung2));

        mockMvc.perform(get("/api/nebenkosten/abrechnungen"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].bezeichnung", is("Nebenkostenabrechnung 2025")))
                .andExpect(jsonPath("$[0].anzahlWohnungen", is(9)));
    }

    @Test
    void getAllAbrechnungen_KeineAbrechnungen_ReturnsEmptyList() throws Exception {
        when(nkAbrechnungService.getAllAbrechnungen()).thenReturn(List.of());

        mockMvc.perform(get("/api/nebenkosten/abrechnungen"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void getAllAbrechnungen_FeatureFlagAus_ReturnsForbidden() throws Exception {
        when(nkAbrechnungService.getAllAbrechnungen())
                .thenThrow(new FeatureDisabledException("FEATURE_FLAG_DEAKTIVIERT"));

        mockMvc.perform(get("/api/nebenkosten/abrechnungen"))
                .andExpect(status().isForbidden());
    }

    // ==================== GET /vorlage ====================

    @Test
    void getVorlage_ReturnsVorschlagAnzahlWohnungen() throws Exception {
        NkAbrechnungDetailDTO vorlage = new NkAbrechnungDetailDTO();
        vorlage.setAbrechnung(new NkAbrechnung());
        vorlage.setAnzahlWohnungenVorschlag(9);
        when(nkAbrechnungService.getVorlage()).thenReturn(vorlage);

        mockMvc.perform(get("/api/nebenkosten/abrechnungen/vorlage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anzahlWohnungenVorschlag", is(9)));
    }

    @Test
    void getVorlage_OhneConsumerEinheiten_VorschlagIstNull() throws Exception {
        NkAbrechnungDetailDTO vorlage = new NkAbrechnungDetailDTO();
        vorlage.setAbrechnung(new NkAbrechnung());
        when(nkAbrechnungService.getVorlage()).thenReturn(vorlage);

        mockMvc.perform(get("/api/nebenkosten/abrechnungen/vorlage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anzahlWohnungenVorschlag").doesNotExist());
    }

    // ==================== GET /{id} ====================

    @Test
    void getAbrechnungById_Found_ReturnsDetail() throws Exception {
        NkAbrechnungDetailDTO detail = new NkAbrechnungDetailDTO();
        detail.setAbrechnung(testAbrechnung1);
        detail.setPositionen(List.of(umlageDTO()));
        when(nkAbrechnungService.getAbrechnungDetail(1L)).thenReturn(Optional.of(detail));

        mockMvc.perform(get("/api/nebenkosten/abrechnungen/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.abrechnung.bezeichnung", is("Nebenkostenabrechnung 2025")))
                .andExpect(jsonPath("$.positionen", hasSize(1)))
                .andExpect(jsonPath("$.positionen[0].art", is("UMLAGE")));
    }

    @Test
    void getAbrechnungById_NotFound_Returns404() throws Exception {
        // Der Zugriff auf eine fremde Abrechnung endet ueber den orgFilter im selben 404.
        when(nkAbrechnungService.getAbrechnungDetail(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/nebenkosten/abrechnungen/999"))
                .andExpect(status().isNotFound());
    }

    // ==================== POST ====================

    @Test
    void createAbrechnung_ValidInput_ReturnsCreated() throws Exception {
        when(nkAbrechnungService.createAbrechnung(any())).thenReturn(testAbrechnung1);

        mockMvc.perform(post("/api/nebenkosten/abrechnungen")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testAbrechnung1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(1)));
    }

    @Test
    void createAbrechnung_ServiceLehntAb_ReturnsBadRequest() throws Exception {
        when(nkAbrechnungService.createAbrechnung(any()))
                .thenThrow(new IllegalArgumentException("NK_FEHLER_ZEITRAUM"));

        mockMvc.perform(post("/api/nebenkosten/abrechnungen")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testAbrechnung1)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createAbrechnung_OhneBezeichnung_ReturnsBadRequest() throws Exception {
        // Bean Validation greift vor dem Service (@Valid am Rumpf).
        NkAbrechnung ohneBezeichnung = abrechnung(null, null, LocalDate.of(2025, 1, 1));

        mockMvc.perform(post("/api/nebenkosten/abrechnungen")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ohneBezeichnung)))
                .andExpect(status().isBadRequest());

        verify(nkAbrechnungService, never()).createAbrechnung(any());
    }

    @Test
    void createAbrechnung_AnzahlWohnungenNull_ReturnsBadRequest() throws Exception {
        NkAbrechnung ungueltig = abrechnung(null, "Nebenkostenabrechnung 2025", LocalDate.of(2025, 1, 1));
        ungueltig.setAnzahlWohnungen(0);

        mockMvc.perform(post("/api/nebenkosten/abrechnungen")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ungueltig)))
                .andExpect(status().isBadRequest());

        verify(nkAbrechnungService, never()).createAbrechnung(any());
    }

    // ==================== PUT /{id} ====================

    @Test
    void updateAbrechnung_ValidInput_ReturnsOk() throws Exception {
        NkAbrechnungDetailDTO detail = new NkAbrechnungDetailDTO();
        detail.setAbrechnung(testAbrechnung1);
        when(nkAbrechnungService.saveAbrechnung(eq(1L), any())).thenReturn(Optional.of(detail));

        mockMvc.perform(put("/api/nebenkosten/abrechnungen/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(detail)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.abrechnung.id", is(1)));
    }

    @Test
    void updateAbrechnung_NotFound_Returns404() throws Exception {
        when(nkAbrechnungService.saveAbrechnung(eq(999L), any())).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/nebenkosten/abrechnungen/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateAbrechnung_BereitsAbgerechnet_ReturnsBadRequest() throws Exception {
        when(nkAbrechnungService.saveAbrechnung(eq(1L), any()))
                .thenThrow(new IllegalArgumentException("NK_FEHLER_ABGERECHNET"));

        mockMvc.perform(put("/api/nebenkosten/abrechnungen/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateAbrechnung_NennerZuKlein_ReturnsBadRequestMitBeidenWerten() throws Exception {
        when(nkAbrechnungService.saveAbrechnung(eq(1L), any()))
                .thenThrow(new IllegalArgumentException("NK_FEHLER_NENNER_ZU_KLEIN: 3285 > 1825"));

        mockMvc.perform(put("/api/nebenkosten/abrechnungen/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().string(org.hamcrest.Matchers.containsString("3285 > 1825")));
    }

    // ==================== PATCH /{id}/abgerechnet ====================

    @Test
    void setAbgerechnet_True_ReturnsOk() throws Exception {
        testAbrechnung1.setAbgerechnet(true);
        when(nkAbrechnungService.setAbgerechnet(1L, true)).thenReturn(Optional.of(testAbrechnung1));

        mockMvc.perform(patch("/api/nebenkosten/abrechnungen/1/abgerechnet")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"abgerechnet\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.abgerechnet", is(true)));
    }

    @Test
    void setAbgerechnet_False_GibtDieAbrechnungWiederFrei() throws Exception {
        when(nkAbrechnungService.setAbgerechnet(1L, false)).thenReturn(Optional.of(testAbrechnung1));

        mockMvc.perform(patch("/api/nebenkosten/abrechnungen/1/abgerechnet")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"abgerechnet\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.abgerechnet", is(false)));
    }

    @Test
    void setAbgerechnet_OhneWert_ReturnsBadRequest() throws Exception {
        mockMvc.perform(patch("/api/nebenkosten/abrechnungen/1/abgerechnet")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(nkAbrechnungService, never()).setAbgerechnet(anyLong(), anyBoolean());
    }

    @Test
    void setAbgerechnet_NotFound_Returns404() throws Exception {
        when(nkAbrechnungService.setAbgerechnet(999L, true)).thenReturn(Optional.empty());

        mockMvc.perform(patch("/api/nebenkosten/abrechnungen/999/abgerechnet")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"abgerechnet\":true}"))
                .andExpect(status().isNotFound());
    }

    // ==================== DELETE /{id} ====================

    @Test
    void deleteAbrechnung_Exists_ReturnsNoContent() throws Exception {
        when(nkAbrechnungService.deleteAbrechnung(1L)).thenReturn(true);

        mockMvc.perform(delete("/api/nebenkosten/abrechnungen/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteAbrechnung_NotFound_Returns404() throws Exception {
        when(nkAbrechnungService.deleteAbrechnung(999L)).thenReturn(false);

        mockMvc.perform(delete("/api/nebenkosten/abrechnungen/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteAbrechnung_FeatureFlagAus_ReturnsForbidden() throws Exception {
        when(nkAbrechnungService.deleteAbrechnung(1L))
                .thenThrow(new FeatureDisabledException("FEATURE_FLAG_DEAKTIVIERT"));

        mockMvc.perform(delete("/api/nebenkosten/abrechnungen/1"))
                .andExpect(status().isForbidden());
    }

    // ==================== Testdaten ====================

    private NkAbrechnung abrechnung(Long id, String bezeichnung, LocalDate von) {
        NkAbrechnung abrechnung = new NkAbrechnung();
        abrechnung.setId(id);
        abrechnung.setOrgId(1L);
        abrechnung.setBezeichnung(bezeichnung);
        abrechnung.setDatumVon(von);
        abrechnung.setDatumBis(von.plusYears(1).minusDays(1));
        abrechnung.setAnzahlWohnungen(9);
        return abrechnung;
    }

    private NkPositionDTO umlageDTO() {
        NkPositionDTO dto = new NkPositionDTO();
        dto.setId(10L);
        dto.setArt(NkPositionsart.UMLAGE);
        dto.setBezeichnung("Allgemeinstrom");
        dto.setReihenfolge(1);
        dto.setEinheit(Mengeneinheit.M3);
        dto.setTotalbetrag(new BigDecimal("900.00"));
        return dto;
    }
}
