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
