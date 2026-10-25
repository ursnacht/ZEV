package ch.nacht.entity;

/**
 * Woher eine MQTT-Meldung kam — abgeleitet aus dem Topic (Specs/Messpunkt-Empfang.md, FR-1.2).
 */
public enum MesspunktEmpfangQuelle {
    /** Topic {@code zev/{orgId}/{messpunkt}/messwert} (Specs/MQTT-Integration.md, FR-2). */
    PI_GATEWAY,

    /** Topic {@code gPlug/{orgId}/{messpunkt}/tele/SENSOR} (Specs/MQTT-Integration.md, FR-10). */
    GPLUG
}
