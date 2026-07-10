package ch.nacht.controller;

import ch.nacht.dto.DatenbankAbfrageRequestDTO;
import ch.nacht.dto.DatenbankAbfrageResponseDTO;
import ch.nacht.service.DatenbankService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST-Controller für die generische Datenbank-Ansicht. Ausschliesslich für zev_admin
 * (Permission {@code datenbank:read}); read-only.
 */
@RestController
@RequestMapping("/api/datenbank")
@PreAuthorize("hasAuthority('datenbank:read')")
public class DatenbankController {

    private static final Logger log = LoggerFactory.getLogger(DatenbankController.class);
    private final DatenbankService datenbankService;

    public DatenbankController(DatenbankService datenbankService) {
        this.datenbankService = datenbankService;
        log.info("DatenbankController initialized");
    }

    @GetMapping("/tabellen")
    public List<String> getTabellen() {
        List<String> tabellen = datenbankService.getTabellen();
        log.info("Datenbank-Ansicht: {} Tabellen gelistet", tabellen.size());
        return tabellen;
    }

    @GetMapping("/standard-filter")
    public ResponseEntity<?> getStandardFilter(@RequestParam String tabelle) {
        try {
            String filter = datenbankService.getStandardFilter(tabelle);
            return ResponseEntity.ok(Map.of("where", filter));
        } catch (IllegalArgumentException e) {
            log.warn("Datenbank-Ansicht: Standard-Filter abgelehnt: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/abfrage")
    public ResponseEntity<?> abfrage(@Valid @RequestBody DatenbankAbfrageRequestDTO request) {
        try {
            DatenbankAbfrageResponseDTO response = datenbankService.abfrage(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("Datenbank-Ansicht: Abfrage abgelehnt: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
