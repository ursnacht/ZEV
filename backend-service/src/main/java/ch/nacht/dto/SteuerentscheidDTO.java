package ch.nacht.dto;

import ch.nacht.entity.Steuerregel;
import ch.nacht.entity.Steuerzustand;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Ein Steuerentscheid für die Anzeige (Specs/Einspeisesteuerung.md, FR-4).
 *
 * <p><b>{@link #zeit} ist Ortszeit</b> (Europe/Zurich) — gespeichert wird UTC, umgerechnet wird
 * erst hier. Dieselbe Aufteilung wie bei {@code PreiszeitreihePunktDTO}: Die Datenbank hält den
 * Zeitpunkt eindeutig, die Anzeige zeigt ihn lesbar.
 *
 * <p>Die Felder {@link #schwellwert} und {@link #speicherwert} sind die <b>beim Entscheid
 * geltenden</b> Werte, nicht die heutigen. Ohne sie liesse sich ein alter Entscheid nach einer
 * Änderung der Konfiguration nicht mehr erklären.
 */
public class SteuerentscheidDTO {

    private LocalDateTime zeit;
    private BigDecimal preis;
    private BigDecimal preisTiefRest;
    private BigDecimal produktion;
    private BigDecimal verbrauch;
    private BigDecimal ueberschuss;
    private Steuerregel regel;
    private Steuerzustand batterieladung;
    private Steuerzustand einspeisung;
    private BigDecimal schwellwert;
    private BigDecimal speicherwert;

    public SteuerentscheidDTO() {
    }

    public LocalDateTime getZeit() {
        return zeit;
    }

    public void setZeit(LocalDateTime zeit) {
        this.zeit = zeit;
    }

    public BigDecimal getPreis() {
        return preis;
    }

    public void setPreis(BigDecimal preis) {
        this.preis = preis;
    }

    public BigDecimal getPreisTiefRest() {
        return preisTiefRest;
    }

    public void setPreisTiefRest(BigDecimal preisTiefRest) {
        this.preisTiefRest = preisTiefRest;
    }

    public BigDecimal getProduktion() {
        return produktion;
    }

    public void setProduktion(BigDecimal produktion) {
        this.produktion = produktion;
    }

    public BigDecimal getVerbrauch() {
        return verbrauch;
    }

    public void setVerbrauch(BigDecimal verbrauch) {
        this.verbrauch = verbrauch;
    }

    public BigDecimal getUeberschuss() {
        return ueberschuss;
    }

    public void setUeberschuss(BigDecimal ueberschuss) {
        this.ueberschuss = ueberschuss;
    }

    public Steuerregel getRegel() {
        return regel;
    }

    public void setRegel(Steuerregel regel) {
        this.regel = regel;
    }

    public Steuerzustand getBatterieladung() {
        return batterieladung;
    }

    public void setBatterieladung(Steuerzustand batterieladung) {
        this.batterieladung = batterieladung;
    }

    public Steuerzustand getEinspeisung() {
        return einspeisung;
    }

    public void setEinspeisung(Steuerzustand einspeisung) {
        this.einspeisung = einspeisung;
    }

    public BigDecimal getSchwellwert() {
        return schwellwert;
    }

    public void setSchwellwert(BigDecimal schwellwert) {
        this.schwellwert = schwellwert;
    }

    public BigDecimal getSpeicherwert() {
        return speicherwert;
    }

    public void setSpeicherwert(BigDecimal speicherwert) {
        this.speicherwert = speicherwert;
    }
}
