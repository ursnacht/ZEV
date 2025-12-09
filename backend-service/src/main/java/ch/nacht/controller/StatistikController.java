package ch.nacht.controller;

import ch.nacht.dto.StatistikDTO;
import ch.nacht.service.StatistikPdfService;
import ch.nacht.service.StatistikService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/statistik")
@PreAuthorize("hasRole('zev')")
public class StatistikController {

    private static final Logger log = LoggerFactory.getLogger(StatistikController.class);
    private final StatistikService statistikService;
    private final StatistikPdfService statistikPdfService;

    public StatistikController(StatistikService statistikService, StatistikPdfService statistikPdfService) {
        this.statistikService = statistikService;
        this.statistikPdfService = statistikPdfService;
        log.info("StatistikController initialized");
    }

    @GetMapping
    public ResponseEntity<StatistikDTO> getStatistik(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate von,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bis) {

        log.info("Statistik request - von: {}, bis: {}", von, bis);

        try {
            if (von.isAfter(bis)) {
                log.warn("Invalid date range - von ({}) is after bis ({})", von, bis);
                return ResponseEntity.badRequest().build();
            }

            StatistikDTO statistik = statistikService.getStatistik(von, bis);

            log.info("Statistik retrieved - {} months, data complete: {}",
                    statistik.getMonate().size(), statistik.isDatenVollstaendig());

            return ResponseEntity.ok(statistik);

        } catch (Exception e) {
            log.error("Failed to retrieve statistik - von: {}, bis: {}, error: {}",
                    von, bis, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/letztes-datum")
    public ResponseEntity<LocalDate> getLetztesMessdatum() {
        log.info("Request for letztes Messdatum");

        try {
            LocalDate letztesMessdatum = statistikService.ermittleLetztesMessdatum();

            if (letztesMessdatum == null) {
                log.info("No measurements found");
                return ResponseEntity.noContent().build();
            }

            log.info("Letztes Messdatum: {}", letztesMessdatum);
            return ResponseEntity.ok(letztesMessdatum);

        } catch (Exception e) {
            log.error("Failed to retrieve letztes Messdatum - error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/export/pdf")
    public ResponseEntity<byte[]> exportPdf(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate von,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bis,
            @RequestParam(defaultValue = "de") String sprache) {

        log.info("PDF export request - von: {}, bis: {}, sprache: {}", von, bis, sprache);

        try {
            if (von.isAfter(bis)) {
                log.warn("Invalid date range - von ({}) is after bis ({})", von, bis);
                return ResponseEntity.badRequest().build();
            }

            StatistikDTO statistik = statistikService.getStatistik(von, bis);
            byte[] pdf = statistikPdfService.generatePdf(statistik, sprache);

            String filename = String.format("statistik_%s_%s.pdf", von, bis);

            log.info("PDF export successful - filename: {}, size: {} bytes", filename, pdf.length);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdf);

        } catch (Exception e) {
            log.error("PDF export failed - von: {}, bis: {}, error: {}",
                    von, bis, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
