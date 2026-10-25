package ch.nacht.dto;

import ch.nacht.entity.MesspunktEmpfangErgebnis;
import ch.nacht.entity.MesspunktEmpfangQuelle;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Eine Zeile der Ansicht Messpunkt-Empfang (Specs/Messpunkt-Empfang.md, API-Endpunkte).
 *
 * <p>Zwei Arten von Zeilen: ein festgehaltener Empfang, oder eine Einheit, deren Messpunkt noch
 * nie gesendet hat ({@link #keinEmpfang} = {@code true}). Bei letzterer sind alle Empfangsfelder
 * {@code null}, auch {@link #id} — es gibt keine Zeile zum Löschen.
 *
 * <p><b>Kein {@code orgId}:</b> Der Mandant ergibt sich aus dem Token.
 *
 * <p>{@link #ersterEmpfang} und {@link #letzterEmpfang} sind in Ortszeit umgerechnet (Anzeige);
 * das <b>Alter</b> rechnet das Backend über absolute Zeitpunkte ({@link #sekundenSeitEmpfang}),
 * damit weder eine falsch gehende Client-Uhr noch die Winterzeit-Umstellung es verfälschen.
 */
public class MesspunktEmpfangDTO {

    private Long id;
    private String messpunkt;
    private List<String> einheiten;
    private MesspunktEmpfangQuelle quelle;
    private LocalDateTime ersterEmpfang;
    private LocalDateTime letzterEmpfang;
    /** Sekunden seit dem letzten Empfang, nie negativ (FR-2.5). */
    private Long sekundenSeitEmpfang;
    private Long anzahlMeldungen;
    private BigDecimal zaehlerstandBezug;
    private BigDecimal zaehlerstandEinspeisung;
    private MesspunktEmpfangErgebnis ergebnis;
    private String grund;
    private boolean keinEmpfang;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMesspunkt() {
        return messpunkt;
    }

    public void setMesspunkt(String messpunkt) {
        this.messpunkt = messpunkt;
    }

    public List<String> getEinheiten() {
        return einheiten;
    }

    public void setEinheiten(List<String> einheiten) {
        this.einheiten = einheiten;
    }

    public MesspunktEmpfangQuelle getQuelle() {
        return quelle;
    }

    public void setQuelle(MesspunktEmpfangQuelle quelle) {
        this.quelle = quelle;
    }

    public LocalDateTime getErsterEmpfang() {
        return ersterEmpfang;
    }

    public void setErsterEmpfang(LocalDateTime ersterEmpfang) {
        this.ersterEmpfang = ersterEmpfang;
    }

    public LocalDateTime getLetzterEmpfang() {
        return letzterEmpfang;
    }

    public void setLetzterEmpfang(LocalDateTime letzterEmpfang) {
        this.letzterEmpfang = letzterEmpfang;
    }

    public Long getSekundenSeitEmpfang() {
        return sekundenSeitEmpfang;
    }

    public void setSekundenSeitEmpfang(Long sekundenSeitEmpfang) {
        this.sekundenSeitEmpfang = sekundenSeitEmpfang;
    }

    public Long getAnzahlMeldungen() {
        return anzahlMeldungen;
    }

    public void setAnzahlMeldungen(Long anzahlMeldungen) {
        this.anzahlMeldungen = anzahlMeldungen;
    }

    public BigDecimal getZaehlerstandBezug() {
        return zaehlerstandBezug;
    }

    public void setZaehlerstandBezug(BigDecimal zaehlerstandBezug) {
        this.zaehlerstandBezug = zaehlerstandBezug;
    }

    public BigDecimal getZaehlerstandEinspeisung() {
        return zaehlerstandEinspeisung;
    }

    public void setZaehlerstandEinspeisung(BigDecimal zaehlerstandEinspeisung) {
        this.zaehlerstandEinspeisung = zaehlerstandEinspeisung;
    }

    public MesspunktEmpfangErgebnis getErgebnis() {
        return ergebnis;
    }

    public void setErgebnis(MesspunktEmpfangErgebnis ergebnis) {
        this.ergebnis = ergebnis;
    }

    public String getGrund() {
        return grund;
    }

    public void setGrund(String grund) {
        this.grund = grund;
    }

    public boolean isKeinEmpfang() {
        return keinEmpfang;
    }

    public void setKeinEmpfang(boolean keinEmpfang) {
        this.keinEmpfang = keinEmpfang;
    }
}
