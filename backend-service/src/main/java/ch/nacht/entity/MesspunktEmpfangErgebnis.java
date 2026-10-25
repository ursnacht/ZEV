package ch.nacht.entity;

/**
 * Was mit der letzten Meldung eines Messpunkts geschah (Specs/Messpunkt-Empfang.md, FR-1.3).
 */
public enum MesspunktEmpfangErgebnis {
    /** Rohdaten für mindestens eine Einheit geschrieben <b>und</b> committet. */
    GESPEICHERT,

    /** Gültige Meldung, aber keine Einheit (ausser Ladestationen) trägt den Messpunkt. */
    UNBEKANNTER_MESSPUNKT,

    /** Verworfen; der Grund steht in {@link MesspunktEmpfang#getGrund()}. */
    UNGUELTIG
}
