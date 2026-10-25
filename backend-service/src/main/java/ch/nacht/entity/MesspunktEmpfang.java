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
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Der letzte MQTT-Empfang eines Messpunkts — auch eines, den keine Einheit trägt
 * (Specs/Messpunkt-Empfang.md).
 *
 * <p><b>Geschrieben wird nur per Upsert</b> ({@code MesspunktEmpfangRepository.upsert}), nie über
 * {@code save}: Eine Zeile je (org_id, messpunkt), deren Zähler bei jeder Meldung steigt.
 *
 * <p><b>Zeitpunkte als {@link Instant}</b>, anders als die Messwerte: Das Alter des letzten
 * Empfangs ist eine Differenz zweier Zeitpunkte und wäre über zonenlose Ortszeit in der Nacht der
 * Winterzeit-Umstellung falsch (FR-1.8).
 *
 * <p>Der Unique-Constraint steht auch hier und nicht nur in der Migration: Die Integrationstests
 * bauen das Schema aus dem Mapping, und ohne ihn scheiterte {@code ON CONFLICT (org_id, messpunkt)}.
 */
@Entity
@Table(name = "messpunkt_empfang", schema = "zev",
        uniqueConstraints = @UniqueConstraint(name = "uq_messpunkt_empfang_org_messpunkt",
                columnNames = {"org_id", "messpunkt"}))
@Filter(name = "orgFilter", condition = "org_id = :orgId")
public class MesspunktEmpfang {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "messpunkt_empfang_seq")
    @SequenceGenerator(name = "messpunkt_empfang_seq", sequenceName = "zev.messpunkt_empfang_seq",
            allocationSize = 1)
    private Long id;

    @Column(name = "org_id", nullable = false)
    private Long orgId;

    @Column(name = "messpunkt", length = 255, nullable = false)
    private String messpunkt;

    @Enumerated(EnumType.STRING)
    @Column(name = "quelle", length = 20, nullable = false)
    private MesspunktEmpfangQuelle quelle;

    @Column(name = "erster_empfang", nullable = false)
    private Instant ersterEmpfang;

    @Column(name = "letzter_empfang", nullable = false)
    private Instant letzterEmpfang;

    @Column(name = "anzahl_meldungen", nullable = false)
    private long anzahlMeldungen;

    @Column(name = "zaehlerstand_bezug")
    private BigDecimal zaehlerstandBezug;

    @Column(name = "zaehlerstand_einspeisung")
    private BigDecimal zaehlerstandEinspeisung;

    @Enumerated(EnumType.STRING)
    @Column(name = "ergebnis", length = 30, nullable = false)
    private MesspunktEmpfangErgebnis ergebnis;

    @Column(name = "grund", length = 255)
    private String grund;

    /** Payload der letzten Meldung, höchstens 10'000 Zeichen (FR-1.9); {@code null} vor V188. */
    @Column(name = "letzte_meldung", columnDefinition = "TEXT")
    private String letzteMeldung;

    public MesspunktEmpfang() {
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

    public String getMesspunkt() {
        return messpunkt;
    }

    public void setMesspunkt(String messpunkt) {
        this.messpunkt = messpunkt;
    }

    public MesspunktEmpfangQuelle getQuelle() {
        return quelle;
    }

    public void setQuelle(MesspunktEmpfangQuelle quelle) {
        this.quelle = quelle;
    }

    public Instant getErsterEmpfang() {
        return ersterEmpfang;
    }

    public void setErsterEmpfang(Instant ersterEmpfang) {
        this.ersterEmpfang = ersterEmpfang;
    }

    public Instant getLetzterEmpfang() {
        return letzterEmpfang;
    }

    public void setLetzterEmpfang(Instant letzterEmpfang) {
        this.letzterEmpfang = letzterEmpfang;
    }

    public long getAnzahlMeldungen() {
        return anzahlMeldungen;
    }

    public void setAnzahlMeldungen(long anzahlMeldungen) {
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

    public String getLetzteMeldung() {
        return letzteMeldung;
    }

    public void setLetzteMeldung(String letzteMeldung) {
        this.letzteMeldung = letzteMeldung;
    }
}
