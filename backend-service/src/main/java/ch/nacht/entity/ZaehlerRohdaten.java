package ch.nacht.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Rohdatensatz der MQTT-Integration: absolute (kumulative) Zählerstände zum Messzeitpunkt.
 * Die Delta-/Intervall-Bildung erfolgt im Aggregations-Job (siehe MQTT-Integration.md).
 */
@Entity
@Table(name = "zaehler_rohdaten", schema = "zev")
@Filter(name = "orgFilter", condition = "org_id = :orgId")
public class ZaehlerRohdaten {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "zaehler_rohdaten_seq")
    @SequenceGenerator(name = "zaehler_rohdaten_seq", sequenceName = "zev.zaehler_rohdaten_seq", allocationSize = 1)
    private Long id;

    @Column(name = "org_id", nullable = false)
    private Long orgId;

    @Column(name = "einheit_id", nullable = false)
    private Long einheitId;

    @Column(name = "zeit", nullable = false)
    private LocalDateTime zeit;

    @Column(name = "zaehlerstand_bezug", precision = 14, scale = 4, nullable = false)
    private BigDecimal zaehlerstandBezug;

    @Column(name = "zaehlerstand_einspeisung", precision = 14, scale = 4, nullable = false)
    private BigDecimal zaehlerstandEinspeisung;

    @Column(name = "empfangen_am")
    private LocalDateTime empfangenAm;

    @Column(name = "verarbeitet", nullable = false)
    private boolean verarbeitet = false;

    @Column(name = "verarbeitet_am")
    private LocalDateTime verarbeitetAm;

    public ZaehlerRohdaten() {
    }

    public ZaehlerRohdaten(Long orgId, Long einheitId, LocalDateTime zeit,
                           BigDecimal zaehlerstandBezug, BigDecimal zaehlerstandEinspeisung) {
        this.orgId = orgId;
        this.einheitId = einheitId;
        this.zeit = zeit;
        this.zaehlerstandBezug = zaehlerstandBezug;
        this.zaehlerstandEinspeisung = zaehlerstandEinspeisung;
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

    public Long getEinheitId() {
        return einheitId;
    }

    public void setEinheitId(Long einheitId) {
        this.einheitId = einheitId;
    }

    public LocalDateTime getZeit() {
        return zeit;
    }

    public void setZeit(LocalDateTime zeit) {
        this.zeit = zeit;
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

    public LocalDateTime getEmpfangenAm() {
        return empfangenAm;
    }

    public void setEmpfangenAm(LocalDateTime empfangenAm) {
        this.empfangenAm = empfangenAm;
    }

    public boolean isVerarbeitet() {
        return verarbeitet;
    }

    public void setVerarbeitet(boolean verarbeitet) {
        this.verarbeitet = verarbeitet;
    }

    public LocalDateTime getVerarbeitetAm() {
        return verarbeitetAm;
    }

    public void setVerarbeitetAm(LocalDateTime verarbeitetAm) {
        this.verarbeitetAm = verarbeitetAm;
    }
}
