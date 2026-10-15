package ch.nacht.entity;

import java.math.BigDecimal;
import java.util.Locale;

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
    KWH("KWH", "KWH"),

    /** Wiederkehrende Pauschale je Monat — wie die Grundgebühr, aber mit eigenem Preis. */
    MONAT("MONAT", "MONATE"),
    /**
     * Pauschale je Kalendertag — für die taggenau abgerechnete Grundgebühr
     * (Specs/Tarifverwaltung.md, FR-3). Die Menge auf der Rechnung ist die Zahl der Tage im
     * Rechnungszeitraum, in denen der Tarif gilt.
     */
    TAG("TAG", "TAGE"),

    /** Zählbare Einheit (Saunagänge, Schlüssel, …); auf der Rechnung als „Stk". */
    STUECK("STUECK", "STUECK"),

    /**
     * Volumen in Kubikmetern — für die Nebenkostenabrechnung (Wasser, Abwasser).
     * Siehe {@code Specs/Nebenkosten/Abrechnung.md}.
     */
    M3("M3", "M3"),

    /**
     * Fläche in Quadratmetern — für die Nebenkostenabrechnung.
     *
     * <p>Anlass ist die <b>Kehrichtgrundgebühr</b>, die nach Wohnfläche verrechnet wird. Wie
     * {@link #M3} eine gemessene Grösse und keine Pauschale: Die Fläche je Wohnung wird erfasst,
     * der Betrag folgt daraus.
     *
     * <p>Wie {@code M3} und {@code CHF} <b>nicht</b> an einem {@link TarifTyp#ZUSATZ}-Tarif
     * wählbar — die Tarifmaske bietet dort nur die Einheiten der Stromabrechnung an.
     */
    M2("M2", "M2"),

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
    CHF("CHF", "CHF");

    /*
     * Die Uebersetzungs-Keys der Einheit auf Rechnungen - EINE Zuordnung fuer ZEV- und
     * NK-Rechnungen. Vorher standen sie in RechnungService (switch) und NkRechnungService (Map)
     * doppelt; TAG musste an beiden Stellen nachgetragen werden. Als Konstruktorparameter kann
     * kein Wert ohne Keys bleiben - ein neuer Enum-Wert kompiliert erst, wenn er sie nennt
     * (statt still als Kilowattstunden beschriftet zu werden).
     * Dieselbe Zuordnung im Frontend: tarif.model.ts, MENGENEINHEIT_KEYS.
     */
    private final String einzahlKey;
    private final String mehrzahlKey;

    Mengeneinheit(String einzahlKey, String mehrzahlKey) {
        this.einzahlKey = einzahlKey;
        this.mehrzahlKey = mehrzahlKey;
    }

    /** Key in der Mehrzahl ("Monate", "Tage") - fuer Spaltenbeschriftungen ohne Menge. */
    public String mehrzahlKey() {
        return mehrzahlKey;
    }

    /** Key passend zur Menge: genau 1 in der Einzahl ("1 Monat"), sonst in der Mehrzahl. */
    public String key(BigDecimal menge) {
        return menge != null && menge.compareTo(BigDecimal.ONE) == 0 ? einzahlKey : mehrzahlKey;
    }

    /**
     * Key zu einer Einheit, die als Text vorliegt (Rechnungszeile, {@code TarifTyp#mengeneinheit()}).
     * Unbekannte Werte bleiben, wie sie sind - der Text ist dann selbst der Key.
     *
     * @return {@code null}, wenn keine Einheit angegeben ist
     */
    public static String keyFuer(String mengeneinheit, BigDecimal menge) {
        if (mengeneinheit == null) {
            return null;
        }
        String name = mengeneinheit.toUpperCase(Locale.ROOT);
        for (Mengeneinheit einheit : values()) {
            if (einheit.name().equals(name)) {
                return einheit.key(menge);
            }
        }
        return name;
    }
}
