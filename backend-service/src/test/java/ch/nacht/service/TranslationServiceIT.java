package ch.nacht.service;

import ch.nacht.AbstractIntegrationTest;
import ch.nacht.entity.Translation;
import ch.nacht.repository.TranslationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(TranslationService.class)
class TranslationServiceIT extends AbstractIntegrationTest {

    @Autowired
    private TranslationService translationService;

    @Autowired
    private TranslationRepository translationRepository;

    @Test
    void shouldDeleteExistingTranslation() {
        // Given
        String key = "test.delete.key";
        Translation translation = new Translation(key, "Löschen", "Delete");
        translationRepository.save(translation);

        // Verify it exists before deletion
        assertThat(translationRepository.findById(key)).isPresent();

        // When
        boolean result = translationService.deleteTranslation(key);

        // Then
        assertThat(result).isTrue();
        assertThat(translationRepository.findById(key)).isEmpty();
    }

    @Test
    void shouldReturnFalseWhenDeletingNonExistingTranslation() {
        // Given
        String key = "non.existing.key";

        // When
        boolean result = translationService.deleteTranslation(key);

        // Then
        assertThat(result).isFalse();
    }
}
