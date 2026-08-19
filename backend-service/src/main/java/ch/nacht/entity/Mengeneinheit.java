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
    STUECK
}
