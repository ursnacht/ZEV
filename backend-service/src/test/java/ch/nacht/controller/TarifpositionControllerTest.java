package ch.nacht.controller;

import ch.nacht.dto.TarifpositionDTO;
import ch.nacht.entity.Erfassungsart;
import ch.nacht.entity.Einheit;
import ch.nacht.entity.EinheitTyp;
import ch.nacht.entity.Tarif;
import ch.nacht.entity.TarifTyp;
import ch.nacht.entity.Tarifposition;
import ch.nacht.service.OrganisationService;
import ch.nacht.service.OrganizationContextService;
import ch.nacht.service.TarifpositionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice-Tests für {@link TarifpositionController} (Spec Ladestromtarif.md).
 *
 * <p>Deckt die Pflicht-Tests je Endpoint sowie das DTO-Mapping ab: Mieter und Tarif werden nur
 * als ID transportiert, die Auflösung übernimmt der Service.
 */
@WebMvcTest(TarifpositionController.class)
@AutoConfigureMockMvc(addFilters = false)
public class TarifpositionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TarifpositionService tarifpositionService;

    @MockitoBean
    private OrganizationContextService organizationContextService;

    @MockitoBean
    private OrganisationService organisationService;

    private ObjectMapper objectMapper;

    private Einheit testEinheit;
    private Tarif ladestromTarif;
    private Tarifposition testPosition1;
    private Tarifposition testPosition2;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        testEinheit = new Einheit("Ladestation 1", EinheitTyp.LADESTATION);
        testEinheit.setId(1L);
        testEinheit.setMesspunkt("RFID-001");

        ladestromTarif = new Tarif(
                "Ladestrom",
                TarifTyp.LADESTROM,
                new BigDecimal("0.35000"),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31));
        ladestromTarif.setId(10L);

        testPosition1 = new Tarifposition(testEinheit, ladestromTarif, 2026, 3, new BigDecimal("120.500"));
        testPosition1.setId(1L);
        testPosition1.setQuellReferenz("LP-01");
        testPosition1.setBemerkung("Beleg 42");

        testPosition2 = new Tarifposition(testEinheit, ladestromTarif, 2026, 4, new BigDecimal("80.000"));
        testPosition2.setId(2L);
        testPosition2.setErfassungsart(Erfassungsart.IMPORT);
    }

    private TarifpositionDTO neuesDto() {
        TarifpositionDTO dto = new TarifpositionDTO();
        dto.setEinheitId(1L);
        dto.setTarifId(10L);
        dto.setJahr(2026);
        dto.setQuartal(3);
        dto.setMenge(new BigDecimal("120.500"));
        dto.setQuellReferenz("LP-01");
        dto.setBemerkung("Beleg 42");
        return dto;
    }

    // ==================== GET /api/tarifpositionen ====================

    @Test
    void getByEinheit_ReturnsListOfPositions() throws Exception {
        when(tarifpositionService.getByEinheit(1L))
                .thenReturn(Arrays.asList(testPosition1, testPosition2));

        mockMvc.perform(get("/api/tarifpositionen").param("einheitId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id", is(1)))
                .andExpect(jsonPath("$[0].einheitId", is(1)))
                .andExpect(jsonPath("$[0].einheitName", is("Ladestation 1")))
                .andExpect(jsonPath("$[0].tarifId", is(10)))
                .andExpect(jsonPath("$[0].tarifBezeichnung", is("Ladestrom")))
                .andExpect(jsonPath("$[0].tarifPreis", is(0.35000)))
                .andExpect(jsonPath("$[0].jahr", is(2026)))
                .andExpect(jsonPath("$[0].quartal", is(3)))
                .andExpect(jsonPath("$[0].menge", is(120.500)))
                .andExpect(jsonPath("$[0].erfassungsart", is("MANUELL")))
                .andExpect(jsonPath("$[0].quellReferenz", is("LP-01")))
                .andExpect(jsonPath("$[1].erfassungsart", is("IMPORT")));
    }

    @Test
    void getByMieter_NoPositions_ReturnsEmptyList() throws Exception {
        when(tarifpositionService.getByEinheit(1L)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/tarifpositionen").param("einheitId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void getByMieter_MissingEinheitId_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/tarifpositionen"))
                .andExpect(status().isBadRequest());

        verify(tarifpositionService, never()).getByEinheit(any());
    }

    // ==================== GET /api/tarifpositionen/{id} ====================

    @Test
    void getTarifpositionById_Found_ReturnsPosition() throws Exception {
        when(tarifpositionService.getTarifpositionById(1L)).thenReturn(Optional.of(testPosition1));

        mockMvc.perform(get("/api/tarifpositionen/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.tarifBezeichnung", is("Ladestrom")))
                .andExpect(jsonPath("$.bemerkung", is("Beleg 42")));
    }

    @Test
    void getTarifpositionById_NotFound_Returns404() throws Exception {
        when(tarifpositionService.getTarifpositionById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/tarifpositionen/999"))
                .andExpect(status().isNotFound());
    }

    // ==================== POST /api/tarifpositionen ====================

    @Test
    void createTarifposition_ValidInput_ReturnsCreated() throws Exception {
        when(tarifpositionService.saveTarifposition(any(Tarifposition.class))).thenReturn(testPosition1);

        mockMvc.perform(post("/api/tarifpositionen")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(neuesDto())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.menge", is(120.500)));
    }

    @Test
    void createTarifposition_MapsDtoIdsOntoEntity() throws Exception {
        when(tarifpositionService.saveTarifposition(any(Tarifposition.class))).thenReturn(testPosition1);

        mockMvc.perform(post("/api/tarifpositionen")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(neuesDto())))
                .andExpect(status().isCreated());

        ArgumentCaptor<Tarifposition> captor = ArgumentCaptor.forClass(Tarifposition.class);
        verify(tarifpositionService).saveTarifposition(captor.capture());
        Tarifposition uebergeben = captor.getValue();

        // Beim Anlegen darf keine ID mitgegeben werden
        assertNull(uebergeben.getId());
        assertEquals(1L, uebergeben.getEinheit().getId());
        assertEquals(10L, uebergeben.getTarif().getId());
        assertEquals(2026, uebergeben.getJahr());
        assertEquals(3, uebergeben.getQuartal());
        assertEquals("LP-01", uebergeben.getQuellReferenz());
    }

    @Test
    void createTarifposition_DuplicatePosition_ReturnsBadRequest() throws Exception {
        when(tarifpositionService.saveTarifposition(any(Tarifposition.class)))
                .thenThrow(new IllegalArgumentException(
                        "Für diesen Mieter und dieses Quartal existiert bereits eine Position"));

        mockMvc.perform(post("/api/tarifpositionen")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(neuesDto())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTarifposition_InvalidTariftyp_ReturnsBadRequest() throws Exception {
        when(tarifpositionService.saveTarifposition(any(Tarifposition.class)))
                .thenThrow(new IllegalArgumentException(
                        "Für den Tariftyp ZEV können keine Positionen erfasst werden"));

        mockMvc.perform(post("/api/tarifpositionen")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(neuesDto())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTarifposition_UnknownMieter_ReturnsBadRequest() throws Exception {
        when(tarifpositionService.saveTarifposition(any(Tarifposition.class)))
                .thenThrow(new IllegalArgumentException("Mieter nicht gefunden"));

        mockMvc.perform(post("/api/tarifpositionen")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(neuesDto())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTarifposition_MalformedJson_ReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/tarifpositionen")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jahr\": \"keine Zahl\"}"))
                .andExpect(status().isBadRequest());

        verify(tarifpositionService, never()).saveTarifposition(any());
    }

    // ==================== PUT /api/tarifpositionen/{id} ====================

    @Test
    void updateTarifposition_ValidInput_ReturnsOk() throws Exception {
        testPosition1.setMenge(new BigDecimal("200.000"));
        when(tarifpositionService.getTarifpositionById(1L)).thenReturn(Optional.of(testPosition1));
        when(tarifpositionService.saveTarifposition(any(Tarifposition.class))).thenReturn(testPosition1);

        TarifpositionDTO dto = neuesDto();
        dto.setId(1L);
        dto.setMenge(new BigDecimal("200.000"));

        mockMvc.perform(put("/api/tarifpositionen/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.menge", is(200.000)));
    }

    @Test
    void updateTarifposition_UsesPathIdNotBodyId() throws Exception {
        when(tarifpositionService.getTarifpositionById(1L)).thenReturn(Optional.of(testPosition1));
        when(tarifpositionService.saveTarifposition(any(Tarifposition.class))).thenReturn(testPosition1);

        TarifpositionDTO dto = neuesDto();
        dto.setId(999L); // abweichende ID im Body darf nicht gewinnen

        mockMvc.perform(put("/api/tarifpositionen/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());

        ArgumentCaptor<Tarifposition> captor = ArgumentCaptor.forClass(Tarifposition.class);
        verify(tarifpositionService).saveTarifposition(captor.capture());
        assertEquals(1L, captor.getValue().getId());
    }

    @Test
    void updateTarifposition_NotFound_Returns404() throws Exception {
        when(tarifpositionService.getTarifpositionById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/tarifpositionen/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(neuesDto())))
                .andExpect(status().isNotFound());

        verify(tarifpositionService, never()).saveTarifposition(any());
    }

    @Test
    void updateTarifposition_DuplicatePosition_ReturnsBadRequest() throws Exception {
        when(tarifpositionService.getTarifpositionById(1L)).thenReturn(Optional.of(testPosition1));
        when(tarifpositionService.saveTarifposition(any(Tarifposition.class)))
                .thenThrow(new IllegalArgumentException(
                        "Für diesen Mieter und dieses Quartal existiert bereits eine Position"));

        mockMvc.perform(put("/api/tarifpositionen/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(neuesDto())))
                .andExpect(status().isBadRequest());
    }

    // ==================== DELETE /api/tarifpositionen/{id} ====================

    @Test
    void deleteTarifposition_Exists_ReturnsNoContent() throws Exception {
        when(tarifpositionService.deleteTarifposition(1L)).thenReturn(true);

        mockMvc.perform(delete("/api/tarifpositionen/1"))
                .andExpect(status().isNoContent());

        verify(tarifpositionService).deleteTarifposition(1L);
    }

    @Test
    void deleteTarifposition_NotFound_Returns404() throws Exception {
        when(tarifpositionService.deleteTarifposition(999L)).thenReturn(false);

        mockMvc.perform(delete("/api/tarifpositionen/999"))
                .andExpect(status().isNotFound());
    }
}
