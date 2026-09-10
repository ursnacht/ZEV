package ch.nacht.dto;

import ch.nacht.entity.Mengeneinheit;
import ch.nacht.entity.NkPositionsart;

import java.math.BigDecimal;

/**
 * Zusammenstellung <b>einer</b> Position über alle Mieter — eine Zeile der Positionsübersicht
 * (Specs/Nebenkosten/Abrechnung.md, FR-2, FR-5 und FR-10).
 *
 * <p>Es gibt eine Zeile je allgemeiner Position, gleich welcher Art, plus <b>eine</b> Zeile für
 * alle Zusatzpositionen zusammen ({@link #zusatz}). Erst damit ergibt die Summe der
 * {@link #summeKosten} über alle Zeilen das Kostentotal aller Mieter — die Mieterzeilen speisen
 * sich aus genau diesen beiden Quellen.
 *
 * <p><b>Die Felder sind bewusst {@code null}-bar</b> und nicht mit {@code 0} vorbelegt: Ein
 * Totalbetrag von {@code 0.00} bei einer Verbrauchsposition sähe aus wie ein vergessener Wert,
 * obwohl die Art gar keinen kennt. Die Maske lässt eine Zelle leer, statt eine Null zu behaupten.
 *
 * <p>Die beiden Abweichungen werden <b>getrennt</b> ausgewiesen, weil sie verschiedene Ursachen
 * haben: {@link #nichtVerteilt} ist der fachlich begründete Leerstandsanteil (Wohnung stand leer,
 * der Eigentümer trägt ihn), {@link #rundungsdifferenz} sind die wenigen Rappen aus dem Runden je
 * Zeile. Zusammengefasst wären sie nicht mehr erklärbar.
 */
public class NkPositionSummeDTO {

    private Long positionId;
    private String bezeichnung;

    /** Erfasster Gesamtbetrag; {@code null} bei {@code VERBRAUCH}, {@code ZUSCHLAG} und Zusatz. */
    private BigDecimal totalbetrag;

    /**
     * Summe der Mengen über alle Mieter — verteilt bei {@code UMLAGE}, erfasst bei
     * {@code VERBRAUCH}.
     *
     * <p>{@code null}, wo es keine Menge gibt: {@code ZUSCHLAG} kennt keine, bei {@code ANTEIL}
     * steht in derselben Spalte der Prozentsatz (s. {@link #summeProzent}), und bei den
     * Zusatzpositionen auch dann, wenn sie <b>verschiedene</b> Mengeneinheiten mischen — „2 Stück
     * plus 3 m³" ist keine Menge.
     */
    private BigDecimal summeMenge;

    /** Mengeneinheit zur {@link #summeMenge}; {@code null}, wenn es keine eindeutige gibt. */
    private Mengeneinheit einheit;

    /**
     * Summe der Beträge, die den Mietern für diese Position belastet werden — bereits gerundete
     * Zeilenbeträge. Über alle Zeilen summiert ergibt das das Kostentotal aller Mieter.
     */
    private BigDecimal summeKosten = BigDecimal.ZERO;

    /**
     * Leerstandsanteil: {@code Totalbetrag × (Nenner − Σ Tage) / Nenner}.
     *
     * <p>{@code null} bei den Arten, die nichts verteilen — dort gibt es keinen Rest.
     */
    private BigDecimal nichtVerteilt;

    /** Rest aus dem Runden je Zeile; höchstens wenige Rappen, wird nicht ausgeglichen. */
    private BigDecimal rundungsdifferenz;

    /**
     * Art der Position; {@code null} in der Zusatz-Zeile, die keine einzelne Position ist.
     * Bestimmt, welche Kontrollzahlen fachlich etwas aussagen: Der Leerstandsanteil gibt es nur
     * bei der zeitanteiligen Umlage, die Summe der Prozentsätze nur beim Anteil.
     */
    private NkPositionsart art;

    /**
     * Nur bei {@code ANTEIL}: Summe der je Mieter erfassten Prozentsätze.
     *
     * <p>Sollte 100 ergeben. Abweichungen werden angezeigt, aber nicht abgewiesen — eine halb
     * erfasste Abrechnung muss zwischenspeicherbar bleiben.
     */
    private BigDecimal summeProzent;

    /**
     * Sammelzeile <b>aller</b> Zusatzpositionen statt einer einzelnen Position.
     *
     * <p>Eine Zeile je Zusatzposition wäre die Mieterliste ein zweites Mal — dieselbe Bezeichnung
     * kommt bei mehreren Mietern vor, und die Übersicht soll Positionen zeigen, nicht Mieter.
     *
     * <p><b>Die Beschriftung liefert die Maske</b>, nicht das Backend: Sie ist ein Anzeigetext und
     * gehört damit zu den Übersetzungen — dieselbe Aufteilung wie beim Zusatz „(Kopie)".
     */
    private boolean zusatz;

    public NkPositionSummeDTO() {
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

    public BigDecimal getSummeKosten() {
        return summeKosten;
    }

    public void setSummeKosten(BigDecimal summeKosten) {
        this.summeKosten = summeKosten;
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

    public BigDecimal getSummeMenge() {
        return summeMenge;
    }

    public void setSummeMenge(BigDecimal summeMenge) {
        this.summeMenge = summeMenge;
    }

    public Mengeneinheit getEinheit() {
        return einheit;
    }

    public void setEinheit(Mengeneinheit einheit) {
        this.einheit = einheit;
    }

    public boolean isZusatz() {
        return zusatz;
    }

    public void setZusatz(boolean zusatz) {
        this.zusatz = zusatz;
    }

    public BigDecimal getSummeProzent() {
        return summeProzent;
    }

    public void setSummeProzent(BigDecimal summeProzent) {
        this.summeProzent = summeProzent;
    }
}
