package ch.nacht.controller;

import ch.nacht.dto.NkRechnungDownloadDTO;
import ch.nacht.dto.NkRechnungLaufDTO;
import ch.nacht.exception.FeatureDisabledException;
import ch.nacht.service.NkRechnungService;
import ch.nacht.service.OrganisationService;
import ch.nacht.service.OrganizationContextService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests der REST-Endpunkte aus {@code Specs/Nebenkosten/RechnungenGenerieren.md} FR-6.
 *
 * <p>Der Schwerpunkt liegt auf der <b>Antwortform</b>: Sie war der Grund fuer einen eigenen
 * Endpunkt statt einer Erweiterung von {@code POST /api/rechnungen/generate}, dessen Antwort sonst
 * je Rechnungsart eine andere geworden waere.
 *
 * <p>Die Permissions prueft {@code ControllerAuthorizationTest} mit echtem Security-Filter — hier
 * laeuft er bewusst nicht ({@code addFilters = false}).
 */
@WebMvcTest(NkRechnungController.class)
@AutoConfigureMockMvc(addFilters = false)
class NkRechnungControllerTest {

    private static final Long ABRECHNUNG_ID = 12L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NkRechnungService nkRechnungService;

    @MockitoBean
    private OrganizationContextService organizationContextService;

    @MockitoBean
    private OrganisationService organisationService;

    // ==================== POST .../{id}/rechnungen ====================

