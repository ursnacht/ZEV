package ch.nacht.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Ein Steuerentscheid der Einspeisesteuerung — was die Steuerung in einem 15-Minuten-Intervall
 * tun <b>würde</b> (Specs/Einspeisesteuerung.md, FR-3).
 *
 * <p><b>Es wird nichts geschaltet.</b> Diese Ausbaustufe ist ein Trockenlauf; es gibt keinen
 * Schreibpfad zur Anlage. Der Zweck des Datensatzes ist Nachvollziehbarkeit: Die Eingangsgrössen
 * stehen neben dem Ergebnis, sonst liesse sich später nicht sagen, warum um 09:15 gesperrt wurde.
 *
 * <p><b>Zeiten in Ortszeit</b> (Europe/Zurich, seit V147). {@link #zeitVon} trägt denselben
 * Zeitbezug wie {@code messwerte.zeit} und {@code zaehler_rohdaten.zeit}; nur
 * {@code preiszeitreihe.zeit_von} liegt in UTC und wird beim Lesen umgerechnet.
 *
 * <p><b>Der Preis dieser Wahl:</b> In der Nacht der Rückstellung auf Winterzeit tritt die Stunde
 * 02:00–03:00 zweimal auf. Beide Durchgänge tragen denselben Schlüssel, der Upsert überschreibt
 * also die vier Entscheide des ersten. Das ist bewusst hingenommen — es ist Nacht, es gibt keinen
 * Überschuss, und dieselben vier Intervalle fehlen in {@code messwerte} ohnehin. Eine eigene
 * Zeitkonvention für diese eine Tabelle hat mehr gekostet, als sie wert war
 * (Specs/Einspeisesteuerung.md, §5).
 *
 * <p><b>Beide Schwellen werden mitgeschrieben</b> ({@link #schwellwert}, {@link #speicherwert}).
 * Sie stehen je Mandant in {@code organisation.konfiguration} und sind über die Maske änderbar;
 * ohne die Kopie am Datensatz wäre ein alter Entscheid nach einer Änderung nicht mehr erklärbar —
 * man sähe die Wirkung und wüsste die Ursache nicht.
 */
@Entity
@Table(name = "steuerentscheid", schema = "zev")
@Filter(name = "orgFilter", condition = "org_id = :orgId")
public class Steuerentscheid {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "steuerentscheid_seq")
    @SequenceGenerator(name = "steuerentscheid_seq", sequenceName = "zev.steuerentscheid_seq",
            allocationSize = 1)
    private Long id;

    @Column(name = "org_id", nullable = false)
    private Long orgId;

    /** Beginn des ausgewerteten Intervalls in <b>Ortszeit</b> (Europe/Zurich). */
    @NotNull
    @Column(name = "zeit_von", nullable = false)
    private LocalDateTime zeitVon;

    /**
     * Einspeisepreis des Intervalls in CHF/kWh; {@code null}, wenn kein Preis vorlag.
     *
     * <p>Darf 0 und <b>negativ</b> sein — bei Überangebot kostet das Einspeisen Geld. Es gibt
     * deshalb weder hier noch in der Datenbank eine Vorzeichenprüfung.
     */
    @Column(name = "preis", precision = 10, scale = 5)
    private BigDecimal preis;

    /**
     * Tiefster erwarteter Preis im <b>Rest</b> des Ortstages; {@code null}, wenn für den Rest des
     * Tages keine Preise vorliegen. Grundlage von {@link Steuerregel#WARTEN_AUF_TAL}.
     */
    @Column(name = "preis_tief_rest", precision = 10, scale = 5)
    private BigDecimal preisTiefRest;

    /**
     * Summe der {@code PRODUCER} im Intervall in kWh, <b>als Betrag</b>.
     *
     * <p>In {@code messwerte.total} steht die Produktion <b>negativ</b> ({@code ΔBezug −
     * ΔEinspeisung}); hier ist sie vorzeichenlos, damit der Überschuss lesbar bleibt.
     */
    @NotNull
    @Column(name = "produktion", precision = 12, scale = 3, nullable = false)
    private BigDecimal produktion;

    /** Summe der {@code CONSUMER} im Intervall in kWh. */
    @NotNull
    @Column(name = "verbrauch", precision = 12, scale = 3, nullable = false)
    private BigDecimal verbrauch;

    /**
     * Summe der {@code BEZUG}-Einheiten in kWh; {@code null} bei Entscheiden vor V149.
     *
     * <p>Geht in <b>keine</b> Regel ein — zusammen mit {@link #ruecklieferung} macht dieser Wert
     * die Energiebilanz prüfbar (FR-5).
     */
    @Column(name = "bezug", precision = 12, scale = 3)
    private BigDecimal bezug;

    /**
     * Summe der {@code RUECKLIEFERUNG}-Einheiten in kWh, <b>als Betrag</b>; {@code null} bei
     * Entscheiden vor V149.
     */
    @Column(name = "ruecklieferung", precision = 12, scale = 3)
    private BigDecimal ruecklieferung;

    /**
     * Ladezustand des Speichers in Prozent am <b>Ende</b> des Intervalls; {@code null}, wenn kein
     * Speicher erfasst ist oder kein Wert vorlag.
     *
     * <p>Geht in <b>keine</b> Regel ein — er erklärt den Entscheid im Nachhinein: „Ladung gesperrt
     * bei 95 %" ist eine andere Aussage als „bei 40 %". Die erste Sperre war wirkungslos, die
     * zweite hat Kapazität freigehalten.
     */
    @Column(name = "soc", precision = 5, scale = 1)
    private BigDecimal soc;

    /**
     * Gemessene <b>Ladung</b> der {@code SPEICHER}-Einheit im Intervall in kWh; {@code null}, wenn
     * kein Speicher erfasst ist.
     *
     * <p>Geht in <b>keine</b> Regel ein. Gegenstück zur Bilanzdifferenz (V149): Die war ein
     * Residuum und enthielt alles nicht Gemessene; dies hier ist der Zählerwert.
     */
    @Column(name = "speicher_ladung", precision = 12, scale = 3)
    private BigDecimal speicherLadung;

    /**
     * Gemessene <b>Entladung</b> der {@code SPEICHER}-Einheit im Intervall in kWh, <b>als
     * Betrag</b>; {@code null}, wenn kein Speicher erfasst ist.
     *
     * <p>Getrennt von {@link #speicherLadung} geführt: Ein Saldo von 0 kann „nichts passiert"
     * heissen oder „2 kWh rein, 2 kWh raus".
     */
    @Column(name = "speicher_entladung", precision = 12, scale = 3)
    private BigDecimal speicherEntladung;

    /** {@code max(0, produktion − verbrauch)} in kWh. */
    @NotNull
    @Column(name = "ueberschuss", precision = 12, scale = 3, nullable = false)
    private BigDecimal ueberschuss;

    /** Die erste zutreffende Regel, die den Entscheid bestimmt hat. */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "regel", length = 30, nullable = false)
    private Steuerregel regel;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "batterieladung", length = 10, nullable = false)
    private Steuerzustand batterieladung;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "einspeisung", length = 10, nullable = false)
    private Steuerzustand einspeisung;

    /** Der beim Entscheid geltende Schwellwert — siehe Klassenkommentar. */
    @NotNull
    @Column(name = "schwellwert", precision = 10, scale = 5, nullable = false)
    private BigDecimal schwellwert;

    /** Der beim Entscheid geltende Wert einer gespeicherten kWh — siehe Klassenkommentar. */
    @NotNull
    @Column(name = "speicherwert", precision = 10, scale = 5, nullable = false)
    private BigDecimal speicherwert;

    @Column(name = "erstellt_am", nullable = false)
    private LocalDateTime erstelltAm;

    public Steuerentscheid() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOrgId() {
        return orgId;
    }

    public void setOrgId(Long orgId) {
        this.orgId = orgId;
    }

    public LocalDateTime getZeitVon() {
        return zeitVon;
    }

    public void setZeitVon(LocalDateTime zeitVon) {
        this.zeitVon = zeitVon;
    }

    public BigDecimal getPreis() {
        return preis;
    }

    public void setPreis(BigDecimal preis) {
        this.preis = preis;
    }

    public BigDecimal getPreisTiefRest() {
        return preisTiefRest;
    }

    public void setPreisTiefRest(BigDecimal preisTiefRest) {
        this.preisTiefRest = preisTiefRest;
    }

    public BigDecimal getProduktion() {
        return produktion;
    }

    public void setProduktion(BigDecimal produktion) {
        this.produktion = produktion;
    }

    public BigDecimal getVerbrauch() {
        return verbrauch;
    }

    public void setVerbrauch(BigDecimal verbrauch) {
        this.verbrauch = verbrauch;
    }

    public BigDecimal getBezug() {
        return bezug;
    }

    public void setBezug(BigDecimal bezug) {
        this.bezug = bezug;
    }

    public BigDecimal getRuecklieferung() {
        return ruecklieferung;
    }

    public void setRuecklieferung(BigDecimal ruecklieferung) {
        this.ruecklieferung = ruecklieferung;
    }

    public BigDecimal getSoc() {
        return soc;
    }

    public void setSoc(BigDecimal soc) {
        this.soc = soc;
    }

    public BigDecimal getSpeicherLadung() {
        return speicherLadung;
    }

    public void setSpeicherLadung(BigDecimal speicherLadung) {
        this.speicherLadung = speicherLadung;
    }

    public BigDecimal getSpeicherEntladung() {
        return speicherEntladung;
    }

    public void setSpeicherEntladung(BigDecimal speicherEntladung) {
        this.speicherEntladung = speicherEntladung;
    }

    public BigDecimal getUeberschuss() {
        return ueberschuss;
    }

    public void setUeberschuss(BigDecimal ueberschuss) {
        this.ueberschuss = ueberschuss;
    }

    public Steuerregel getRegel() {
        return regel;
    }

    public void setRegel(Steuerregel regel) {
        this.regel = regel;
    }

    public Steuerzustand getBatterieladung() {
        return batterieladung;
    }

    public void setBatterieladung(Steuerzustand batterieladung) {
        this.batterieladung = batterieladung;
    }

    public Steuerzustand getEinspeisung() {
        return einspeisung;
    }

    public void setEinspeisung(Steuerzustand einspeisung) {
        this.einspeisung = einspeisung;
    }

    public BigDecimal getSchwellwert() {
        return schwellwert;
    }

    public void setSchwellwert(BigDecimal schwellwert) {
        this.schwellwert = schwellwert;
    }

    public BigDecimal getSpeicherwert() {
        return speicherwert;
    }

    public void setSpeicherwert(BigDecimal speicherwert) {
        this.speicherwert = speicherwert;
    }

    public LocalDateTime getErstelltAm() {
        return erstelltAm;
    }

    public void setErstelltAm(LocalDateTime erstelltAm) {
        this.erstelltAm = erstelltAm;
    }

    @Override
    public String toString() {
        return "Steuerentscheid{id=" + id + ", zeitVon=" + zeitVon + ", regel=" + regel +
               ", batterieladung=" + batterieladung + ", einspeisung=" + einspeisung +
               ", ueberschuss=" + ueberschuss + "}";
    }
}
