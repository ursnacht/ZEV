package ch.nacht.dto;

import ch.nacht.entity.Mengeneinheit;
import ch.nacht.entity.NkPositionsart;

import java.math.BigDecimal;

/**
 * Eine berechnete Zeile im Block eines Mieters (Specs/Nebenkosten/Abrechnung.md, FR-3).
 *
 * <p>{@link #zusatzId} unterscheidet die beiden Herkünfte: Ist sie gesetzt, stammt die Zeile aus
 * {@code nk_zusatz} und ist vollständig bearbeitbar; sonst aus {@code nk_position}. Ihre
 * {@link #art} ist dann {@code VERBRAUCH}, weil eine Zusatzzeile genau so rechnet (Menge mal
 * Betrag pro Einheit) — die Unterscheidung macht allein die ID.
 */
public class NkZeileDTO {

    /** ID der allgemeinen Position; {@code null} bei einer Zusatzzeile. */
    private Long positionId;

    /** ID der Zusatzposition; {@code null} bei einer allgemeinen Position. */
    private Long zusatzId;

    private NkPositionsart art;
    private Integer reihenfolge;
    private String bezeichnung;
    private Mengeneinheit einheit;

    /** Menge der Zeile; bei UMLAGE nur gefüllt, wenn eine Gesamtmenge erfasst ist. */
    private BigDecimal menge;

    private BigDecimal betragProEinheit;
    private BigDecimal prozentsatz;

    /** Zeilenbetrag, bereits auf zwei Nachkommastellen gerundet (FR-5). */
    private BigDecimal betrag;

    public NkZeileDTO() {
    }

    public Long getPositionId() {
        return positionId;
    }

    public void setPositionId(Long positionId) {
        this.positionId = positionId;
    }

    public Long getZusatzId() {
        return zusatzId;
    }

    public void setZusatzId(Long zusatzId) {
        this.zusatzId = zusatzId;
    }

    public NkPositionsart getArt() {
        return art;
    }

    public void setArt(NkPositionsart art) {
        this.art = art;
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

    public BigDecimal getProzentsatz() {
        return prozentsatz;
    }

    public void setProzentsatz(BigDecimal prozentsatz) {
        this.prozentsatz = prozentsatz;
    }

    public BigDecimal getBetrag() {
        return betrag;
    }

    public void setBetrag(BigDecimal betrag) {
        this.betrag = betrag;
    }
}
