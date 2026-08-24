package ch.nacht.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO for a single invoice containing all data needed for PDF generation.
 *
 * <p><b>Betraege sind {@link BigDecimal}</b> und nie {@code double} - dieselbe Zusicherung wie in
 * {@code Debitor} und in der Nebenkostenabrechnung. Der Endbetrag wandert unveraendert in die
 * Debitorenkontrolle und in den Einzahlungsschein; eine Umrechnung an der Grenze gibt es nicht
 * mehr.
 */
public class RechnungDTO {

    private Long einheitId;
    private String einheitName;
    private String messpunkt;

    // Mieter-Daten
    private Long mieterId;
    private String mieterName;
    private String mieterStrasse;
    private String mieterPlzOrt;

    private LocalDate von;
    private LocalDate bis;
    private LocalDate erstellungsdatum;

    // Tariff lines (multiple lines possible per type if tariffs change within period)
    private List<TarifZeileDTO> tarifZeilen = new ArrayList<>();

    // Totals
    private BigDecimal totalBetrag = BigDecimal.ZERO;  // CHF (before rounding)
    private BigDecimal rundung = BigDecimal.ZERO;      // CHF (rounding to 5 Rappen)
    private BigDecimal endBetrag = BigDecimal.ZERO;    // CHF (final amount to pay)

    // Invoice configuration
    private String zahlungsfrist;
    private String iban;
    private String stellerName;
    private String stellerStrasse;
    private String stellerPlzOrt;

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

    public Long getMieterId() {
        return mieterId;
    }

    public void setMieterId(Long mieterId) {
        this.mieterId = mieterId;
    }

    public String getMieterName() {
        return mieterName;
    }

    public void setMieterName(String mieterName) {
        this.mieterName = mieterName;
    }

    public String getMieterStrasse() {
        return mieterStrasse;
    }

    public void setMieterStrasse(String mieterStrasse) {
        this.mieterStrasse = mieterStrasse;
    }

    public String getMieterPlzOrt() {
        return mieterPlzOrt;
    }

    public void setMieterPlzOrt(String mieterPlzOrt) {
        this.mieterPlzOrt = mieterPlzOrt;
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

    public BigDecimal getTotalBetrag() {
        return totalBetrag;
    }

    public void setTotalBetrag(BigDecimal totalBetrag) {
        this.totalBetrag = totalBetrag;
    }

    public BigDecimal getRundung() {
        return rundung;
    }

    public void setRundung(BigDecimal rundung) {
        this.rundung = rundung;
    }

    public BigDecimal getEndBetrag() {
        return endBetrag;
    }

    public void setEndBetrag(BigDecimal endBetrag) {
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
}
