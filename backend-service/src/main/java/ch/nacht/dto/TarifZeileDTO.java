package ch.nacht.dto;

import ch.nacht.entity.TarifTyp;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO for a single tariff line on an invoice.
 * Multiple lines can exist per tariff type if tariffs change within the invoice period.
 *
 * <p><b>Betraege sind {@link BigDecimal}</b> - wie in {@code Debitor} und in der
 * Nebenkostenabrechnung. {@code preis} wird unveraendert aus {@code Tarif.preis}
 * ({@code NUMERIC(10,5)}) uebernommen, {@code betrag} ist das exakte Produkt
 * {@code menge x preis}; gerundet wird erst der Endbetrag der Rechnung auf 5 Rappen.
 */
public class TarifZeileDTO {

    private String bezeichnung;
    private LocalDate von;
    private LocalDate bis;
    private BigDecimal menge;      // kWh (rounded)
    private BigDecimal preis;      // CHF/kWh
    private BigDecimal betrag;     // CHF
    private TarifTyp typ;
    private String mengeneinheit; // "kWh" for ZEV/VNB, "Monate" for GRUNDGEBUEHR

    public TarifZeileDTO() {
    }

    public TarifZeileDTO(String bezeichnung, LocalDate von, LocalDate bis, BigDecimal menge, BigDecimal preis, BigDecimal betrag, TarifTyp typ) {
        this.bezeichnung = bezeichnung;
        this.von = von;
        this.bis = bis;
        this.menge = menge;
        this.preis = preis;
        this.betrag = betrag;
        this.typ = typ;
        this.mengeneinheit = "kWh";
    }

    public TarifZeileDTO(String bezeichnung, LocalDate von, LocalDate bis, BigDecimal menge, BigDecimal preis, BigDecimal betrag, TarifTyp typ, String mengeneinheit) {
        this.bezeichnung = bezeichnung;
        this.von = von;
        this.bis = bis;
        this.menge = menge;
        this.preis = preis;
        this.betrag = betrag;
        this.typ = typ;
        this.mengeneinheit = mengeneinheit;
    }

    public String getBezeichnung() {
        return bezeichnung;
    }

    public void setBezeichnung(String bezeichnung) {
        this.bezeichnung = bezeichnung;
    }

    public LocalDate getVon() {
        return von;
    }

    public void setVon(LocalDate von) {
        this.von = von;
    }

    public LocalDate getBis() {
        return bis;
    }

    public void setBis(LocalDate bis) {
        this.bis = bis;
    }

    public BigDecimal getMenge() {
        return menge;
    }

    public void setMenge(BigDecimal menge) {
        this.menge = menge;
    }

    public BigDecimal getPreis() {
        return preis;
    }

    public void setPreis(BigDecimal preis) {
        this.preis = preis;
    }

    public BigDecimal getBetrag() {
        return betrag;
    }

    public void setBetrag(BigDecimal betrag) {
        this.betrag = betrag;
    }

    public TarifTyp getTyp() {
        return typ;
    }

    public void setTyp(TarifTyp typ) {
        this.typ = typ;
    }

    public String getMengeneinheit() {
        return mengeneinheit;
    }

    public void setMengeneinheit(String mengeneinheit) {
        this.mengeneinheit = mengeneinheit;
    }
}
