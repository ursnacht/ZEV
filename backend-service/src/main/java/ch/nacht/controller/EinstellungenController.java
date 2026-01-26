package ch.nacht.controller;

import ch.nacht.dto.EinstellungenDTO;
import ch.nacht.service.EinstellungenService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for managing tenant-specific settings.
 */
@RestController
@RequestMapping("/api/einstellungen")
@PreAuthorize("hasRole('zev_admin')")
public class EinstellungenController {

    private static final Logger log = LoggerFactory.getLogger(EinstellungenController.class);
    private final EinstellungenService einstellungenService;

    public EinstellungenController(EinstellungenService einstellungenService) {
        this.einstellungenService = einstellungenService;
        log.info("EinstellungenController initialized");
    }

    /**
     * Get current settings for the tenant.
     * Returns 204 No Content if no settings exist yet.
     */
    @GetMapping
    public ResponseEntity<EinstellungenDTO> getEinstellungen() {
        log.info("Fetching settings");
        EinstellungenDTO einstellungen = einstellungenService.getEinstellungen();
        if (einstellungen == null) {
            log.info("No settings configured yet");
            return ResponseEntity.noContent().build();
        }
        log.info("Retrieved settings with id: {}", einstellungen.getId());
        return ResponseEntity.ok(einstellungen);
    }

    /**
     * Save or update settings for the tenant.
     */
    @PutMapping
    public ResponseEntity<?> saveEinstellungen(@Valid @RequestBody EinstellungenDTO dto) {
        log.info("Saving settings");
        try {
            EinstellungenDTO saved = einstellungenService.saveEinstellungen(dto);
            log.info("Settings saved with id: {}", saved.getId());
            return ResponseEntity.ok(saved);
        } catch (IllegalArgumentException e) {
            log.warn("Failed to save settings: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
