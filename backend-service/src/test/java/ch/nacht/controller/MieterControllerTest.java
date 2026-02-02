package ch.nacht.controller;

import ch.nacht.entity.Mieter;
import ch.nacht.service.MieterService;
import ch.nacht.service.OrganizationContextService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MieterController.class)
@AutoConfigureMockMvc(addFilters = false)
public class MieterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MieterService mieterService;

    @MockBean
    private OrganizationContextService organizationContextService;

    private ObjectMapper objectMapper;

    private Mieter testMieter;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        testMieter = new Mieter("Max Muster", LocalDate.of(2024, 1, 1), 1L);
        testMieter.setId(1L);
        testMieter.setStrasse("Teststrasse 1");
        testMieter.setPlz("3000");
        testMieter.setOrt("Bern");
        testMieter.setMietende(LocalDate.of(2024, 12, 31));
    }

    // ==================== GET /api/mieter Tests ====================

    @Test
    void getAllMieter_ReturnsList() throws Exception {
        Mieter mieter2 = new Mieter("Anna Beispiel", LocalDate.of(2024, 4, 1), 2L);
        mieter2.setId(2L);
        mieter2.setStrasse("Andere Strasse 2");
        mieter2.setPlz("8000");
        mieter2.setOrt("Zürich");

        when(mieterService.getAllMieter()).thenReturn(Arrays.asList(testMieter, mieter2));

        mockMvc.perform(get("/api/mieter"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name", is("Max Muster")))
                .andExpect(jsonPath("$[1].name", is("Anna Beispiel")));

        verify(mieterService).getAllMieter();
    }

    @Test
    void getAllMieter_EmptyList_ReturnsEmptyArray() throws Exception {
        when(mieterService.getAllMieter()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/mieter"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ==================== GET /api/mieter/{id} Tests ====================

    @Test
    void getMieterById_Found_ReturnsMieter() throws Exception {
        when(mieterService.getMieterById(1L)).thenReturn(Optional.of(testMieter));

        mockMvc.perform(get("/api/mieter/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Max Muster")))
                .andExpect(jsonPath("$.strasse", is("Teststrasse 1")))
                .andExpect(jsonPath("$.plz", is("3000")))
                .andExpect(jsonPath("$.ort", is("Bern")))
                .andExpect(jsonPath("$.mietbeginn", is("2024-01-01")))
                .andExpect(jsonPath("$.mietende", is("2024-12-31")))
                .andExpect(jsonPath("$.einheitId", is(1)));
    }

    @Test
    void getMieterById_NotFound_Returns404() throws Exception {
        when(mieterService.getMieterById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/mieter/99"))
                .andExpect(status().isNotFound());
    }

    // ==================== POST /api/mieter Tests ====================

    @Test
    void createMieter_ValidInput_ReturnsCreated() throws Exception {
        Mieter newMieter = new Mieter("Neue Mieterin", LocalDate.of(2025, 1, 1), 2L);
        newMieter.setStrasse("Neue Strasse 1");
        newMieter.setPlz("8000");
        newMieter.setOrt("Zürich");
        newMieter.setMietende(LocalDate.of(2025, 12, 31));

        Mieter savedMieter = new Mieter("Neue Mieterin", LocalDate.of(2025, 1, 1), 2L);
        savedMieter.setId(2L);
        savedMieter.setStrasse("Neue Strasse 1");
        savedMieter.setPlz("8000");
        savedMieter.setOrt("Zürich");
        savedMieter.setMietende(LocalDate.of(2025, 12, 31));

        when(mieterService.saveMieter(any(Mieter.class))).thenReturn(savedMieter);

        mockMvc.perform(post("/api/mieter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newMieter)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(2)))
                .andExpect(jsonPath("$.name", is("Neue Mieterin")));
    }

    @Test
    void createMieter_OverlappingDates_ReturnsBadRequest() throws Exception {
        Mieter newMieter = new Mieter("Overlap", LocalDate.of(2024, 6, 1), 1L);
        newMieter.setStrasse("Strasse");
        newMieter.setPlz("3000");
        newMieter.setOrt("Bern");
        newMieter.setMietende(LocalDate.of(2024, 12, 31));

        when(mieterService.saveMieter(any(Mieter.class)))
                .thenThrow(new IllegalArgumentException("Mietzeit überschneidet sich mit bestehendem Mieter"));

        mockMvc.perform(post("/api/mieter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newMieter)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createMieter_MissingName_ReturnsBadRequest() throws Exception {
        String json = """
                {
                    "name": "",
                    "strasse": "Strasse 1",
                    "plz": "3000",
                    "ort": "Bern",
                    "mietbeginn": "2024-01-01",
                    "einheitId": 1
                }
                """;

        mockMvc.perform(post("/api/mieter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());

        verify(mieterService, never()).saveMieter(any());
    }

    @Test
    void createMieter_MissingMietbeginn_ReturnsBadRequest() throws Exception {
        String json = """
                {
                    "name": "Test",
                    "strasse": "Strasse 1",
                    "plz": "3000",
                    "ort": "Bern",
                    "einheitId": 1
                }
                """;

        mockMvc.perform(post("/api/mieter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());

        verify(mieterService, never()).saveMieter(any());
    }

    @Test
    void createMieter_MissingEinheitId_ReturnsBadRequest() throws Exception {
        String json = """
                {
                    "name": "Test",
                    "strasse": "Strasse 1",
                    "plz": "3000",
                    "ort": "Bern",
                    "mietbeginn": "2024-01-01"
                }
                """;

        mockMvc.perform(post("/api/mieter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());

        verify(mieterService, never()).saveMieter(any());
    }

    // ==================== PUT /api/mieter/{id} Tests ====================

    @Test
    void updateMieter_ValidInput_ReturnsOk() throws Exception {
        when(mieterService.getMieterById(1L)).thenReturn(Optional.of(testMieter));

        Mieter updated = new Mieter("Max Muster Updated", LocalDate.of(2024, 1, 1), 1L);
        updated.setId(1L);
        updated.setStrasse("Neue Strasse");
        updated.setPlz("3001");
        updated.setOrt("Bern");
        updated.setMietende(LocalDate.of(2025, 6, 30));

        when(mieterService.saveMieter(any(Mieter.class))).thenReturn(updated);

        mockMvc.perform(put("/api/mieter/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testMieter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Max Muster Updated")));
    }

    @Test
    void updateMieter_NotFound_Returns404() throws Exception {
        when(mieterService.getMieterById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/mieter/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testMieter)))
                .andExpect(status().isNotFound());

        verify(mieterService, never()).saveMieter(any());
    }

    @Test
    void updateMieter_InvalidDates_ReturnsBadRequest() throws Exception {
        when(mieterService.getMieterById(1L)).thenReturn(Optional.of(testMieter));
        when(mieterService.saveMieter(any(Mieter.class)))
                .thenThrow(new IllegalArgumentException("Mietende muss nach Mietbeginn liegen"));

        mockMvc.perform(put("/api/mieter/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testMieter)))
                .andExpect(status().isBadRequest());
    }

    // ==================== DELETE /api/mieter/{id} Tests ====================

    @Test
    void deleteMieter_Exists_ReturnsNoContent() throws Exception {
        when(mieterService.deleteMieter(1L)).thenReturn(true);

        mockMvc.perform(delete("/api/mieter/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteMieter_NotFound_Returns404() throws Exception {
        when(mieterService.deleteMieter(99L)).thenReturn(false);

        mockMvc.perform(delete("/api/mieter/99"))
                .andExpect(status().isNotFound());
    }
}
