package ch.nacht.dto;

import ch.nacht.entity.Verteilmodus;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class StatistikDTO {
    private LocalDate messwerteBisDate;
    private boolean datenVollstaendig;
    private List<String> fehlendeEinheiten = new ArrayList<>();
    private List<LocalDate> fehlendeTage = new ArrayList<>();
    private List<MonatsStatistikDTO> monate = new ArrayList<>();

    /**
     * Der gesamte gewählte Zeitraum, gerechnet wie ein Monat — <b>nicht</b> als Summe der Monate.
     *
     * <p>Jahr und Monat sind hier 0; das Panel trägt stattdessen von–bis.
     */
    private MonatsStatistikDTO gesamt;
    private double toleranz;
    private Verteilmodus verteilmodus;

    public StatistikDTO() {
    }

    public Verteilmodus getVerteilmodus() {
        return verteilmodus;
    }

    public void setVerteilmodus(Verteilmodus verteilmodus) {
        this.verteilmodus = verteilmodus;
    }

    public LocalDate getMesswerteBisDate() {
        return messwerteBisDate;
    }

    public void setMesswerteBisDate(LocalDate messwerteBisDate) {
        this.messwerteBisDate = messwerteBisDate;
    }

    public boolean isDatenVollstaendig() {
        return datenVollstaendig;
    }

    public void setDatenVollstaendig(boolean datenVollstaendig) {
        this.datenVollstaendig = datenVollstaendig;
    }

    public List<String> getFehlendeEinheiten() {
        return fehlendeEinheiten;
    }

    public void setFehlendeEinheiten(List<String> fehlendeEinheiten) {
        this.fehlendeEinheiten = fehlendeEinheiten;
    }

    public List<LocalDate> getFehlendeTage() {
        return fehlendeTage;
    }

    public void setFehlendeTage(List<LocalDate> fehlendeTage) {
        this.fehlendeTage = fehlendeTage;
    }

    public List<MonatsStatistikDTO> getMonate() {
        return monate;
    }

    public MonatsStatistikDTO getGesamt() {
        return gesamt;
    }

    public void setGesamt(MonatsStatistikDTO gesamt) {
        this.gesamt = gesamt;
    }

    public void setMonate(List<MonatsStatistikDTO> monate) {
        this.monate = monate;
    }

    public double getToleranz() {
        return toleranz;
    }

    public void setToleranz(double toleranz) {
        this.toleranz = toleranz;
    }
}
