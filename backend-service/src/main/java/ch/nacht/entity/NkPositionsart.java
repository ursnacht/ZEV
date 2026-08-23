package ch.nacht.entity;

/**
 * Rechenart einer allgemeinen Position der Nebenkostenabrechnung
 * (Specs/Nebenkosten/Abrechnung.md, FR-2).
 *
 * <p>Die drei Arten rechnen grundverschieden — deshalb sind es Arten und nicht bloss
 * unterschiedlich befüllte Zeilen: Eine Umlage verteilt einen Gesamtbetrag, ein Verbrauch
 * braucht eine je Mieter gemessene Menge, ein Zuschlag eine Bemessungsgrundlage.
 */
public enum NkPositionsart {

    /**
     * Gesamtkosten nach Schlüssel verteilt (Allgemeinstrom, Regenwasser).
     * Zeitanteilig auf die Mieter; der Nenner ist die <b>mögliche</b> Mietdauer aller Wohnungen,
     * weshalb Leerstand einen unverteilten Anteil hinterlässt.
     */
    UMLAGE,

    /** Je Mieter gemessene Menge mal Preis je Einheit (Warmwasser, Heizung). */
    VERBRAUCH,

    /**
     * Prozent auf die Summe aller Zeilen mit kleinerer Reihenfolge (Verwaltungskosten).
     * Kaskadierend: Ein zweiter Zuschlag schliesst den ersten ein.
     */
    ZUSCHLAG,

    /**
     * Gesamtbetrag nach einem <b>je Mieter erfassten Prozentsatz</b> verteilt (Heizkosten).
     *
     * <p>Für Kosten, deren Verteilschlüssel von aussen kommt — etwa aus der Abrechnung eines
     * Wärmezählerdienstes. Der Prozentsatz steht wie eine Verbrauchsmenge in
     * {@code nk_verbrauch.menge}; die Bedeutung ergibt sich aus dieser Art.
     *
     * <p>Nicht zu verwechseln mit {@link #ZUSCHLAG}: Dort ist der Prozentsatz an der Position
     * erfasst und rechnet auf die Summe der Zeilen davor. Hier trägt jeder Mieter seinen eigenen
     * Prozentsatz, und Bezugsgrösse ist der Totalbetrag dieser Position.
     *
     * <p>Die Summe der Prozentsätze <b>sollte</b> 100 ergeben; das wird als Kontrollzahl
     * ausgewiesen, aber nicht erzwungen — sonst liesse sich eine halb erfasste Abrechnung nicht
     * zwischenspeichern.
     */
    ANTEIL
}
