package ch.nacht.entity;

/**
 * Die Regel, die einen Steuerentscheid bestimmt hat
 * (Specs/Einspeisesteuerung.md, FR-2).
 *
 * <p>Die Reihenfolge der Konstanten ist die <b>Auswertungsreihenfolge</b>: Die erste zutreffende
 * Regel gewinnt und wird protokolliert. Sie ist Teil der Fachlichkeit, nicht Kosmetik — wer sie
 * ändert, ändert das Verhalten der Steuerung.
 */
public enum Steuerregel {

    /** Preis unter 0: Einspeisen kostet Geld. Laden bleibt frei, eingespiesen wird nichts. */
    PREIS_NEGATIV,

    /** Die Vergütung liegt über dem Wert einer gespeicherten kWh — einspeisen lohnt mehr. */
    EINSPEISEN_LOHNT,

    /**
     * Heute ist noch ein günstigeres Intervall zu erwarten.
     *
     * <p>Der Kern der Steuerung: Die knappe Batteriekapazität wird für das Preistal freigehalten,
     * statt sie jetzt mit teurerem Strom zu füllen.
     */
    WARTEN_AUF_TAL,

    /**
     * Kein Überschuss gemessen — keine Sperre.
     *
     * <p><b>Steht bewusst NACH den Preisregeln.</b> Davor machte sie die Steuerung wirkungslos:
     * Solange die Batterie lädt, wird ihre Ladeleistung am Zähler des Produzenten als Bezug
     * gegengerechnet und der Überschuss erscheint als 0 — die Preisregeln wurden nie erreicht, und
     * entschieden wurde erst, wenn die Batterie voll war.
     *
     * <p>Sie wird trotzdem protokolliert: Eine Lücke im Protokoll liesse später offen, ob die
     * Steuerung überhaupt lief. Der Entscheid ist derselbe wie bei {@link #LADEN}
     * ({@code FREI}/{@code FREI}); unterschiedlich ist nur die Begründung.
     */
    KEIN_UEBERSCHUSS,

    /**
     * Laden, sobald Überschuss da ist.
     *
     * <p>Deckt den bewölkten Tag ab: Ein hoher Mittagspreis heisst, dass der ganze Markt wenig
     * Solarstrom erwartet — dann ist die Gelegenheit knapp, nicht die Kapazität.
     */
    LADEN
}
