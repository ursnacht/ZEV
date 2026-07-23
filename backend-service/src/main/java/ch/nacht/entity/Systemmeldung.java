package ch.nacht.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.Filter;

import java.time.LocalDateTime;

/**
 * Persistente, benutzer­sichtbare Betriebsmeldung (Systemmeldung).
 * Wird zusätzlich zum Log-Eintrag erzeugt (Start: Bilanzmodell-Fehler). Wiederkehrende
 * Fehler werden nach {@code meldungKey} dedupliziert (Zähler + Zeitstempel-Update).
 */
@Entity
@Table(name = "systemmeldung", schema = "zev")
@Filter(name = "orgFilter", condition = "org_id = :orgId")
public class Systemmeldung {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "systemmeldung_seq")
    @SequenceGenerator(name = "systemmeldung_seq", sequenceName = "zev.systemmeldung_seq", allocationSize = 1)
    private Long id;

    @Column(name = "org_id", nullable = false)
    private Long orgId;

    @NotNull(message = "Level is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "level", length = 10, nullable = false)
    private MeldungLevel level;

    @NotBlank(message = "Kategorie is required")
    @Size(max = 50, message = "Kategorie must not exceed 50 characters")
    @Column(name = "kategorie", length = 50, nullable = false)
    private String kategorie;

    @NotBlank(message = "Meldung-Key is required")
    @Size(max = 100, message = "Meldung-Key must not exceed 100 characters")
    @Column(name = "meldung_key", length = 100, nullable = false)
    private String meldungKey;

    @Size(max = 500, message = "Parameter must not exceed 500 characters")
    @Column(name = "parameter", length = 500)
    private String parameter;

    @NotNull(message = "Erstmals aufgetreten is required")
    @Column(name = "erstmals_aufgetreten", nullable = false)
    private LocalDateTime erstmalsAufgetreten;

    @NotNull(message = "Zuletzt aufgetreten is required")
    @Column(name = "zuletzt_aufgetreten", nullable = false)
    private LocalDateTime zuletztAufgetreten;

    @Column(name = "erledigt", nullable = false)
    private boolean erledigt = false;

    @Column(name = "erledigt_am")
    private LocalDateTime erledigtAm;

    @Column(name = "erledigt_automatisch", nullable = false)
    private boolean erledigtAutomatisch = false;

    @Column(name = "zaehler", nullable = false)
    private int zaehler = 1;

    public Systemmeldung() {
    }

    public Systemmeldung(MeldungLevel level, String kategorie, String meldungKey, String parameter,
                         LocalDateTime erstmalsAufgetreten, LocalDateTime zuletztAufgetreten) {
        this.level = level;
        this.kategorie = kategorie;
        this.meldungKey = meldungKey;
        this.parameter = parameter;
        this.erstmalsAufgetreten = erstmalsAufgetreten;
        this.zuletztAufgetreten = zuletztAufgetreten;
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

    public MeldungLevel getLevel() {
        return level;
    }

    public void setLevel(MeldungLevel level) {
        this.level = level;
    }

    public String getKategorie() {
        return kategorie;
    }

    public void setKategorie(String kategorie) {
        this.kategorie = kategorie;
    }

    public String getMeldungKey() {
        return meldungKey;
    }

    public void setMeldungKey(String meldungKey) {
        this.meldungKey = meldungKey;
    }

    public String getParameter() {
        return parameter;
    }

    public void setParameter(String parameter) {
        this.parameter = parameter;
    }

    public LocalDateTime getErstmalsAufgetreten() {
        return erstmalsAufgetreten;
    }

    public void setErstmalsAufgetreten(LocalDateTime erstmalsAufgetreten) {
        this.erstmalsAufgetreten = erstmalsAufgetreten;
    }

    public LocalDateTime getZuletztAufgetreten() {
        return zuletztAufgetreten;
    }

    public void setZuletztAufgetreten(LocalDateTime zuletztAufgetreten) {
        this.zuletztAufgetreten = zuletztAufgetreten;
    }

    public boolean isErledigt() {
        return erledigt;
    }

    public void setErledigt(boolean erledigt) {
        this.erledigt = erledigt;
    }

    public LocalDateTime getErledigtAm() {
        return erledigtAm;
    }

    public void setErledigtAm(LocalDateTime erledigtAm) {
        this.erledigtAm = erledigtAm;
    }

    public boolean isErledigtAutomatisch() {
        return erledigtAutomatisch;
    }

    public void setErledigtAutomatisch(boolean erledigtAutomatisch) {
        this.erledigtAutomatisch = erledigtAutomatisch;
    }

    public int getZaehler() {
        return zaehler;
    }

    public void setZaehler(int zaehler) {
        this.zaehler = zaehler;
    }

    @Override
    public String toString() {
        return "Systemmeldung{id=" + id + ", orgId=" + orgId + ", level=" + level + ", kategorie='" + kategorie +
               "', meldungKey='" + meldungKey + "', erledigt=" + erledigt + ", zaehler=" + zaehler + "}";
    }
}
