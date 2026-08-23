package ch.nacht.controller;

import ch.nacht.dto.NkAbrechnungDetailDTO;
import ch.nacht.entity.NkAbrechnung;
import ch.nacht.service.NkAbrechnungService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller für die Nebenkostenabrechnungen (Specs/Nebenkosten/Abrechnung.md, FR-6).
 *
 * <p>Zusätzlich zur Permission prüft der Service den Feature-Flag
 * {@code NEBENKOSTENABRECHNUNG} und antwortet mit {@code 403}, wenn er für den Mandanten aus ist.
 */
@RestController
@RequestMapping("/api/nebenkosten/abrechnungen")
@PreAuthorize("hasAuthority('nebenkosten:manage')")
public class NkAbrechnungController {

    private static final Logger log = LoggerFactory.getLogger(NkAbrechnungController.class);
    private final NkAbrechnungService nkAbrechnungService;

    public NkAbrechnungController(NkAbrechnungService nkAbrechnungService) {
        this.nkAbrechnungService = nkAbrechnungService;
        log.info("NkAbrechnungController initialized");
    }

    @GetMapping
    public List<NkAbrechnung> getAllAbrechnungen() {
        log.info("Fetching all Nebenkostenabrechnungen");
        List<NkAbrechnung> abrechnungen = nkAbrechnungService.getAllAbrechnungen();
        log.info("Retrieved {} Nebenkostenabrechnungen", abrechnungen.size());
        return abrechnungen;
    }

    /**
     * Vorlage für eine neue Abrechnung. Steht vor {@code /{id}}, weil die Maske die vorgeschlagene
     * Anzahl Wohnungen schon vor dem ersten Speichern braucht.
     */
    @GetMapping("/vorlage")
    public ResponseEntity<NkAbrechnungDetailDTO> getVorlage() {
        log.info("Fetching Nebenkostenabrechnung template");
        return ResponseEntity.ok(nkAbrechnungService.getVorlage());
    }

    @GetMapping("/{id}")
    public ResponseEntity<NkAbrechnungDetailDTO> getAbrechnungById(@PathVariable Long id) {
        log.info("Fetching Nebenkostenabrechnung with id: {}", id);
        return nkAbrechnungService.getAbrechnungDetail(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> {
                    log.warn("Nebenkostenabrechnung not found with id: {}", id);
                    return ResponseEntity.notFound().build();
                });
    }

    @PostMapping
    public ResponseEntity<?> createAbrechnung(@Valid @RequestBody NkAbrechnung abrechnung) {
        log.info("Creating new Nebenkostenabrechnung: {}", abrechnung.getBezeichnung());
        try {
            NkAbrechnung saved = nkAbrechnungService.createAbrechnung(abrechnung);
            log.info("Created Nebenkostenabrechnung with id: {}", saved.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (IllegalArgumentException e) {
            log.warn("Failed to create Nebenkostenabrechnung: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateAbrechnung(@PathVariable Long id,
                                              @RequestBody NkAbrechnungDetailDTO detail) {
        log.info("Updating Nebenkostenabrechnung with id: {}", id);
        try {
            return nkAbrechnungService.saveAbrechnung(id, detail)
                    .<ResponseEntity<?>>map(ResponseEntity::ok)
                    .orElseGet(() -> {
                        log.warn("Cannot update - Nebenkostenabrechnung not found with id: {}", id);
                        return ResponseEntity.notFound().build();
                    });
        } catch (IllegalArgumentException e) {
            log.warn("Failed to update Nebenkostenabrechnung: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Setzt oder löst „abgerechnet". Eigener Endpunkt, weil das Flag als einziges Feld auch auf
     * einer abgeschlossenen Abrechnung änderbar ist — sonst liesse sie sich nie wieder öffnen.
     */
    @PatchMapping("/{id}/abgerechnet")
    public ResponseEntity<?> setAbgerechnet(@PathVariable Long id,
                                            @RequestBody Map<String, Boolean> body) {
        Boolean abgerechnet = body.get("abgerechnet");
        if (abgerechnet == null) {
            log.warn("PATCH abgerechnet without value for id: {}", id);
            return ResponseEntity.badRequest().body("NK_FEHLER_ABGERECHNET_FEHLT");
        }

        log.info("Setting abgerechnet={} for Nebenkostenabrechnung {}", abgerechnet, id);
        return nkAbrechnungService.setAbgerechnet(id, abgerechnet)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> {
                    log.warn("Cannot patch - Nebenkostenabrechnung not found with id: {}", id);
                    return ResponseEntity.notFound().build();
                });
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAbrechnung(@PathVariable Long id) {
        log.info("Deleting Nebenkostenabrechnung with id: {}", id);
        if (nkAbrechnungService.deleteAbrechnung(id)) {
            log.info("Successfully deleted Nebenkostenabrechnung with id: {}", id);
            return ResponseEntity.noContent().build();
        }
        log.warn("Cannot delete - Nebenkostenabrechnung not found with id: {}", id);
        return ResponseEntity.notFound().build();
    }
}
