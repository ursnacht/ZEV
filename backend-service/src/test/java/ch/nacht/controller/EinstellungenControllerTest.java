package ch.nacht.controller;

import ch.nacht.dto.EinstellungenDTO;
import ch.nacht.dto.RechnungKonfigurationDTO;
import ch.nacht.service.EinstellungenService;
import ch.nacht.service.OrganisationService;
import ch.nacht.service.OrganizationContextService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EinstellungenController.class)
@AutoConfigureMockMvc(addFilters = false)
public class EinstellungenControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EinstellungenService einstellungenService;

    @MockitoBean
    private OrganizationContextService organizationContextService;

    @MockitoBean
    private OrganisationService organisationService;

    private ObjectMapper objectMapper;

    private EinstellungenDTO testDTO;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        RechnungKonfigurationDTO.StellerDTO steller = new RechnungKonfigurationDTO.StellerDTO(
            "Urs Nacht", "Hangstrasse 14a", "3044", "Innerberg"
        );
        RechnungKonfigurationDTO rechnung = new RechnungKonfigurationDTO(
            "30 Tage", "CH7006300016946459910", steller
        );
        testDTO = new EinstellungenDTO(1L, rechnung);
    }

    // ==================== GET /api/einstellungen Tests ====================

    @Test
    void getEinstellungen_Found_ReturnsOk() throws Exception {
        when(einstellungenService.getEinstellungen()).thenReturn(testDTO);

        mockMvc.perform(get("/api/einstellungen"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", is(1)))
            .andExpect(jsonPath("$.rechnung.zahlungsfrist", is("30 Tage")))
            .andExpect(jsonPath("$.rechnung.iban", is("CH7006300016946459910")))
            .andExpect(jsonPath("$.rechnung.steller.name", is("Urs Nacht")))
            .andExpect(jsonPath("$.rechnung.steller.strasse", is("Hangstrasse 14a")))
            .andExpect(jsonPath("$.rechnung.steller.plz", is("3044")))
            .andExpect(jsonPath("$.rechnung.steller.ort", is("Innerberg")));

        verify(einstellungenService).getEinstellungen();
    }

    @Test
    void getEinstellungen_NotConfigured_Returns204NoContent() throws Exception {
        when(einstellungenService.getEinstellungen()).thenReturn(null);

        mockMvc.perform(get("/api/einstellungen"))
            .andExpect(status().isNoContent());

        verify(einstellungenService).getEinstellungen();
    }

    // ==================== PUT /api/einstellungen Tests ====================

    @Test
    void saveEinstellungen_ValidInput_ReturnsOk() throws Exception {
        when(einstellungenService.saveEinstellungen(any(EinstellungenDTO.class))).thenReturn(testDTO);

        mockMvc.perform(put("/api/einstellungen")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testDTO)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", is(1)))
            .andExpect(jsonPath("$.rechnung.zahlungsfrist", is("30 Tage")))
            .andExpect(jsonPath("$.rechnung.iban", is("CH7006300016946459910")))
            .andExpect(jsonPath("$.rechnung.steller.name", is("Urs Nacht")));

        verify(einstellungenService).saveEinstellungen(any(EinstellungenDTO.class));
    }

    @Test
    void saveEinstellungen_MissingRechnung_ReturnsBadRequest() throws Exception {
        String json = """
            {
                "id": null,
                "rechnung": null
            }
            """;

        mockMvc.perform(put("/api/einstellungen")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isBadRequest());

        verify(einstellungenService, never()).saveEinstellungen(any());
    }

    @Test
    void saveEinstellungen_MissingZahlungsfrist_ReturnsBadRequest() throws Exception {
        String json = """
            {
                "rechnung": {
                    "zahlungsfrist": "",
                    "iban": "CH7006300016946459910",
                    "steller": {
                        "name": "Urs Nacht",
                        "strasse": "Hangstrasse 14a",
                        "plz": "3044",
                        "ort": "Innerberg"
                    }
                }
            }
            """;

        mockMvc.perform(put("/api/einstellungen")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isBadRequest());

        verify(einstellungenService, never()).saveEinstellungen(any());
    }

    @Test
    void saveEinstellungen_MissingIban_ReturnsBadRequest() throws Exception {
        String json = """
            {
                "rechnung": {
                    "zahlungsfrist": "30 Tage",
                    "iban": "",
                    "steller": {
                        "name": "Urs Nacht",
                        "strasse": "Hangstrasse 14a",
                        "plz": "3044",
                        "ort": "Innerberg"
                    }
                }
            }
            """;

        mockMvc.perform(put("/api/einstellungen")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isBadRequest());

        verify(einstellungenService, never()).saveEinstellungen(any());
    }

    @Test
    void saveEinstellungen_InvalidIbanFormat_ReturnsBadRequest() throws Exception {
        String json = """
            {
                "rechnung": {
                    "zahlungsfrist": "30 Tage",
                    "iban": "DE89370400440532013000",
                    "steller": {
                        "name": "Urs Nacht",
                        "strasse": "Hangstrasse 14a",
                        "plz": "3044",
                        "ort": "Innerberg"
                    }
                }
            }
            """;

        mockMvc.perform(put("/api/einstellungen")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isBadRequest());

        verify(einstellungenService, never()).saveEinstellungen(any());
    }

    @Test
    void saveEinstellungen_MissingSteller_ReturnsBadRequest() throws Exception {
        String json = """
            {
                "rechnung": {
                    "zahlungsfrist": "30 Tage",
                    "iban": "CH7006300016946459910",
                    "steller": null
                }
            }
            """;

        mockMvc.perform(put("/api/einstellungen")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isBadRequest());

        verify(einstellungenService, never()).saveEinstellungen(any());
    }

    @Test
    void saveEinstellungen_MissingStellerName_ReturnsBadRequest() throws Exception {
        String json = """
            {
                "rechnung": {
                    "zahlungsfrist": "30 Tage",
                    "iban": "CH7006300016946459910",
                    "steller": {
                        "name": "",
                        "strasse": "Hangstrasse 14a",
                        "plz": "3044",
                        "ort": "Innerberg"
                    }
                }
            }
            """;

        mockMvc.perform(put("/api/einstellungen")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isBadRequest());

        verify(einstellungenService, never()).saveEinstellungen(any());
    }

    @Test
    void saveEinstellungen_JsonConversionError_ReturnsBadRequest() throws Exception {
        when(einstellungenService.saveEinstellungen(any(EinstellungenDTO.class)))
            .thenThrow(new IllegalArgumentException("Error converting settings to JSON"));

        mockMvc.perform(put("/api/einstellungen")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testDTO)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void saveEinstellungen_NewSettings_ReturnsOk() throws Exception {
        EinstellungenDTO newDTO = new EinstellungenDTO(null, testDTO.getRechnung());
        EinstellungenDTO savedDTO = new EinstellungenDTO(2L, testDTO.getRechnung());

        when(einstellungenService.saveEinstellungen(any(EinstellungenDTO.class))).thenReturn(savedDTO);

        mockMvc.perform(put("/api/einstellungen")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(newDTO)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", is(2)));
    }
}
