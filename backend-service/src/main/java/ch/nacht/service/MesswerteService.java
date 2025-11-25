package ch.nacht.service;

import ch.nacht.SolarDistribution;
import ch.nacht.entity.EinheitTyp;
import ch.nacht.entity.Messwerte;
import ch.nacht.repository.MesswerteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class MesswerteService {

    private final MesswerteRepository messwerteRepository;

    public MesswerteService(MesswerteRepository messwerteRepository) {
        this.messwerteRepository = messwerteRepository;
    }

    @Transactional
    public CalculationResult calculateSolarDistribution(LocalDateTime dateFrom, LocalDateTime dateTo) {
        // Get all distinct timestamps in the date range
        List<LocalDateTime> distinctZeiten = messwerteRepository.findDistinctZeitBetween(dateFrom, dateTo);

        int processedTimestamps = 0;
        int processedRecords = 0;
        double totalSolarProduced = 0.0;
        double totalDistributed = 0.0;

        // Process each timestamp
        for (LocalDateTime zeit : distinctZeiten) {
            // Get all producers for this timestamp
            List<Messwerte> producers = messwerteRepository.findByZeitAndEinheitTyp(zeit, EinheitTyp.PRODUCER);

            // Skip if no producers
            if (producers.isEmpty()) {
                continue;
            }

            // Calculate total solar production at this timestamp
            double solarProduction = producers.stream()
                    .mapToDouble(Messwerte::getTotal)
                    .sum();

            totalSolarProduced += solarProduction;

            // Get all consumers for this timestamp
            List<Messwerte> consumers = messwerteRepository.findByZeitAndEinheitTyp(zeit, EinheitTyp.CONSUMER);

            // Skip if no consumers
            if (consumers.isEmpty()) {
                continue;
            }

            // Get consumption values
            List<Double> consumptions = consumers.stream()
                    .map(Messwerte::getTotal)
                    .collect(Collectors.toList());

            // Calculate distribution using the existing algorithm
            List<Double> distributions = SolarDistribution.distributeSolarPower(solarProduction, consumptions);

            // Update zev_calculated for each consumer
            for (int i = 0; i < consumers.size(); i++) {
                Messwerte consumer = consumers.get(i);
                Double distributedAmount = distributions.get(i);
                consumer.setZevCalculated(distributedAmount);
                messwerteRepository.save(consumer);
                totalDistributed += distributedAmount;
                processedRecords++;
            }

            processedTimestamps++;
        }

        return new CalculationResult(
                processedTimestamps,
                processedRecords,
                dateFrom,
                dateTo,
                totalSolarProduced,
                totalDistributed
        );
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
