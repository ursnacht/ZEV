package ch.nacht.service;

import ch.nacht.entity.Metrik;
import ch.nacht.repository.MetrikRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service für benutzerdefinierte Metriken mit Datenbank-Persistierung.
 * Metriken werden bei Änderungen in der Datenbank gespeichert und beim Start geladen.
 */
@Service
public class MetricsService {

    private static final Logger log = LoggerFactory.getLogger(MetricsService.class);

    private static final String METRIC_MESSDATEN_UPLOAD_TOTAL = "zev.messdaten.upload.total";
    private static final String METRIC_MESSDATEN_UPLOAD_ZEITPUNKT = "zev.messdaten.upload.letzter_zeitpunkt";
    private static final String METRIC_SOLARVERTEILUNG_TOTAL = "zev.solarverteilung.berechnung.total";
    private static final String METRIC_SOLARVERTEILUNG_ZEITPUNKT = "zev.solarverteilung.berechnung.letzter_zeitpunkt";

    private final MetrikRepository metrikRepository;
    private final ObjectMapper objectMapper;

    // AtomicLong für alle Metriken (Gauges, damit Werte setzbar sind)
    private final AtomicLong messdatenUploadTotal;
    private final AtomicLong solarverteilungTotal;
    private final AtomicLong letzterMessdatenUploadTimestamp;
    private final AtomicLong letzteSolarverteilungTimestamp;

    public MetricsService(MeterRegistry meterRegistry, MetrikRepository metrikRepository) {
        this.metrikRepository = metrikRepository;
        this.objectMapper = new ObjectMapper();

        // Alle Metriken als Gauge registrieren (damit Werte setzbar sind)
        this.messdatenUploadTotal = new AtomicLong(0);
        Gauge.builder(METRIC_MESSDATEN_UPLOAD_TOTAL, messdatenUploadTotal, AtomicLong::get)
                .description("Gesamtanzahl der Messdaten-Uploads")
                .register(meterRegistry);

        this.solarverteilungTotal = new AtomicLong(0);
        Gauge.builder(METRIC_SOLARVERTEILUNG_TOTAL, solarverteilungTotal, AtomicLong::get)
                .description("Gesamtanzahl der Solarverteilungsberechnungen")
                .register(meterRegistry);

        this.letzterMessdatenUploadTimestamp = new AtomicLong(0);
        Gauge.builder(METRIC_MESSDATEN_UPLOAD_ZEITPUNKT, letzterMessdatenUploadTimestamp, AtomicLong::get)
                .description("Unix-Timestamp des letzten Messdaten-Uploads")
                .register(meterRegistry);

        this.letzteSolarverteilungTimestamp = new AtomicLong(0);
        Gauge.builder(METRIC_SOLARVERTEILUNG_ZEITPUNKT, letzteSolarverteilungTimestamp, AtomicLong::get)
                .description("Unix-Timestamp der letzten Solarverteilungsberechnung")
                .register(meterRegistry);

        log.info("MetricsService initialisiert - Metriken registriert");
    }

    /**
     * Lädt persistierte Metriken aus der Datenbank beim Anwendungsstart.
     */
    @PostConstruct
    public void loadPersistedMetrics() {
        log.info("Lade persistierte Metriken aus der Datenbank...");

        loadMetric(METRIC_MESSDATEN_UPLOAD_TOTAL, messdatenUploadTotal);
        loadMetric(METRIC_SOLARVERTEILUNG_TOTAL, solarverteilungTotal);
        loadMetric(METRIC_MESSDATEN_UPLOAD_ZEITPUNKT, letzterMessdatenUploadTimestamp);
        loadMetric(METRIC_SOLARVERTEILUNG_ZEITPUNKT, letzteSolarverteilungTimestamp);

        log.info("Metriken geladen - Uploads: {}, Berechnungen: {}",
                messdatenUploadTotal.get(), solarverteilungTotal.get());
    }

    private void loadMetric(String name, AtomicLong target) {
        metrikRepository.findByName(name).ifPresent(metrik -> {
            try {
                Map<String, Object> valueMap = objectMapper.readValue(metrik.getValue(), Map.class);
                Object value = valueMap.get("value");
                if (value instanceof Number) {
                    target.set(((Number) value).longValue());
                    log.debug("Metrik '{}' geladen mit Wert: {}", name, target.get());
                }
            } catch (JsonProcessingException e) {
                log.warn("Konnte Metrik '{}' nicht laden: {}", name, e.getMessage());
            }
        });
    }

    @Transactional
    private void persistMetric(String name, long value) {
        try {
            String jsonValue = objectMapper.writeValueAsString(Map.of("value", value));

            Metrik metrik = metrikRepository.findByName(name)
                    .orElseGet(() -> new Metrik(name, jsonValue));

            metrik.setValue(jsonValue);
            metrikRepository.save(metrik);

            log.debug("Metrik '{}' persistiert mit Wert: {}", name, value);
        } catch (JsonProcessingException e) {
            log.error("Konnte Metrik '{}' nicht persistieren: {}", name, e.getMessage());
        }
    }

    /**
     * Wird aufgerufen, wenn Messdaten hochgeladen wurden.
     */
    @Transactional
    public void recordMessdatenUpload() {
        long newTotal = messdatenUploadTotal.incrementAndGet();
        long now = Instant.now().getEpochSecond();
        letzterMessdatenUploadTimestamp.set(now);

        persistMetric(METRIC_MESSDATEN_UPLOAD_TOTAL, newTotal);
        persistMetric(METRIC_MESSDATEN_UPLOAD_ZEITPUNKT, now);

        log.debug("Metrik aktualisiert: Messdaten-Upload #{} um {}", newTotal, LocalDateTime.now());
    }

    /**
     * Wird aufgerufen, wenn eine Solarverteilung berechnet wurde.
     */
    @Transactional
    public void recordSolarverteilungBerechnung() {
        long newTotal = solarverteilungTotal.incrementAndGet();
        long now = Instant.now().getEpochSecond();
        letzteSolarverteilungTimestamp.set(now);

        persistMetric(METRIC_SOLARVERTEILUNG_TOTAL, newTotal);
        persistMetric(METRIC_SOLARVERTEILUNG_ZEITPUNKT, now);

        log.debug("Metrik aktualisiert: Solarverteilung #{} um {}", newTotal, LocalDateTime.now());
    }

    /**
     * Liefert den Zeitpunkt des letzten Messdaten-Uploads.
     */
    public LocalDateTime getLetzterMessdatenUpload() {
        long timestamp = letzterMessdatenUploadTimestamp.get();
        if (timestamp == 0) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(timestamp), ZoneId.systemDefault());
    }

    /**
     * Liefert den Zeitpunkt der letzten Solarverteilungsberechnung.
     */
    public LocalDateTime getLetzteSolarverteilung() {
        long timestamp = letzteSolarverteilungTimestamp.get();
        if (timestamp == 0) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(timestamp), ZoneId.systemDefault());
    }

    /**
     * Liefert die Gesamtanzahl der Messdaten-Uploads.
     */
    public long getMessdatenUploadTotal() {
        return messdatenUploadTotal.get();
    }

    /**
     * Liefert die Gesamtanzahl der Solarverteilungsberechnungen.
     */
    public long getSolarverteilungTotal() {
        return solarverteilungTotal.get();
    }
}
