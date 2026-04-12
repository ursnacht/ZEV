package ch.nacht.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Entity representing a debitor entry for invoice tracking.
 */
@Entity
@Table(name = "debitor", schema = "zev")
@Filter(name = "orgFilter", condition = "org_id = :orgId")
public class Debitor {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "debitor_seq")
    @SequenceGenerator(name = "debitor_seq", sequenceName = "zev.debitor_seq", allocationSize = 1)
    private Long id;

    @Column(name = "org_id", nullable = false)
    private Long orgId;

    @NotNull(message = "Mieter is required")
    @Column(name = "mieter_id", nullable = false)
    private Long mieterId;

    @NotNull(message = "Betrag is required")
    @DecimalMin(value = "0.01", message = "Betrag must be greater than 0")
    @Column(name = "betrag", precision = 10, scale = 2, nullable = false)
    private BigDecimal betrag;

    @NotNull(message = "Datum von is required")
    @Column(name = "datum_von", nullable = false)
    private LocalDate datumVon;

    @NotNull(message = "Datum bis is required")
    @Column(name = "datum_bis", nullable = false)
    private LocalDate datumBis;

    @Column(name = "zahldatum")
    private LocalDate zahldatum;

    public Debitor() {
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

    public Long getMieterId() {
        return mieterId;
    }

    public void setMieterId(Long mieterId) {
        this.mieterId = mieterId;
    }

    public BigDecimal getBetrag() {
        return betrag;
    }

    public void setBetrag(BigDecimal betrag) {
        this.betrag = betrag;
    }

    public LocalDate getDatumVon() {
        return datumVon;
    }

    public void setDatumVon(LocalDate datumVon) {
        this.datumVon = datumVon;
    }

    public LocalDate getDatumBis() {
        return datumBis;
    }

    public void setDatumBis(LocalDate datumBis) {
        this.datumBis = datumBis;
    }

    public LocalDate getZahldatum() {
        return zahldatum;
    }

    public void setZahldatum(LocalDate zahldatum) {
        this.zahldatum = zahldatum;
    }

    @Override
    public String toString() {
        return "Debitor{id=" + id + ", orgId=" + orgId + ", mieterId=" + mieterId +
               ", betrag=" + betrag + ", datumVon=" + datumVon + ", datumBis=" + datumBis +
               ", zahldatum=" + zahldatum + "}";
    }
}
