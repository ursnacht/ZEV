package ch.nacht.dto;

import ch.nacht.entity.MeldungLevel;
import ch.nacht.entity.Systemmeldung;

import java.time.LocalDateTime;

/**
 * DTO für eine Systemmeldung (ohne {@code org_id} – kein Mandanten-Leak an den Client).
 */
public class SystemmeldungDTO {

    private Long id;
    private MeldungLevel level;
    private String kategorie;
    private String meldungKey;
    private String parameter;
    private LocalDateTime erstmalsAufgetreten;
    private LocalDateTime zuletztAufgetreten;
    private boolean erledigt;
    private LocalDateTime erledigtAm;
    private boolean erledigtAutomatisch;
    private int zaehler;

    public SystemmeldungDTO() {
    }

    public static SystemmeldungDTO fromEntity(Systemmeldung m) {
        SystemmeldungDTO dto = new SystemmeldungDTO();
        dto.id = m.getId();
        dto.level = m.getLevel();
        dto.kategorie = m.getKategorie();
        dto.meldungKey = m.getMeldungKey();
        dto.parameter = m.getParameter();
        dto.erstmalsAufgetreten = m.getErstmalsAufgetreten();
        dto.zuletztAufgetreten = m.getZuletztAufgetreten();
        dto.erledigt = m.isErledigt();
        dto.erledigtAm = m.getErledigtAm();
        dto.erledigtAutomatisch = m.isErledigtAutomatisch();
        dto.zaehler = m.getZaehler();
        return dto;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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
}
