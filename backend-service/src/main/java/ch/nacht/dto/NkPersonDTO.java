package ch.nacht.dto;

/**
 * Anzahl Personen je Wohnung eines Mieters (Specs/Nebenkosten/Abrechnung.md, FR-2).
 *
 * <p>Zähler der Umlage pro Person. Fehlt der Eintrag, gilt <b>1</b> — dann rechnet eine Umlage pro
 * Person genau wie eine Umlage pro Wohnung.
 */
public class NkPersonDTO {

    /** {@code null}, solange nichts erfasst ist — dann zeigt die Maske die Vorgabe 1. */
    private Long id;

    private Long mieterId;
    private Integer anzahlPersonen;

    public NkPersonDTO() {
    }

    public NkPersonDTO(Long mieterId, Integer anzahlPersonen) {
        this.mieterId = mieterId;
        this.anzahlPersonen = anzahlPersonen;
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

    public Integer getAnzahlPersonen() {
        return anzahlPersonen;
    }

    public void setAnzahlPersonen(Integer anzahlPersonen) {
        this.anzahlPersonen = anzahlPersonen;
    }
}
