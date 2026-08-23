package ch.nacht.dto;

import ch.nacht.entity.Mengeneinheit;

import java.math.BigDecimal;

/**
 * Frei erfasste Zusatzposition eines Mieters (Specs/Nebenkosten/Abrechnung.md, FR-3).
 */
public class NkZusatzDTO {

    /** {@code null} bei einer neu angelegten Zeile. */
    private Long id;

    private Long mieterId;
    private Integer reihenfolge;
    private String bezeichnung;
    private Mengeneinheit einheit;
    private BigDecimal menge;
    private BigDecimal betragProEinheit;

    public NkZusatzDTO() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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
}
