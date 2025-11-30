package ch.nacht.service;

import ch.nacht.entity.Translation;
import ch.nacht.repository.TranslationRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class TranslationService {

    private final TranslationRepository translationRepository;

    public TranslationService(TranslationRepository translationRepository) {
        this.translationRepository = translationRepository;
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
}
