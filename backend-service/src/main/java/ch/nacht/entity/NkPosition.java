package ch.nacht.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;

/**
 * Allgemeine Position einer Nebenkostenabrechnung (Specs/Nebenkosten/Abrechnung.md, FR-2).
 *
 * <p>Welche Felder gefüllt sein müssen, hängt von der {@link NkPositionsart} ab; die Regel steht
 * zusätzlich als CHECK-Constraint {@code ck_nk_position_felder} in der Datenbank. Die
 * Bean-Validation-Annotationen hier decken nur die Wertebereiche ab — die art-abhängige
 * Pflichtfeldprüfung macht der Service, weil sie mehrere Felder gleichzeitig betrachtet.
 */
@Entity
@Table(name = "nk_position", schema = "zev")
@Filter(name = "orgFilter", condition = "org_id = :orgId")
public class NkPosition {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "nk_position_seq")
    @SequenceGenerator(name = "nk_position_seq", sequenceName = "zev.nk_position_seq", allocationSize = 1)
    private Long id;

    @Column(name = "org_id", nullable = false)
    private Long orgId;

    @NotNull(message = "Abrechnung is required")
    @Column(name = "abrechnung_id", nullable = false)
    private Long abrechnungId;

    @NotNull(message = "Art is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "art", length = 20, nullable = false)
    private NkPositionsart art;

    @NotBlank(message = "Bezeichnung is required")
    @Size(max = 150, message = "Bezeichnung must not exceed 150 characters")
    @Column(name = "bezeichnung", length = 150, nullable = false)
    private String bezeichnung;

    /**
     * Reihenfolge in der Abrechnung — fachlich tragend, nicht bloss Anzeige: Ein ZUSCHLAG rechnet
     * auf die Summe aller Zeilen mit <b>kleinerer</b> Nummer.
     */
    @NotNull(message = "Reihenfolge is required")
    @Column(name = "reihenfolge", nullable = false)
    private Integer reihenfolge;

    /** Mengeneinheit bei UMLAGE und VERBRAUCH; bei ZUSCHLAG leer. */
    @Enumerated(EnumType.STRING)
    @Column(name = "einheit", length = 20)
    private Mengeneinheit einheit;

    /** Nur UMLAGE: der zu verteilende Gesamtbetrag. */
    @PositiveOrZero(message = "Totalbetrag must not be negative")
    @Column(name = "totalbetrag", precision = 12, scale = 2)
    private BigDecimal totalbetrag;

    /** Nur UMLAGE, optional: die zu verteilende Gesamtmenge (rein informativ). */
    @PositiveOrZero(message = "Gesamtmenge must not be negative")
    @Column(name = "gesamtmenge", precision = 12, scale = 3)
    private BigDecimal gesamtmenge;

    /** Nur VERBRAUCH: Preis je Mengeneinheit. */
    @PositiveOrZero(message = "Betrag pro Einheit must not be negative")
    @Column(name = "betrag_pro_einheit", precision = 12, scale = 4)
    private BigDecimal betragProEinheit;

    /** Nur ZUSCHLAG: Prozent auf die Summe der Positionen davor. */
    @PositiveOrZero(message = "Prozentsatz must not be negative")
    @DecimalMax(value = "100.00", message = "Prozentsatz must not exceed 100")
    @Column(name = "prozentsatz", precision = 5, scale = 2)
    private BigDecimal prozentsatz;

    public NkPosition() {
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

    public NkPositionsart getArt() {
        return art;
    }

    public void setArt(NkPositionsart art) {
        this.art = art;
    }

    public String getBezeichnung() {
        return bezeichnung;
    }

    public void setBezeichnung(String bezeichnung) {
        this.bezeichnung = bezeichnung;
    }

    public Integer getReihenfolge() {
        return reihenfolge;
    }

    public void setReihenfolge(Integer reihenfolge) {
        this.reihenfolge = reihenfolge;
    }

    public Mengeneinheit getEinheit() {
        return einheit;
    }

    public void setEinheit(Mengeneinheit einheit) {
        this.einheit = einheit;
    }

    public BigDecimal getTotalbetrag() {
        return totalbetrag;
    }

    public void setTotalbetrag(BigDecimal totalbetrag) {
        this.totalbetrag = totalbetrag;
    }

    public BigDecimal getGesamtmenge() {
        return gesamtmenge;
    }

    public void setGesamtmenge(BigDecimal gesamtmenge) {
        this.gesamtmenge = gesamtmenge;
    }

    public BigDecimal getBetragProEinheit() {
        return betragProEinheit;
    }

    public void setBetragProEinheit(BigDecimal betragProEinheit) {
        this.betragProEinheit = betragProEinheit;
    }

    public BigDecimal getProzentsatz() {
        return prozentsatz;
    }

    public void setProzentsatz(BigDecimal prozentsatz) {
        this.prozentsatz = prozentsatz;
    }

    @Override
    public String toString() {
        return "NkPosition{id=" + id + ", abrechnungId=" + abrechnungId + ", art=" + art
                + ", bezeichnung='" + bezeichnung + "', reihenfolge=" + reihenfolge + "}";
    }
}
