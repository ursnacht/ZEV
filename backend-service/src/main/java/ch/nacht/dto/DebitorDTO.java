package ch.nacht.dto;

import ch.nacht.entity.Debitorherkunft;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO for debitor entries including joined mieter and einheit names.
 *
 * <p>{@link #herkunft} ist als Enum typisiert und nicht als {@code String}: Ein unbekannter Wert
 * scheitert damit schon an Jackson und ergibt eine {@code 400}, statt bis in den
 * CHECK-Constraint zu laufen und als {@code 500} zurueckzukommen. Fehlt das Feld ganz, bleibt es
 * {@code null} und {@code DebitorService} setzt {@code ZEV} (Rueckwaertskompatibilitaet).
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
    private Debitorherkunft herkunft;

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

    public Debitorherkunft getHerkunft() {
        return herkunft;
    }

    public void setHerkunft(Debitorherkunft herkunft) {
        this.herkunft = herkunft;
    }
}
