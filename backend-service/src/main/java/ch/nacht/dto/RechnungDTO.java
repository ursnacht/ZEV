package ch.nacht.dto;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO for a single invoice containing all data needed for PDF generation.
 */
public class RechnungDTO {

    private Long einheitId;
    private String einheitName;
    private String mietername;
    private String messpunkt;

    private LocalDate von;
    private LocalDate bis;
    private LocalDate erstellungsdatum;

    // Tariff lines (multiple lines possible per type if tariffs change within period)
    private List<TarifZeileDTO> tarifZeilen = new ArrayList<>();

    // Totals
    private double totalBetrag;     // CHF (before rounding)
    private double rundung;         // CHF (rounding to 5 Rappen)
    private double endBetrag;       // CHF (final amount to pay)

    // Invoice configuration
    private String zahlungsfrist;
    private String iban;
    private String stellerName;
    private String stellerStrasse;
    private String stellerPlzOrt;
    private String adresseStrasse;
    private String adressePlzOrt;

    public Long getEinheitId() {
        return einheitId;
    }

    public void setEinheitId(Long einheitId) {
        this.einheitId = einheitId;
    }

    public String getEinheitName() {
        return einheitName;
    }

    public void setEinheitName(String einheitName) {
        this.einheitName = einheitName;
    }

    public String getMietername() {
        return mietername;
    }

    public void setMietername(String mietername) {
        this.mietername = mietername;
    }

    public String getMesspunkt() {
        return messpunkt;
    }

    public void setMesspunkt(String messpunkt) {
        this.messpunkt = messpunkt;
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

    public LocalDate getErstellungsdatum() {
        return erstellungsdatum;
    }

    public void setErstellungsdatum(LocalDate erstellungsdatum) {
        this.erstellungsdatum = erstellungsdatum;
    }

    public List<TarifZeileDTO> getTarifZeilen() {
        return tarifZeilen;
    }

    public void setTarifZeilen(List<TarifZeileDTO> tarifZeilen) {
        this.tarifZeilen = tarifZeilen;
    }

    public void addTarifZeile(TarifZeileDTO zeile) {
        this.tarifZeilen.add(zeile);
    }

    public double getTotalBetrag() {
        return totalBetrag;
    }

    public void setTotalBetrag(double totalBetrag) {
        this.totalBetrag = totalBetrag;
    }

    public double getRundung() {
        return rundung;
    }

    public void setRundung(double rundung) {
        this.rundung = rundung;
    }

    public double getEndBetrag() {
        return endBetrag;
    }

    public void setEndBetrag(double endBetrag) {
        this.endBetrag = endBetrag;
    }

    public String getZahlungsfrist() {
        return zahlungsfrist;
    }

    public void setZahlungsfrist(String zahlungsfrist) {
        this.zahlungsfrist = zahlungsfrist;
    }

    public String getIban() {
        return iban;
    }

    public void setIban(String iban) {
        this.iban = iban;
    }

    public String getStellerName() {
        return stellerName;
    }

    public void setStellerName(String stellerName) {
        this.stellerName = stellerName;
    }

    public String getStellerStrasse() {
        return stellerStrasse;
    }

    public void setStellerStrasse(String stellerStrasse) {
        this.stellerStrasse = stellerStrasse;
    }

    public String getStellerPlzOrt() {
        return stellerPlzOrt;
    }

    public void setStellerPlzOrt(String stellerPlzOrt) {
        this.stellerPlzOrt = stellerPlzOrt;
    }

    public String getAdresseStrasse() {
        return adresseStrasse;
    }

    public void setAdresseStrasse(String adresseStrasse) {
        this.adresseStrasse = adresseStrasse;
    }

    public String getAdressePlzOrt() {
        return adressePlzOrt;
    }

    public void setAdressePlzOrt(String adressePlzOrt) {
        this.adressePlzOrt = adressePlzOrt;
    }
}
