package ch.nacht.controller;

import ch.nacht.dto.LizenzenDTO;
import ch.nacht.service.LizenzenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/lizenzen")
@PreAuthorize("hasRole('zev')")
public class LizenzenController {

    private static final Logger log = LoggerFactory.getLogger(LizenzenController.class);
    private final LizenzenService lizenzenService;

    public LizenzenController(LizenzenService lizenzenService) {
        this.lizenzenService = lizenzenService;
        log.info("LizenzenController initialized");
    }

    @GetMapping
    public ResponseEntity<?> getBackendLizenzen() {
        log.info("Lieferung Backend-Lizenzen");
        try {
            List<LizenzenDTO> lizenzen = lizenzenService.getBackendLizenzen();
            log.info("Liefere {} Backend-Lizenzen", lizenzen.size());
            return ResponseEntity.ok(lizenzen);
        } catch (IllegalStateException e) {
            log.error("SBOM nicht verfügbar: {}", e.getMessage());
            return ResponseEntity.status(503).body(e.getMessage());
        }
    }
}
