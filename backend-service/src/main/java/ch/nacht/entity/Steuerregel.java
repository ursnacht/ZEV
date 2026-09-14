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

    /**
     * Produktion deckt den Verbrauch nicht — es gibt nichts zu entscheiden.
     *
     * <p>Wird trotzdem protokolliert: Eine Lücke im Protokoll liesse später offen, ob die
     * Steuerung überhaupt lief.
     */
    KEIN_UEBERSCHUSS,

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
     * Laden, sobald Überschuss da ist.
     *
     * <p>Deckt den bewölkten Tag ab: Ein hoher Mittagspreis heisst, dass der ganze Markt wenig
     * Solarstrom erwartet — dann ist die Gelegenheit knapp, nicht die Kapazität.
     */
    LADEN
}
