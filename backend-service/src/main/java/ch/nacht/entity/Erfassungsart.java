package ch.nacht.entity;

/**
 * Herkunft einer manuell bzw. automatisch erfassten {@link Tarifposition}.
 *
 * <p>Bewusst <b>nicht</b> {@code Quelle} benannt: {@link Quelle} ist bereits mit der Herkunft
 * von Messwerten belegt (CSV/MQTT/API).
 */
public enum Erfassungsart {

    /** Von Hand in der Anwendung erfasst. */
    MANUELL,

    /** Automatisch aus dem Lademanagement übernommen (noch nicht umgesetzt). */
    IMPORT
}
