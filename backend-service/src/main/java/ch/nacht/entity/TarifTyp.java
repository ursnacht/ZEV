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
     * <p>Zusätzlich manuell erfassbar (siehe {@link #MANUELL_ERFASST}): Eine Ladestation kann
     * eine eigene Grundgebühr tragen, deren Monate nicht aus dem Rechnungszeitraum folgen. Die
     * automatische Berechnung bleibt davon unberührt — beide Zeilen erscheinen nebeneinander.
     * Die Menge einer solchen Position zählt <b>Monate</b>, nicht kWh.
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
     * <p>{@link #GRUNDGEBUEHR} steht hier <b>zusätzlich</b> zu seiner automatischen Berechnung:
     * Der Typ ist nicht ausschliesslich manuell, sondern beides. Deshalb prüft der Service die
     * Eindeutigkeit je Einheit, Quartal und <b>Typ</b> — sonst schlössen sich eine Ladestrom-
     * und eine Grundgebühr-Position im selben Quartal gegenseitig aus.
     */
    public static final Set<TarifTyp> MANUELL_ERFASST = EnumSet.of(LADESTROM, GRUNDGEBUEHR);

    /**
     * Mengeneinheit einer manuell erfassten Position dieses Typs.
     *
     * @return {@code "MONAT"} für {@link #GRUNDGEBUEHR}, sonst {@code "KWH"}
     */
    public String mengeneinheit() {
        return this == GRUNDGEBUEHR ? "MONAT" : "KWH";
    }
}
