package ch.nacht.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Die Nebenkostenrechnung eines Mieters — Eingangsdaten des Templates
 * (Specs/Nebenkosten/RechnungenGenerieren.md, FR-3 und FR-8).
 *
 * <p>Aufgebaut aus dem Mieterblock der Abrechnung, <b>ohne neu zu rechnen</b>: {@code zeilen},
 * {@code kostentotal}, {@code akontoTotal} und {@code saldo} stammen unveraendert aus
 * {@code NkAbrechnungService.getAbrechnungDetail} — derselben Quelle, die die Maske anzeigt.
 *
 * <p>Gerundet wird allein der <b>Endbetrag</b> auf 5 Rappen, weil der QR-Zahlteil einen zahlbaren
 * Betrag verlangt; die Differenz steht als {@link #rundung} auf der Rechnung. Die Zeilenrundung auf
 * 1 Rappen der Abrechnung bleibt unberuehrt.
 *
 * <p>Ein <b>positiver</b> {@link #saldo} ist eine Nachzahlung, ein negativer ein Guthaben. Die
 * Rechnung benennt beides ausdruecklich, statt nur ein Vorzeichen zu zeigen.
 */
public class NkRechnungDTO {

    // Abrechnung
    private Long abrechnungId;
    private String bezeichnung;
    private LocalDate von;
    private LocalDate bis;
    private LocalDate erstellungsdatum;

    // Mieter
    private Long mieterId;
    private String mieterName;
    private String mieterStrasse;
    private String mieterPlzOrt;

    private List<NkRechnungZeileDTO> zeilen = new ArrayList<>();

    /** Summe der Zeilenbetraege. */
    private BigDecimal kostentotal = BigDecimal.ZERO;

    private BigDecimal akontoAnzahlMonate = BigDecimal.ZERO;
    private BigDecimal akontoBetragProMonat = BigDecimal.ZERO;
    private BigDecimal akontoKorrektur = BigDecimal.ZERO;
    private BigDecimal akontoTotal = BigDecimal.ZERO;

    /** {@code kostentotal − akontoTotal}, ungerundet. */
    private BigDecimal saldo = BigDecimal.ZERO;

    /** {@code endBetrag − saldo} — die Rundung auf 5 Rappen, auf der Rechnung ausgewiesen. */
    private BigDecimal rundung = BigDecimal.ZERO;

    /** Zahlbarer Betrag: {@code saldo} auf 5 Rappen gerundet. Bei Guthaben negativ. */
    private BigDecimal endBetrag = BigDecimal.ZERO;

    // Konfiguration (Einstellungen)
    private String zahlungsfrist;
    private String iban;
    private String stellerName;
    private String stellerStrasse;
    private String stellerPlzOrt;

    public NkRechnungDTO() {
    }

    /** {@code true}, wenn eine Forderung entsteht — Saldo groesser als 0 (FR-4). */
    public boolean isNachzahlung() {
        return endBetrag.compareTo(BigDecimal.ZERO) > 0;
    }

    public Long getAbrechnungId() {
        return abrechnungId;
    }

    public void setAbrechnungId(Long abrechnungId) {
        this.abrechnungId = abrechnungId;
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

    public LocalDate getErstellungsdatum() {
        return erstellungsdatum;
    }

    public void setErstellungsdatum(LocalDate erstellungsdatum) {
        this.erstellungsdatum = erstellungsdatum;
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

    public List<NkRechnungZeileDTO> getZeilen() {
        return zeilen;
    }

    public void setZeilen(List<NkRechnungZeileDTO> zeilen) {
        this.zeilen = zeilen;
    }

    public BigDecimal getKostentotal() {
        return kostentotal;
    }

    public void setKostentotal(BigDecimal kostentotal) {
        this.kostentotal = kostentotal;
    }

    public BigDecimal getAkontoAnzahlMonate() {
        return akontoAnzahlMonate;
    }

    public void setAkontoAnzahlMonate(BigDecimal akontoAnzahlMonate) {
        this.akontoAnzahlMonate = akontoAnzahlMonate;
    }

    public BigDecimal getAkontoBetragProMonat() {
        return akontoBetragProMonat;
    }

    public void setAkontoBetragProMonat(BigDecimal akontoBetragProMonat) {
        this.akontoBetragProMonat = akontoBetragProMonat;
    }

    public BigDecimal getAkontoKorrektur() {
        return akontoKorrektur;
    }

    public void setAkontoKorrektur(BigDecimal akontoKorrektur) {
        this.akontoKorrektur = akontoKorrektur;
    }

    public BigDecimal getAkontoTotal() {
        return akontoTotal;
    }

    public void setAkontoTotal(BigDecimal akontoTotal) {
        this.akontoTotal = akontoTotal;
    }

    public BigDecimal getSaldo() {
        return saldo;
    }

    public void setSaldo(BigDecimal saldo) {
        this.saldo = saldo;
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

    @Override
    public String toString() {
        return "NkRechnungDTO{abrechnungId=" + abrechnungId + ", mieterId=" + mieterId +
               ", mieterName='" + mieterName + "', zeilen=" + zeilen.size() +
               ", kostentotal=" + kostentotal + ", akontoTotal=" + akontoTotal +
               ", saldo=" + saldo + ", endBetrag=" + endBetrag + "}";
    }
}
