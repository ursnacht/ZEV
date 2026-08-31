package ch.nacht.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Der berechnete Block eines Mieters (Specs/Nebenkosten/Abrechnung.md, FR-3 und FR-4).
 *
 * <p>Ein <b>positiver</b> {@link #saldo} ist eine Nachzahlung, ein negativer ein Guthaben. Die
 * Oberfläche benennt beides ausdrücklich, statt nur ein Vorzeichen zu zeigen.
 */
public class NkMieterAbrechnungDTO {

    private Long mieterId;
    private String name;

    /**
     * Miettage im Zeitraum, bereits mit der Zahl der Wohnungen multipliziert — der Zähler der
     * Umlage. Wird auch für die Prüfung {@code Σ Tage(i) <= Nenner} gebraucht (FR-2).
     */
    private long tage;

    /** {@code true}, wenn dem Mieter keine Wohnung zugeordnet ist — dann sind alle Tage 0. */
    private boolean ohneWohnung;

    private List<NkZeileDTO> zeilen = new ArrayList<>();

    /** Summe der Zeilenbeträge. */
    private BigDecimal kostentotal = BigDecimal.ZERO;

    private BigDecimal akontoAnzahlMonate = BigDecimal.ZERO;
    private BigDecimal akontoBetragProMonat = BigDecimal.ZERO;
    private BigDecimal akontoKorrektur = BigDecimal.ZERO;

    /** {@code Anzahl Monate × Betrag pro Monat + Korrektur}. */
    private BigDecimal akontoTotal = BigDecimal.ZERO;

    /** {@code Kostentotal − Akonto total}. */
    private BigDecimal saldo = BigDecimal.ZERO;

    public NkMieterAbrechnungDTO() {
    }

    /**
     * Personen je Wohnung dieses Mieters (Vorgabe 1) — die Maske zeigt sie als Eingabefeld, die
     * Rechnung nutzt sie als Faktor der Umlage pro Person.
     */
    private int anzahlPersonen = 1;

    /**
     * Zähler der Umlage pro Person: {@code tage x anzahlPersonen}, also Miettage mal Wohnungen mal
     * Personen. Sichtbar gemacht, damit sich der Anteil einer Personenumlage nachrechnen lässt.
     */
    private long personenTage;

    public int getAnzahlPersonen() {
        return anzahlPersonen;
    }

    public void setAnzahlPersonen(int anzahlPersonen) {
        this.anzahlPersonen = anzahlPersonen;
    }

    public long getPersonenTage() {
        return personenTage;
    }

    public void setPersonenTage(long personenTage) {
        this.personenTage = personenTage;
    }

    public Long getMieterId() {
        return mieterId;
    }

    public void setMieterId(Long mieterId) {
        this.mieterId = mieterId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getTage() {
        return tage;
    }

    public void setTage(long tage) {
        this.tage = tage;
    }

    public boolean isOhneWohnung() {
        return ohneWohnung;
    }

    public void setOhneWohnung(boolean ohneWohnung) {
        this.ohneWohnung = ohneWohnung;
    }

    public List<NkZeileDTO> getZeilen() {
        return zeilen;
    }

    public void setZeilen(List<NkZeileDTO> zeilen) {
        this.zeilen = zeilen != null ? zeilen : new ArrayList<>();
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
}
