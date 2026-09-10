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

    /**
     * Betrag, auf den sich der {@link #prozentsatz} bezieht — die <b>Bezugsgrösse</b> der Zeile.
     *
     * <p>Je Art eine andere Grösse, aber immer dieselbe Rolle: {@code Bezugsbetrag × Prozentsatz =
     * Betrag}.
     * <ul>
     *   <li>{@code UMLAGE} / {@code UMLAGE_PERSON}: der Totalbetrag der Position; der Prozentsatz
     *       ist der Zeit- bzw. Personenanteil dieses Mieters.</li>
     *   <li>{@code ANTEIL}: der Totalbetrag der Position.</li>
     *   <li>{@code ZUSCHLAG}: das <b>Zwischentotal</b> der Zeilen davor, auf dem der Zuschlag
     *       rechnet.</li>
     *   <li>{@code VERBRAUCH} und Zusatzzeilen: {@code null} — dort ist
     *       {@link #betragProEinheit} die Bezugsgrösse, und die Menge steht in einer eigenen
     *       Spalte.</li>
     * </ul>
     *
     * <p><b>Eigenes Feld und nicht {@link #betragProEinheit} mitbenutzt:</b> Das ist ein Preis je
     * Einheit, nicht ein Gesamtbetrag. Beides in ein Feld zu legen hiesse, dass niemand mehr am
     * Namen erkennt, was drinsteht.
     */
    private BigDecimal bezugsbetrag;

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

    public BigDecimal getBezugsbetrag() {
        return bezugsbetrag;
    }

    public void setBezugsbetrag(BigDecimal bezugsbetrag) {
        this.bezugsbetrag = bezugsbetrag;
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
