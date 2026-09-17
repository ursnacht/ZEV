package ch.nacht.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * JSON-Payload einer MQTT-Messwert-Nachricht (absolute Zählerstände, Wirkenergie kWh).
 * Vertrag siehe {@code Specs/MQTT-Integration.md} (FR-3).
 *
 * <p>{@code timestamp} kommt als lokale Zeit mit Offset (ISO 8601, z. B.
 * {@code 2026-07-10T14:30:00+02:00}); die lokale Wanduhrzeit wird verbatim gespeichert.
 *
 * <p>{@code seriennummer} ist <b>optional</b> (Zählertausch-Erkennung, siehe
 * {@code Specs/Zaehlertausch-Erkennung.md}): fehlt sie, läuft die Aggregation über den Fallback.
 *
 * <p>{@code zustand} ist <b>optional</b> und trägt Momentanwerte des Geräts — Grössenname auf
 * Zahlenwert, z.B. {@code {"soc": 87.5}} (siehe {@code Specs/Gerätezustand.md}). Sie laufen neben
 * den Zählerständen her und werden nie aggregiert.
 */
public class ZaehlerMesswertPayloadDTO {

    private OffsetDateTime timestamp;
    private BigDecimal zaehlerstandBezug;
    private BigDecimal zaehlerstandEinspeisung;
    private String seriennummer;

    /**
     * Momentanwerte des Geräts: Grössenname auf Zahlenwert. Optional; fehlt das Objekt, entsteht
     * kein Zustandswert.
     *
     * <p><b>{@code Object} als Werttyp, nicht {@code BigDecimal}</b> — und das ist keine
     * Nachlässigkeit: Beim engeren Typ scheitert Jackson schon beim Deserialisieren an
     * {@code {"soc": "abc"}}, an {@code "zustand": 5} oder an einem verschachtelten Objekt. Der
     * Fehler landet im generischen {@code catch} von {@code MqttIngestService.handle} und verwirft
     * die <b>ganze</b> Nachricht — samt der Zählerstände, die in Ordnung waren. Ein Tippfehler in
     * der Pi-Konfiguration kostete dann die Energiemengen.
     *
     * <p>Die Umwandlung in eine Zahl passiert deshalb <b>je Eintrag</b> im Ingest, wo ein einzelner
     * ungültiger Wert nur sich selbst verliert.
     */
    private Map<String, Object> zustand;

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

    public Map<String, Object> getZustand() {
        return zustand;
    }

    public void setZustand(Map<String, Object> zustand) {
        this.zustand = zustand;
    }
}
