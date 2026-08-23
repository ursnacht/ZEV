package ch.nacht.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;

/**
 * Frei erfasste Zusatzposition eines einzelnen Mieters
 * (Specs/Nebenkosten/Abrechnung.md, FR-3).
 *
 * <p>Sie teilt sich den Nummernraum der {@link NkPosition}: Ein Zuschlag rechnet auf die Summe
 * aller Zeilen mit kleinerer {@link #reihenfolge} — allgemeine wie mieterspezifische. Bei
 * Gleichstand zählt die allgemeine Position zuerst.
 */
@Entity
@Table(name = "nk_zusatz", schema = "zev")
@Filter(name = "orgFilter", condition = "org_id = :orgId")
public class NkZusatz {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "nk_zusatz_seq")
    @SequenceGenerator(name = "nk_zusatz_seq", sequenceName = "zev.nk_zusatz_seq", allocationSize = 1)
    private Long id;

    @Column(name = "org_id", nullable = false)
    private Long orgId;

    @NotNull(message = "Abrechnung is required")
    @Column(name = "abrechnung_id", nullable = false)
    private Long abrechnungId;

    @NotNull(message = "Mieter is required")
    @Column(name = "mieter_id", nullable = false)
    private Long mieterId;

    @NotNull(message = "Reihenfolge is required")
    @Column(name = "reihenfolge", nullable = false)
    private Integer reihenfolge;

    @NotBlank(message = "Bezeichnung is required")
    @Size(max = 150, message = "Bezeichnung must not exceed 150 characters")
    @Column(name = "bezeichnung", length = 150, nullable = false)
    private String bezeichnung;

    @NotNull(message = "Einheit is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "einheit", length = 20, nullable = false)
    private Mengeneinheit einheit;

    @NotNull(message = "Menge is required")
    @PositiveOrZero(message = "Menge must not be negative")
    @Column(name = "menge", precision = 12, scale = 3, nullable = false)
    private BigDecimal menge;

    @NotNull(message = "Betrag pro Einheit is required")
    @PositiveOrZero(message = "Betrag pro Einheit must not be negative")
    @Column(name = "betrag_pro_einheit", precision = 12, scale = 4, nullable = false)
    private BigDecimal betragProEinheit;

    public NkZusatz() {
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

    public Long getAbrechnungId() {
        return abrechnungId;
    }

    public void setAbrechnungId(Long abrechnungId) {
        this.abrechnungId = abrechnungId;
    }

    public Long getMieterId() {
        return mieterId;
    }

    public void setMieterId(Long mieterId) {
        this.mieterId = mieterId;
    }

    public Integer getReihenfolge() {
        return reihenfolge;
    }

    public void setReihenfolge(Integer reihenfolge) {
        this.reihenfolge = reihenfolge;
    }

    public String getBezeichnung() {
        return bezeichnung;
    }

    public void setBezeichnung(String bezeichnung) {
        this.bezeichnung = bezeichnung;
    }

    public Mengeneinheit getEinheit() {
        return einheit;
    }

    public void setEinheit(Mengeneinheit einheit) {
        this.einheit = einheit;
    }

    public BigDecimal getMenge() {
        return menge;
    }

    public void setMenge(BigDecimal menge) {
        this.menge = menge;
    }

    public BigDecimal getBetragProEinheit() {
        return betragProEinheit;
    }

    public void setBetragProEinheit(BigDecimal betragProEinheit) {
        this.betragProEinheit = betragProEinheit;
    }

    @Override
    public String toString() {
        return "NkZusatz{id=" + id + ", abrechnungId=" + abrechnungId + ", mieterId=" + mieterId
                + ", reihenfolge=" + reihenfolge + ", bezeichnung='" + bezeichnung + "'}";
    }
}
