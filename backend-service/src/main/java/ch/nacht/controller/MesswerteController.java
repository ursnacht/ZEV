package ch.nacht.controller;

import ch.nacht.entity.Einheit;
import ch.nacht.entity.FeatureFlag;
import ch.nacht.exception.FeatureDisabledException;
import ch.nacht.service.CalculationProgressService;
import ch.nacht.service.EinheitService;
import ch.nacht.service.FeatureFlagService;
import ch.nacht.service.MesswerteService;
import ch.nacht.service.MetricsService;
import ch.nacht.service.OrganizationContextService;
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
    private final MetricsService metricsService;
    private final EinheitService einheitService;
    private final CalculationProgressService calculationProgressService;
    private final OrganizationContextService organizationContextService;
    private final FeatureFlagService featureFlagService;

    public MesswerteController(MesswerteService messwerteService, MetricsService metricsService,
                               EinheitService einheitService,
                               CalculationProgressService calculationProgressService,
                               OrganizationContextService organizationContextService,
                               FeatureFlagService featureFlagService) {
        this.messwerteService = messwerteService;
        this.metricsService = metricsService;
        this.einheitService = einheitService;
        this.calculationProgressService = calculationProgressService;
        this.organizationContextService = organizationContextService;
        this.featureFlagService = featureFlagService;
        log.info("MesswerteController initialized");
    }

    @PostMapping("/upload")
    @PreAuthorize("hasAuthority('messwerte:write')")
    public ResponseEntity<Map<String, Object>> uploadCsv(
            @RequestParam("date") String dateStr,
            @RequestParam("einheitId") Long einheitId,
            @RequestParam("file") MultipartFile file) {

        log.info("CSV upload request received - einheitId: {}, date: {}, filename: {}",
                einheitId, dateStr, file.getOriginalFilename());

        // Feature-Gate: Upload nur bei aktivem Flag zulassen (unabhängig von der Rolle).
        Long orgId = organizationContextService.getCurrentOrgId();
        if (!featureFlagService.isEnabled(orgId, FeatureFlag.MESSWERTE_UPLOAD)) {
            log.warn("Upload rejected - feature MESSWERTE_UPLOAD disabled for org: {}", orgId);
            throw new FeatureDisabledException("FEATURE_FLAG_DEAKTIVIERT");
        }

        try {
            Map<String, Object> result = messwerteService.processCsvUpload(file, einheitId, dateStr);

            // Einheit-Namen für Metrik abrufen
            String einheitName = einheitService.getEinheitById(einheitId)
                    .map(Einheit::getName)
                    .orElse("unbekannt");
            metricsService.recordMessdatenUpload(einheitName);

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

    @PostMapping("/upload-bilanz")
    @PreAuthorize("hasAuthority('messwerte:write')")
    public ResponseEntity<Map<String, Object>> uploadBilanzCsv(
            @RequestParam("file") MultipartFile file) {

        log.info("Bilanz CSV upload request received - filename: {}", file.getOriginalFilename());

        // Feature-Gate wie beim Einheiten-Upload (unabhängig von der Rolle).
        Long orgId = organizationContextService.getCurrentOrgId();
        if (!featureFlagService.isEnabled(orgId, FeatureFlag.MESSWERTE_UPLOAD)) {
            log.warn("Bilanz upload rejected - feature MESSWERTE_UPLOAD disabled for org: {}", orgId);
            throw new FeatureDisabledException("FEATURE_FLAG_DEAKTIVIERT");
        }

        try {
            Map<String, Object> result = messwerteService.processBilanzCsvUpload(file);
            log.info("Bilanz CSV upload successful - result: {}", result);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Bilanz CSV upload failed - filename: {}, error: {}",
                    file.getOriginalFilename(), e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()));
        }
    }

    @PostMapping("/calculate-distribution")
    @PreAuthorize("hasAuthority('messwerte:write')")
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

            metricsService.recordSolarverteilungBerechnung();
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
    @PreAuthorize("hasAuthority('messwerte:read')")
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

    @GetMapping("/calculation-progress")
    @PreAuthorize("hasAuthority('messwerte:write')")
    public ResponseEntity<Map<String, Object>> getCalculationProgress() {
        CalculationProgressService.CalculationProgress progress =
                calculationProgressService.getProgress(organizationContextService.getCurrentOrgId());
        return ResponseEntity.ok(Map.of(
                "total", progress.total(),
                "processed", progress.processed()));
    }
}
