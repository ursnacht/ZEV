package ch.nacht.service;

import ch.nacht.ProportionalConsumptionDistribution;
import ch.nacht.SolarDistribution;
import ch.nacht.entity.Einheit;
import ch.nacht.entity.EinheitTyp;
import ch.nacht.entity.Messwerte;
import ch.nacht.repository.EinheitRepository;
import ch.nacht.repository.MesswerteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MesswerteService {

    private static final Logger log = LoggerFactory.getLogger(MesswerteService.class);
    private final MesswerteRepository messwerteRepository;
    private final EinheitRepository einheitRepository;
    private final OrganizationContextService organizationContextService;
    private final HibernateFilterService hibernateFilterService;

    public MesswerteService(MesswerteRepository messwerteRepository,
                            EinheitRepository einheitRepository,
                            OrganizationContextService organizationContextService,
                            HibernateFilterService hibernateFilterService) {
        this.messwerteRepository = messwerteRepository;
        this.einheitRepository = einheitRepository;
        this.organizationContextService = organizationContextService;
        this.hibernateFilterService = hibernateFilterService;
        log.info("MesswerteService initialized");
    }

    @Transactional
    @CacheEvict(value = "statistik", allEntries = true)
    public Map<String, Object> processCsvUpload(MultipartFile file, Long einheitId, String dateStr) throws Exception {
        hibernateFilterService.enableOrgFilter();
        log.info("Starting CSV upload processing - einheitId: {}, date: {}, filename: {}, size: {} bytes",
                einheitId, dateStr, file.getOriginalFilename(), file.getSize());

        // Fetch the Einheit entity
        Einheit einheit = einheitRepository.findById(einheitId)
                .orElseThrow(() -> {
                    log.error("Einheit not found with id: {}", einheitId);
                    return new RuntimeException("Einheit not found with id: " + einheitId);
                });

        log.debug("Found einheit: {} (type: {})", einheit.getName(), einheit.getTyp());

        LocalDate date = LocalDate.parse(dateStr);
        LocalDateTime zeit = LocalDateTime.of(date, LocalTime.of(0, 0));

        List<Messwerte> messwerteList = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String line;
            reader.readLine(); // Skip header line
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String[] parts = line.split("[,;]");
                if (parts.length >= 3) {
                    Double total = Double.parseDouble(parts[1].trim());
                    Double zev = Double.parseDouble(parts[2].trim());

                    Messwerte messwert = new Messwerte(zeit, total, zev, einheit);
                    messwert.setOrgId(organizationContextService.getCurrentOrgId());
                    messwerteList.add(messwert);
                    zeit = zeit.plusMinutes(15);
                } else {
                    log.warn("Skipping invalid line {} in CSV: insufficient columns", lineNumber);
                }
            }
            log.info("Parsed {} records from CSV file", messwerteList.size());
        } catch (Exception e) {
            log.error("Error reading CSV file: {}", e.getMessage(), e);
            throw e;
        }

        // Delete existing messwerte for the same einheit and entire month to allow
        // overwrite
        LocalDateTime dateTimeFrom = date.withDayOfMonth(1).atStartOfDay();
        LocalDateTime dateTimeTo = date.withDayOfMonth(date.lengthOfMonth()).atTime(23, 59, 59);
        List<Messwerte> existingMesswerte = messwerteRepository.findByEinheitAndZeitBetween(einheit, dateTimeFrom,
                dateTimeTo);
        if (!existingMesswerte.isEmpty()) {
            log.info("Deleting {} existing messwerte records for month {}", existingMesswerte.size(), date.getMonth());
            messwerteRepository.deleteAll(existingMesswerte);
        }

        messwerteRepository.saveAll(messwerteList);
        log.info("Successfully saved {} messwerte records for einheit: {}", messwerteList.size(), einheit.getName());

        return Map.of(
                "status", "success",
                "count", messwerteList.size(),
                "einheitId", einheitId,
                "einheitName", einheit.getName());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getMesswerteByEinheit(Long einheitId, LocalDate dateFrom, LocalDate dateTo) {
        hibernateFilterService.enableOrgFilter();
        log.info("Fetching messwerte for einheitId: {}, dateFrom: {}, dateTo: {}", einheitId, dateFrom, dateTo);

        LocalDateTime dateTimeFrom = dateFrom.atStartOfDay();
        LocalDateTime dateTimeTo = dateTo.atTime(23, 59, 59);

        Einheit einheit = einheitRepository.findById(einheitId)
                .orElseThrow(() -> {
                    log.error("Einheit not found with id: {}", einheitId);
                    return new RuntimeException("Einheit not found");
                });

        List<Messwerte> messwerte = messwerteRepository.findByEinheitAndZeitBetween(einheit, dateTimeFrom, dateTimeTo);
        log.info("Found {} messwerte records for einheit: {}", messwerte.size(), einheit.getName());

        return messwerte.stream()
                .map(m -> {
                    Map<String, Object> data = new java.util.HashMap<>();
                    data.put("zeit", m.getZeit().toString());
                    data.put("total", m.getTotal() != null ? m.getTotal() : 0.0);
                    data.put("zevCalculated", m.getZevCalculated() != null ? m.getZevCalculated() : 0.0);
                    return data;
                })
                .collect(Collectors.toList());
    }

    @Transactional
    @CacheEvict(value = "statistik", allEntries = true)
    public CalculationResult calculateSolarDistribution(LocalDateTime dateFrom, LocalDateTime dateTo,
            String algorithm) {
        hibernateFilterService.enableOrgFilter();
        log.info("Starting solar distribution calculation - dateFrom: {}, dateTo: {}, algorithm: {}",
                dateFrom, dateTo, algorithm);

        long startTime = System.currentTimeMillis();

        // Get all distinct timestamps in the date range
        List<LocalDateTime> distinctZeiten = messwerteRepository.findDistinctZeitBetween(dateFrom, dateTo);
        log.info("Found {} distinct timestamps to process", distinctZeiten.size());

        int processedTimestamps = 0;
        int processedRecords = 0;
        BigDecimal totalSolarProduced = BigDecimal.ZERO;
        BigDecimal totalDistributed = BigDecimal.ZERO;

        // Process each timestamp
        for (LocalDateTime zeit : distinctZeiten) {
            // Get all producers for this timestamp
            List<Messwerte> producers = messwerteRepository.findByZeitAndEinheitTyp(zeit, EinheitTyp.PRODUCER);

            // Skip if no producers
            if (producers.isEmpty()) {
                log.debug("No producers found for timestamp: {}", zeit);
                continue;
            }

            // Calculate total solar production at this timestamp
            // Producer values are negative (production) or positive (control unit consumption)
            // Sum gives net production (negative), we need absolute value for distribution
            BigDecimal netProduction = producers.stream()
                    .map(m -> BigDecimal.valueOf(m.getTotal()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Net production should be negative (production > consumption)
            // If positive or zero, there's nothing to distribute
            BigDecimal solarProduction = netProduction.compareTo(BigDecimal.ZERO) < 0
                    ? netProduction.abs()
                    : BigDecimal.ZERO;

            totalSolarProduced = totalSolarProduced.add(solarProduction);
            log.debug("Timestamp: {}, Solar production: {} kWh from {} producers",
                    zeit, solarProduction, producers.size());

            // Get all consumers for this timestamp
            List<Messwerte> consumers = messwerteRepository.findByZeitAndEinheitTyp(zeit, EinheitTyp.CONSUMER);

            // Skip if no consumers
            if (consumers.isEmpty()) {
                log.debug("No consumers found for timestamp: {}", zeit);
                continue;
            }

            // Get consumption values
            List<BigDecimal> consumptions = consumers.stream()
                    .map(m -> BigDecimal.valueOf(m.getTotal()))
                    .collect(Collectors.toList());

            // Calculate distribution using selected algorithm
            List<BigDecimal> distributions;
            if ("PROPORTIONAL".equalsIgnoreCase(algorithm)) {
                log.debug("Using PROPORTIONAL distribution algorithm");
                distributions = ProportionalConsumptionDistribution.distributeSolarPower(solarProduction, consumptions);
            } else {
                log.debug("Using EQUAL_SHARE distribution algorithm");
                // Default to EQUAL_SHARE
                distributions = SolarDistribution.distributeSolarPower(solarProduction, consumptions);
            }

            // Update zev_calculated for each consumer
            for (int i = 0; i < consumers.size(); i++) {
                Messwerte consumer = consumers.get(i);
                BigDecimal distributedAmount = distributions.get(i);
                consumer.setZevCalculated(distributedAmount.doubleValue());
                messwerteRepository.save(consumer);
                totalDistributed = totalDistributed.add(distributedAmount);
                processedRecords++;
            }

            processedTimestamps++;

            if (processedTimestamps % 100 == 0) {
                log.debug("Progress: {} timestamps processed", processedTimestamps);
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info(
                "Solar distribution calculation completed - timestamps: {}, records: {}, totalProduced: {} kWh, totalDistributed: {} kWh, duration: {} ms",
                processedTimestamps, processedRecords, totalSolarProduced, totalDistributed, duration);

        return new CalculationResult(
                processedTimestamps,
                processedRecords,
                dateFrom,
                dateTo,
                totalSolarProduced.doubleValue(),
                totalDistributed.doubleValue());
    }

    public static class CalculationResult {
        private final int processedTimestamps;
        private final int processedRecords;
        private final LocalDateTime dateFrom;
        private final LocalDateTime dateTo;
        private final double totalSolarProduced;
        private final double totalDistributed;

        public CalculationResult(int processedTimestamps, int processedRecords, LocalDateTime dateFrom,
                LocalDateTime dateTo, double totalSolarProduced, double totalDistributed) {
            this.processedTimestamps = processedTimestamps;
            this.processedRecords = processedRecords;
            this.dateFrom = dateFrom;
            this.dateTo = dateTo;
            this.totalSolarProduced = totalSolarProduced;
            this.totalDistributed = totalDistributed;
        }

        public int getProcessedTimestamps() {
            return processedTimestamps;
        }

        public int getProcessedRecords() {
            return processedRecords;
        }

        public LocalDateTime getDateFrom() {
            return dateFrom;
        }

        public LocalDateTime getDateTo() {
            return dateTo;
        }

        public double getTotalSolarProduced() {
            return totalSolarProduced;
        }

        public double getTotalDistributed() {
            return totalDistributed;
        }
    }
}
