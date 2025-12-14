package ch.nacht.dto;

import ch.nacht.entity.TarifTyp;

import java.time.LocalDate;

/**
 * DTO for a single tariff line on an invoice.
 * Multiple lines can exist per tariff type if tariffs change within the invoice period.
 */
public class TarifZeileDTO {

    private String bezeichnung;
    private LocalDate von;
    private LocalDate bis;
    private double menge;      // kWh (rounded)
    private double preis;      // CHF/kWh
    private double betrag;     // CHF
    private TarifTyp typ;

    public TarifZeileDTO() {
    }

    public TarifZeileDTO(String bezeichnung, LocalDate von, LocalDate bis, double menge, double preis, double betrag, TarifTyp typ) {
        this.bezeichnung = bezeichnung;
        this.von = von;
        this.bis = bis;
        this.menge = menge;
        this.preis = preis;
        this.betrag = betrag;
        this.typ = typ;
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

    public double getMenge() {
        return menge;
    }

    public void setMenge(double menge) {
        this.menge = menge;
    }

    public double getPreis() {
        return preis;
    }

    public void setPreis(double preis) {
        this.preis = preis;
    }

    public double getBetrag() {
        return betrag;
    }

    public void setBetrag(double betrag) {
        this.betrag = betrag;
    }

    public TarifTyp getTyp() {
        return typ;
    }

    public void setTyp(TarifTyp typ) {
        this.typ = typ;
    }
}
