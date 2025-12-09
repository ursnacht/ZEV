package ch.nacht.dto;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class StatistikDTO {
    private LocalDate messwerteBisDate;
    private boolean datenVollstaendig;
    private List<String> fehlendeEinheiten = new ArrayList<>();
    private List<LocalDate> fehlendeTage = new ArrayList<>();
    private List<MonatsStatistikDTO> monate = new ArrayList<>();

    public StatistikDTO() {
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

    public void setMonate(List<MonatsStatistikDTO> monate) {
        this.monate = monate;
    }
}
