package ch.nacht.dto;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class MonatsStatistikDTO {
    // Summen pro Einheit
    private List<EinheitSummenDTO> einheitSummen = new ArrayList<>();
    private int jahr;
    private int monat;
    private LocalDate von;
    private LocalDate bis;
    private boolean datenVollstaendig;
    private List<String> fehlendeEinheiten = new ArrayList<>();
    private List<LocalDate> fehlendeTage = new ArrayList<>();

    // Summen
    private Double summeProducerTotal;
    private Double summeConsumerTotal;
    private Double summeProducerZev;
    private Double summeConsumerZev;
    private Double summeConsumerZevCalculated;

    // Vergleiche
    private boolean summenCDGleich;
    private Double differenzCD;
    private boolean summenCEGleich;
    private Double differenzCE;
    private boolean summenDEGleich;
    private Double differenzDE;

    // Tage mit Abweichungen
    private List<TagMitAbweichungDTO> tageAbweichungen = new ArrayList<>();

    public MonatsStatistikDTO() {
    }

    public int getJahr() {
        return jahr;
    }

    public void setJahr(int jahr) {
        this.jahr = jahr;
    }

    public int getMonat() {
        return monat;
    }

    public void setMonat(int monat) {
        this.monat = monat;
    }

    public LocalDate getVon() {
        return von;
    }

    public void setVon(LocalDate von) {
        this.von = von;
    }

    public LocalDate getBis() {
        return bis;
    }

    public void setBis(LocalDate bis) {
        this.bis = bis;
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

    public Double getSummeProducerTotal() {
        return summeProducerTotal;
    }

    public void setSummeProducerTotal(Double summeProducerTotal) {
        this.summeProducerTotal = summeProducerTotal;
    }

    public Double getSummeConsumerTotal() {
        return summeConsumerTotal;
    }

    public void setSummeConsumerTotal(Double summeConsumerTotal) {
        this.summeConsumerTotal = summeConsumerTotal;
    }

    public Double getSummeProducerZev() {
        return summeProducerZev;
    }

    public void setSummeProducerZev(Double summeProducerZev) {
        this.summeProducerZev = summeProducerZev;
    }

    public Double getSummeConsumerZev() {
        return summeConsumerZev;
    }

    public void setSummeConsumerZev(Double summeConsumerZev) {
        this.summeConsumerZev = summeConsumerZev;
    }

    public Double getSummeConsumerZevCalculated() {
        return summeConsumerZevCalculated;
    }

    public void setSummeConsumerZevCalculated(Double summeConsumerZevCalculated) {
        this.summeConsumerZevCalculated = summeConsumerZevCalculated;
    }

    public boolean isSummenCDGleich() {
        return summenCDGleich;
    }

    public void setSummenCDGleich(boolean summenCDGleich) {
        this.summenCDGleich = summenCDGleich;
    }

    public Double getDifferenzCD() {
        return differenzCD;
    }

    public void setDifferenzCD(Double differenzCD) {
        this.differenzCD = differenzCD;
    }

    public boolean isSummenCEGleich() {
        return summenCEGleich;
    }

    public void setSummenCEGleich(boolean summenCEGleich) {
        this.summenCEGleich = summenCEGleich;
    }

    public Double getDifferenzCE() {
        return differenzCE;
    }

    public void setDifferenzCE(Double differenzCE) {
        this.differenzCE = differenzCE;
    }

    public boolean isSummenDEGleich() {
        return summenDEGleich;
    }

    public void setSummenDEGleich(boolean summenDEGleich) {
        this.summenDEGleich = summenDEGleich;
    }

    public Double getDifferenzDE() {
        return differenzDE;
    }

    public void setDifferenzDE(Double differenzDE) {
        this.differenzDE = differenzDE;
    }

    public List<TagMitAbweichungDTO> getTageAbweichungen() {
        return tageAbweichungen;
    }

    public void setTageAbweichungen(List<TagMitAbweichungDTO> tageAbweichungen) {
        this.tageAbweichungen = tageAbweichungen;
    }

    public List<EinheitSummenDTO> getEinheitSummen() {
        return einheitSummen;
    }

    public void setEinheitSummen(List<EinheitSummenDTO> einheitSummen) {
        this.einheitSummen = einheitSummen;
    }
}
