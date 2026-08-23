package ch.nacht.dto;

import java.math.BigDecimal;

/**
 * Erfasste Menge eines Mieters zu einer VERBRAUCH-Position.
 *
 * <p>Bewusst <b>ohne</b> {@code positionId}: Die Menge steht in {@link NkPositionDTO} eingebettet.
 * Sonst müsste eine neu angelegte Position erst gespeichert werden, bevor sich Mengen dazu
 * erfassen liessen — der Client hätte für sie noch keine ID.
 */
public class NkVerbrauchDTO {

    private Long mieterId;
    private BigDecimal menge;

    public NkVerbrauchDTO() {
    }

    public NkVerbrauchDTO(Long mieterId, BigDecimal menge) {
        this.mieterId = mieterId;
        this.menge = menge;
    }

    public Long getMieterId() {
        return mieterId;
    }

    public void setMieterId(Long mieterId) {
        this.mieterId = mieterId;
    }

    public BigDecimal getMenge() {
        return menge;
    }

    public void setMenge(BigDecimal menge) {
        this.menge = menge;
    }
}
