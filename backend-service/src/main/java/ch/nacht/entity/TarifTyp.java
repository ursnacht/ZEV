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
     */
    public static final Set<TarifTyp> MANUELL_ERFASST = EnumSet.of(LADESTROM);
}
