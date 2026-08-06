package ch.nacht.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * JSON-Payload einer MQTT-Messwert-Nachricht (absolute Zählerstände, Wirkenergie kWh).
 * Vertrag siehe {@code Specs/MQTT-Integration.md} (FR-3).
 *
 * <p>{@code timestamp} kommt als lokale Zeit mit Offset (ISO 8601, z. B.
 * {@code 2026-07-10T14:30:00+02:00}); die lokale Wanduhrzeit wird verbatim gespeichert.
 *
 * <p>{@code seriennummer} ist <b>optional</b> (Zählertausch-Erkennung, siehe
 * {@code Specs/Zaehlertausch-Erkennung.md}): fehlt sie, läuft die Aggregation über den Fallback.
 */
public class ZaehlerMesswertPayloadDTO {

    private OffsetDateTime timestamp;
    private BigDecimal zaehlerstandBezug;
    private BigDecimal zaehlerstandEinspeisung;
    private String seriennummer;

    public OffsetDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(OffsetDateTime timestamp) {
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

    public String getSeriennummer() {
        return seriennummer;
    }

    public void setSeriennummer(String seriennummer) {
        this.seriennummer = seriennummer;
    }
}
