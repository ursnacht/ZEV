package ch.nacht.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;

/**
 * Je Mieter erfasste Menge zu einer VERBRAUCH-Position (Specs/Nebenkosten/Abrechnung.md, FR-2).
 *
 * <p>Eine fehlende Zeile bedeutet „keine Menge erfasst" und damit Betrag null — nicht dasselbe wie
 * eine erfasste 0, die eine bewusste Aussage ist. Beide ergeben denselben Betrag; unterschieden
 * werden sie nur in der Anzeige.
 */
@Entity
@Table(name = "nk_verbrauch", schema = "zev")
@Filter(name = "orgFilter", condition = "org_id = :orgId")
public class NkVerbrauch {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "nk_verbrauch_seq")
    @SequenceGenerator(name = "nk_verbrauch_seq", sequenceName = "zev.nk_verbrauch_seq", allocationSize = 1)
    private Long id;

    @Column(name = "org_id", nullable = false)
    private Long orgId;

    @NotNull(message = "Position is required")
    @Column(name = "position_id", nullable = false)
    private Long positionId;

    @NotNull(message = "Mieter is required")
    @Column(name = "mieter_id", nullable = false)
    private Long mieterId;

    @NotNull(message = "Menge is required")
    @PositiveOrZero(message = "Menge must not be negative")
    @Column(name = "menge", precision = 12, scale = 3, nullable = false)
    private BigDecimal menge;

    public NkVerbrauch() {
    }

    public NkVerbrauch(Long positionId, Long mieterId, BigDecimal menge) {
        this.positionId = positionId;
        this.mieterId = mieterId;
        this.menge = menge;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOrgId() {
        return orgId;
    }

    public void setOrgId(Long orgId) {
        this.orgId = orgId;
    }

    public Long getPositionId() {
        return positionId;
    }

    public void setPositionId(Long positionId) {
        this.positionId = positionId;
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

    @Override
    public String toString() {
        return "NkVerbrauch{id=" + id + ", positionId=" + positionId
                + ", mieterId=" + mieterId + ", menge=" + menge + "}";
    }
}
