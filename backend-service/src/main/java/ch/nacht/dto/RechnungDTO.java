package ch.nacht.dto;

import java.time.LocalDate;

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

    // ZEV tariff (self-consumed solar energy)
    private double zevMenge;        // kWh
    private double zevPreis;        // CHF/kWh
    private double zevBetrag;       // CHF
    private String zevBezeichnung;

    // EWB tariff (grid energy)
    private double ewbMenge;        // kWh
    private double ewbPreis;        // CHF/kWh
    private double ewbBetrag;       // CHF
    private String ewbBezeichnung;

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

    public double getZevMenge() {
        return zevMenge;
    }

    public void setZevMenge(double zevMenge) {
        this.zevMenge = zevMenge;
    }

    public double getZevPreis() {
        return zevPreis;
    }

    public void setZevPreis(double zevPreis) {
        this.zevPreis = zevPreis;
    }

    public double getZevBetrag() {
        return zevBetrag;
    }

    public void setZevBetrag(double zevBetrag) {
        this.zevBetrag = zevBetrag;
    }

    public String getZevBezeichnung() {
        return zevBezeichnung;
    }

    public void setZevBezeichnung(String zevBezeichnung) {
        this.zevBezeichnung = zevBezeichnung;
    }

    public double getEwbMenge() {
        return ewbMenge;
    }

    public void setEwbMenge(double ewbMenge) {
        this.ewbMenge = ewbMenge;
    }

    public double getEwbPreis() {
        return ewbPreis;
    }

    public void setEwbPreis(double ewbPreis) {
        this.ewbPreis = ewbPreis;
    }

    public double getEwbBetrag() {
        return ewbBetrag;
    }

    public void setEwbBetrag(double ewbBetrag) {
        this.ewbBetrag = ewbBetrag;
    }

    public String getEwbBezeichnung() {
        return ewbBezeichnung;
    }

    public void setEwbBezeichnung(String ewbBezeichnung) {
        this.ewbBezeichnung = ewbBezeichnung;
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
