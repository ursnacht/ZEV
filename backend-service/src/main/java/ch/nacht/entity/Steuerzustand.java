package ch.nacht.entity;

/**
 * Sollzustand einer Stellgrösse der Einspeisesteuerung
 * (Specs/Einspeisesteuerung.md, FR-2).
 *
 * <p><b>Ein Soll, kein Ist.</b> In dieser Ausbaustufe wird nichts geschaltet — der Wert sagt, was
 * die Steuerung tun <i>würde</i>. Ob die Anlage tatsächlich lädt oder einspeist, steht hier nicht.
 *
 * <p><b>Bezugsgrösse ist immer der PV-Überschuss</b> des Intervalls, nicht die Anlage als Ganzes:
 * Eine gesperrte Batterieladung heisst „dieser Überschuss soll nicht in den Speicher, sondern ins
 * Netz" — eine Umlenkung, kein Abschalten. Über das <i>Entladen</i> und über den Füllstand sagen
 * beide Werte nichts; die Batterie ist in den Daten nicht erfasst (FR-2).
 */
public enum Steuerzustand {

    /**
     * Erlaubt — die Anlage entscheidet wie ohne Steuerung.
     *
     * <p>Nicht zu lesen als „es wird geladen bzw. eingespiesen": Die Regel kennt den Ladezustand
     * nicht und sagt nur, dass sie nichts dagegen hat.
     */
    FREI,

    /** Untersagt: dieser Überschuss soll nicht in die Batterie bzw. nicht ins Netz. */
    GESPERRT
}
