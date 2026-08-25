package ch.nacht.controller;

import ch.nacht.entity.MeldungLevel;
import ch.nacht.entity.Systemmeldung;
import ch.nacht.service.OrganisationService;
import ch.nacht.service.OrganizationContextService;
import ch.nacht.service.SystemmeldungService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests der REST-Schnittstelle für Systemmeldungen (`Specs/Systemmeldungen.md`).
 *
 * <p>Geprüft wird, was die Schicht selbst leistet: Parameter-Defaults, die Übersetzung von
 * Service-Ausnahmen in HTTP-Codes, die Form der Antwort (Paginierung mit {@code hatMehr} statt
 * Gesamt-Count) und die Reihenfolge der Pfad-Zuordnung. Die Fachlogik liegt im Service und ist
 * dort getestet.
 *
 * <p>Die Autorisierung ist hier <b>nicht</b> geprüft: {@code addFilters = false} schaltet die
 * Security-Kette ab. Dafür ist {@code ControllerAuthorizationTest} zuständig.
 */
@WebMvcTest(SystemmeldungController.class)
@AutoConfigureMockMvc(addFilters = false)
class SystemmeldungControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SystemmeldungService systemmeldungService;

    @MockitoBean
    private OrganizationContextService organizationContextService;

    @MockitoBean
    private OrganisationService organisationService;

    private Systemmeldung meldung;

    @BeforeEach
    void setUp() {
        meldung = new Systemmeldung(MeldungLevel.ERROR,
                SystemmeldungService.KATEGORIE_BILANZMODELL,
                SystemmeldungService.KEY_KEINE_BILANZDATEN, "15.01.2024 10:15",
                LocalDateTime.of(2024, 1, 15, 10, 15), LocalDateTime.of(2024, 1, 16, 10, 15));
        meldung.setId(7L);
        meldung.setOrgId(42L);
        meldung.setZaehler(3);
    }

    // ==================== GET /api/systemmeldungen ====================

    @Test
    void getSystemmeldungen_ReturnsItemsAndPaging() throws Exception {
        when(systemmeldungService.getSeite(any(), any(), any(), anyInt(), anyInt(), anyString(), anyString()))
                .thenReturn(seiteMit(List.of(meldung), true));

        mockMvc.perform(get("/api/systemmeldungen"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id", is(7)))
                .andExpect(jsonPath("$.items[0].level", is("ERROR")))
                .andExpect(jsonPath("$.items[0].meldungKey", is(SystemmeldungService.KEY_KEINE_BILANZDATEN)))
                .andExpect(jsonPath("$.items[0].zaehler", is(3)))
                .andExpect(jsonPath("$.hatMehr", is(true)))
                .andExpect(jsonPath("$.page", is(0)));
    }

    /**
     * Die Antwort trägt {@code hatMehr} und <b>keinen</b> Gesamt-Count.
     *
     * <p>Das ist der Entscheid aus FR-1.12: ein {@code Slice} statt einer {@code Page}, damit die
     * teure Count-Abfrage entfällt. Wer hier später einen {@code totalElements} erwartet, soll am
     * roten Test sehen, dass das eine bewusste Auslassung ist.
     */
    @Test
    void getSystemmeldungen_ReturnsNoTotalCount() throws Exception {
        when(systemmeldungService.getSeite(any(), any(), any(), anyInt(), anyInt(), anyString(), anyString()))
                .thenReturn(seiteMit(List.of(meldung), false));

        mockMvc.perform(get("/api/systemmeldungen"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hatMehr", is(false)))
                .andExpect(jsonPath("$.totalElements").doesNotExist())
                .andExpect(jsonPath("$.totalPages").doesNotExist());
    }

    /** Ohne Parameter gelten die Defaults aus FR-1.3/1.4/1.12: Seite 0, 50 Zeilen, zuletzt zuerst. */
    @Test
    void getSystemmeldungen_AppliesDefaultParameters() throws Exception {
        when(systemmeldungService.getSeite(any(), any(), any(), anyInt(), anyInt(), anyString(), anyString()))
                .thenReturn(seiteMit(List.of(), false));

        mockMvc.perform(get("/api/systemmeldungen"))
                .andExpect(status().isOk());

        verify(systemmeldungService).getSeite(
                eq(null), eq(null), eq(null), eq(0), eq(50), eq("zuletztAufgetreten"), eq("DESC"));
    }

    @Test
    void getSystemmeldungen_PassesFilterAndSortToService() throws Exception {
        when(systemmeldungService.getSeite(any(), any(), any(), anyInt(), anyInt(), anyString(), anyString()))
                .thenReturn(seiteMit(List.of(), false));

        mockMvc.perform(get("/api/systemmeldungen")
                        .param("erledigt", "false")
                        .param("kategorie", SystemmeldungService.KATEGORIE_MQTT)
                        .param("level", "WARN")
                        .param("page", "2")
                        .param("size", "10")
                        .param("sortSpalte", "level")
                        .param("sortRichtung", "ASC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page", is(2)));

        verify(systemmeldungService).getSeite(eq(false), eq(SystemmeldungService.KATEGORIE_MQTT),
                eq(MeldungLevel.WARN), eq(2), eq(10), eq("level"), eq("ASC"));
    }

    @Test
    void getSystemmeldungen_UnknownLevel_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/systemmeldungen").param("level", "KATASTROPHE"))
                .andExpect(status().isBadRequest());

        verify(systemmeldungService, never()).getSeite(any(), any(), any(), anyInt(), anyInt(), any(), any());
    }

    @Test
    void getSystemmeldungen_EmptyResult_ReturnsEmptyItems() throws Exception {
        when(systemmeldungService.getSeite(any(), any(), any(), anyInt(), anyInt(), anyString(), anyString()))
                .thenReturn(seiteMit(List.of(), false));

        mockMvc.perform(get("/api/systemmeldungen"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.hatMehr", is(false)));
    }

    // ==================== GET /api/systemmeldungen/kategorien ====================

    @Test
    void getKategorien_ReturnsList() throws Exception {
        when(systemmeldungService.getKategorien()).thenReturn(
                List.of(SystemmeldungService.KATEGORIE_BILANZMODELL, SystemmeldungService.KATEGORIE_MQTT));

        mockMvc.perform(get("/api/systemmeldungen/kategorien"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0]", is(SystemmeldungService.KATEGORIE_BILANZMODELL)));
    }

    @Test
    void getKategorien_NoEntries_ReturnsEmptyList() throws Exception {
        when(systemmeldungService.getKategorien()).thenReturn(List.of());

        mockMvc.perform(get("/api/systemmeldungen/kategorien"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ==================== PUT /api/systemmeldungen/{id}/erledigt ====================

    @Test
    void setErledigt_ValidInput_ReturnsUpdatedEntry() throws Exception {
        meldung.setErledigt(true);
        meldung.setErledigtAm(LocalDateTime.of(2024, 2, 1, 9, 0));
        when(systemmeldungService.setErledigt(7L, true)).thenReturn(meldung);

        mockMvc.perform(put("/api/systemmeldungen/7/erledigt").param("erledigt", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(7)))
                .andExpect(jsonPath("$.erledigt", is(true)));
    }

    @Test
    void setErledigt_NotFound_Returns404WithKey() throws Exception {
        when(systemmeldungService.setErledigt(anyLong(), anyBoolean()))
                .thenThrow(new IllegalArgumentException("SYSTEMMELDUNG_NICHT_GEFUNDEN"));

        mockMvc.perform(put("/api/systemmeldungen/99/erledigt").param("erledigt", "true"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", is("SYSTEMMELDUNG_NICHT_GEFUNDEN")));
    }

    /**
     * Der Reopen-Konflikt ist ein fachlicher Fehler und muss als 400 mit Übersetzungs-Key ankommen
     * — nicht als 500. Nur so kann die Maske dem Benutzer erklären, warum das Wieder-Öffnen
     * abgelehnt wurde.
     */
    @Test
    void setErledigt_ReopenConflict_Returns400WithKey() throws Exception {
        when(systemmeldungService.setErledigt(7L, false))
                .thenThrow(new IllegalStateException("SYSTEMMELDUNG_REOPEN_KONFLIKT"));

        mockMvc.perform(put("/api/systemmeldungen/7/erledigt").param("erledigt", "false"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("SYSTEMMELDUNG_REOPEN_KONFLIKT")));
    }

    @Test
    void setErledigt_MissingParameter_ReturnsBadRequest() throws Exception {
        mockMvc.perform(put("/api/systemmeldungen/7/erledigt"))
                .andExpect(status().isBadRequest());

        verify(systemmeldungService, never()).setErledigt(anyLong(), anyBoolean());
    }

    // ==================== DELETE /api/systemmeldungen/erledigt ====================

    @Test
    void deleteErledigte_ReturnsDeletedCount() throws Exception {
        when(systemmeldungService.loescheAlleErledigten()).thenReturn(4);

        mockMvc.perform(delete("/api/systemmeldungen/erledigt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anzahl", is(4)));
    }

    @Test
    void deleteErledigte_NothingToDelete_ReturnsZero() throws Exception {
        when(systemmeldungService.loescheAlleErledigten()).thenReturn(0);

        mockMvc.perform(delete("/api/systemmeldungen/erledigt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anzahl", is(0)));
    }

    /**
     * {@code /erledigt} ist die Aufräumaktion und darf nicht als ID gelesen werden.
     *
     * <p>Beide Pfade liegen unter {@code DELETE /api/systemmeldungen/…}; die Reihenfolge der
     * Methoden im Controller entscheidet. Ginge die Zuordnung schief, versuchte Spring
     * {@code "erledigt"} in ein {@code Long} zu wandeln — und der Aufräum-Button wäre kaputt.
     */
    @Test
    void deleteErledigte_LiteralPathWinsOverIdPath() throws Exception {
        when(systemmeldungService.loescheAlleErledigten()).thenReturn(2);

        mockMvc.perform(delete("/api/systemmeldungen/erledigt"))
                .andExpect(status().isOk());

        verify(systemmeldungService).loescheAlleErledigten();
        verify(systemmeldungService, never()).delete(anyLong());
    }

    // ==================== DELETE /api/systemmeldungen/{id} ====================

    @Test
    void delete_Exists_ReturnsNoContent() throws Exception {
        when(systemmeldungService.delete(7L)).thenReturn(true);

        mockMvc.perform(delete("/api/systemmeldungen/7"))
                .andExpect(status().isNoContent());

        verify(systemmeldungService).delete(7L);
    }

    @Test
    void delete_NotFound_Returns404() throws Exception {
        when(systemmeldungService.delete(99L)).thenReturn(false);

        mockMvc.perform(delete("/api/systemmeldungen/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_NonNumericId_ReturnsBadRequest() throws Exception {
        mockMvc.perform(delete("/api/systemmeldungen/abc"))
                .andExpect(status().isBadRequest());

        verify(systemmeldungService, never()).delete(anyLong());
    }

    // ==================== Testdaten ====================

    /** {@code SliceImpl} mit {@code hasNext}-Flag — dieselbe Form, die der Service liefert. */
    private Slice<Systemmeldung> seiteMit(List<Systemmeldung> inhalt, boolean hatMehr) {
        return new SliceImpl<>(inhalt, PageRequest.of(0, 50), hatMehr);
    }
}
