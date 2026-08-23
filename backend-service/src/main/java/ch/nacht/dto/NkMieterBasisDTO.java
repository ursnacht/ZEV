package ch.nacht.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Eingabe der Berechnung je Mieter (Specs/Nebenkosten/Abrechnung.md, FR-2 und FR-4).
 *
 * <p>Bewusst kein {@code Mieter}-Entity: Der Berechnungsservice soll ohne Datenbank prüfbar sein
 * und braucht genau diese fünf Angaben. Die Miettage und die anteiligen Monate rechnet er selbst
 * aus — dort steckt die Kalenderarithmetik, die getestet werden muss.
 */
public class NkMieterBasisDTO {

    private Long mieterId;
    private String name;
    private LocalDate mietbeginn;
    private LocalDate mietende;

    /**
     * Anzahl der Wohnungen dieses Mieters ({@code CONSUMER}-Einheiten).
     *
     * <p>Multipliziert seine Miettage: Wer zwei Wohnungen mietet, trägt zwei Anteile (FR-2).
     * <b>Nur Wohnungen</b> zählen — eine Ladestation ist keine und würde den Anteil verdoppeln,
     * obwohl der Nenner sie nicht kennt.
     */
    private int anzahlWohnungen;

    /** Stammdatum des Mieters; Vorbelegung des Akonto-Monatsbetrags, {@code null} zulässig. */
    private BigDecimal akontoProMonat;

    public NkMieterBasisDTO() {
    }

    public NkMieterBasisDTO(Long mieterId, String name, LocalDate mietbeginn, LocalDate mietende,
                            int anzahlWohnungen, BigDecimal akontoProMonat) {
        this.mieterId = mieterId;
        this.name = name;
        this.mietbeginn = mietbeginn;
        this.mietende = mietende;
        this.anzahlWohnungen = anzahlWohnungen;
        this.akontoProMonat = akontoProMonat;
    }

    public Long getMieterId() {
        return mieterId;
    }

    public void setMieterId(Long mieterId) {
        this.mieterId = mieterId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LocalDate getMietbeginn() {
        return mietbeginn;
    }

    public void setMietbeginn(LocalDate mietbeginn) {
        this.mietbeginn = mietbeginn;
    }

    public LocalDate getMietende() {
        return mietende;
    }

    public void setMietende(LocalDate mietende) {
        this.mietende = mietende;
    }

    public int getAnzahlWohnungen() {
        return anzahlWohnungen;
    }

    public void setAnzahlWohnungen(int anzahlWohnungen) {
        this.anzahlWohnungen = anzahlWohnungen;
    }

    public BigDecimal getAkontoProMonat() {
        return akontoProMonat;
    }

    public void setAkontoProMonat(BigDecimal akontoProMonat) {
        this.akontoProMonat = akontoProMonat;
    }
}
