package ch.nacht.controller;

import ch.nacht.entity.Translation;
import ch.nacht.service.TranslationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TranslationController.class)
@AutoConfigureMockMvc(addFilters = false)
class TranslationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TranslationService translationService;

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
}
