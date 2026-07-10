package ch.nacht.service;

import ch.nacht.entity.Translation;
import ch.nacht.repository.TranslationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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

    /**
     * Importiert eine Liste von Übersetzungen. Neue Keys werden immer angelegt; nicht in der
     * Liste enthaltene Keys bleiben unverändert (kein Löschen). Der Umgang mit bereits
     * bestehenden Keys hängt von {@code ueberschreiben} ab:
     * <ul>
     *   <li>{@code true}: bestehende Keys werden überschrieben (Upsert).</li>
     *   <li>{@code false}: bestehende Keys werden übersprungen (nur neue Keys werden angelegt).</li>
     * </ul>
     * Der Import ist atomar: bei einem ungültigen Eintrag wird nichts gespeichert.
     *
     * @return Anzahl tatsächlich gespeicherter (angelegter/aktualisierter) Übersetzungen
     */
    @Transactional
    public int importTranslations(List<Translation> translations, boolean ueberschreiben) {
        for (Translation t : translations) {
            if (t.getKey() == null || t.getKey().isBlank()) {
                throw new IllegalArgumentException("TRANSLATION_IMPORT_KEY_FEHLT");
            }
            if (t.getKey().length() > 200) {
                throw new IllegalArgumentException("TRANSLATION_IMPORT_KEY_ZU_LANG");
            }
        }

        List<Translation> toSave = translations;
        if (!ueberschreiben) {
            Set<String> existing = translationRepository.findAll().stream()
                    .map(Translation::getKey)
                    .collect(Collectors.toSet());
            toSave = translations.stream()
                    .filter(t -> !existing.contains(t.getKey()))
                    .toList();
        }

        List<Translation> saved = translationRepository.saveAll(toSave);
        log.info("Imported {} translations (ueberschreiben={}, eingereicht={})",
                saved.size(), ueberschreiben, translations.size());
        return saved.size();
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
