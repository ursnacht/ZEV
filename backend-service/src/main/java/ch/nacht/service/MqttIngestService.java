package ch.nacht.service;

import ch.nacht.dto.ZaehlerMesswertPayloadDTO;
import ch.nacht.entity.Einheit;
import ch.nacht.entity.ZaehlerRohdaten;
import ch.nacht.repository.EinheitRepository;
import ch.nacht.repository.ZaehlerRohdatenRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * Verarbeitet eingehende MQTT-Messwert-Nachrichten (FR-4): Topic/Payload parsen, validieren,
 * Einheit über (org_id, messpunkt) auflösen und absolute Zählerstände als Rohdaten persistieren.
 *
 * <p>Kein Request-Scope/JWT: die Mandanten-ID stammt aus dem Topic und wird explizit gesetzt
 * (kein {@code OrganizationContextService}, kein {@code orgFilter}). Fehler werden geloggt und
 * die Nachricht verworfen – niemals nach aussen geworfen (der Adapter gilt als konsumiert).
 */
@Service
@Profile("mqtt")
public class MqttIngestService {

    private static final Logger log = LoggerFactory.getLogger(MqttIngestService.class);

    private final EinheitRepository einheitRepository;
    private final ZaehlerRohdatenRepository rohdatenRepository;
    private final ObjectMapper objectMapper;
    private final MqttMetrics metrics;

    public MqttIngestService(EinheitRepository einheitRepository,
                             ZaehlerRohdatenRepository rohdatenRepository,
                             ObjectMapper objectMapper,
                             MqttMetrics metrics) {
        this.einheitRepository = einheitRepository;
        this.rohdatenRepository = rohdatenRepository;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
    }

    @Transactional
    public void handle(String topic, String payload) {
        metrics.recordReceived();
        try {
            // 1) Topic parsen: zev/{orgId}/{messpunkt}/messwert
            if (topic == null) {
                log.warn("MQTT: Nachricht ohne Topic verworfen");
                metrics.recordFailed();
                return;
            }
            String[] parts = topic.split("/");
            if (parts.length != 4 || !"zev".equals(parts[0]) || !"messwert".equals(parts[3])) {
                log.warn("MQTT: ungültiges Topic '{}' verworfen", topic);
                metrics.recordFailed();
                return;
            }
            long orgId;
            try {
                orgId = Long.parseLong(parts[1]);
            } catch (NumberFormatException e) {
                log.warn("MQTT: ungültige orgId im Topic '{}' verworfen", topic);
                metrics.recordFailed();
                return;
            }
            String messpunkt = parts[2];

            // 2) Payload parsen + validieren
            ZaehlerMesswertPayloadDTO p = objectMapper.readValue(payload, ZaehlerMesswertPayloadDTO.class);
            if (p.getTimestamp() == null || p.getZaehlerstandBezug() == null
                    || p.getZaehlerstandEinspeisung() == null) {
                log.warn("MQTT: Pflichtfeld fehlt (Topic {}) – verworfen", topic);
                metrics.recordFailed();
                return;
            }
            if (p.getZaehlerstandBezug().signum() < 0 || p.getZaehlerstandEinspeisung().signum() < 0) {
                log.warn("MQTT: negativer Zählerstand (Topic {}) – verworfen", topic);
                metrics.recordFailed();
                return;
            }

            // 3) Einheit über (org_id, messpunkt) auflösen (Mandanten-Isolation)
            Optional<Einheit> einheitOpt = einheitRepository.findByOrgIdAndMesspunkt(orgId, messpunkt);
            if (einheitOpt.isEmpty()) {
                log.warn("MQTT: unbekannter Messpunkt (org={}, messpunkt={}) – verworfen", orgId, messpunkt);
                metrics.recordFailed();
                return;
            }
            Einheit einheit = einheitOpt.get();

            // 4) Rohdaten upsert (org_id explizit)
            LocalDateTime zeit = LocalDateTime.ofInstant(p.getTimestamp(), ZoneOffset.UTC);
            ZaehlerRohdaten row = rohdatenRepository.findByEinheitIdAndZeit(einheit.getId(), zeit).orElse(null);
            if (row == null) {
                row = new ZaehlerRohdaten(orgId, einheit.getId(), zeit,
                        p.getZaehlerstandBezug(), p.getZaehlerstandEinspeisung());
            } else {
                row.setZaehlerstandBezug(p.getZaehlerstandBezug());
                row.setZaehlerstandEinspeisung(p.getZaehlerstandEinspeisung());
            }
            row.setEmpfangenAm(LocalDateTime.now());
            row.setVerarbeitet(false);
            rohdatenRepository.save(row);

            metrics.recordProcessed();
            log.debug("MQTT: Rohdaten gespeichert (org={}, messpunkt={}, zeit={})", orgId, messpunkt, zeit);
        } catch (Exception e) {
            metrics.recordFailed();
            log.warn("MQTT: Nachricht verworfen (Topic {}): {}", topic, e.getMessage());
        }
    }
}
