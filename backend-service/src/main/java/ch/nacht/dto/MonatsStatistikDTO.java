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

    // Berechnete Werte (nur für den Summen-Vergleich gegen die Bilanz-Einheiten)
    private Double bezugVonVnb;       // Verbrauch(Consumer Total) − zev(Consumer, gemessen)
    private Double ruecklieferung;    // Produktion(Producer Total) − zev(Producer)

    // Statistik-Kennzahlen (Spec Statistik-Kennzahlen.md); berechnet, kein Schema-Change.
    // Prozentwerte als Anteil (0..1); null = "–"/n/a (Nenner 0 bzw. fehlende Bilanz-Daten).
    private Double autarkiegrad;              // Cz / C
    private Double eigenverbrauchsquote;      // Pz / P
    private Double netzbezugsquote;           // (C − Cz) / C
    private Double einspeisequote;            // (P − Pz) / P
    private Double zevEigenverbrauch;         // Cz (kWh)
    // Gemessene Gegenstuecke: aus dem Netzbezug der BEZUG-Bilanz-Einheit statt aus dem ZEV-Anteil
    // der Consumer. Die Differenz zu den obigen Werten ist der Beitrag, der weder direkt aus der
    // PV noch aus dem Netz kam - typischerweise die Batterie-Entladung.
    private Double autarkiegradGemessen;      // 1 − B / C
    private Double netzbezugsquoteGemessen;   // B / C
    private boolean bilanzKennzahlenVerfuegbar;   // BEZUG-Einheit vorhanden und C > 0
    private boolean bilanzBezugLueckenhaft;       // BEZUG deckt nicht alle Consumer-Intervalle ab
    // Im Bilanzmodus stammt auch der ZEV-Anteil der Consumer aus den Bilanzdaten: Uebersprungene
    // Intervalle bleiben ohne zev und zaehlen dadurch voll als Netzbezug. Die gerechneten Quoten
    // sind dann staerker verzerrt als die gemessenen - und in die andere Richtung.
    private boolean verteilungLueckenhaft;
    // Batterie-Kennzahlen (berechnet/geschätzt aus der Energiebilanz); null = nicht ermittelbar.
    private Double batterieNetto;             // P − C + B − R (kWh)
    private Double batterieGeladen;           // Σ max(0, Netto_i) (kWh, Stufe 2)
    private Double batterieEntladen;          // Σ max(0, −Netto_i) (kWh, Stufe 2)
    private Double batterieWirkungsgrad;      // entladen / geladen
    private boolean batterieKennzahlenVerfuegbar; // Producer + Bezug + Rücklieferung vorhanden

    // Bilanzmesspunkte (Netzanschluss): Summen der Typen BEZUG (positiv) / RUECKLIEFERUNG (Betrag)
    private Double bilanzBezug;
    private Double bilanzRuecklieferung;

    // Namen der Bilanz-Einheiten (max. eine je Typ); null = keine Einheit vorhanden ->
    // Bilanz-Zeile und zugehöriger Vergleich werden nicht angezeigt (FR-4.6/FR-5.7)
    private String bilanzBezugName;
    private String bilanzRuecklieferungName;

    // Vergleiche
    private boolean summenCDGleich;
    private Double differenzCD;
    private boolean summenCEGleich;
    private Double differenzCE;
    private boolean summenDEGleich;
    private Double differenzDE;

    // Vergleiche gegen die Bilanzmesspunkte
    private boolean bezugBilanzGleich;
    private Double bezugBilanzDifferenz;
    private boolean ruecklieferungBilanzGleich;
    private Double ruecklieferungBilanzDifferenz;

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

    public Double getBezugVonVnb() {
        return bezugVonVnb;
    }

    public void setBezugVonVnb(Double bezugVonVnb) {
        this.bezugVonVnb = bezugVonVnb;
    }

    public Double getRuecklieferung() {
        return ruecklieferung;
    }

    public void setRuecklieferung(Double ruecklieferung) {
        this.ruecklieferung = ruecklieferung;
    }

    public Double getBilanzBezug() {
        return bilanzBezug;
    }

    public void setBilanzBezug(Double bilanzBezug) {
        this.bilanzBezug = bilanzBezug;
    }

    public Double getBilanzRuecklieferung() {
        return bilanzRuecklieferung;
    }

    public void setBilanzRuecklieferung(Double bilanzRuecklieferung) {
        this.bilanzRuecklieferung = bilanzRuecklieferung;
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

    public String getBilanzBezugName() {
        return bilanzBezugName;
    }

    public void setBilanzBezugName(String bilanzBezugName) {
        this.bilanzBezugName = bilanzBezugName;
    }

    public String getBilanzRuecklieferungName() {
        return bilanzRuecklieferungName;
    }

    public void setBilanzRuecklieferungName(String bilanzRuecklieferungName) {
        this.bilanzRuecklieferungName = bilanzRuecklieferungName;
    }

    public boolean isBezugBilanzGleich() {
        return bezugBilanzGleich;
    }

    public void setBezugBilanzGleich(boolean bezugBilanzGleich) {
        this.bezugBilanzGleich = bezugBilanzGleich;
    }

    public Double getBezugBilanzDifferenz() {
        return bezugBilanzDifferenz;
    }

    public void setBezugBilanzDifferenz(Double bezugBilanzDifferenz) {
        this.bezugBilanzDifferenz = bezugBilanzDifferenz;
    }

    public boolean isRuecklieferungBilanzGleich() {
        return ruecklieferungBilanzGleich;
    }

    public void setRuecklieferungBilanzGleich(boolean ruecklieferungBilanzGleich) {
        this.ruecklieferungBilanzGleich = ruecklieferungBilanzGleich;
    }

    public Double getRuecklieferungBilanzDifferenz() {
        return ruecklieferungBilanzDifferenz;
    }

    public void setRuecklieferungBilanzDifferenz(Double ruecklieferungBilanzDifferenz) {
        this.ruecklieferungBilanzDifferenz = ruecklieferungBilanzDifferenz;
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

    public Double getAutarkiegrad() {
        return autarkiegrad;
    }

    public void setAutarkiegrad(Double autarkiegrad) {
        this.autarkiegrad = autarkiegrad;
    }

    public Double getEigenverbrauchsquote() {
        return eigenverbrauchsquote;
    }

    public void setEigenverbrauchsquote(Double eigenverbrauchsquote) {
        this.eigenverbrauchsquote = eigenverbrauchsquote;
    }

    public Double getNetzbezugsquote() {
        return netzbezugsquote;
    }

    public void setNetzbezugsquote(Double netzbezugsquote) {
        this.netzbezugsquote = netzbezugsquote;
    }

    public Double getEinspeisequote() {
        return einspeisequote;
    }

    public void setEinspeisequote(Double einspeisequote) {
        this.einspeisequote = einspeisequote;
    }

    public Double getZevEigenverbrauch() {
        return zevEigenverbrauch;
    }

    public void setZevEigenverbrauch(Double zevEigenverbrauch) {
        this.zevEigenverbrauch = zevEigenverbrauch;
    }

    public Double getBatterieNetto() {
        return batterieNetto;
    }

    public void setBatterieNetto(Double batterieNetto) {
        this.batterieNetto = batterieNetto;
    }

    public Double getBatterieGeladen() {
        return batterieGeladen;
    }

    public void setBatterieGeladen(Double batterieGeladen) {
        this.batterieGeladen = batterieGeladen;
    }

    public Double getBatterieEntladen() {
        return batterieEntladen;
    }

    public void setBatterieEntladen(Double batterieEntladen) {
        this.batterieEntladen = batterieEntladen;
    }

    public Double getBatterieWirkungsgrad() {
        return batterieWirkungsgrad;
    }

    public void setBatterieWirkungsgrad(Double batterieWirkungsgrad) {
        this.batterieWirkungsgrad = batterieWirkungsgrad;
    }

    public boolean isBatterieKennzahlenVerfuegbar() {
        return batterieKennzahlenVerfuegbar;
    }

    public void setBatterieKennzahlenVerfuegbar(boolean batterieKennzahlenVerfuegbar) {
        this.batterieKennzahlenVerfuegbar = batterieKennzahlenVerfuegbar;
    }

    public Double getAutarkiegradGemessen() {
        return autarkiegradGemessen;
    }

    public void setAutarkiegradGemessen(Double autarkiegradGemessen) {
        this.autarkiegradGemessen = autarkiegradGemessen;
    }

    public Double getNetzbezugsquoteGemessen() {
        return netzbezugsquoteGemessen;
    }

    public void setNetzbezugsquoteGemessen(Double netzbezugsquoteGemessen) {
        this.netzbezugsquoteGemessen = netzbezugsquoteGemessen;
    }

    public boolean isBilanzKennzahlenVerfuegbar() {
        return bilanzKennzahlenVerfuegbar;
    }

    public void setBilanzKennzahlenVerfuegbar(boolean bilanzKennzahlenVerfuegbar) {
        this.bilanzKennzahlenVerfuegbar = bilanzKennzahlenVerfuegbar;
    }

    public boolean isBilanzBezugLueckenhaft() {
        return bilanzBezugLueckenhaft;
    }

    public void setBilanzBezugLueckenhaft(boolean bilanzBezugLueckenhaft) {
        this.bilanzBezugLueckenhaft = bilanzBezugLueckenhaft;
    }

    public boolean isVerteilungLueckenhaft() {
        return verteilungLueckenhaft;
    }

    public void setVerteilungLueckenhaft(boolean verteilungLueckenhaft) {
        this.verteilungLueckenhaft = verteilungLueckenhaft;
    }
}
