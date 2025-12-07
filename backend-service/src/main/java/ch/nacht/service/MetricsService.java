package ch.nacht.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service für benutzerdefinierte Metriken.
 * Publiziert Metriken für Messdaten-Upload und Solarverteilungsberechnung.
 */
@Service
public class MetricsService {

    private static final Logger log = LoggerFactory.getLogger(MetricsService.class);

    private final Counter messdatenUploadCounter;
    private final Counter solarverteilungBerechnetCounter;
    private final AtomicLong letzterMessdatenUploadTimestamp;
    private final AtomicLong letzteSolarverteilungTimestamp;

    public MetricsService(MeterRegistry meterRegistry) {
        // Counter für Anzahl der Uploads
        this.messdatenUploadCounter = Counter.builder("zev.messdaten.upload.total")
                .description("Gesamtanzahl der Messdaten-Uploads")
                .register(meterRegistry);

        // Counter für Anzahl der Berechnungen
        this.solarverteilungBerechnetCounter = Counter.builder("zev.solarverteilung.berechnung.total")
                .description("Gesamtanzahl der Solarverteilungsberechnungen")
                .register(meterRegistry);

        // Gauge für letzten Upload-Zeitpunkt (als Unix-Timestamp)
        this.letzterMessdatenUploadTimestamp = new AtomicLong(0);
        Gauge.builder("zev.messdaten.upload.letzter_zeitpunkt", letzterMessdatenUploadTimestamp, AtomicLong::get)
                .description("Unix-Timestamp des letzten Messdaten-Uploads")
                .register(meterRegistry);

        // Gauge für letzte Berechnung (als Unix-Timestamp)
        this.letzteSolarverteilungTimestamp = new AtomicLong(0);
        Gauge.builder("zev.solarverteilung.berechnung.letzter_zeitpunkt", letzteSolarverteilungTimestamp, AtomicLong::get)
                .description("Unix-Timestamp der letzten Solarverteilungsberechnung")
                .register(meterRegistry);

        log.info("MetricsService initialisiert - Metriken registriert");
    }

    /**
     * Wird aufgerufen, wenn Messdaten hochgeladen wurden.
     */
    public void recordMessdatenUpload() {
        messdatenUploadCounter.increment();
        long now = Instant.now().getEpochSecond();
        letzterMessdatenUploadTimestamp.set(now);
        log.debug("Metrik aktualisiert: Messdaten-Upload um {}", LocalDateTime.now());
    }

    /**
     * Wird aufgerufen, wenn eine Solarverteilung berechnet wurde.
     */
    public void recordSolarverteilungBerechnung() {
        solarverteilungBerechnetCounter.increment();
        long now = Instant.now().getEpochSecond();
        letzteSolarverteilungTimestamp.set(now);
        log.debug("Metrik aktualisiert: Solarverteilung berechnet um {}", LocalDateTime.now());
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
}
