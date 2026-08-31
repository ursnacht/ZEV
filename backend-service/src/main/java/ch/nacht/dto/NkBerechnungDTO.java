package ch.nacht.dto;

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

    private List<NkUmlageInfoDTO> umlagen = new ArrayList<>();

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

    public List<NkUmlageInfoDTO> getUmlagen() {
        return umlagen;
    }

    public void setUmlagen(List<NkUmlageInfoDTO> umlagen) {
        this.umlagen = umlagen != null ? umlagen : new ArrayList<>();
    }
}
