package ch.nacht.service;

import ch.nacht.dto.ZaehlerMesswertPayloadDTO;
import ch.nacht.entity.Einheit;
import ch.nacht.entity.EinheitTyp;
import ch.nacht.entity.ZaehlerRohdaten;
import ch.nacht.repository.EinheitRepository;
import ch.nacht.repository.ZaehlerRohdatenRepository;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Verarbeitet eingehende MQTT-Messwert-Nachrichten (FR-4): Topic/Payload parsen, validieren,
 * Einheiten über (org_id, messpunkt) auflösen und absolute Zählerstände als Rohdaten persistieren.
 *
 * <p>Mehrere Einheiten je Messpunkt sind zulässig (Bilanzmesspunkt FR-2.3): die Bilanz-Typen
 * BEZUG/RUECKLIEFERUNG dürfen denselben Messpunkt teilen; die Meldung wird dann aufgeteilt und
 * je Einheit nur das relevante Register übernommen (BEZUG: nur Bezug, RUECKLIEFERUNG: nur
 * Einspeisung; das jeweils andere = 0).
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
        // Offset-behaftete Zeit NICHT auf die Kontext-Zeitzone normalisieren, damit die vom Pi
        // gesendete lokale Wanduhrzeit verbatim erhalten bleibt (OffsetDateTime.toLocalDateTime()).
        this.objectMapper = objectMapper.copy()
                .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);
        this.metrics = metrics;
    }

    @Transactional
    public void handle(String topic, String payload) {
        log.info("MQTT data received. Topic: {}, payload: {}", topic, payload);
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

            // 3) Einheiten über (org_id, messpunkt) auflösen (Mandanten-Isolation). Mehrere
            //    Treffer sind zulässig: BEZUG/RUECKLIEFERUNG dürfen denselben Bilanzmesspunkt
            //    teilen – die Meldung wird dann je Einheit auf das relevante Register projiziert.
            List<Einheit> einheiten = einheitRepository.findAllByOrgIdAndMesspunkt(orgId, messpunkt);
            if (einheiten.isEmpty()) {
                log.warn("MQTT: unbekannter Messpunkt (org={}, messpunkt={}) – verworfen", orgId, messpunkt);
                metrics.recordFailed();
                return;
            }

            // 4) Rohdaten upsert je Einheit (org_id explizit)
            // Der Pi sendet die lokale Zeit mit Offset (ISO 8601); die lokale Wanduhrzeit
            // wird verbatim übernommen – konsistent mit dem CSV-Upload und der messwerte-Tabelle
            // (naive lokale Zeit). Unabhängig von der Backend-Zeitzone.
            LocalDateTime zeit = p.getTimestamp().toLocalDateTime();
            for (Einheit einheit : einheiten) {
                upsertRohdaten(orgId, einheit, zeit, p);
            }

            metrics.recordProcessed();
            log.debug("MQTT: Rohdaten gespeichert (org={}, messpunkt={}, zeit={}, einheiten={})",
                    orgId, messpunkt, zeit, einheiten.size());
        } catch (Exception e) {
            metrics.recordFailed();
            log.warn("MQTT: Nachricht verworfen (Topic {}): {}", topic, e.getMessage());
        }
    }

    /**
     * Schreibt (Upsert) den Rohdatensatz einer Einheit. Bilanz-Typen erhalten nur ihr
     * relevantes Register (FR-2.4): BEZUG nur den Bezug, RUECKLIEFERUNG nur die Einspeisung,
     * das jeweils andere Register wird auf 0 gesetzt – so zählt eine BEZUG-Einheit nie
     * Einspeisung (und umgekehrt), auch wenn der physische Bilanzzähler beide Register in
     * einer Meldung liefert. PRODUCER/CONSUMER übernehmen die Payload unverändert.
     */
    private void upsertRohdaten(long orgId, Einheit einheit, LocalDateTime zeit, ZaehlerMesswertPayloadDTO p) {
        BigDecimal bezug = p.getZaehlerstandBezug();
        BigDecimal einspeisung = p.getZaehlerstandEinspeisung();
        if (einheit.getTyp() == EinheitTyp.BEZUG) {
            einspeisung = BigDecimal.ZERO;
        } else if (einheit.getTyp() == EinheitTyp.RUECKLIEFERUNG) {
            bezug = BigDecimal.ZERO;
        }

        ZaehlerRohdaten row = rohdatenRepository.findByEinheitIdAndZeit(einheit.getId(), zeit).orElse(null);
        if (row == null) {
            row = new ZaehlerRohdaten(orgId, einheit.getId(), zeit, bezug, einspeisung);
        } else {
            row.setZaehlerstandBezug(bezug);
            row.setZaehlerstandEinspeisung(einspeisung);
        }
        row.setEmpfangenAm(LocalDateTime.now());
        row.setVerarbeitet(false);
        rohdatenRepository.save(row);
    }
}
