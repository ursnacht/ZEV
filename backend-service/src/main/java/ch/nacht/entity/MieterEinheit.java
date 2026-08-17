package ch.nacht.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import org.hibernate.annotations.Filter;

/**
 * Zuordnung eines Mieters zu einer Einheit (Specs/Ladestationen.md).
 *
 * <p>Ein Mieter kann mehreren Einheiten zugeordnet sein — Wohnung und Ladestation(en) —, damit
 * alles auf <b>einer</b> Rechnung erscheint. Der Mietzeitraum bleibt am Mieter und gilt für alle
 * seine Einheiten.
 *
 * <p>Bewusst eine eigene Entity statt {@code @ManyToMany}: Die Tabelle trägt wie alle anderen eine
 * {@code org_id} mit Hibernate-Filter, und die setzt JPA bei einer reinen Join-Tabelle nicht.
 *
 * <p>Ebenfalls bewusst ein <b>Surrogatschlüssel</b> statt eines zusammengesetzten: Eine eigene
 * Schlüsselklasse läge im Paket {@code entity}, wäre selbst keine Entity und verstiesse damit
 * gegen die ArchUnit-Regel „Entities sollten mit {@code @Entity} annotiert sein". Fachlich
 * eindeutig ist das Paar (mieter_id, einheit_id) über einen Unique-Constraint.
 */
@Entity
@Table(name = "mieter_einheit", schema = "zev")
@Filter(name = "orgFilter", condition = "org_id = :orgId")
public class MieterEinheit {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "mieter_einheit_seq")
    @SequenceGenerator(name = "mieter_einheit_seq", sequenceName = "zev.mieter_einheit_seq",
            allocationSize = 1)
    private Long id;

    @Column(name = "org_id", nullable = false)
    private Long orgId;

    @Column(name = "mieter_id", nullable = false)
    private Long mieterId;

    @Column(name = "einheit_id", nullable = false)
    private Long einheitId;

    public MieterEinheit() {
    }

    public MieterEinheit(Long orgId, Long mieterId, Long einheitId) {
        this.orgId = orgId;
        this.mieterId = mieterId;
        this.einheitId = einheitId;
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

    public Long getMieterId() {
        return mieterId;
    }

    public void setMieterId(Long mieterId) {
        this.mieterId = mieterId;
    }

    public Long getEinheitId() {
        return einheitId;
    }

    public void setEinheitId(Long einheitId) {
        this.einheitId = einheitId;
    }

    @Override
    public String toString() {
        return "MieterEinheit{mieterId=" + mieterId + ", einheitId=" + einheitId + "}";
    }

}
