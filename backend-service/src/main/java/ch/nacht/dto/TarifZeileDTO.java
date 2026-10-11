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

    /**
     * Reihenfolge des Tarifs dieser Zeile (Specs/Tarifverwaltung.md, FR-4) - nur zum Sortieren
     * der Zeilen im {@code RechnungService}, nicht Teil der API.
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    private int reihenfolge;

    /**
     * Uebersetzungs-Key der Bezugsgroesse des Preises ("CHF / Jahr"), wenn sie von der
     * Mengeneinheit abweicht - beim Jahrespreis (Specs/Tarifverwaltung.md, FR-6). Sonst
     * {@code null}: Die Rechnung zeigt dann "CHF / <Mengeneinheit>".
     */
    private String preisEinheit;

    /**
     * Uebersetzungs-Key der Einheit hinter der Menge ("123 kWh", "3 Monate", "1 Monat"), gesetzt
     * vom {@code RechnungService} fuer jede Zeile (Specs/RechnungenGenerieren.md). {@code null}:
     * nur die Zahl.
     */
    private String mengeEinheit;

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

    public String getPreisEinheit() {
        return preisEinheit;
    }

    public void setPreisEinheit(String preisEinheit) {
        this.preisEinheit = preisEinheit;
    }

    public String getMengeEinheit() {
        return mengeEinheit;
    }

    public void setMengeEinheit(String mengeEinheit) {
        this.mengeEinheit = mengeEinheit;
    }

    public int getReihenfolge() {
        return reihenfolge;
    }

    public void setReihenfolge(int reihenfolge) {
        this.reihenfolge = reihenfolge;
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
