package ch.nacht.entity;

/**
 * Mengeneinheit eines Tarifs mit frei wählbarer Einheit ({@link TarifTyp#ZUSATZ}).
 *
 * <p>Bei allen übrigen Tariftypen ergibt sich die Einheit aus dem Typ selbst
 * ({@link TarifTyp#mengeneinheit()}) und das Feld am Tarif bleibt leer.
 *
 * <p>Die Werte landen als String in {@code zev.tarif.mengeneinheit} und werden auf der Rechnung
 * als {@code mengeneinheit} der Zeile ausgegeben — dieselbe Stelle, an der ZEV/VNB {@code KWH}
 * und die Grundgebühr {@code MONAT} ausweisen.
 */
public enum Mengeneinheit {

    /** Verbrauchsmenge in Kilowattstunden. */
    KWH,

    /** Wiederkehrende Pauschale je Monat — wie die Grundgebühr, aber mit eigenem Preis. */
    MONAT,

    /** Zählbare Einheit (Saunagänge, Schlüssel, …); auf der Rechnung als „Stk". */
    STUECK,

    /**
     * Volumen in Kubikmetern — für die Nebenkostenabrechnung (Wasser, Abwasser).
     * Siehe {@code Specs/Nebenkosten/Abrechnung.md}.
     */
    M3,

    /**
     * Betrag in Schweizer Franken; angezeigt als „Fr.".
     *
     * <p>Nur für die Nebenkostenabrechnung und dort für <b>Umlagen</b>, deren verteilte Grösse
     * selbst ein Betrag ist (Grünabfuhr, Versicherungsprämie). Die Mengenspalte trägt dann
     * denselben Wert wie die Betragsspalte — das ist gewollt und macht sichtbar, dass hier ein
     * Betrag und keine gemessene Menge verteilt wird.
     *
     * <p>An einem {@link TarifTyp#ZUSATZ}-Tarif wird die Einheit bewusst <b>nicht</b> angeboten:
     * „CHF pro Fr." wäre keine sinnvolle Preisangabe.
     */
    CHF
}
