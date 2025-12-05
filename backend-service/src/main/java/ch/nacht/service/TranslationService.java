package ch.nacht.service;

import ch.nacht.entity.Translation;
import ch.nacht.repository.TranslationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class TranslationService {

    private static final Logger log = LoggerFactory.getLogger(TranslationService.class);
    private final TranslationRepository translationRepository;

    public TranslationService(TranslationRepository translationRepository) {
        this.translationRepository = translationRepository;
        log.info("TranslationService initialized");
    }

    public List<Translation> getAllTranslations() {
        return translationRepository.findAllByOrderByKeyAsc();
    }

    public Translation saveTranslation(Translation translation) {
        return translationRepository.save(translation);
    }

    public Optional<Translation> getTranslationByKey(String key) {
        return translationRepository.findById(key);
    }

    public boolean deleteTranslation(String key) {
        log.info("Attempting to delete translation with key: {}", key);
        Optional<Translation> translation = translationRepository.findById(key);
        if (translation.isPresent()) {
            translationRepository.deleteById(key);
            log.info("Successfully deleted translation with key: {}", key);
            return true;
        } else {
            log.warn("Translation not found for deletion - key: {}", key);
            return false;
        }
    }
}
