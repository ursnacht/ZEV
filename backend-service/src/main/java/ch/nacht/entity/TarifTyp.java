package ch.nacht.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * Enum for tariff types.
 */
public enum TarifTyp {
    /**
     * ZEV (Zusammenschluss zum Eigenverbrauch) - Self-consumed solar energy.
     * Calculated from messwerte.zev_calculated
     */
    ZEV,

    /**
     * VNB (Verteilnetzbetreiber) - Grid energy from network operator.
     * Calculated from messwerte.total - messwerte.zev_calculated
     */
    VNB,

    /**
     * GRUNDGEBUEHR - Monthly fixed fee per electricity meter.
     * Calculated as: number of full calendar months × fixed price per meter.
     *
     * <p><b>Nicht</b> manuell erfassbar. Der Versuch scheiterte an der Überschneidungsregel:
     * Je Zeitraum ist nur <i>ein</i> Grundgebühr-Tarif gültig, ein eigener Tarif für Ladestationen
     * mit eigenem Preis also gar nicht anlegbar. Und diese Regel aufzuheben verbietet sich, weil
     * {@code RechnungService.berechneGrundgebuehrZeilen} jeden gültigen Grundgebühr-Tarif
     * automatisch auf <i>jede</i> Konsumenten-Rechnung schreibt — ein zweiter Tarif landete damit
     * bei allen Wohnungen. Eine Grundgebühr für Ladestationen wird über {@link #ZUSATZ} mit
     * Mengeneinheit <i>Monat</i> abgebildet (Specs/Tarifpositionen.md).
     */
    GRUNDGEBUEHR,

    /**
     * LADESTROM - Charging current for vehicles, billed at its own price.
     * Unlike ZEV/VNB the quantity does not come from measurements but from manually
     * captured (later imported) {@link Tarifposition} entries per tenant and quarter.
     */
    LADESTROM;

    /**
     * Tariff types whose quantities are captured manually as {@link Tarifposition} instead of
     * being derived from measurements or the billing period.
     *
     * <p>Deliberately a <b>set</b>: a further use case (Sauna, Waschküche, …) only extends this
     * set — table, service and UI stay unchanged.
     *
     * <p>Der Service prüft die Eindeutigkeit einer Position je Einheit, Quartal und <b>Typ</b> —
     * nicht gegen diese Menge als Ganzes. Sonst schlössen sich Positionen verschiedener Typen im
     * selben Quartal gegenseitig aus.
     */
    public static final Set<TarifTyp> MANUELL_ERFASST = EnumSet.of(LADESTROM);

    /**
     * Mengeneinheit, die sich allein aus dem Typ ergibt.
     *
     * <p>{@link #GRUNDGEBUEHR} rechnet Monate, alles Übrige kWh. Für Typen mit <b>frei
     * wählbarer</b> Einheit ist stattdessen der Wert am Tarif massgebend.
     *
     * @return {@code "MONAT"} für {@link #GRUNDGEBUEHR}, sonst {@code "KWH"}
     */
    public String mengeneinheit() {
        return this == GRUNDGEBUEHR ? "MONAT" : "KWH";
    }
}
