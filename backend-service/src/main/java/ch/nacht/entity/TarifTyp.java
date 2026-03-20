package ch.nacht.entity;

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
    GRUNDGEBUEHR
}
