package ch.nacht.controller;

import ch.nacht.entity.Translation;
import ch.nacht.service.TranslationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/translations")
public class TranslationController {

    private final TranslationService translationService;

    public TranslationController(TranslationService translationService) {
        this.translationService = translationService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Map<String, String>>> getAllTranslations() {
        List<Translation> translations = translationService.getAllTranslations();
        Map<String, String> de = translations.stream()
                .collect(Collectors.toMap(Translation::getKey, t -> t.getDeutsch() != null ? t.getDeutsch() : ""));
        Map<String, String> en = translations.stream()
                .collect(Collectors.toMap(Translation::getKey, t -> t.getEnglisch() != null ? t.getEnglisch() : ""));

        return ResponseEntity.ok(Map.of("de", de, "en", en));
    }

    @GetMapping("/list")
    @PreAuthorize("hasRole('zev_admin')")
    public ResponseEntity<List<Translation>> getTranslationsList() {
        return ResponseEntity.ok(translationService.getAllTranslations());
    }

    @PostMapping
    @PreAuthorize("hasRole('zev_admin')")
    public ResponseEntity<Translation> createTranslation(@RequestBody Translation translation) {
        return ResponseEntity.ok(translationService.saveTranslation(translation));
    }

    @PutMapping("/{key}")
    @PreAuthorize("hasRole('zev_admin')")
    public ResponseEntity<Translation> updateTranslation(@PathVariable String key,
            @RequestBody Translation translation) {
        if (!key.equals(translation.getKey())) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(translationService.saveTranslation(translation));
    }
}
