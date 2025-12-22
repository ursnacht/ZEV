package ch.nacht.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Metriken-Entity für die Persistierung von Anwendungsmetriken.
 *
 * Hinweis: Diese Entity verwendet KEINEN @Filter, da Metriken beim
 * Anwendungsstart geladen werden (ohne Org-Kontext) und explizit
 * per findByNameAndOrgId gesucht werden.
 */
@Entity
@Table(name = "metriken", uniqueConstraints = {
    @UniqueConstraint(name = "metriken_name_org_id_key", columnNames = {"name", "org_id"})
})
public class Metrik {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(nullable = false)
    private String name;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String value;

    @Column(nullable = false)
    private LocalDateTime zeitstempel;

    public Metrik() {
    }

    public Metrik(String name, String value) {
        this.name = name;
        this.value = value;
        this.zeitstempel = LocalDateTime.now();
    }

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        this.zeitstempel = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public LocalDateTime getZeitstempel() {
        return zeitstempel;
    }

    public void setZeitstempel(LocalDateTime zeitstempel) {
        this.zeitstempel = zeitstempel;
    }

    public UUID getOrgId() {
        return orgId;
    }

    public void setOrgId(UUID orgId) {
        this.orgId = orgId;
    }
}
