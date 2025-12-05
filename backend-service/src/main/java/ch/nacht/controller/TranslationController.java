package ch.nacht.controller;

import ch.nacht.entity.Translation;
import ch.nacht.service.TranslationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/translations")
public class TranslationController {

    private static final Logger log = LoggerFactory.getLogger(TranslationController.class);
    private final TranslationService translationService;

    public TranslationController(TranslationService translationService) {
        this.translationService = translationService;
        log.info("TranslationController initialized");
    }

    @GetMapping
    public ResponseEntity<Map<String, Map<String, String>>> getAllTranslations() {
        log.info("Fetching all translations");
        List<Translation> translations = translationService.getAllTranslations();
        Map<String, String> de = translations.stream()
                .collect(Collectors.toMap(Translation::getKey, t -> t.getDeutsch() != null ? t.getDeutsch() : ""));
        Map<String, String> en = translations.stream()
                .collect(Collectors.toMap(Translation::getKey, t -> t.getEnglisch() != null ? t.getEnglisch() : ""));

        log.info("Retrieved {} translations (de: {}, en: {})", translations.size(), de.size(), en.size());
        return ResponseEntity.ok(Map.of("de", de, "en", en));
    }

    @GetMapping("/list")
    @PreAuthorize("hasRole('zev_admin')")
    public ResponseEntity<List<Translation>> getTranslationsList() {
        log.info("Admin fetching translations list");
        List<Translation> translations = translationService.getAllTranslations();
        log.info("Retrieved {} translations for admin", translations.size());
        return ResponseEntity.ok(translations);
    }

    @PostMapping
    @PreAuthorize("hasRole('zev_admin')")
    public ResponseEntity<Translation> createTranslation(@RequestBody Translation translation) {
        log.info("Creating new translation with key: {}", translation.getKey());
        Translation saved = translationService.saveTranslation(translation);
        log.info("Created translation: {}", saved.getKey());
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{key}")
    @PreAuthorize("hasRole('zev_admin')")
    public ResponseEntity<Translation> updateTranslation(@PathVariable String key,
            @RequestBody Translation translation) {
        log.info("Updating translation with key: {}", key);
        if (!key.equals(translation.getKey())) {
            log.warn("Translation key mismatch - path: {}, body: {}", key, translation.getKey());
            return ResponseEntity.badRequest().build();
        }
        Translation updated = translationService.saveTranslation(translation);
        log.info("Updated translation: {}", updated.getKey());
        return ResponseEntity.ok(updated);
    }
}
