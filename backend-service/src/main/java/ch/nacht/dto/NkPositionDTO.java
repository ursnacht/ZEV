package ch.nacht.dto;

import ch.nacht.entity.Mengeneinheit;
import ch.nacht.entity.NkPositionsart;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Allgemeine Position beim Speichern und Lesen einer Abrechnung
 * (Specs/Nebenkosten/Abrechnung.md, FR-2 und FR-6).
 *
 * <p>Die {@link #reihenfolge} wird beim Speichern <b>nicht</b> vom Client übernommen, sondern aus
 * der Position in der Liste neu vergeben — genau so, wie die Maske sie per Drag &amp; Drop
 * hergibt (FR-7).
 */
public class NkPositionDTO {

    /** {@code null} bei einer neu angelegten Position. */
    private Long id;

    private NkPositionsart art;
    private String bezeichnung;
    private Integer reihenfolge;
    private Mengeneinheit einheit;
    private BigDecimal totalbetrag;
    private BigDecimal gesamtmenge;
    private BigDecimal betragProEinheit;
    private BigDecimal prozentsatz;

    /** Nur bei VERBRAUCH gefüllt: die je Mieter erfassten Mengen. */
    private List<NkVerbrauchDTO> verbraeuche = new ArrayList<>();

    public NkPositionDTO() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public NkPositionsart getArt() {
        return art;
    }

    public void setArt(NkPositionsart art) {
        this.art = art;
    }

    public String getBezeichnung() {
        return bezeichnung;
    }

    public void setBezeichnung(String bezeichnung) {
        this.bezeichnung = bezeichnung;
    }

    public Integer getReihenfolge() {
        return reihenfolge;
    }

    public void setReihenfolge(Integer reihenfolge) {
        this.reihenfolge = reihenfolge;
    }

    public Mengeneinheit getEinheit() {
        return einheit;
    }

    public void setEinheit(Mengeneinheit einheit) {
        this.einheit = einheit;
    }

    public BigDecimal getTotalbetrag() {
        return totalbetrag;
    }

    public void setTotalbetrag(BigDecimal totalbetrag) {
        this.totalbetrag = totalbetrag;
    }

    public BigDecimal getGesamtmenge() {
        return gesamtmenge;
    }

    public void setGesamtmenge(BigDecimal gesamtmenge) {
        this.gesamtmenge = gesamtmenge;
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

    public List<NkVerbrauchDTO> getVerbraeuche() {
        return verbraeuche;
    }

    public void setVerbraeuche(List<NkVerbrauchDTO> verbraeuche) {
        this.verbraeuche = verbraeuche != null ? verbraeuche : new ArrayList<>();
    }
}
