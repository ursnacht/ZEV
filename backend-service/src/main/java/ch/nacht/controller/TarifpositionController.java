package ch.nacht.controller;

import ch.nacht.dto.TarifpositionDTO;
import ch.nacht.entity.Einheit;
import ch.nacht.entity.Tarif;
import ch.nacht.entity.Tarifposition;
import ch.nacht.service.TarifpositionService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for manually captured tariff positions.
 *
 * <p>Schreiben erfordert {@code rechnungen:manage} (abrechnungsnahe, operative Daten), Lesen
 * {@code mieter:read} — siehe {@code Specs/Ladestromtarif.md} NFR-2.
 */
@RestController
@RequestMapping("/api/tarifpositionen")
@PreAuthorize("hasAuthority('rechnungen:manage')")
public class TarifpositionController {

    private static final Logger log = LoggerFactory.getLogger(TarifpositionController.class);
    private final TarifpositionService tarifpositionService;

    public TarifpositionController(TarifpositionService tarifpositionService) {
        this.tarifpositionService = tarifpositionService;
        log.info("TarifpositionController initialized");
    }

    @GetMapping
    @PreAuthorize("hasAuthority('mieter:read')")
    public List<TarifpositionDTO> getByEinheit(@RequestParam Long einheitId) {
        log.info("Fetching tariff positions for einheit {}", einheitId);
        List<TarifpositionDTO> positionen = tarifpositionService.getByEinheit(einheitId).stream()
                .map(TarifpositionDTO::von)
                .toList();
        log.info("Retrieved {} tariff positions", positionen.size());
        return positionen;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('mieter:read')")
    public ResponseEntity<TarifpositionDTO> getTarifpositionById(@PathVariable Long id) {
        return tarifpositionService.getTarifpositionById(id)
                .map(position -> ResponseEntity.ok(TarifpositionDTO.von(position)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> createTarifposition(@Valid @RequestBody TarifpositionDTO dto) {
        try {
            Tarifposition gespeichert = tarifpositionService.saveTarifposition(nachEntity(dto, null));
            return ResponseEntity.status(HttpStatus.CREATED).body(TarifpositionDTO.von(gespeichert));
        } catch (IllegalArgumentException e) {
            log.warn("Creating tariff position failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateTarifposition(@PathVariable Long id,
                                                 @Valid @RequestBody TarifpositionDTO dto) {
        if (tarifpositionService.getTarifpositionById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        try {
            Tarifposition gespeichert = tarifpositionService.saveTarifposition(nachEntity(dto, id));
            return ResponseEntity.ok(TarifpositionDTO.von(gespeichert));
        } catch (IllegalArgumentException e) {
            log.warn("Updating tariff position {} failed: {}", id, e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTarifposition(@PathVariable Long id) {
        if (tarifpositionService.deleteTarifposition(id)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * Map the DTO onto an entity. Einheit and Tarif carry the ID only — der Service löst sie auf
     * und prüft dabei Existenz, Mandant, Einheiten-Typ und zulässigen Tariftyp.
     */
    private Tarifposition nachEntity(TarifpositionDTO dto, Long id) {
        Tarifposition position = new Tarifposition();
        position.setId(id);
        if (dto.getEinheitId() != null) {
            Einheit einheit = new Einheit();
            einheit.setId(dto.getEinheitId());
            position.setEinheit(einheit);
        }
        if (dto.getTarifId() != null) {
            Tarif tarif = new Tarif();
            tarif.setId(dto.getTarifId());
            position.setTarif(tarif);
        }
        position.setJahr(dto.getJahr());
        position.setQuartal(dto.getQuartal());
        position.setMenge(dto.getMenge());
        position.setErfassungsart(dto.getErfassungsart());
        position.setQuellReferenz(dto.getQuellReferenz());
        position.setBemerkung(dto.getBemerkung());
        return position;
    }
}
