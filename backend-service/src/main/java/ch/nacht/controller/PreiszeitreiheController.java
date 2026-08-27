package ch.nacht.controller;

import ch.nacht.dto.PreiszeitreiheDownloadDTO;
import ch.nacht.dto.PreiszeitreihePunktDTO;
import ch.nacht.exception.PreiszeitreiheQuelleException;
import ch.nacht.service.PreiszeitreiheService;

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
 * REST-Endpunkte der Preiszeitreihe (Specs/Preiszeitreihe.md, FR-4).
 *
 * <p>Dieselbe Permission wie die Seite, auf der das Diagramm sitzt: {@code /tarife} verlangt
 * {@code tarife:manage}. Zusaetzlich prueft der Service das Feature-Flag — sonst waere die API
 * ueber jeden HTTP-Client erreichbar, obwohl das Panel verborgen ist.
 */
@RestController
@RequestMapping("/api/preiszeitreihe")
@PreAuthorize("hasAuthority('tarife:manage')")
public class PreiszeitreiheController {

    private static final Logger log = LoggerFactory.getLogger(PreiszeitreiheController.class);

    private final PreiszeitreiheService preiszeitreiheService;

    public PreiszeitreiheController(PreiszeitreiheService preiszeitreiheService) {
        this.preiszeitreiheService = preiszeitreiheService;
        log.info("PreiszeitreiheController initialized");
    }

    /**
     * Werte einer Spanne. {@code von}/{@code bis} sind Datumsangaben in Ortszeit, beide inklusive.
     *
     * @return {@code 200} mit den Punkten (leer moeglich), {@code 400} bei ungueltiger Spanne
     */
    @GetMapping
    public ResponseEntity<?> getPunkte(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate von,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bis) {
        log.info("Fetching Preiszeitreihe from {} to {}", von, bis);
        try {
            List<PreiszeitreihePunktDTO> punkte = preiszeitreiheService.getPunkte(von, bis);
            log.info("Retrieved {} Preiszeitreihe points", punkte.size());
            return ResponseEntity.ok(punkte);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid Preiszeitreihe range: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Holt die Preise jetzt bei der Quelle.
     *
     * <p>Die Statuscodes sind eindeutig getrennt, damit Maske und Test sie unterscheiden koennen:
     * {@code 502}, wenn die <b>Quelle</b> versagt (dort liegt der Fehler, nicht beim Aufrufer),
     * {@code 400} bei lokaler Fehlkonfiguration. Der Rumpf ist immer Klartext — ein Objekt-Rumpf
     * erscheint in der Maske als {@code [object Object]}.
     *
     * @return {@code 200} mit den Zaehlwerten, sonst {@code 502} bzw. {@code 400} im Klartext
     */
    @PostMapping("/download")
    public ResponseEntity<?> download() {
        log.info("Manual Preiszeitreihe download requested");
        try {
            PreiszeitreiheDownloadDTO ergebnis = preiszeitreiheService.download();
            log.info("Preiszeitreihe download done: {} new, {} updated",
                    ergebnis.neu(), ergebnis.aktualisiert());
            return ResponseEntity.ok(ergebnis);
        } catch (PreiszeitreiheQuelleException e) {
            log.warn("Preiszeitreihe download failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("Preiszeitreihe download misconfigured: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
