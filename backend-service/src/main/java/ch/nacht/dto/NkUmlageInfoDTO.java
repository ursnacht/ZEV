package ch.nacht.dto;

import ch.nacht.entity.NkPositionsart;

import java.math.BigDecimal;

/**
 * Kontrollzahlen einer <b>verteilenden</b> Position — {@code UMLAGE} oder {@code ANTEIL}
 * (Specs/Nebenkosten/Abrechnung.md, FR-2 und FR-5).
 *
 * <p>Die beiden Abweichungen werden <b>getrennt</b> ausgewiesen, weil sie verschiedene Ursachen
 * haben: {@link #nichtVerteilt} ist der fachlich begründete Leerstandsanteil (Wohnung stand leer,
 * der Eigentümer trägt ihn), {@link #rundungsdifferenz} sind die wenigen Rappen aus dem Runden je
 * Zeile. Zusammengefasst wären sie nicht mehr erklärbar.
 */
public class NkUmlageInfoDTO {

    private Long positionId;
    private String bezeichnung;

    /** Erfasster Gesamtbetrag der Position. */
    private BigDecimal totalbetrag = BigDecimal.ZERO;

    /** Summe der auf die Mieter verteilten, bereits gerundeten Beträge. */
    private BigDecimal summeVerteilt = BigDecimal.ZERO;

    /** Leerstandsanteil: {@code Totalbetrag × (Nenner − Σ Tage) / Nenner}. */
    private BigDecimal nichtVerteilt = BigDecimal.ZERO;

    /** Rest aus dem Runden je Zeile; höchstens wenige Rappen, wird nicht ausgeglichen. */
    private BigDecimal rundungsdifferenz = BigDecimal.ZERO;

    /**
     * Art der Position — {@code UMLAGE} oder {@code ANTEIL}. Bestimmt, welche Kontrollzahlen
     * fachlich etwas aussagen: Der Leerstandsanteil gibt es nur bei der zeitanteiligen Umlage,
     * die Summe der Prozentsätze nur beim Anteil.
     */
    private NkPositionsart art;

    /**
     * Nur bei {@code ANTEIL}: Summe der je Mieter erfassten Prozentsätze.
     *
     * <p>Sollte 100 ergeben. Abweichungen werden angezeigt, aber nicht abgewiesen — eine halb
     * erfasste Abrechnung muss zwischenspeicherbar bleiben.
     */
    private BigDecimal summeProzent = BigDecimal.ZERO;

    public NkUmlageInfoDTO() {
    }

    public Long getPositionId() {
        return positionId;
    }

    public void setPositionId(Long positionId) {
        this.positionId = positionId;
    }

    public String getBezeichnung() {
        return bezeichnung;
    }

    public void setBezeichnung(String bezeichnung) {
        this.bezeichnung = bezeichnung;
    }

    public BigDecimal getTotalbetrag() {
        return totalbetrag;
    }

    public void setTotalbetrag(BigDecimal totalbetrag) {
        this.totalbetrag = totalbetrag;
    }

    public BigDecimal getSummeVerteilt() {
        return summeVerteilt;
    }

    public void setSummeVerteilt(BigDecimal summeVerteilt) {
        this.summeVerteilt = summeVerteilt;
    }

    public BigDecimal getNichtVerteilt() {
        return nichtVerteilt;
    }

    public void setNichtVerteilt(BigDecimal nichtVerteilt) {
        this.nichtVerteilt = nichtVerteilt;
    }

    public BigDecimal getRundungsdifferenz() {
        return rundungsdifferenz;
    }

    public void setRundungsdifferenz(BigDecimal rundungsdifferenz) {
        this.rundungsdifferenz = rundungsdifferenz;
    }

    public NkPositionsart getArt() {
        return art;
    }

    public void setArt(NkPositionsart art) {
        this.art = art;
    }

    public BigDecimal getSummeProzent() {
        return summeProzent;
    }

    public void setSummeProzent(BigDecimal summeProzent) {
        this.summeProzent = summeProzent;
    }
}
