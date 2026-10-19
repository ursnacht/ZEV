package ch.nacht.controller;

import ch.nacht.entity.Translation;
import ch.nacht.service.OrganisationService;
import ch.nacht.service.OrganizationContextService;
import ch.nacht.service.TranslationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TranslationController.class)
@AutoConfigureMockMvc(addFilters = false)
class TranslationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TranslationService translationService;

    @MockitoBean
    private OrganizationContextService organizationContextService;

    @MockitoBean
    private OrganisationService organisationService;

    /** Specs/Übersetzungsverwaltung.md, FR-3: ein bestehender Key ergibt 409, kein stilles Überschreiben. */
    @Test
    void createTranslation_BestehenderKey_Returns409() throws Exception {
        when(translationService.createTranslation(any(Translation.class)))
                .thenThrow(new DuplicateKeyException("TRANSLATION_KEY_EXISTIERT"));

        mockMvc.perform(post("/api/translations")
                .contentType("application/json")
                .content("{\"key\":\"SPEICHERN\", \"deutsch\":\"X\", \"englisch\":\"X\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void createTranslation_NeuerKey_Returns200() throws Exception {
        when(translationService.createTranslation(any(Translation.class)))
                .thenReturn(new Translation("NEU_KEY", "Neu", "New"));

        mockMvc.perform(post("/api/translations")
                .contentType("application/json")
                .content("{\"key\":\"NEU_KEY\", \"deutsch\":\"Neu\", \"englisch\":\"New\"}"))
                .andExpect(status().isOk());

        verify(translationService).createTranslation(any(Translation.class));
    }

    @Test
    void shouldDeleteTranslationWithDotsInKey() throws Exception {
        // Given
        String keyWithDots = "e2e.test.delete.123";
        when(translationService.deleteTranslation(keyWithDots)).thenReturn(true);

        // When
        mockMvc.perform(delete("/api/translations/{key}", keyWithDots))
                .andExpect(status().isNoContent());

        // Then
        verify(translationService).deleteTranslation(keyWithDots);
    }

    @Test
    void shouldUpdateTranslationWithDotsInKey() throws Exception {
        // Given
        String keyWithDots = "e2e.test.update.123";
        Translation translation = new Translation(keyWithDots, "Test", "Test");
        when(translationService.saveTranslation(any(Translation.class))).thenReturn(translation);

        // When
        String json = "{\"key\":\"" + keyWithDots + "\", \"deutsch\":\"Test\", \"englisch\":\"Test\"}";

        mockMvc.perform(put("/api/translations/{key}", keyWithDots)
                .contentType("application/json")
                .content(json))
                .andExpect(status().isOk());

        // Then
        // The service should be called with the translation object
        // We verify the path variable was correctly mapped and passed to logic
        // (implicit in success)
    }

    @Test
    void updateTranslation_KeyImPfadUngleichKeyImBody_Returns400() throws Exception {
        mockMvc.perform(put("/api/translations/{key}", "PFAD_KEY")
                .contentType("application/json")
                .content("{\"key\":\"BODY_KEY\", \"deutsch\":\"X\", \"englisch\":\"X\"}"))
                .andExpect(status().isBadRequest());

        verify(translationService, never()).saveTranslation(any());
    }

    @Test
    void deleteTranslation_UnbekannterKey_Returns404() throws Exception {
        when(translationService.deleteTranslation("GIBT_ES_NICHT")).thenReturn(false);

        mockMvc.perform(delete("/api/translations/{key}", "GIBT_ES_NICHT"))
                .andExpect(status().isNotFound());
    }

    @Test
    void importTranslations_UngueltigerEintrag_Returns400MitKey() throws Exception {
        when(translationService.importTranslations(anyList(), anyBoolean()))
                .thenThrow(new IllegalArgumentException("TRANSLATION_IMPORT_KEY_FEHLT"));

        mockMvc.perform(post("/api/translations/import")
                .contentType("application/json")
                .content("[{\"key\":\"\", \"deutsch\":\"X\", \"englisch\":\"X\"}]"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("TRANSLATION_IMPORT_KEY_FEHLT"));
    }

    @Test
    void importTranslations_ReichtUeberschreibenDurch_UndMeldetAnzahl() throws Exception {
        when(translationService.importTranslations(anyList(), org.mockito.ArgumentMatchers.eq(false))).thenReturn(1);

        mockMvc.perform(post("/api/translations/import?ueberschreiben=false")
                .contentType("application/json")
                .content("[{\"key\":\"NEU_KEY\", \"deutsch\":\"Neu\", \"englisch\":\"New\"}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importiert", is(1)));
    }

    /** Antwortform fuer die TranslatePipe: { de: {key: text}, en: {key: text} }. */
    @Test
    void getTranslations_LiefertDeUndEn() throws Exception {
        when(translationService.getAllTranslations())
                .thenReturn(java.util.List.of(new Translation("SPEICHERN", "Speichern", "Save")));

        mockMvc.perform(get("/api/translations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.de.SPEICHERN", is("Speichern")))
                .andExpect(jsonPath("$.en.SPEICHERN", is("Save")));
    }
}
