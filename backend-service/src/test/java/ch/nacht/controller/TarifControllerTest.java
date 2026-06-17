package ch.nacht.controller;

import ch.nacht.entity.Tarif;
import ch.nacht.entity.TarifTyp;
import ch.nacht.exception.TarifLuecke;
import ch.nacht.exception.TarifLueckePeriode;
import ch.nacht.service.OrganisationService;
import ch.nacht.service.OrganizationContextService;
import ch.nacht.service.TarifService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TarifController.class)
@AutoConfigureMockMvc(addFilters = false)
public class TarifControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TarifService tarifService;

    @MockitoBean
    private OrganizationContextService organizationContextService;

    @MockitoBean
    private OrganisationService organisationService;

    private ObjectMapper objectMapper;

    private Tarif zevTarif;
    private Tarif vnbTarif;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        zevTarif = new Tarif(
            "ZEV Tarif 2024",
            TarifTyp.ZEV,
            new BigDecimal("0.20000"),
            LocalDate.of(2024, 1, 1),
            LocalDate.of(2024, 12, 31)
        );
        zevTarif.setId(1L);

        vnbTarif = new Tarif(
            "VNB Tarif 2024",
            TarifTyp.VNB,
            new BigDecimal("0.34192"),
            LocalDate.of(2024, 1, 1),
            LocalDate.of(2024, 12, 31)
        );
        vnbTarif.setId(2L);
    }

    @Test
    void getAllTarife_ReturnsListOfTarife() throws Exception {
        when(tarifService.getAllTarife()).thenReturn(Arrays.asList(zevTarif, vnbTarif));

        mockMvc.perform(get("/api/tarife"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].bezeichnung", is("ZEV Tarif 2024")))
            .andExpect(jsonPath("$[1].bezeichnung", is("VNB Tarif 2024")));
    }

    @Test
    void getTarifById_Found_ReturnsTarif() throws Exception {
        when(tarifService.getTarifById(1L)).thenReturn(Optional.of(zevTarif));

        mockMvc.perform(get("/api/tarife/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.bezeichnung", is("ZEV Tarif 2024")))
            .andExpect(jsonPath("$.tariftyp", is("ZEV")))
            .andExpect(jsonPath("$.preis", is(0.20000)));
    }

    @Test
    void getTarifById_NotFound_Returns404() throws Exception {
        when(tarifService.getTarifById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/tarife/999"))
            .andExpect(status().isNotFound());
    }

    @Test
    void createTarif_ValidInput_ReturnsCreated() throws Exception {
        Tarif newTarif = new Tarif(
            "ZEV 2025",
            TarifTyp.ZEV,
            new BigDecimal("0.21000"),
            LocalDate.of(2025, 1, 1),
            LocalDate.of(2025, 12, 31)
        );

        when(tarifService.saveTarif(any(Tarif.class))).thenReturn(newTarif);

        mockMvc.perform(post("/api/tarife")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(newTarif)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.bezeichnung", is("ZEV 2025")));
    }

    @Test
    void createTarif_EmptyBezeichnung_ReturnsBadRequest() throws Exception {
        Tarif invalidTarif = new Tarif(
            "",  // empty
            TarifTyp.ZEV,
            new BigDecimal("0.20000"),
            LocalDate.of(2024, 1, 1),
            LocalDate.of(2024, 12, 31)
        );

        mockMvc.perform(post("/api/tarife")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidTarif)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createTarif_NullTariftyp_ReturnsBadRequest() throws Exception {
        String json = """
            {
                "bezeichnung": "Test",
                "tariftyp": null,
                "preis": 0.20,
                "gueltigVon": "2024-01-01",
                "gueltigBis": "2024-12-31"
            }
            """;

        mockMvc.perform(post("/api/tarife")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createTarif_NegativePreis_ReturnsBadRequest() throws Exception {
        Tarif invalidTarif = new Tarif(
            "Test",
            TarifTyp.ZEV,
            new BigDecimal("-0.10"),  // negative
            LocalDate.of(2024, 1, 1),
            LocalDate.of(2024, 12, 31)
        );

        mockMvc.perform(post("/api/tarife")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidTarif)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createTarif_OverlappingTarif_ReturnsBadRequest() throws Exception {
        Tarif overlapping = new Tarif(
            "ZEV 2024 Alt",
            TarifTyp.ZEV,
            new BigDecimal("0.19000"),
            LocalDate.of(2024, 6, 1),
            LocalDate.of(2024, 12, 31)
        );

        when(tarifService.saveTarif(any(Tarif.class)))
            .thenThrow(new IllegalArgumentException("Tarif überschneidet sich mit bestehendem Tarif"));

        mockMvc.perform(post("/api/tarife")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(overlapping)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void updateTarif_ValidInput_ReturnsOk() throws Exception {
        zevTarif.setBezeichnung("ZEV 2024 Updated");
        when(tarifService.getTarifById(1L)).thenReturn(Optional.of(zevTarif));
        when(tarifService.saveTarif(any(Tarif.class))).thenReturn(zevTarif);

        mockMvc.perform(put("/api/tarife/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(zevTarif)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.bezeichnung", is("ZEV 2024 Updated")));
    }

    @Test
    void updateTarif_NotFound_Returns404() throws Exception {
        when(tarifService.getTarifById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/tarife/999")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(zevTarif)))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteTarif_Exists_ReturnsNoContent() throws Exception {
        when(tarifService.deleteTarif(1L)).thenReturn(true);

        mockMvc.perform(delete("/api/tarife/1"))
            .andExpect(status().isNoContent());

        verify(tarifService).deleteTarif(1L);
    }

    @Test
    void deleteTarif_NotFound_Returns404() throws Exception {
        when(tarifService.deleteTarif(999L)).thenReturn(false);

        mockMvc.perform(delete("/api/tarife/999"))
            .andExpect(status().isNotFound());
    }

    // ==================== Validation Endpoint Tests ====================

    @Test
    void validateTarife_QuartaleMode_ReturnsValidResult() throws Exception {
        TarifService.ValidationResult validResult = new TarifService.ValidationResult(true, Collections.emptyList());
        when(tarifService.validateQuartale()).thenReturn(validResult);

        mockMvc.perform(post("/api/tarife/validate?modus=quartale"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.valid", is(true)))
            .andExpect(jsonPath("$.luecken", hasSize(0)));

        verify(tarifService).validateQuartale();
        verify(tarifService, never()).validateJahre();
    }

    @Test
    void validateTarife_JahreMode_ReturnsValidResult() throws Exception {
        TarifService.ValidationResult validResult = new TarifService.ValidationResult(true, Collections.emptyList());
        when(tarifService.validateJahre()).thenReturn(validResult);

        mockMvc.perform(post("/api/tarife/validate?modus=jahre"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.valid", is(true)))
            .andExpect(jsonPath("$.luecken", hasSize(0)));

        verify(tarifService).validateJahre();
        verify(tarifService, never()).validateQuartale();
    }

    @Test
    void validateTarife_DefaultMode_UsesQuartale() throws Exception {
        TarifService.ValidationResult validResult = new TarifService.ValidationResult(true, Collections.emptyList());
        when(tarifService.validateQuartale()).thenReturn(validResult);

        mockMvc.perform(post("/api/tarife/validate"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.valid", is(true)));

        verify(tarifService).validateQuartale();
    }

    @Test
    void validateTarife_ValidationErrors_ReturnsErrorsInResponse() throws Exception {
        TarifService.ValidationResult invalidResult = new TarifService.ValidationResult(
            false,
            Arrays.asList(
                new TarifLueckePeriode("Q1/2024", List.of(new TarifLuecke("VNB", "01.01.2024", false))),
                new TarifLueckePeriode("Q2/2024", List.of(new TarifLuecke("ZEV", "01.04.2024", false)))
            )
        );
        when(tarifService.validateQuartale()).thenReturn(invalidResult);

        mockMvc.perform(post("/api/tarife/validate?modus=quartale"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.valid", is(false)))
            .andExpect(jsonPath("$.luecken", hasSize(2)))
            .andExpect(jsonPath("$.luecken[0].periode", is("Q1/2024")))
            .andExpect(jsonPath("$.luecken[0].luecken[0].tarifTyp", is("VNB")))
            .andExpect(jsonPath("$.luecken[0].luecken[0].datum", is("01.01.2024")))
            .andExpect(jsonPath("$.luecken[1].periode", is("Q2/2024")))
            .andExpect(jsonPath("$.luecken[1].luecken[0].tarifTyp", is("ZEV")));
    }

    @Test
    void validateTarife_NoTarife_ReturnsValidWithMessage() throws Exception {
        TarifService.ValidationResult noTarifeResult = new TarifService.ValidationResult(true, Collections.emptyList());
        when(tarifService.validateJahre()).thenReturn(noTarifeResult);

        mockMvc.perform(post("/api/tarife/validate?modus=jahre"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.valid", is(true)))
            .andExpect(jsonPath("$.luecken", hasSize(0)));
    }
}
