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
import java.util.concurrent.atomic.AtomicReference;

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

    // AtomicLong für Zähler-Metriken
    private final AtomicLong messdatenUploadTotal;
    private final AtomicLong solarverteilungTotal;

    // AtomicReference<Instant> für Zeitstempel-Metriken
    private final AtomicReference<Instant> letzterMessdatenUpload;
    private final AtomicReference<Instant> letzteSolarverteilung;

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

        this.letzterMessdatenUpload = new AtomicReference<>(null);
        Gauge.builder(METRIC_MESSDATEN_UPLOAD_ZEITPUNKT, letzterMessdatenUpload,
                        ref -> ref.get() != null ? ref.get().getEpochSecond() : 0)
                .description("Unix-Timestamp des letzten Messdaten-Uploads")
                .register(meterRegistry);

        this.letzteSolarverteilung = new AtomicReference<>(null);
        Gauge.builder(METRIC_SOLARVERTEILUNG_ZEITPUNKT, letzteSolarverteilung,
                        ref -> ref.get() != null ? ref.get().getEpochSecond() : 0)
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
        loadTimestampMetric(METRIC_MESSDATEN_UPLOAD_ZEITPUNKT, letzterMessdatenUpload);
        loadTimestampMetric(METRIC_SOLARVERTEILUNG_ZEITPUNKT, letzteSolarverteilung);

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

    private void loadTimestampMetric(String name, AtomicReference<Instant> target) {
        metrikRepository.findByName(name).ifPresent(metrik -> {
            try {
                Map<String, Object> valueMap = objectMapper.readValue(metrik.getValue(), Map.class);
                Object value = valueMap.get("value");
                if (value instanceof String) {
                    // ISO-8601 Timestamp parsen (z.B. "2025-12-07T16:30:00")
                    LocalDateTime ldt = LocalDateTime.parse((String) value);
                    target.set(ldt.atZone(ZoneId.systemDefault()).toInstant());
                    log.debug("Zeitstempel-Metrik '{}' geladen: {}", name, target.get());
                } else if (value instanceof Number) {
                    // Fallback: Epochensekunden (für Rückwärtskompatibilität)
                    long epochSecond = ((Number) value).longValue();
                    if (epochSecond > 0) {
                        target.set(Instant.ofEpochSecond(epochSecond));
                        log.debug("Zeitstempel-Metrik '{}' geladen (Epoch): {}", name, target.get());
                    }
                }
            } catch (Exception e) {
                log.warn("Konnte Zeitstempel-Metrik '{}' nicht laden: {}", name, e.getMessage());
            }
        });
    }

    @Transactional
    private void persistMetric(String name, long value) {
        try {
            String jsonValue = objectMapper.writeValueAsString(Map.of("value", value));
            saveMetrik(name, jsonValue);
            log.debug("Metrik '{}' persistiert mit Wert: {}", name, value);
        } catch (JsonProcessingException e) {
            log.error("Konnte Metrik '{}' nicht persistieren: {}", name, e.getMessage());
        }
    }

    @Transactional
    private void persistTimestampMetric(String name, Instant timestamp) {
        try {
            // Als lesbarer ISO-8601 Timestamp speichern (z.B. "2025-12-07T16:30:00")
            LocalDateTime ldt = LocalDateTime.ofInstant(timestamp, ZoneId.systemDefault());
            String jsonValue = objectMapper.writeValueAsString(Map.of("value", ldt.toString()));
            saveMetrik(name, jsonValue);
            log.debug("Zeitstempel-Metrik '{}' persistiert: {}", name, ldt);
        } catch (JsonProcessingException e) {
            log.error("Konnte Zeitstempel-Metrik '{}' nicht persistieren: {}", name, e.getMessage());
        }
    }

    private void saveMetrik(String name, String jsonValue) {
        Metrik metrik = metrikRepository.findByName(name)
                .orElseGet(() -> new Metrik(name, jsonValue));
        metrik.setValue(jsonValue);
        metrikRepository.save(metrik);
    }

    /**
     * Wird aufgerufen, wenn Messdaten hochgeladen wurden.
     */
    @Transactional
    public void recordMessdatenUpload() {
        long newTotal = messdatenUploadTotal.incrementAndGet();
        Instant now = Instant.now();
        letzterMessdatenUpload.set(now);

        persistMetric(METRIC_MESSDATEN_UPLOAD_TOTAL, newTotal);
        persistTimestampMetric(METRIC_MESSDATEN_UPLOAD_ZEITPUNKT, now);

        log.debug("Metrik aktualisiert: Messdaten-Upload #{} um {}", newTotal, now);
    }

    /**
     * Wird aufgerufen, wenn eine Solarverteilung berechnet wurde.
     */
    @Transactional
    public void recordSolarverteilungBerechnung() {
        long newTotal = solarverteilungTotal.incrementAndGet();
        Instant now = Instant.now();
        letzteSolarverteilung.set(now);

        persistMetric(METRIC_SOLARVERTEILUNG_TOTAL, newTotal);
        persistTimestampMetric(METRIC_SOLARVERTEILUNG_ZEITPUNKT, now);

        log.debug("Metrik aktualisiert: Solarverteilung #{} um {}", newTotal, now);
    }

    /**
     * Liefert den Zeitpunkt des letzten Messdaten-Uploads.
     */
    public Instant getLetzterMessdatenUpload() {
        return letzterMessdatenUpload.get();
    }

    /**
     * Liefert den Zeitpunkt des letzten Messdaten-Uploads als LocalDateTime.
     */
    public LocalDateTime getLetzterMessdatenUploadAsLocalDateTime() {
        Instant instant = letzterMessdatenUpload.get();
        return instant != null ? LocalDateTime.ofInstant(instant, ZoneId.systemDefault()) : null;
    }

    /**
     * Liefert den Zeitpunkt der letzten Solarverteilungsberechnung.
     */
    public Instant getLetzteSolarverteilung() {
        return letzteSolarverteilung.get();
    }

    /**
     * Liefert den Zeitpunkt der letzten Solarverteilungsberechnung als LocalDateTime.
     */
    public LocalDateTime getLetzteSolarverteilungAsLocalDateTime() {
        Instant instant = letzteSolarverteilung.get();
        return instant != null ? LocalDateTime.ofInstant(instant, ZoneId.systemDefault()) : null;
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
