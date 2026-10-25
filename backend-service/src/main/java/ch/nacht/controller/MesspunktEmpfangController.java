package ch.nacht.controller;

import ch.nacht.dto.MesspunktEmpfangDTO;
import ch.nacht.dto.MesspunktEmpfangMeldungDTO;
import ch.nacht.service.MesspunktEmpfangService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Ansicht Messpunkt-Empfang (Specs/Messpunkt-Empfang.md).
 *
 * <p>Autorisierung wie {@code EinheitController}: die Klasse verlangt {@code einheit:write}, das
 * Lesen ist mit {@code einheit:read} überschrieben. Bewusst ohne {@code @Profile("mqtt")}.
 */
@RestController
@RequestMapping("/api/messpunkt-empfang")
@PreAuthorize("hasAuthority('einheit:write')")
public class MesspunktEmpfangController {

    private static final Logger log = LoggerFactory.getLogger(MesspunktEmpfangController.class);
    private final MesspunktEmpfangService messpunktEmpfangService;

    public MesspunktEmpfangController(MesspunktEmpfangService messpunktEmpfangService) {
        this.messpunktEmpfangService = messpunktEmpfangService;
        log.info("MesspunktEmpfangController initialized");
    }

    @GetMapping
    @PreAuthorize("hasAuthority('einheit:read')")
    public List<MesspunktEmpfangDTO> getAllMesspunktEmpfang() {
        log.info("Fetching all messpunkt-empfang");
        List<MesspunktEmpfangDTO> zeilen = messpunktEmpfangService.getAllMesspunktEmpfang();
        log.info("Retrieved {} messpunkt-empfang", zeilen.size());
        return zeilen;
    }

    @GetMapping("/{id}/letzte-meldung")
    @PreAuthorize("hasAuthority('einheit:read')")
    public ResponseEntity<MesspunktEmpfangMeldungDTO> getLetzteMeldung(@PathVariable Long id) {
        log.info("Fetching letzte Meldung of messpunkt-empfang with id: {}", id);
        return messpunktEmpfangService.getLetzteMeldung(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> {
                    log.warn("Messpunkt-empfang not found with id: {}", id);
                    return ResponseEntity.notFound().build();
                });
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMesspunktEmpfang(@PathVariable Long id) {
        log.info("Deleting messpunkt-empfang with id: {}", id);
        if (messpunktEmpfangService.deleteMesspunktEmpfang(id)) {
            log.info("Successfully deleted messpunkt-empfang with id: {}", id);
            return ResponseEntity.noContent().build();
        } else {
            log.warn("Cannot delete - messpunkt-empfang not found with id: {}", id);
            return ResponseEntity.notFound().build();
        }
    }
}
