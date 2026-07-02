package ch.nacht.controller;

import ch.nacht.dto.FeatureFlagDTO;
import ch.nacht.dto.FeatureFlagUpdateDTO;
import ch.nacht.service.FeatureFlagService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for reading and managing tenant-specific feature flags.
 * <p>
 * Read access ({@code GET /api/feature-flags}) is available to any authenticated {@code zev}
 * user so the frontend can render conditionally. Management endpoints require {@code zev_admin}.
 * The {@code org_id} is always derived server-side from the JWT, never from the client.
 */
@RestController
@RequestMapping("/api/feature-flags")
public class FeatureFlagController {

    private static final Logger log = LoggerFactory.getLogger(FeatureFlagController.class);
    private final FeatureFlagService featureFlagService;

    public FeatureFlagController(FeatureFlagService featureFlagService) {
        this.featureFlagService = featureFlagService;
        log.info("FeatureFlagController initialized");
    }

    /**
     * Effektive Flags des aktuellen Mandanten als Key→Boolean-Map.
     */
    @GetMapping
    @PreAuthorize("hasRole('zev')")
    public Map<String, Boolean> getEffectiveFlags() {
        Long orgId = featureFlagService.getCurrentOrgId();
        log.info("Fetching effective feature flags for org: {}", orgId);
        return featureFlagService.getEffectiveFlags(orgId);
    }

    /**
     * Admin-Sicht: alle deklarierten Flags inkl. Default, effektivem Wert und Quelle.
     */
    @GetMapping("/admin")
    @PreAuthorize("hasRole('zev_admin')")
    public List<FeatureFlagDTO> getAdminFlags() {
        Long orgId = featureFlagService.getCurrentOrgId();
        log.info("Fetching admin feature flags for org: {}", orgId);
        return featureFlagService.getAdminFlags(orgId);
    }

    /**
     * Setzt eine mandantenspezifische Überschreibung. Unbekannter Key → HTTP 400.
     */
    @PutMapping("/{key}")
    @PreAuthorize("hasRole('zev_admin')")
    public ResponseEntity<?> setOverride(@PathVariable String key, @Valid @RequestBody FeatureFlagUpdateDTO body) {
        Long orgId = featureFlagService.getCurrentOrgId();
        log.info("Setting feature flag override: org={}, flag={}, enabled={}", orgId, key, body.getEnabled());
        try {
            featureFlagService.setOverride(orgId, key, body.getEnabled());
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            log.warn("Failed to set feature flag override: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Entfernt eine mandantenspezifische Überschreibung (Flag fällt auf Default zurück).
     * Unbekannter Key → HTTP 400.
     */
    @DeleteMapping("/{key}")
    @PreAuthorize("hasRole('zev_admin')")
    public ResponseEntity<?> removeOverride(@PathVariable String key) {
        Long orgId = featureFlagService.getCurrentOrgId();
        log.info("Removing feature flag override: org={}, flag={}", orgId, key);
        try {
            featureFlagService.removeOverride(orgId, key);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            log.warn("Failed to remove feature flag override: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
