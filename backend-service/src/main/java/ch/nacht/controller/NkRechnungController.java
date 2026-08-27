package ch.nacht.controller;

import ch.nacht.dto.NkRechnungLaufDTO;
import ch.nacht.service.NkRechnungService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;

/**
 * REST controller fuer die Rechnungen aus einer Nebenkostenabrechnung
 * (Specs/Nebenkosten/RechnungenGenerieren.md, FR-6).
 *
 * <p>Eigener Endpunkt statt einer Erweiterung von {@code POST /api/rechnungen/generate}: Dessen
 * Antwort waere sonst art-abhaengig geworden, und die Validierung von {@code von}/{@code bis}/
 * {@code einheitIds} haette aus der Bean-Validation in eine Verzweigung wandern muessen. Hier hat
 * die Antwort genau eine Form.
 *
 * <p><b>Beide Permissions.</b> Es ist eine NK-Aktion, aber sie stellt Rechnungen und bucht
 * Forderungen. Heute halten alle drei Fachrollen beide (Specs/Berechtigungen.md), die Forderung ist
 * also keine Einschraenkung — sie bleibt aber richtig, wenn die Rollen spaeter auseinanderlaufen.
 *
 * <p>Zusaetzlich prueft {@code NkRechnungService} den Feature-Flag
 * {@code NEBENKOSTENABRECHNUNG} und antwortet mit {@code 403}, wenn er fuer den Mandanten aus ist —
 * auch beim Download eines schon erzeugten PDF.
 */
@RestController
@RequestMapping("/api/nebenkosten/abrechnungen/{abrechnungId}/rechnungen")
@PreAuthorize("hasAuthority('nebenkosten:manage') and hasAuthority('rechnungen:manage')")
public class NkRechnungController {

    private static final Logger log = LoggerFactory.getLogger(NkRechnungController.class);

    private final NkRechnungService nkRechnungService;

    public NkRechnungController(NkRechnungService nkRechnungService) {
        this.nkRechnungService = nkRechnungService;
        log.info("NkRechnungController initialized");
    }

    /**
     * Erzeugt je Mieter eine Rechnung und bucht die Forderungen.
     *
     * @param abrechnungId ID der Nebenkostenabrechnung
     * @param request      optionale Sprache; der Rest steht im Pfad
     * @return Ergebnis des Laufs, oder {@code 404}, wenn die Abrechnung nicht erreichbar ist
     */
    @PostMapping
    public ResponseEntity<NkRechnungLaufDTO> erzeugeRechnungen(
            @PathVariable Long abrechnungId,
            @RequestBody(required = false) RechnungslaufRequest request) {
        log.info("Creating NK invoices for abrechnung {}", abrechnungId);
        String sprache = request != null ? request.sprache : null;

        try {
            return ResponseEntity.ok(nkRechnungService.erzeugeRechnungen(abrechnungId, sprache));
        } catch (NoSuchElementException e) {
            // Unbekannt und fremder Mandant sind bewusst nicht unterscheidbar: Die gefilterte
            // Abfrage liefert in beiden Faellen nichts, und eine eigene Meldung fuer "existiert,
            // gehoert aber jemand anderem" waere eine Auskunft ueber fremde Daten.
            log.warn("Nebenkostenabrechnung nicht erreichbar: {}", abrechnungId);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Laedt das erzeugte PDF eines Mieters.
     *
     * <p>Eigene Route statt {@code GET /api/rechnungen/download/{key}}: Dort muesste dem
     * Schluessel angesehen werden, welcher Art er ist. Ueber diese Route ist die Art
     * strukturell gegeben — und damit auch die Flag-Pruefung.
     *
     * @param abrechnungId ID der Nebenkostenabrechnung
     * @param mieterId     ID des Mieters
     * @return das PDF als Anhang, oder {@code 404}, wenn es abgelaufen oder unbekannt ist
     */
    @GetMapping("/{mieterId}/pdf")
    public ResponseEntity<byte[]> ladePdf(@PathVariable Long abrechnungId,
                                          @PathVariable Long mieterId) {
        log.info("Download requested for NK invoice abrechnung={} mieter={}", abrechnungId, mieterId);

        return nkRechnungService.ladePdf(abrechnungId, mieterId)
                .map(download -> {
                    log.info("Serving NK invoice: {}, size: {} bytes",
                            download.filename(), download.pdf().length);
                    return ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_DISPOSITION,
                                    "attachment; filename=\"" + download.filename() + "\"")
                            .contentType(MediaType.APPLICATION_PDF)
                            .contentLength(download.pdf().length)
                            .body(download.pdf());
                })
                .orElseGet(() -> {
                    log.warn("NK invoice not found or expired: abrechnung={} mieter={}",
                            abrechnungId, mieterId);
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
                });
    }

    /**
     * Request-Body des Laufs. Nur die Sprache — Abrechnung und Mieter stehen im Pfad, und dadurch
     * entfaellt jede Validierung, die von einer Rechnungsart abhaengt.
     */
    public static class RechnungslaufRequest {
        public String sprache;
    }
}
