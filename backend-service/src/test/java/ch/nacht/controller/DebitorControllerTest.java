package ch.nacht.controller;

import ch.nacht.dto.DebitorDTO;
import ch.nacht.service.DebitorService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DebitorController.class)
@AutoConfigureMockMvc(addFilters = false)
public class DebitorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DebitorService debitorService;

    @MockitoBean
    private OrganizationContextService organizationContextService;

    @MockitoBean
    private OrganisationService organisationService;

    private ObjectMapper objectMapper;
    private DebitorDTO testDebitor;

    private static final LocalDate VON = LocalDate.of(2024, 1, 1);
    private static final LocalDate BIS = LocalDate.of(2024, 3, 31);

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        testDebitor = new DebitorDTO();
        testDebitor.setId(1L);
        testDebitor.setMieterId(10L);
        testDebitor.setMieterName("Max Muster");
        testDebitor.setEinheitName("EG links");
        testDebitor.setBetrag(new BigDecimal("125.50"));
        testDebitor.setDatumVon(VON);
        testDebitor.setDatumBis(BIS);
    }

    // ==================== GET /api/debitoren ====================

    @Test
    void getDebitoren_ReturnsListOfDebitors() throws Exception {
        when(debitorService.getDebitoren(VON, BIS)).thenReturn(List.of(testDebitor));

        mockMvc.perform(get("/api/debitoren")
                .param("von", "2024-01-01")
                .param("bis", "2024-03-31"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id", is(1)))
            .andExpect(jsonPath("$[0].mieterName", is("Max Muster")))
            .andExpect(jsonPath("$[0].einheitName", is("EG links")))
            .andExpect(jsonPath("$[0].betrag", is(125.50)));
    }

    @Test
    void getDebitoren_EmptyList_ReturnsEmptyArray() throws Exception {
        when(debitorService.getDebitoren(any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/debitoren")
                .param("von", "2024-01-01")
                .param("bis", "2024-03-31"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    }

    // ==================== POST /api/debitoren ====================

    @Test
    void createDebitor_ValidInput_ReturnsCreated() throws Exception {
        when(debitorService.create(any())).thenReturn(testDebitor);

        mockMvc.perform(post("/api/debitoren")
                .contentType(MediaType.APPLICATION_JSON)
                .content(buildValidRequestJson()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id", is(1)))
            .andExpect(jsonPath("$.mieterName", is("Max Muster")));

        verify(debitorService).create(any());
    }

    @Test
    void createDebitor_ServiceThrowsIllegalArgument_ReturnsBadRequest() throws Exception {
        when(debitorService.create(any()))
                .thenThrow(new IllegalArgumentException("Betrag muss grösser als 0 sein"));

        mockMvc.perform(post("/api/debitoren")
                .contentType(MediaType.APPLICATION_JSON)
                .content(buildValidRequestJson()))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createDebitor_WithZahldatum_ReturnsCreated() throws Exception {
        DebitorDTO withZahldatum = new DebitorDTO();
        withZahldatum.setId(2L);
        withZahldatum.setMieterId(10L);
        withZahldatum.setBetrag(new BigDecimal("125.50"));
        withZahldatum.setDatumVon(VON);
        withZahldatum.setDatumBis(BIS);
        withZahldatum.setZahldatum(BIS.plusDays(5));
        when(debitorService.create(any())).thenReturn(withZahldatum);

        String json = """
            {
                "mieterId": 10,
                "betrag": 125.50,
                "datumVon": "2024-01-01",
                "datumBis": "2024-03-31",
                "zahldatum": "2024-04-05"
            }
            """;

        mockMvc.perform(post("/api/debitoren")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.zahldatum", is("2024-04-05")));
    }

    // ==================== PUT /api/debitoren/{id} ====================

    @Test
    void updateDebitor_Found_ReturnsOk() throws Exception {
        when(debitorService.getDebitorById(1L)).thenReturn(Optional.of(testDebitor));
        when(debitorService.update(eq(1L), any())).thenReturn(testDebitor);

        mockMvc.perform(put("/api/debitoren/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(buildValidRequestJson()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", is(1)))
            .andExpect(jsonPath("$.betrag", is(125.50)));
    }

    @Test
    void updateDebitor_NotFound_Returns404() throws Exception {
        when(debitorService.getDebitorById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/debitoren/99")
                .contentType(MediaType.APPLICATION_JSON)
                .content(buildValidRequestJson()))
            .andExpect(status().isNotFound());

        verify(debitorService, never()).update(any(), any());
    }

    @Test
    void updateDebitor_ServiceThrowsIllegalArgument_ReturnsBadRequest() throws Exception {
        when(debitorService.getDebitorById(1L)).thenReturn(Optional.of(testDebitor));
        when(debitorService.update(eq(1L), any()))
                .thenThrow(new IllegalArgumentException("Datum von muss vor oder gleich Datum bis liegen"));

        mockMvc.perform(put("/api/debitoren/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(buildValidRequestJson()))
            .andExpect(status().isBadRequest());
    }

    // ==================== DELETE /api/debitoren/{id} ====================

    @Test
    void deleteDebitor_Exists_ReturnsNoContent() throws Exception {
        when(debitorService.delete(1L)).thenReturn(true);

        mockMvc.perform(delete("/api/debitoren/1"))
            .andExpect(status().isNoContent());

        verify(debitorService).delete(1L);
    }

    @Test
    void deleteDebitor_NotFound_Returns404() throws Exception {
        when(debitorService.delete(99L)).thenReturn(false);

        mockMvc.perform(delete("/api/debitoren/99"))
            .andExpect(status().isNotFound());
    }

    // ==================== Helpers ====================

    private String buildValidRequestJson() {
        return """
            {
                "mieterId": 10,
                "betrag": 125.50,
                "datumVon": "2024-01-01",
                "datumBis": "2024-03-31"
            }
            """;
    }
}
