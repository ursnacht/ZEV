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
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Ein Momentanwert eines Geräts zu einem Zeitpunkt — Ladezustand und später Verwandtes
 * (Specs/Gerätezustand.md).
 *
 * <p><b>Zustand, nicht Verbrauch.</b> Diese Werte werden <b>nie aggregiert und nie verrechnet</b>.
 * Ein Ladestand von 87 % ist keine Energiemenge, und die Differenz zweier Ladestände ist keine
 * Kilowattstunde. Alles ab {@link ZaehlerRohdaten} rechnet mit Deltas kumulativer Zählerstände;
 * wer einen Zustandswert durch dieselbe Maschine schickt, erhält „Δ SOC" — eine Grösse, die bei
 * jedem Ladezyklus das Vorzeichen wechselt.
 *
 * <p>Deshalb fehlt hier auch das {@code verarbeitet}-Feld der Rohdaten: Es gibt keinen
 * Verarbeitungsschritt, der Wert wird gelesen, wo er gebraucht wird.
 *
 * <p><b>Welche Grössen es gibt, steht in {@link Zustandsgroesse}</b> — nicht in der Datenbank. Die
 * Tabelle trägt bewusst keinen CHECK über {@code groesse}, damit eine neue Grösse keine Migration
 * braucht.
 */
@Entity
@Table(name = "geraetezustand", schema = "zev")
@Filter(name = "orgFilter", condition = "org_id = :orgId")
public class Geraetezustand {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "geraetezustand_seq")
    @SequenceGenerator(name = "geraetezustand_seq", sequenceName = "zev.geraetezustand_seq",
            allocationSize = 1)
    private Long id;

    @Column(name = "org_id", nullable = false)
    private Long orgId;

    /**
     * Zugeordnete Einheit — als {@code Long}, nicht als {@code @ManyToOne}.
     *
     * <p>Wie {@link ZaehlerRohdaten#getEinheitId()}: Im Ingest ist die Einheit ohnehin schon
     * aufgelöst, eine Relation brächte nur Lazy-Loading auf dem heissesten Pfad des Systems.
     */
    @Column(name = "einheit_id", nullable = false)
    private Long einheitId;

    /** Messzeitpunkt als <b>lokale Wanduhrzeit</b> (Europe/Zurich), wie {@code messwerte.zeit}. */
    @Column(name = "zeit", nullable = false)
    private LocalDateTime zeit;

    /** Welche Grösse — die Einheit (%, °C) folgt daraus, nicht aus einer eigenen Spalte. */
    @Enumerated(EnumType.STRING)
    @Column(name = "groesse", length = 20, nullable = false)
    private Zustandsgroesse groesse;

    /** Zahlenwert in der Einheit der Grösse; auf drei Nachkommastellen gerundet. */
    @Column(name = "wert", precision = 12, scale = 3, nullable = false)
    private BigDecimal wert;

    @Column(name = "empfangen_am")
    private LocalDateTime empfangenAm;

    public Geraetezustand() {
    }

    public Geraetezustand(Long orgId, Long einheitId, LocalDateTime zeit,
                          Zustandsgroesse groesse, BigDecimal wert) {
        this.orgId = orgId;
        this.einheitId = einheitId;
        this.zeit = zeit;
        this.groesse = groesse;
        this.wert = wert;
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

    public Zustandsgroesse getGroesse() {
        return groesse;
    }

    public void setGroesse(Zustandsgroesse groesse) {
        this.groesse = groesse;
    }

    public BigDecimal getWert() {
        return wert;
    }

    public void setWert(BigDecimal wert) {
        this.wert = wert;
    }

    public LocalDateTime getEmpfangenAm() {
        return empfangenAm;
    }

    public void setEmpfangenAm(LocalDateTime empfangenAm) {
        this.empfangenAm = empfangenAm;
    }
}
