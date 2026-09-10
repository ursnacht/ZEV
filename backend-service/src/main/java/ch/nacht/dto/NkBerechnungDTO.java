package ch.nacht.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Ergebnis des Berechnungsservice: die Blöcke aller Mieter und die Kontrollzahlen je
 * Umlageposition (Specs/Nebenkosten/Abrechnung.md, FR-2 bis FR-5).
 */
public class NkBerechnungDTO {

    /** Nenner der Umlage: {@code Anzahl Wohnungen × Tage im Zeitraum}. */
    private long nenner;

    /** Summe der Miettage aller Mieter; muss {@code <= nenner} sein (FR-2). */
    private long summeTage;

    /** Nenner der Umlage pro Person: {@code Anzahl Personen x Tage im Zeitraum} (FR-2). */
    private long nennerPerson;

    /**
     * Summe {@code Miettage x Wohnungen x Personen} aller Mieter; muss {@code <= nennerPerson}
     * sein — dieselbe Regel wie bei den Wohnungen, nur mit Köpfen gewichtet.
     */
    private long summePersonenTage;

    private List<NkMieterAbrechnungDTO> mieter = new ArrayList<>();

    private List<NkPositionSummeDTO> positionSummen = new ArrayList<>();

    /**
     * Summe der {@code summeKosten} aller Zeilen der Positionsübersicht.
     *
     * <p><b>Muss dem Kostentotal aller Mieter entsprechen</b> (FR-10): Beide zählen dieselben
     * Zeilenbeträge, nur einmal je Position und einmal je Mieter gebündelt. Weicht die eine von
     * der anderen ab, fehlt der Übersicht eine Quelle — genau dafür steht die Zahl da.
     */
    private BigDecimal summeKosten = BigDecimal.ZERO;

    /**
     * Summe der {@code nichtVerteilt} aller Zeilen der Positionsübersicht — der Betrag, der
     * <b>keinem Mieter</b> belastet wird (FR-10).
     *
     * <p>Er entsteht bei Leerstand: Deckt die Summe der Miettage den Nenner nicht, bleibt der
     * entsprechende Anteil einer Umlage liegen; bei {@code ANTEIL} entsteht er, wenn die erfassten
     * Prozente nicht 100 % ergeben. In der Praxis trägt ihn der Eigentümer, und die Summe sagt,
     * wie viel das über alle Positionen zusammen ist.
     *
     * <p><b>Nie {@code null}, auch wenn keine Position einen Betrag beisteuert</b> — anders als in
     * der einzelnen Zeile. Dort hiesse eine {@code 0.00} bei einer Verbrauchsposition „unverteilt
     * nichts", obwohl die Art gar nichts unverteilt lassen kann; hier ist die {@code 0.00} eine
     * gerechnete Aussage: Es blieb nichts liegen. Zeilen ohne den Begriff werden übersprungen.
     */
    private BigDecimal summeNichtVerteilt = BigDecimal.ZERO;

    public NkBerechnungDTO() {
    }

    public long getNenner() {
        return nenner;
    }

    public void setNenner(long nenner) {
        this.nenner = nenner;
    }

    public long getSummeTage() {
        return summeTage;
    }

    public long getNennerPerson() {
        return nennerPerson;
    }

    public void setNennerPerson(long nennerPerson) {
        this.nennerPerson = nennerPerson;
    }

    public long getSummePersonenTage() {
        return summePersonenTage;
    }

    public void setSummePersonenTage(long summePersonenTage) {
        this.summePersonenTage = summePersonenTage;
    }

    public void setSummeTage(long summeTage) {
        this.summeTage = summeTage;
    }

    public List<NkMieterAbrechnungDTO> getMieter() {
        return mieter;
    }

    public void setMieter(List<NkMieterAbrechnungDTO> mieter) {
        this.mieter = mieter != null ? mieter : new ArrayList<>();
    }

    public List<NkPositionSummeDTO> getPositionSummen() {
        return positionSummen;
    }

    public void setPositionSummen(List<NkPositionSummeDTO> positionSummen) {
        this.positionSummen = positionSummen != null ? positionSummen : new ArrayList<>();
    }

    public BigDecimal getSummeKosten() {
        return summeKosten;
    }

    public void setSummeKosten(BigDecimal summeKosten) {
        this.summeKosten = summeKosten;
    }

    public BigDecimal getSummeNichtVerteilt() {
        return summeNichtVerteilt;
    }

    public void setSummeNichtVerteilt(BigDecimal summeNichtVerteilt) {
        this.summeNichtVerteilt = summeNichtVerteilt;
    }
}
