package ch.nacht.dto;

import java.math.BigDecimal;

/**
 * Eine Zeile auf der Nebenkostenrechnung
 * (Specs/Nebenkosten/RechnungenGenerieren.md, FR-3 und FR-8).
 *
 * <p>Uebernimmt {@link NkZeileDTO} des Mieterblocks, <b>ohne neu zu rechnen</b>. Die Felder sind
 * bereits fuer die Darstellung aufbereitet: {@code menge}, {@code betragProEinheit} und
 * {@code prozentsatz} sind {@code null}, wo die Positionsart sie nicht kennt — das Template gibt
 * dann eine leere Zelle aus, statt eine Null zu zeigen.
 *
 * <p>Diese Klasse ist die DataSource des Detail-Bands und wird von JasperReports ueber die Getter
 * gelesen; die Feldnamen im {@code .jrxml} muessen dazu passen.
 */
public class NkRechnungZeileDTO {

    private String bezeichnung;

    /**
     * <b>Uebersetzungsschluessel</b> der Mengeneinheit ({@code KWH}, {@code M3}, {@code CHF}, …),
     * nicht der fertige Text — das Template loest ihn wie bei der Quartalsrechnung ueber
     * {@code $P{TRANSLATIONS}.get($F{mengeneinheit})} auf. {@code null}, wo keine Einheit gilt.
     */
    private String mengeneinheit;

    private BigDecimal menge;
    private BigDecimal betragProEinheit;

    /** Prozentsatz 0–100 (Zuschlag oder Anteil); {@code null} bei den uebrigen Arten. */
    private BigDecimal prozentsatz;

    /** Zeilenbetrag, bereits auf zwei Nachkommastellen gerundet. */
    private BigDecimal betrag = BigDecimal.ZERO;

    public NkRechnungZeileDTO() {
    }

    public String getBezeichnung() {
        return bezeichnung;
    }

    public void setBezeichnung(String bezeichnung) {
        this.bezeichnung = bezeichnung;
    }

    public String getMengeneinheit() {
        return mengeneinheit;
    }

    public void setMengeneinheit(String mengeneinheit) {
        this.mengeneinheit = mengeneinheit;
    }

    public BigDecimal getMenge() {
        return menge;
    }

    public void setMenge(BigDecimal menge) {
        this.menge = menge;
    }

    public BigDecimal getBetragProEinheit() {
        return betragProEinheit;
    }

    public void setBetragProEinheit(BigDecimal betragProEinheit) {
        this.betragProEinheit = betragProEinheit;
    }

    public BigDecimal getProzentsatz() {
        return prozentsatz;
    }

    public void setProzentsatz(BigDecimal prozentsatz) {
        this.prozentsatz = prozentsatz;
    }

    public BigDecimal getBetrag() {
        return betrag;
    }

    public void setBetrag(BigDecimal betrag) {
        this.betrag = betrag;
    }

    @Override
    public String toString() {
        return "NkRechnungZeileDTO{bezeichnung='" + bezeichnung + "', menge=" + menge +
               ", betragProEinheit=" + betragProEinheit + ", prozentsatz=" + prozentsatz +
               ", betrag=" + betrag + "}";
    }
}
