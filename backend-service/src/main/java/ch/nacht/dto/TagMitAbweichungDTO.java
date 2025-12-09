package ch.nacht.dto;

import java.time.LocalDate;

public class TagMitAbweichungDTO {
    private LocalDate datum;
    private String abweichungstyp;
    private Double differenz;

    public TagMitAbweichungDTO() {
    }

    public TagMitAbweichungDTO(LocalDate datum, String abweichungstyp, Double differenz) {
        this.datum = datum;
        this.abweichungstyp = abweichungstyp;
        this.differenz = differenz;
    }

    public LocalDate getDatum() {
        return datum;
    }

    public void setDatum(LocalDate datum) {
        this.datum = datum;
    }

    public String getAbweichungstyp() {
        return abweichungstyp;
    }

    public void setAbweichungstyp(String abweichungstyp) {
        this.abweichungstyp = abweichungstyp;
    }

    public Double getDifferenz() {
        return differenz;
    }

    public void setDifferenz(Double differenz) {
        this.differenz = differenz;
    }
}
