package ch.nacht.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * JSON-Payload einer MQTT-Messwert-Nachricht (absolute Zählerstände, Wirkenergie kWh).
 * Vertrag siehe {@code Specs/MQTT-Integration.md} (FR-3).
 */
public class ZaehlerMesswertPayloadDTO {

    private Instant timestamp;
    private BigDecimal zaehlerstandBezug;
    private BigDecimal zaehlerstandEinspeisung;

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public BigDecimal getZaehlerstandBezug() {
        return zaehlerstandBezug;
    }

    public void setZaehlerstandBezug(BigDecimal zaehlerstandBezug) {
        this.zaehlerstandBezug = zaehlerstandBezug;
    }

    public BigDecimal getZaehlerstandEinspeisung() {
        return zaehlerstandEinspeisung;
    }

    public void setZaehlerstandEinspeisung(BigDecimal zaehlerstandEinspeisung) {
        this.zaehlerstandEinspeisung = zaehlerstandEinspeisung;
    }
}
