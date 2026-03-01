package ch.nacht.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Interne Repräsentation einer Keycloak-Organisation (Mandant).
 * Entkoppelt die externe Keycloak-UUID vom internen Primärschlüssel.
 * Kein @Filter — diese Tabelle ist nicht mandantenspezifisch gefiltert.
 */
@Entity
@Table(name = "organisation", schema = "zev")
public class Organisation {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "organisation_seq")
    @SequenceGenerator(name = "organisation_seq", sequenceName = "zev.organisation_seq", allocationSize = 1)
    private Long id;

    @Column(name = "keycloak_org_id", nullable = false, unique = true)
    private UUID keycloakOrgId;

    @Column(name = "name", length = 255, nullable = false)
    private String name;

    @Column(name = "erstellt_am", nullable = false)
    private LocalDateTime erstelltAm;

    public Organisation() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public UUID getKeycloakOrgId() {
        return keycloakOrgId;
    }

    public void setKeycloakOrgId(UUID keycloakOrgId) {
        this.keycloakOrgId = keycloakOrgId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LocalDateTime getErstelltAm() {
        return erstelltAm;
    }

    public void setErstelltAm(LocalDateTime erstelltAm) {
        this.erstelltAm = erstelltAm;
    }

    @Override
    public String toString() {
        return "Organisation{id=" + id + ", keycloakOrgId=" + keycloakOrgId + ", name='" + name + "'}";
    }
}
