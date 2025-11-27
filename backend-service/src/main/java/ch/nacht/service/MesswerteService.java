package ch.nacht.service;

import ch.nacht.SolarDistribution;
import ch.nacht.entity.Einheit;
import ch.nacht.entity.EinheitTyp;
import ch.nacht.entity.Messwerte;
import ch.nacht.repository.EinheitRepository;
import ch.nacht.repository.MesswerteRepository;
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

    private final MesswerteRepository messwerteRepository;
    private final EinheitRepository einheitRepository;

    public MesswerteService(MesswerteRepository messwerteRepository, EinheitRepository einheitRepository) {
        this.messwerteRepository = messwerteRepository;
        this.einheitRepository = einheitRepository;
    }

    @Transactional
    public Map<String, Object> processCsvUpload(MultipartFile file, Long einheitId, String dateStr) throws Exception {
        // Fetch the Einheit entity
        Einheit einheit = einheitRepository.findById(einheitId)
                .orElseThrow(() -> new RuntimeException("Einheit not found with id: " + einheitId));

        LocalDate date = LocalDate.parse(dateStr);
        LocalDateTime zeit = LocalDateTime.of(date, LocalTime.of(0, 15));

        List<Messwerte> messwerteList = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String line;
            reader.readLine(); // Skip header line
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("[,;]");
                if (parts.length >= 3) {
                    Double total = Math.abs(Double.parseDouble(parts[1].trim()));
                    Double zev = Math.abs(Double.parseDouble(parts[2].trim()));

                    messwerteList.add(new Messwerte(zeit, total, zev, einheit));
                    zeit = zeit.plusMinutes(15);
                }
            }
        }

        // Delete existing messwerte for the same einheit and date range to allow overwrite
        LocalDateTime dateTimeFrom = date.atStartOfDay();
        LocalDateTime dateTimeTo = date.atTime(23, 59, 59);
        List<Messwerte> existingMesswerte = messwerteRepository.findByEinheitAndZeitBetween(einheit, dateTimeFrom, dateTimeTo);
        if (!existingMesswerte.isEmpty()) {
            messwerteRepository.deleteAll(existingMesswerte);
        }

        messwerteRepository.saveAll(messwerteList);

        return Map.of(
                "status", "success",
                "count", messwerteList.size(),
                "einheitId", einheitId,
                "einheitName", einheit.getName());
    }

    public List<Map<String, Object>> getMesswerteByEinheit(Long einheitId, LocalDate dateFrom, LocalDate dateTo) {
        LocalDateTime dateTimeFrom = dateFrom.atStartOfDay();
        LocalDateTime dateTimeTo = dateTo.atTime(23, 59, 59);

        Einheit einheit = einheitRepository.findById(einheitId)
                .orElseThrow(() -> new RuntimeException("Einheit not found"));

        List<Messwerte> messwerte = messwerteRepository.findByEinheitAndZeitBetween(einheit, dateTimeFrom, dateTimeTo);

        return messwerte.stream()
                .map(m -> {
                    Map<String, Object> data = new java.util.HashMap<>();
                    data.put("zeit", m.getZeit().toString());
                    data.put("total", m.getTotal());
                    data.put("zevCalculated", m.getZevCalculated());
                    return data;
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public CalculationResult calculateSolarDistribution(LocalDateTime dateFrom, LocalDateTime dateTo) {
        // Get all distinct timestamps in the date range
        List<LocalDateTime> distinctZeiten = messwerteRepository.findDistinctZeitBetween(dateFrom, dateTo);

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
                continue;
            }

            // Calculate total solar production at this timestamp
            BigDecimal solarProduction = producers.stream()
                    .map(m -> BigDecimal.valueOf(m.getTotal()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            totalSolarProduced = totalSolarProduced.add(solarProduction);

            // Get all consumers for this timestamp
            List<Messwerte> consumers = messwerteRepository.findByZeitAndEinheitTyp(zeit, EinheitTyp.CONSUMER);

            // Skip if no consumers
            if (consumers.isEmpty()) {
                continue;
            }

            // Get consumption values
            List<BigDecimal> consumptions = consumers.stream()
                    .map(m -> BigDecimal.valueOf(m.getTotal()))
                    .collect(Collectors.toList());

            // Calculate distribution using the existing algorithm
            List<BigDecimal> distributions = SolarDistribution.distributeSolarPower(solarProduction, consumptions);

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
        }

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
