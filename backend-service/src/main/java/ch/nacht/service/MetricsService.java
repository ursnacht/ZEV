package ch.nacht.service;

import ch.nacht.entity.Metrik;
import ch.nacht.repository.MetrikRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Service für benutzerdefinierte Metriken mit Datenbank-Persistierung.
 * Metriken werden pro Organisation in der Datenbank gespeichert.
 * Prometheus-Metriken sind global (nicht org-spezifisch).
 */
@Service
public class MetricsService {

    private static final Logger log = LoggerFactory.getLogger(MetricsService.class);

    private static final String METRIC_MESSDATEN_UPLOAD_TOTAL = "zev.messdaten.upload.total";
    private static final String METRIC_MESSDATEN_UPLOAD_ZEITPUNKT = "zev.messdaten.upload.letzter_zeitpunkt";
    private static final String METRIC_SOLARVERTEILUNG_TOTAL = "zev.solarverteilung.berechnung.total";
    private static final String METRIC_SOLARVERTEILUNG_ZEITPUNKT = "zev.solarverteilung.berechnung.letzter_zeitpunkt";

    private final MetrikRepository metrikRepository;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;
    private final OrganizationContextService organizationContextService;

    // AtomicLong für Zähler-Metriken (Prometheus - global)
    private final AtomicLong messdatenUploadTotal;
    private final AtomicLong solarverteilungTotal;

    // AtomicReference<Instant> für Zeitstempel-Metriken (Prometheus - global)
    private final AtomicReference<Instant> letzterMessdatenUpload;
    private final AtomicReference<Instant> letzteSolarverteilung;

    // Einheit-Name für den letzten Upload (für dynamisches Label)
    private final AtomicReference<String> letzteUploadEinheit = new AtomicReference<>("unbekannt");

    // Referenz auf die aktuelle Gauge (für Neuregistrierung)
    private Gauge letzterMessdatenUploadGauge;

    public MetricsService(MeterRegistry meterRegistry,
                          MetrikRepository metrikRepository,
                          OrganizationContextService organizationContextService) {
        this.metrikRepository = metrikRepository;
        this.meterRegistry = meterRegistry;
        this.organizationContextService = organizationContextService;
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
        // Gauge mit Default-Label registrieren
        registerUploadGauge("unbekannt");

        this.letzteSolarverteilung = new AtomicReference<>(null);
        Gauge.builder(METRIC_SOLARVERTEILUNG_ZEITPUNKT, letzteSolarverteilung,
                        ref -> ref.get() != null ? ref.get().getEpochSecond() : 0)
                .description("Unix-Timestamp der letzten Solarverteilungsberechnung")
                .register(meterRegistry);

        log.info("MetricsService initialisiert - Metriken registriert");
    }

    /**
     * Hilfsmethode um die aktuelle Org-ID zu holen.
     */
    private Long getCurrentOrgId() {
        return organizationContextService.getCurrentOrgId();
    }

    /**
     * Sucht eine Metrik nach Name für die aktuelle Organisation.
     */
    private java.util.Optional<Metrik> findMetrikByName(String name) {
        Long orgId = getCurrentOrgId();
        if (orgId == null) {
            return java.util.Optional.empty();
        }
        return metrikRepository.findByNameAndOrgId(name, orgId);
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
        Long orgId = getCurrentOrgId();
        if (orgId == null) {
            log.warn("Keine Organisation im Kontext - Metrik '{}' wird nicht gespeichert", name);
            return;
        }
        Metrik metrik = metrikRepository.findByNameAndOrgId(name, orgId)
                .orElseGet(() -> {
                    Metrik newMetrik = new Metrik(name, jsonValue);
                    newMetrik.setOrgId(orgId);
                    return newMetrik;
                });
        metrik.setValue(jsonValue);
        metrikRepository.save(metrik);
    }

    /**
     * Registriert die Upload-Gauge mit dem angegebenen Einheit-Label.
     * Falls bereits eine Gauge registriert ist, wird diese zuerst entfernt.
     */
    private void registerUploadGauge(String einheitName) {
        // Alte Gauge entfernen falls vorhanden
        if (letzterMessdatenUploadGauge != null) {
            meterRegistry.remove(letzterMessdatenUploadGauge);
        }

        // Neue Gauge mit Label registrieren
        letzterMessdatenUploadGauge = Gauge.builder(METRIC_MESSDATEN_UPLOAD_ZEITPUNKT, letzterMessdatenUpload,
                        ref -> ref.get() != null ? ref.get().getEpochSecond() : 0)
                .tag("einheit", einheitName)
                .description("Unix-Timestamp des letzten Messdaten-Uploads")
                .register(meterRegistry);

        log.debug("Upload-Gauge registriert mit Einheit: {}", einheitName);
    }

    /**
     * Sanitisiert den Einheit-Namen für die Verwendung als Prometheus-Label.
     */
    private String sanitizeEinheitName(String name) {
        if (name == null || name.isBlank()) {
            return "unbekannt";
        }
        // Prometheus-Labels erlauben: [a-zA-Z_][a-zA-Z0-9_]*
        // Wir behalten lesbare Zeichen und ersetzen problematische
        return name.trim();
    }

    /**
     * Persistiert den Upload-Zeitstempel zusammen mit dem Einheit-Namen.
     */
    @Transactional
    private void persistTimestampMetricWithEinheit(String name, Instant timestamp, String einheitName) {
        try {
            LocalDateTime ldt = LocalDateTime.ofInstant(timestamp, ZoneId.systemDefault());
            Map<String, Object> valueMap = new HashMap<>();
            valueMap.put("value", ldt.toString());
            valueMap.put("einheit", einheitName);
            String jsonValue = objectMapper.writeValueAsString(valueMap);
            saveMetrik(name, jsonValue);
            log.debug("Zeitstempel-Metrik '{}' persistiert: {} (Einheit: {})", name, ldt, einheitName);
        } catch (JsonProcessingException e) {
            log.error("Konnte Zeitstempel-Metrik '{}' nicht persistieren: {}", name, e.getMessage());
        }
    }

    /**
     * Wird aufgerufen, wenn Messdaten für eine Einheit hochgeladen wurden.
     *
     * @param einheitName Name der Einheit für das Label
     */
    @Transactional
    public void recordMessdatenUpload(String einheitName) {
        long newTotal = messdatenUploadTotal.incrementAndGet();
        Instant now = Instant.now();
        letzterMessdatenUpload.set(now);

        // Einheit-Name sanitisieren und speichern
        String sanitizedName = sanitizeEinheitName(einheitName);
        letzteUploadEinheit.set(sanitizedName);

        // Gauge neu registrieren mit aktuellem Label
        registerUploadGauge(sanitizedName);

        // Persistieren
        persistMetric(METRIC_MESSDATEN_UPLOAD_TOTAL, newTotal);
        persistTimestampMetricWithEinheit(METRIC_MESSDATEN_UPLOAD_ZEITPUNKT, now, sanitizedName);

        log.debug("Metrik aktualisiert: Messdaten-Upload #{} für Einheit '{}' um {}", newTotal, sanitizedName, now);
    }

    /**
     * Wird aufgerufen, wenn Messdaten hochgeladen wurden (ohne Einheit).
     * @deprecated Verwende {@link #recordMessdatenUpload(String)} mit Einheit-Name.
     */
    @Deprecated
    @Transactional
    public void recordMessdatenUpload() {
        recordMessdatenUpload("unbekannt");
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
     * Liefert den Namen der Einheit des letzten Messdaten-Uploads.
     */
    public String getLetzteUploadEinheit() {
        return letzteUploadEinheit.get();
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
