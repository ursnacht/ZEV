package ch.nacht.controller;

import ch.nacht.dto.MesspunktEmpfangDTO;
import ch.nacht.dto.MesspunktEmpfangMeldungDTO;
import ch.nacht.entity.MesspunktEmpfangErgebnis;
import ch.nacht.entity.MesspunktEmpfangQuelle;
import ch.nacht.service.MesspunktEmpfangService;
import ch.nacht.service.OrganisationService;
import ch.nacht.service.OrganizationContextService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Endpunkte der Ansicht Messpunkt-Empfang (Specs/Messpunkt-Empfang.md, API-Endpunkte). Die
 * Permissions prüft {@code ControllerAuthorizationTest} mit echter Security.
 */
@WebMvcTest(MesspunktEmpfangController.class)
@AutoConfigureMockMvc(addFilters = false)
public class MesspunktEmpfangControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MesspunktEmpfangService messpunktEmpfangService;

    @MockitoBean
    private OrganizationContextService organizationContextService;

    @MockitoBean
    private OrganisationService organisationService;

    @Test
    void getAllMesspunktEmpfang_ReturnsList() throws Exception {
        MesspunktEmpfangDTO empfang = new MesspunktEmpfangDTO();
        empfang.setId(7L);
        empfang.setMesspunkt("MP-001");
        empfang.setEinheiten(List.of("Wohnung A"));
        empfang.setQuelle(MesspunktEmpfangQuelle.GPLUG);
        empfang.setLetzterEmpfang(LocalDateTime.of(2026, 10, 25, 2, 59, 30));
        empfang.setSekundenSeitEmpfang(660L);
        empfang.setErgebnis(MesspunktEmpfangErgebnis.UNGUELTIG);
        empfang.setGrund("Pflichtfeld fehlt");
        MesspunktEmpfangDTO keinEmpfang = new MesspunktEmpfangDTO();
        keinEmpfang.setMesspunkt("MP-NEU");
        keinEmpfang.setEinheiten(List.of("Neu"));
        keinEmpfang.setKeinEmpfang(true);
        when(messpunktEmpfangService.getAllMesspunktEmpfang()).thenReturn(List.of(empfang, keinEmpfang));

        mockMvc.perform(get("/api/messpunkt-empfang"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].messpunkt", is("MP-001")))
                .andExpect(jsonPath("$[0].einheiten[0]", is("Wohnung A")))
                .andExpect(jsonPath("$[0].quelle", is("GPLUG")))
                .andExpect(jsonPath("$[0].sekundenSeitEmpfang", is(660)))
                .andExpect(jsonPath("$[0].ergebnis", is("UNGUELTIG")))
                .andExpect(jsonPath("$[0].grund", is("Pflichtfeld fehlt")))
                .andExpect(jsonPath("$[0].keinEmpfang", is(false)))
                .andExpect(jsonPath("$[1].keinEmpfang", is(true)))
                .andExpect(jsonPath("$[1].id", nullValue()));
    }

    @Test
    void getAllMesspunktEmpfang_AntwortOhneOrgIdUndPayload() throws Exception {
        MesspunktEmpfangDTO empfang = new MesspunktEmpfangDTO();
        empfang.setId(7L);
        empfang.setMesspunkt("MP-001");
        when(messpunktEmpfangService.getAllMesspunktEmpfang()).thenReturn(List.of(empfang));

        mockMvc.perform(get("/api/messpunkt-empfang"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orgId").doesNotExist())
                .andExpect(jsonPath("$[0].letzteMeldung").doesNotExist())
                .andExpect(jsonPath("$[0].payload").doesNotExist());
    }

    @Test
    void getLetzteMeldung_Found_ReturnsPayload() throws Exception {
        when(messpunktEmpfangService.getLetzteMeldung(7L))
                .thenReturn(Optional.of(new MesspunktEmpfangMeldungDTO("{\"zaehlerstandBezug\":1.5}")));

        mockMvc.perform(get("/api/messpunkt-empfang/7/letzte-meldung"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload", is("{\"zaehlerstandBezug\":1.5}")));
    }

    @Test
    void getLetzteMeldung_OhnePayload_ReturnsNull() throws Exception {
        // Zeile von vor V188: Die Zeile gibt es, den Payload nicht
        when(messpunktEmpfangService.getLetzteMeldung(7L))
                .thenReturn(Optional.of(new MesspunktEmpfangMeldungDTO(null)));

        mockMvc.perform(get("/api/messpunkt-empfang/7/letzte-meldung"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload", nullValue()));
    }

    @Test
    void getLetzteMeldung_NotFound_Returns404() throws Exception {
        // Unbekannte oder fremde ID: Der Service sucht unter dem orgFilter und findet nichts
        when(messpunktEmpfangService.getLetzteMeldung(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/messpunkt-empfang/99/letzte-meldung"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteMesspunktEmpfang_Exists_ReturnsNoContent() throws Exception {
        when(messpunktEmpfangService.deleteMesspunktEmpfang(7L)).thenReturn(true);

        mockMvc.perform(delete("/api/messpunkt-empfang/7"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteMesspunktEmpfang_NotFound_Returns404() throws Exception {
        // Unbekannte oder fremde ID: findFirstById unter dem orgFilter findet nichts
        when(messpunktEmpfangService.deleteMesspunktEmpfang(99L)).thenReturn(false);

        mockMvc.perform(delete("/api/messpunkt-empfang/99"))
                .andExpect(status().isNotFound());
    }
}
