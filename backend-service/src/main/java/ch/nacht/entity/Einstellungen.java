package ch.nacht.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/**
 * Entity for tenant-specific settings.
 * Each tenant (org_id) has exactly one settings record.
 *
 * Note: This entity does NOT use @Filter because settings are
 * explicitly queried by org_id from the security context.
 */
@Entity
@Table(name = "einstellungen", schema = "zev")
public class Einstellungen {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "einstellungen_seq")
    @SequenceGenerator(name = "einstellungen_seq", sequenceName = "zev.einstellungen_seq", allocationSize = 1)
    private Long id;

    @NotNull
    @Column(name = "org_id", nullable = false, unique = true)
    private UUID orgId;

    @NotNull
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "konfiguration", columnDefinition = "jsonb", nullable = false)
    private String konfiguration;

    public Einstellungen() {
    }

    public Einstellungen(UUID orgId, String konfiguration) {
        this.orgId = orgId;
        this.konfiguration = konfiguration;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public UUID getOrgId() {
        return orgId;
    }

    public void setOrgId(UUID orgId) {
        this.orgId = orgId;
    }

    public String getKonfiguration() {
        return konfiguration;
    }

    public void setKonfiguration(String konfiguration) {
        this.konfiguration = konfiguration;
    }

    @Override
    public String toString() {
        return "Einstellungen{id=" + id + ", orgId=" + orgId + ", konfiguration=" + konfiguration + "}";
    }
}
