package ch.nacht.dto;

import java.math.BigDecimal;

/**
 * Akonto-Angaben eines Mieters zu einer Abrechnung (Specs/Nebenkosten/Abrechnung.md, FR-4).
 *
 * <p>Alle drei Werte sind erfasst und nicht abgeleitet: Vorgeschlagen werden sie aus Mietdauer und
 * Stammdatum, überschrieben werden dürfen sie trotzdem — eine Zahlung, die anders geflossen ist,
 * muss erfassbar bleiben.
 */
public class NkAkontoDTO {

    /** {@code null}, solange nichts erfasst ist — dann zeigt die Maske den Vorschlag. */
    private Long id;

    private Long mieterId;
    private BigDecimal anzahlMonate;
    private BigDecimal betragProMonat;
    private BigDecimal korrektur;

    public NkAkontoDTO() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getMieterId() {
        return mieterId;
    }

    public void setMieterId(Long mieterId) {
        this.mieterId = mieterId;
    }

    public BigDecimal getAnzahlMonate() {
        return anzahlMonate;
    }

    public void setAnzahlMonate(BigDecimal anzahlMonate) {
        this.anzahlMonate = anzahlMonate;
    }

    public BigDecimal getBetragProMonat() {
        return betragProMonat;
    }

    public void setBetragProMonat(BigDecimal betragProMonat) {
        this.betragProMonat = betragProMonat;
    }

    public BigDecimal getKorrektur() {
        return korrektur;
    }

    public void setKorrektur(BigDecimal korrektur) {
        this.korrektur = korrektur;
    }
}
