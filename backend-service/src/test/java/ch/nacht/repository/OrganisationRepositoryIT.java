package ch.nacht.repository;

import ch.nacht.AbstractIntegrationTest;
import ch.nacht.entity.Organisation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for OrganisationRepository using Testcontainers.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class OrganisationRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private OrganisationRepository organisationRepository;

    private static final UUID KEYCLOAK_ORG_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final UUID OTHER_KEYCLOAK_ORG_ID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-fa2345678901");

    @BeforeEach
    void setUp() {
        organisationRepository.deleteAll();
    }

    private Organisation createOrganisation(UUID keycloakOrgId, String name) {
        Organisation org = new Organisation();
        org.setKeycloakOrgId(keycloakOrgId);
        org.setName(name);
        org.setErstelltAm(LocalDateTime.now());
        return org;
    }

    @Test
    void shouldSaveAndFindById() {
        // Given
        Organisation org = createOrganisation(KEYCLOAK_ORG_ID, "Test Organisation");

        // When
        Organisation saved = organisationRepository.save(org);

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getKeycloakOrgId()).isEqualTo(KEYCLOAK_ORG_ID);
        assertThat(saved.getName()).isEqualTo("Test Organisation");
        assertThat(saved.getErstelltAm()).isNotNull();

        Optional<Organisation> found = organisationRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getKeycloakOrgId()).isEqualTo(KEYCLOAK_ORG_ID);
    }

    @Test
    void shouldFindByKeycloakOrgId_Found() {
        // Given
        organisationRepository.save(createOrganisation(KEYCLOAK_ORG_ID, "Test Organisation"));

        // When
        Optional<Organisation> found = organisationRepository.findByKeycloakOrgId(KEYCLOAK_ORG_ID);

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Test Organisation");
        assertThat(found.get().getKeycloakOrgId()).isEqualTo(KEYCLOAK_ORG_ID);
    }

    @Test
    void shouldFindByKeycloakOrgId_NotFound() {
        // When
        Optional<Organisation> found = organisationRepository.findByKeycloakOrgId(UUID.randomUUID());

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    void shouldFindByKeycloakOrgId_ReturnsCorrectOrg_WhenMultipleExist() {
        // Given
        organisationRepository.save(createOrganisation(KEYCLOAK_ORG_ID, "Org 1"));
        organisationRepository.save(createOrganisation(OTHER_KEYCLOAK_ORG_ID, "Org 2"));

        // When
        Optional<Organisation> found = organisationRepository.findByKeycloakOrgId(OTHER_KEYCLOAK_ORG_ID);

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Org 2");
    }

    @Test
    void shouldUpdateOrganisationName() {
        // Given
        Organisation org = organisationRepository.save(createOrganisation(KEYCLOAK_ORG_ID, "Original Name"));
        Long id = org.getId();

        // When
        org.setName("Updated Name");
        organisationRepository.save(org);

        // Then
        Optional<Organisation> updated = organisationRepository.findById(id);
        assertThat(updated).isPresent();
        assertThat(updated.get().getName()).isEqualTo("Updated Name");
    }

    @Test
    void shouldDeleteOrganisation() {
        // Given
        Organisation org = organisationRepository.save(createOrganisation(KEYCLOAK_ORG_ID, "To Delete"));
        Long id = org.getId();

        // When
        organisationRepository.deleteById(id);

        // Then
        Optional<Organisation> found = organisationRepository.findById(id);
        assertThat(found).isEmpty();
    }

    @Test
    void shouldAssignSequentialIds() {
        // Given
        Organisation org1 = organisationRepository.save(createOrganisation(KEYCLOAK_ORG_ID, "Org 1"));
        Organisation org2 = organisationRepository.save(createOrganisation(OTHER_KEYCLOAK_ORG_ID, "Org 2"));

        // Then
        assertThat(org1.getId()).isNotNull();
        assertThat(org2.getId()).isNotNull();
        assertThat(org1.getId()).isNotEqualTo(org2.getId());
    }

    @Test
    void konfiguration_WirdGespeichertUndGeladen() {
        // Given
        Organisation org = createOrganisation(KEYCLOAK_ORG_ID, "Test Org");
        org.setKonfiguration("{\"zahlungsfrist\":\"30 Tage\"}");
        Organisation saved = organisationRepository.save(org);

        // When
        Organisation loaded = organisationRepository.findById(saved.getId()).orElseThrow();

        // Then
        assertThat(loaded.getKonfiguration()).contains("30 Tage");
    }

    @Test
    void konfiguration_IstNullbar() {
        // Given — konfiguration nicht gesetzt
        Organisation org = createOrganisation(OTHER_KEYCLOAK_ORG_ID, "Org ohne Einstellungen");
        Organisation saved = organisationRepository.save(org);

        // When
        Organisation loaded = organisationRepository.findById(saved.getId()).orElseThrow();

        // Then
        assertThat(loaded.getKonfiguration()).isNull();
    }

    @Test
    void konfiguration_WirdAktualisiert() {
        // Given
        Organisation org = createOrganisation(KEYCLOAK_ORG_ID, "Test Org");
        org.setKonfiguration("{\"zahlungsfrist\":\"30 Tage\"}");
        Organisation saved = organisationRepository.save(org);

        // When
        saved.setKonfiguration("{\"zahlungsfrist\":\"60 Tage\",\"iban\":\"CH7006300016946459910\"}");
        organisationRepository.save(saved);

        // Then
        Organisation updated = organisationRepository.findById(saved.getId()).orElseThrow();
        assertThat(updated.getKonfiguration()).contains("60 Tage");
        assertThat(updated.getKonfiguration()).doesNotContain("30 Tage");
    }
}
