package ch.nacht.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO for debitor entries including joined mieter and einheit names.
 */
public class DebitorDTO {

    private Long id;
    private Long mieterId;
    private String mieterName;
    private String einheitName;
    private BigDecimal betrag;
    private LocalDate datumVon;
    private LocalDate datumBis;
    private LocalDate zahldatum;

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

    public String getMieterName() {
        return mieterName;
    }

    public void setMieterName(String mieterName) {
        this.mieterName = mieterName;
    }

    public String getEinheitName() {
        return einheitName;
    }

    public void setEinheitName(String einheitName) {
        this.einheitName = einheitName;
    }

    public BigDecimal getBetrag() {
        return betrag;
    }

    public void setBetrag(BigDecimal betrag) {
        this.betrag = betrag;
    }

    public LocalDate getDatumVon() {
        return datumVon;
    }

    public void setDatumVon(LocalDate datumVon) {
        this.datumVon = datumVon;
    }

    public LocalDate getDatumBis() {
        return datumBis;
    }

    public void setDatumBis(LocalDate datumBis) {
        this.datumBis = datumBis;
    }

    public LocalDate getZahldatum() {
        return zahldatum;
    }

    public void setZahldatum(LocalDate zahldatum) {
        this.zahldatum = zahldatum;
    }
}
