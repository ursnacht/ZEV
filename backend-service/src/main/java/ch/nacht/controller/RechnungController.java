package ch.nacht.controller;

import ch.nacht.dto.RechnungDTO;
import ch.nacht.service.RechnungPdfService;
import ch.nacht.service.RechnungService;
import ch.nacht.service.RechnungStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for invoice generation and download.
 */
@RestController
@RequestMapping("/api/rechnungen")
@PreAuthorize("hasRole('zev_admin')")
public class RechnungController {

    private static final Logger log = LoggerFactory.getLogger(RechnungController.class);

    private final RechnungService rechnungService;
    private final RechnungPdfService rechnungPdfService;
    private final RechnungStorageService rechnungStorageService;

    public RechnungController(RechnungService rechnungService,
                              RechnungPdfService rechnungPdfService,
                              RechnungStorageService rechnungStorageService) {
        this.rechnungService = rechnungService;
        this.rechnungPdfService = rechnungPdfService;
        this.rechnungStorageService = rechnungStorageService;
        log.info("RechnungController initialized");
    }

    /**
     * Generate invoices for the selected units and time period.
     *
     * @param request Request containing date range and unit IDs
     * @return List of generated invoice metadata
     */
    @PostMapping("/generate")
    public ResponseEntity<Map<String, Object>> generateRechnungen(@RequestBody GenerateRequest request) {
        log.info("Generating invoices for {} units from {} to {}",
                request.einheitIds.size(), request.von, request.bis);

        // Validate request
        if (request.von == null || request.bis == null) {
            log.warn("Invalid date range: von={}, bis={}", request.von, request.bis);
            return ResponseEntity.badRequest().body(Map.of("error", "Date range is required"));
        }
        if (request.von.isAfter(request.bis)) {
            log.warn("Invalid date range: von ({}) is after bis ({})", request.von, request.bis);
            return ResponseEntity.badRequest().body(Map.of("error", "Start date must be before end date"));
        }
        if (request.einheitIds == null || request.einheitIds.isEmpty()) {
            log.warn("No units selected for invoice generation");
            return ResponseEntity.badRequest().body(Map.of("error", "At least one unit must be selected"));
        }

        // Clear previous invoices
        rechnungStorageService.clearAll();

        // Calculate invoices
        List<RechnungDTO> rechnungen = rechnungService.berechneRechnungen(
                request.einheitIds, request.von, request.bis);

        // Generate PDFs and store them
        List<Map<String, Object>> generatedList = new ArrayList<>();
        String sprache = request.sprache != null ? request.sprache : "de";

        for (RechnungDTO rechnung : rechnungen) {
            try {
                byte[] pdf = rechnungPdfService.generatePdf(rechnung, sprache);
                String key = rechnungStorageService.sanitizeKey(rechnung.getEinheitName());
                rechnungStorageService.store(key, pdf);

                Map<String, Object> meta = new HashMap<>();
                meta.put("einheitId", rechnung.getEinheitId());
                meta.put("einheitName", rechnung.getEinheitName());
                meta.put("mietername", rechnung.getMietername());
                meta.put("endBetrag", rechnung.getEndBetrag());
                meta.put("filename", rechnungStorageService.getFilename(key));
                meta.put("downloadKey", key);
                generatedList.add(meta);

                log.debug("Generated invoice for unit {}: {} CHF",
                        rechnung.getEinheitName(), rechnung.getEndBetrag());
            } catch (Exception e) {
                log.error("Failed to generate invoice for unit {}: {}",
                        rechnung.getEinheitName(), e.getMessage(), e);
            }
        }

        log.info("Successfully generated {} invoices", generatedList.size());

        Map<String, Object> response = new HashMap<>();
        response.put("rechnungen", generatedList);
        response.put("count", generatedList.size());

        return ResponseEntity.ok(response);
    }

    /**
     * Download a generated invoice by unit name key.
     *
     * @param key The sanitized unit name (download key)
     * @return PDF file as attachment
     */
    @GetMapping("/download/{key}")
    public ResponseEntity<byte[]> downloadRechnung(@PathVariable String key) {
        log.info("Download requested for invoice: {}", key);

        return rechnungStorageService.get(key)
                .map(pdf -> {
                    String filename = rechnungStorageService.getFilename(key);
                    log.info("Serving invoice download: {}, size: {} bytes", filename, pdf.length);

                    return ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                            .contentType(MediaType.APPLICATION_PDF)
                            .contentLength(pdf.length)
                            .body(pdf);
                })
                .orElseGet(() -> {
                    log.warn("Invoice not found or expired: {}", key);
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
                });
    }

    /**
     * Request body for invoice generation.
     */
    public static class GenerateRequest {
        public LocalDate von;
        public LocalDate bis;
        public List<Long> einheitIds;
        public String sprache;
    }
}
