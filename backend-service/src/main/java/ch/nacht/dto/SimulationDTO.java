package ch.nacht.dto;

import ch.nacht.entity.Steuerregel;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * Ergebnis einer Rückrechnung der Steuerregel (Specs/Einspeisesteuerung.md, FR-6).
 *
 * <p><b>Bewusst ohne Ertrag in Franken.</b> Ein belastbarer Vergleich „mit Regel gegen ohne"
 * bräuchte ein Batteriemodell und gemessene Lade-/Entladedaten — beides fehlt heute. Eine Zahl in
 * Franken, die auf geratener Kapazität beruht, sähe belastbarer aus als sie ist. Die Grössen hier
 * genügen, um den Schwellwert einzugrenzen: Sie zeigen, <b>wie oft</b> und <b>wie lange</b> die
 * Regel greift und <b>wie viel Energie</b> sie bewegt.
 *
 * <p><b>Gezählt werden nur Intervalle mit Überschuss</b> — sonst stünde {@code PREIS_NEGATIV}
 * vielfach über den Fällen, in denen die Steuerung tatsächlich etwas entschieden hat.
 */
public class SimulationDTO {

    private LocalDate von;
    private LocalDate bis;
    private BigDecimal schwellwert;
    private BigDecimal speicherwert;
    /** Der erprobte Mindest-Preisabstand — gehoert wie die Schwellen zum Ergebnis. */
    private BigDecimal mindestAbstand;

    /** Ausgewertete Tage (Tage mit Preisen). */
    private int tage;

    /** Ausgewertete Intervalle — nur solche mit Überschuss. */
    private int intervalle;

    /** Auslösungen je Regel. */
    private Map<Steuerregel, Integer> jeRegel;

    private BigDecimal stundenLadungGesperrt;
    private BigDecimal stundenEinspeisungGesperrt;

    /**
     * Überschuss-kWh in Intervallen mit gesperrter Ladung — die Energie, die die Regel vom
     * Speicher weg in die Einspeisung lenkt.
     */
    private BigDecimal energieVerschoben;

    public SimulationDTO() {
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

    public BigDecimal getSchwellwert() {
        return schwellwert;
    }

    public void setSchwellwert(BigDecimal schwellwert) {
        this.schwellwert = schwellwert;
    }

    public BigDecimal getSpeicherwert() {
        return speicherwert;
    }

    public BigDecimal getMindestAbstand() {
        return mindestAbstand;
    }

    public void setMindestAbstand(BigDecimal mindestAbstand) {
        this.mindestAbstand = mindestAbstand;
    }

    public void setSpeicherwert(BigDecimal speicherwert) {
        this.speicherwert = speicherwert;
    }

    public int getTage() {
        return tage;
    }

    public void setTage(int tage) {
        this.tage = tage;
    }

    public int getIntervalle() {
        return intervalle;
    }

    public void setIntervalle(int intervalle) {
        this.intervalle = intervalle;
    }

    public Map<Steuerregel, Integer> getJeRegel() {
        return jeRegel;
    }

    public void setJeRegel(Map<Steuerregel, Integer> jeRegel) {
        this.jeRegel = jeRegel;
    }

    public BigDecimal getStundenLadungGesperrt() {
        return stundenLadungGesperrt;
    }

    public void setStundenLadungGesperrt(BigDecimal stundenLadungGesperrt) {
        this.stundenLadungGesperrt = stundenLadungGesperrt;
    }

    public BigDecimal getStundenEinspeisungGesperrt() {
        return stundenEinspeisungGesperrt;
    }

    public void setStundenEinspeisungGesperrt(BigDecimal stundenEinspeisungGesperrt) {
        this.stundenEinspeisungGesperrt = stundenEinspeisungGesperrt;
    }

    public BigDecimal getEnergieVerschoben() {
        return energieVerschoben;
    }

    public void setEnergieVerschoben(BigDecimal energieVerschoben) {
        this.energieVerschoben = energieVerschoben;
    }
}
