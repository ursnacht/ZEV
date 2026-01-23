package ch.nacht.controller;

import ch.nacht.entity.Mieter;
import ch.nacht.service.MieterService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for managing tenants.
 */
@RestController
@RequestMapping("/api/mieter")
@PreAuthorize("hasRole('zev_admin')")
public class MieterController {

    private static final Logger log = LoggerFactory.getLogger(MieterController.class);
    private final MieterService mieterService;

    public MieterController(MieterService mieterService) {
        this.mieterService = mieterService;
        log.info("MieterController initialized");
    }

    @GetMapping
    public List<Mieter> getAllMieter() {
        log.info("Fetching all mieter");
        List<Mieter> mieter = mieterService.getAllMieter();
        log.info("Retrieved {} mieter", mieter.size());
        return mieter;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Mieter> getMieterById(@PathVariable Long id) {
        log.info("Fetching mieter with id: {}", id);
        return mieterService.getMieterById(id)
                .map(mieter -> {
                    log.info("Found mieter: {}", mieter.getName());
                    return ResponseEntity.ok(mieter);
                })
                .orElseGet(() -> {
                    log.warn("Mieter not found with id: {}", id);
                    return ResponseEntity.notFound().build();
                });
    }

    @PostMapping
    public ResponseEntity<?> createMieter(@Valid @RequestBody Mieter mieter) {
        log.info("Creating new mieter: {}", mieter.getName());
        try {
            Mieter saved = mieterService.saveMieter(mieter);
            log.info("Created mieter with id: {}", saved.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (IllegalArgumentException e) {
            log.warn("Failed to create mieter: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateMieter(@PathVariable Long id, @Valid @RequestBody Mieter mieter) {
        log.info("Updating mieter with id: {}", id);

        if (!mieterService.getMieterById(id).isPresent()) {
            log.warn("Cannot update - mieter not found with id: {}", id);
            return ResponseEntity.notFound().build();
        }

        try {
            mieter.setId(id);
            Mieter updated = mieterService.saveMieter(mieter);
            log.info("Successfully updated mieter: {}", updated.getName());
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            log.warn("Failed to update mieter: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMieter(@PathVariable Long id) {
        log.info("Deleting mieter with id: {}", id);
        if (mieterService.deleteMieter(id)) {
            log.info("Successfully deleted mieter with id: {}", id);
            return ResponseEntity.noContent().build();
        } else {
            log.warn("Cannot delete - mieter not found with id: {}", id);
            return ResponseEntity.notFound().build();
        }
    }
}
