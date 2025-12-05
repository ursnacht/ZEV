package ch.nacht.controller;

import ch.nacht.service.MesswerteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/messwerte")
public class MesswerteController {

    private static final Logger log = LoggerFactory.getLogger(MesswerteController.class);
    private final MesswerteService messwerteService;

    public MesswerteController(MesswerteService messwerteService) {
        this.messwerteService = messwerteService;
        log.info("MesswerteController initialized");
    }

    @PostMapping("/upload")
    @PreAuthorize("hasRole('zev_admin')")
    public ResponseEntity<Map<String, Object>> uploadCsv(
            @RequestParam("date") String dateStr,
            @RequestParam("einheitId") Long einheitId,
            @RequestParam("file") MultipartFile file) {

        log.info("CSV upload request received - einheitId: {}, date: {}, filename: {}",
                einheitId, dateStr, file.getOriginalFilename());

        try {
            Map<String, Object> result = messwerteService.processCsvUpload(file, einheitId, dateStr);
            log.info("CSV upload successful - einheitId: {}, result: {}", einheitId, result);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("CSV upload failed - einheitId: {}, date: {}, error: {}",
                    einheitId, dateStr, e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()));
        }
    }

    @PostMapping("/calculate-distribution")
    @PreAuthorize("hasRole('zev_admin')")
    public ResponseEntity<Map<String, Object>> calculateDistribution(
            @RequestParam("dateFrom") String dateFromStr,
            @RequestParam("dateTo") String dateToStr,
            @RequestParam(value = "algorithm", defaultValue = "EQUAL_SHARE") String algorithm) {

        log.info("Distribution calculation request - dateFrom: {}, dateTo: {}, algorithm: {}",
                dateFromStr, dateToStr, algorithm);

        try {
            LocalDate dateFrom = LocalDate.parse(dateFromStr);
            LocalDate dateTo = LocalDate.parse(dateToStr);

            // Convert to LocalDateTime (start of day and end of day)
            LocalDateTime dateTimeFrom = dateFrom.atStartOfDay();
            LocalDateTime dateTimeTo = dateTo.atTime(23, 59, 59);

            log.debug("Parsed date range - from: {}, to: {}", dateTimeFrom, dateTimeTo);

            // Call the service to calculate distribution with selected algorithm
            MesswerteService.CalculationResult result = messwerteService.calculateSolarDistribution(dateTimeFrom,
                    dateTimeTo, algorithm);

            log.info(
                    "Distribution calculation completed - algorithm: {}, timestamps: {}, records: {}, totalProduced: {}, totalDistributed: {}",
                    algorithm, result.getProcessedTimestamps(), result.getProcessedRecords(),
                    result.getTotalSolarProduced(), result.getTotalDistributed());

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "algorithm", algorithm,
                    "processedTimestamps", result.getProcessedTimestamps(),
                    "processedRecords", result.getProcessedRecords(),
                    "dateFrom", result.getDateFrom().toString(),
                    "dateTo", result.getDateTo().toString(),
                    "totalSolarProduced", result.getTotalSolarProduced(),
                    "totalDistributed", result.getTotalDistributed()));

        } catch (Exception e) {
            log.error("Distribution calculation failed - dateFrom: {}, dateTo: {}, algorithm: {}, error: {}",
                    dateFromStr, dateToStr, algorithm, e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()));
        }
    }

    @GetMapping("/by-einheit")
    @PreAuthorize("hasRole('zev')")
    public ResponseEntity<List<Map<String, Object>>> getMesswerteByEinheit(
            @RequestParam("einheitId") Long einheitId,
            @RequestParam("dateFrom") String dateFromStr,
            @RequestParam("dateTo") String dateToStr) {

        log.info("Get messwerte request - einheitId: {}, dateFrom: {}, dateTo: {}",
                einheitId, dateFromStr, dateToStr);

        try {
            LocalDate dateFrom = LocalDate.parse(dateFromStr);
            LocalDate dateTo = LocalDate.parse(dateToStr);

            List<Map<String, Object>> result = messwerteService.getMesswerteByEinheit(einheitId, dateFrom, dateTo);

            log.info("Retrieved {} messwerte records for einheitId: {}", result.size(), einheitId);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Failed to retrieve messwerte - einheitId: {}, error: {}",
                    einheitId, e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }
}
