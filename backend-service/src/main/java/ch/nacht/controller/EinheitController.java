package ch.nacht.controller;

import ch.nacht.dto.EinheitMatchRequestDTO;
import ch.nacht.dto.EinheitMatchResponseDTO;
import ch.nacht.entity.Einheit;
import ch.nacht.service.EinheitMatchingService;
import ch.nacht.service.EinheitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/einheit")
@PreAuthorize("hasRole('zev_admin')")
public class EinheitController {

    private static final Logger log = LoggerFactory.getLogger(EinheitController.class);
    private final EinheitService einheitService;
    private final EinheitMatchingService einheitMatchingService;

    public EinheitController(EinheitService einheitService,
                             EinheitMatchingService einheitMatchingService) {
        this.einheitService = einheitService;
        this.einheitMatchingService = einheitMatchingService;
        log.info("EinheitController initialized");
    }

    @GetMapping
    @PreAuthorize("hasRole('zev')")
    public List<Einheit> getAllEinheiten() {
        log.info("Fetching all einheiten");
        List<Einheit> einheiten = einheitService.getAllEinheiten();
        log.info("Retrieved {} einheiten", einheiten.size());
        return einheiten;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Einheit> getEinheitById(@PathVariable Long id) {
        log.info("Fetching einheit with id: {}", id);
        return einheitService.getEinheitById(id)
                .map(einheit -> {
                    log.info("Found einheit: {}", einheit.getName());
                    return ResponseEntity.ok(einheit);
                })
                .orElseGet(() -> {
                    log.warn("Einheit not found with id: {}", id);
                    return ResponseEntity.notFound().build();
                });
    }

    @PostMapping
    public ResponseEntity<Einheit> createEinheit(@jakarta.validation.Valid @RequestBody Einheit einheit) {
        log.info("Creating new einheit: {}", einheit.getName());
        Einheit saved = einheitService.createEinheit(einheit);
        log.info("Created einheit with id: {}", saved.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Einheit> updateEinheit(@PathVariable Long id,
            @jakarta.validation.Valid @RequestBody Einheit einheit) {
        log.info("Updating einheit with id: {}", id);
        return einheitService.updateEinheit(id, einheit)
                .map(updated -> {
                    log.info("Successfully updated einheit: {}", updated.getName());
                    return ResponseEntity.ok(updated);
                })
                .orElseGet(() -> {
                    log.warn("Cannot update - einheit not found with id: {}", id);
                    return ResponseEntity.notFound().build();
                });
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEinheit(@PathVariable Long id) {
        log.info("Deleting einheit with id: {}", id);
        if (einheitService.deleteEinheit(id)) {
            log.info("Successfully deleted einheit with id: {}", id);
            return ResponseEntity.noContent().build();
        } else {
            log.warn("Cannot delete - einheit not found with id: {}", id);
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/match")
    public ResponseEntity<EinheitMatchResponseDTO> matchEinheit(
            @jakarta.validation.Valid @RequestBody EinheitMatchRequestDTO request) {
        log.info("Matching einheit by filename: {}", request.getFilename());
        try {
            EinheitMatchResponseDTO response =
                    einheitMatchingService.matchEinheitByFilename(request.getFilename());
            log.info("Match result: matched={}, einheitId={}",
                    response.isMatched(), response.getEinheitId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error during einheit matching: {}", e.getMessage());
            return ResponseEntity.ok(EinheitMatchResponseDTO.builder()
                    .matched(false)
                    .message("KI-Service nicht verfügbar")
                    .confidence(0.0)
                    .build());
        }
    }
}