    @Test
    void erzeugeRechnungen_ReturnsLaufMitKennzahlen() throws Exception {
        when(nkRechnungService.erzeugeRechnungen(eq(ABRECHNUNG_ID), any())).thenReturn(lauf());

        mockMvc.perform(post("/api/nebenkosten/abrechnungen/12/rechnungen")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sprache\":\"de\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.abrechnungId", is(12)))
                .andExpect(jsonPath("$.bezeichnung", is("Nebenkosten 2026")))
                .andExpect(jsonPath("$.von", is("2026-01-01")))
                .andExpect(jsonPath("$.bis", is("2026-12-31")))
                .andExpect(jsonPath("$.anzahlRechnungen", is(2)))
                .andExpect(jsonPath("$.anzahlForderungen", is(1)))
                .andExpect(jsonPath("$.summeForderungen", is(812.35)))
                .andExpect(jsonPath("$.rechnungen", hasSize(2)));
    }

    @Test
    void erzeugeRechnungen_ZeileTraegtAlleFelder() throws Exception {
        when(nkRechnungService.erzeugeRechnungen(eq(ABRECHNUNG_ID), any())).thenReturn(lauf());

        mockMvc.perform(post("/api/nebenkosten/abrechnungen/12/rechnungen")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rechnungen[0].mieterId", is(45)))
                .andExpect(jsonPath("$.rechnungen[0].mieterName", is("Max Muster")))
                .andExpect(jsonPath("$.rechnungen[0].saldo", is(812.35)))
                .andExpect(jsonPath("$.rechnungen[0].forderungGebucht", is(true)))
                .andExpect(jsonPath("$.rechnungen[0].filename", is("Nebenkosten_2026_Max_Muster.pdf")))
                .andExpect(jsonPath("$.rechnungen[0].fehler", is(nullValue())));
    }

    /** Ein Guthaben erscheint in der Liste — mit PDF, aber ohne Forderung (FR-4). */
    @Test
    void erzeugeRechnungen_GuthabenZeile_OhneForderung() throws Exception {
        when(nkRechnungService.erzeugeRechnungen(eq(ABRECHNUNG_ID), any())).thenReturn(lauf());

        mockMvc.perform(post("/api/nebenkosten/abrechnungen/12/rechnungen")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(jsonPath("$.rechnungen[1].forderungGebucht", is(false)))
                .andExpect(jsonPath("$.rechnungen[1].saldo", is(-480.00)));
    }

    /**
     * Der Rumpf ist optional: Ohne ihn gilt die Ersatzsprache. Ein {@code 400} hier waere eine
     * Huerde ohne Zweck — Abrechnung und Mieter stehen im Pfad.
     */
    @Test
    void erzeugeRechnungen_OhneBody_ReturnsOk() throws Exception {
        when(nkRechnungService.erzeugeRechnungen(eq(ABRECHNUNG_ID), eq(null))).thenReturn(lauf());

        mockMvc.perform(post("/api/nebenkosten/abrechnungen/12/rechnungen"))
                .andExpect(status().isOk());

        verify(nkRechnungService).erzeugeRechnungen(ABRECHNUNG_ID, null);
    }

    @Test
    void erzeugeRechnungen_GibtSpracheWeiter() throws Exception {
        when(nkRechnungService.erzeugeRechnungen(eq(ABRECHNUNG_ID), eq("en"))).thenReturn(lauf());

        mockMvc.perform(post("/api/nebenkosten/abrechnungen/12/rechnungen")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sprache\":\"en\"}"))
                .andExpect(status().isOk());

        verify(nkRechnungService).erzeugeRechnungen(ABRECHNUNG_ID, "en");
    }

    /**
     * Unbekannt und fremder Mandant sind <b>nicht unterscheidbar</b>: Die gefilterte Abfrage
     * liefert in beiden Faellen nichts, und eine eigene Meldung waere eine Auskunft ueber fremde
     * Daten.
     */
    @Test
    void erzeugeRechnungen_AbrechnungNichtErreichbar_Returns404() throws Exception {
        when(nkRechnungService.erzeugeRechnungen(anyLong(), any()))
                .thenThrow(new NoSuchElementException("Nebenkostenabrechnung not found: 99"));

        mockMvc.perform(post("/api/nebenkosten/abrechnungen/99/rechnungen")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    /**
     * Eine nicht abgeschlossene Abrechnung wird mit {@code 400} und dem Uebersetzungsschluessel
     * abgewiesen — die Sichtbarkeit des Menueeintrags im Browser ist keine Absicherung.
     */
    @Test
    void erzeugeRechnungen_NichtAbgerechnet_Returns400MitSchluessel() throws Exception {
        when(nkRechnungService.erzeugeRechnungen(anyLong(), any()))
                .thenThrow(new IllegalStateException("NK_FEHLER_NICHT_ABGERECHNET"));

        mockMvc.perform(post("/api/nebenkosten/abrechnungen/12/rechnungen")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("NK_FEHLER_NICHT_ABGERECHNET")));
    }

    /** Der Flag wird im Service geprueft; hier zaehlt, dass daraus beim Client ein 403 wird. */
    @Test
    void erzeugeRechnungen_FlagAus_Returns403() throws Exception {
        when(nkRechnungService.erzeugeRechnungen(anyLong(), any()))
                .thenThrow(new FeatureDisabledException("FEATURE_FLAG_DEAKTIVIERT"));

        mockMvc.perform(post("/api/nebenkosten/abrechnungen/12/rechnungen")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error", is("FEATURE_FLAG_DEAKTIVIERT")));
    }

    // ==================== GET .../{id}/rechnungen/{mieterId}/pdf ====================

    @Test
    void ladePdf_Vorhanden_ReturnsPdfAttachment() throws Exception {
        byte[] pdf = new byte[]{37, 80, 68, 70}; // %PDF
        when(nkRechnungService.ladePdf(ABRECHNUNG_ID, 45L))
                .thenReturn(Optional.of(new NkRechnungDownloadDTO(pdf, "Nebenkosten_2026_Max_Muster.pdf")));

        mockMvc.perform(get("/api/nebenkosten/abrechnungen/12/rechnungen/45/pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"Nebenkosten_2026_Max_Muster.pdf\""))
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(content().bytes(pdf));
    }

    @Test
    void ladePdf_LiefertLesbarenDateinamen() throws Exception {
        when(nkRechnungService.ladePdf(ABRECHNUNG_ID, 45L))
                .thenReturn(Optional.of(new NkRechnungDownloadDTO(
                        "%PDF".getBytes(StandardCharsets.US_ASCII), "Nebenkosten_2026_Max_Muster.pdf")));

        // Nicht "12_45.pdf": Der Ablageschluessel besteht aus zwei IDs, der gespeicherte Name
        // wird mitgefuehrt.
        mockMvc.perform(get("/api/nebenkosten/abrechnungen/12/rechnungen/45/pdf"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"Nebenkosten_2026_Max_Muster.pdf\""));
    }

    /** Nach 30 Minuten ist die Ablage leer — das Frontend zeigt darauf einen Hinweis. */
    @Test
    void ladePdf_Abgelaufen_Returns404() throws Exception {
        when(nkRechnungService.ladePdf(ABRECHNUNG_ID, 45L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/nebenkosten/abrechnungen/12/rechnungen/45/pdf"))
                .andExpect(status().isNotFound());
    }

    @Test
    void ladePdf_FlagAus_Returns403() throws Exception {
        when(nkRechnungService.ladePdf(anyLong(), anyLong()))
                .thenThrow(new FeatureDisabledException("FEATURE_FLAG_DEAKTIVIERT"));

        mockMvc.perform(get("/api/nebenkosten/abrechnungen/12/rechnungen/45/pdf"))
                .andExpect(status().isForbidden());

        verify(nkRechnungService, never()).erzeugeRechnungen(anyLong(), any());
    }

    // ==================== Helpers ====================

    /** Ein Lauf mit einer Nachzahlung und einem Guthaben — beide Faelle in einer Antwort. */
    private NkRechnungLaufDTO lauf() {
        NkRechnungLaufDTO.NkRechnungErgebnisDTO nachzahlung = new NkRechnungLaufDTO.NkRechnungErgebnisDTO();
        nachzahlung.setMieterId(45L);
        nachzahlung.setMieterName("Max Muster");
        nachzahlung.setSaldo(new BigDecimal("812.35"));
        nachzahlung.setForderungGebucht(true);
        nachzahlung.setFilename("Nebenkosten_2026_Max_Muster.pdf");

        NkRechnungLaufDTO.NkRechnungErgebnisDTO guthaben = new NkRechnungLaufDTO.NkRechnungErgebnisDTO();
        guthaben.setMieterId(46L);
        guthaben.setMieterName("Erika Beispiel");
        guthaben.setSaldo(new BigDecimal("-480.00"));
        guthaben.setForderungGebucht(false);
        guthaben.setFilename("Nebenkosten_2026_Erika_Beispiel.pdf");

        NkRechnungLaufDTO lauf = new NkRechnungLaufDTO();
        lauf.setAbrechnungId(ABRECHNUNG_ID);
        lauf.setBezeichnung("Nebenkosten 2026");
        lauf.setVon(LocalDate.of(2026, 1, 1));
        lauf.setBis(LocalDate.of(2026, 12, 31));
        lauf.setAnzahlRechnungen(2);
        lauf.setAnzahlForderungen(1);
        lauf.setSummeForderungen(new BigDecimal("812.35"));
        lauf.setRechnungen(List.of(nachzahlung, guthaben));
        return lauf;
    }
}
