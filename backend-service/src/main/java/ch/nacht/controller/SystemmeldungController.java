package ch.nacht.controller;

import ch.nacht.dto.SystemmeldungDTO;
import ch.nacht.entity.MeldungLevel;
import ch.nacht.entity.Systemmeldung;
import ch.nacht.service.SystemmeldungService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Slice;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST-Controller für Systemmeldungen. Lesen mit {@code systemmeldungen:read} (alle Fachrollen),
 * Verwalten (erledigt umschalten, löschen) mit {@code systemmeldungen:manage} (zev_admin/org_admin).
 * Liste serverseitig paginiert/sortiert/gefiltert (analog Datenbank-Ansicht, {@code hatMehr}-Flag).
 */
@RestController
@RequestMapping("/api/systemmeldungen")
public class SystemmeldungController {

    private static final Logger log = LoggerFactory.getLogger(SystemmeldungController.class);
    private final SystemmeldungService systemmeldungService;

    public SystemmeldungController(SystemmeldungService systemmeldungService) {
        this.systemmeldungService = systemmeldungService;
        log.info("SystemmeldungController initialized");
    }

    @GetMapping
    @PreAuthorize("hasAuthority('systemmeldungen:read')")
    public Map<String, Object> getSystemmeldungen(
            @RequestParam(required = false) Boolean erledigt,
            @RequestParam(required = false) String kategorie,
            @RequestParam(required = false) MeldungLevel level,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "zuletztAufgetreten") String sortSpalte,
            @RequestParam(defaultValue = "DESC") String sortRichtung) {

        Slice<Systemmeldung> slice = systemmeldungService.getSeite(
                erledigt, kategorie, level, page, size, sortSpalte, sortRichtung);
        List<SystemmeldungDTO> items = slice.getContent().stream()
                .map(SystemmeldungDTO::fromEntity)
                .toList();
        return Map.of(
                "items", items,
                "hatMehr", slice.hasNext(),
                "page", page);
    }

    @GetMapping("/kategorien")
    @PreAuthorize("hasAuthority('systemmeldungen:read')")
    public List<String> getKategorien() {
        return systemmeldungService.getKategorien();
    }

    @PutMapping("/{id}/erledigt")
    @PreAuthorize("hasAuthority('systemmeldungen:manage')")
    public ResponseEntity<?> setErledigt(@PathVariable Long id, @RequestParam boolean erledigt) {
        try {
            SystemmeldungDTO dto = SystemmeldungDTO.fromEntity(systemmeldungService.setErledigt(id, erledigt));
            return ResponseEntity.ok(dto);
        } catch (IllegalArgumentException e) {
            log.warn("Systemmeldung {} nicht gefunden: {}", id, e.getMessage());
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            log.warn("Erledigt-Umschalten abgelehnt (id={}): {}", id, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('systemmeldungen:manage')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        boolean geloescht = systemmeldungService.delete(id);
        return geloescht ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
