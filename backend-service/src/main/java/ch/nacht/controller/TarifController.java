package ch.nacht.controller;

import ch.nacht.entity.Tarif;
import ch.nacht.service.TarifService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for managing tariffs.
 */
@RestController
@RequestMapping("/api/tarife")
@PreAuthorize("hasAuthority('tarife:manage')")
public class TarifController {

    private static final Logger log = LoggerFactory.getLogger(TarifController.class);
    private final TarifService tarifService;

    public TarifController(TarifService tarifService) {
        this.tarifService = tarifService;
        log.info("TarifController initialized");
    }

    @GetMapping
    public List<Tarif> getAllTarife() {
        log.info("Fetching all tarife");
        List<Tarif> tarife = tarifService.getAllTarife();
        log.info("Retrieved {} tarife", tarife.size());
        return tarife;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Tarif> getTarifById(@PathVariable Long id) {
        log.info("Fetching tarif with id: {}", id);
        return tarifService.getTarifById(id)
                .map(tarif -> {
                    log.info("Found tarif: {}", tarif.getBezeichnung());
                    return ResponseEntity.ok(tarif);
                })
                .orElseGet(() -> {
                    log.warn("Tarif not found with id: {}", id);
                    return ResponseEntity.notFound().build();
                });
    }

    @PostMapping
    public ResponseEntity<?> createTarif(@Valid @RequestBody Tarif tarif) {
        log.info("Creating new tarif: {}", tarif.getBezeichnung());
        try {
            Tarif saved = tarifService.saveTarif(tarif);
            log.info("Created tarif with id: {}", saved.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (IllegalArgumentException e) {
            log.warn("Failed to create tarif: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateTarif(@PathVariable Long id, @Valid @RequestBody Tarif tarif) {
        log.info("Updating tarif with id: {}", id);

        if (!tarifService.getTarifById(id).isPresent()) {
            log.warn("Cannot update - tarif not found with id: {}", id);
            return ResponseEntity.notFound().build();
        }

        try {
            tarif.setId(id);
            Tarif updated = tarifService.saveTarif(tarif);
            log.info("Successfully updated tarif: {}", updated.getBezeichnung());
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            log.warn("Failed to update tarif: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTarif(@PathVariable Long id) {
        log.info("Deleting tarif with id: {}", id);
        if (tarifService.deleteTarif(id)) {
            log.info("Successfully deleted tarif with id: {}", id);
            return ResponseEntity.noContent().build();
        } else {
            log.warn("Cannot delete - tarif not found with id: {}", id);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Prüft die Tarifabdeckung. Bewusst {@code GET}: Der Aufruf liest nur und verändert nichts.
     *
     * <p>Früher {@code POST} — das kostete einen schwer auffindbaren Fehler: Angular schickte
     * dabei einen Rumpf-Body ({@code {}}), den diese Methode mangels {@code @RequestBody} nie
     * las. Über den Reverse-Proxy blieb die Verbindung danach unbrauchbar; Caddy brach die
     * Antwort mit „aborting with incomplete response" ab und der Browser meldete
     * {@code ERR_INCOMPLETE_CHUNKED_ENCODING}. In der Oberfläche erschien statt der Lücken-Liste
     * die generische Meldung „Fehler bei der Validierung", reproduzierbar in etwa der Hälfte
     * aller Aufrufe. Ein GET hat keinen Body und kann so nicht mehr betroffen sein.</p>
     */
    @GetMapping("/validate")
    public ResponseEntity<TarifService.ValidationResult> validateTarife(
            @RequestParam(defaultValue = "quartale") String modus) {
        log.info("Validating tarife with modus: {}", modus);

        TarifService.ValidationResult result;
        if ("jahre".equalsIgnoreCase(modus)) {
            result = tarifService.validateJahre();
        } else {
            result = tarifService.validateQuartale();
        }

        log.info("Validation result: valid={}, periods with gaps={}", result.valid(), result.luecken().size());
        return ResponseEntity.ok(result);
    }
}
