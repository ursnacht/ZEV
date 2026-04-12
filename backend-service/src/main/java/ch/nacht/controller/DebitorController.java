package ch.nacht.controller;

import ch.nacht.dto.DebitorDTO;
import ch.nacht.service.DebitorService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * REST controller for debitor entries (invoice tracking).
 */
@RestController
@RequestMapping("/api/debitoren")
@PreAuthorize("hasRole('zev_admin')")
public class DebitorController {

    private static final Logger log = LoggerFactory.getLogger(DebitorController.class);

    private final DebitorService debitorService;

    public DebitorController(DebitorService debitorService) {
        this.debitorService = debitorService;
        log.info("DebitorController initialized");
    }

    @GetMapping
    public List<DebitorDTO> getDebitoren(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate von,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bis) {
        log.info("Fetching debitors from {} to {}", von, bis);
        List<DebitorDTO> debitoren = debitorService.getDebitoren(von, bis);
        log.info("Retrieved {} debitors", debitoren.size());
        return debitoren;
    }

    @PostMapping
    public ResponseEntity<?> createDebitor(@Valid @RequestBody DebitorDTO dto) {
        log.info("Creating debitor entry for mieterId={}", dto.getMieterId());
        try {
            DebitorDTO created = debitorService.create(dto);
            log.info("Created debitor entry id={}", created.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            log.warn("Failed to create debitor: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateDebitor(@PathVariable Long id, @Valid @RequestBody DebitorDTO dto) {
        log.info("Updating debitor entry id={}", id);
        if (debitorService.getDebitorById(id).isEmpty()) {
            log.warn("Cannot update - debitor not found: id={}", id);
            return ResponseEntity.notFound().build();
        }
        try {
            DebitorDTO updated = debitorService.update(id, dto);
            log.info("Updated debitor entry id={}", id);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            log.warn("Failed to update debitor: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDebitor(@PathVariable Long id) {
        log.info("Deleting debitor entry id={}", id);
        if (debitorService.delete(id)) {
            log.info("Deleted debitor entry id={}", id);
            return ResponseEntity.noContent().build();
        } else {
            log.warn("Cannot delete - debitor not found: id={}", id);
            return ResponseEntity.notFound().build();
        }
    }
}
