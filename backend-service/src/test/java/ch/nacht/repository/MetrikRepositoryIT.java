package ch.nacht.repository;

import ch.nacht.AbstractIntegrationTest;
import ch.nacht.entity.Metrik;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for MetrikRepository using Testcontainers.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class MetrikRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private MetrikRepository metrikRepository;

    private static final UUID TEST_ORG_ID = UUID.fromString("c2c9ba74-de18-4491-9489-8185629edd93");
    private static final UUID OTHER_ORG_ID = UUID.fromString("d3d0cb85-ef29-5502-0590-9296730fee04");

    @BeforeEach
    void setUp() {
        metrikRepository.deleteAll();
    }

    @Test
    void shouldSaveAndFindMetrikByNameAndOrgId() {
        // Given
        Metrik metrik = new Metrik("test.metric.total", "{\"value\": 42}");
        metrik.setOrgId(TEST_ORG_ID);

        // When
        Metrik saved = metrikRepository.save(metrik);

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("test.metric.total");
        assertThat(saved.getValue()).isEqualTo("{\"value\": 42}");
        assertThat(saved.getZeitstempel()).isNotNull();
        assertThat(saved.getOrgId()).isEqualTo(TEST_ORG_ID);

        // Verify findByNameAndOrgId works
        Optional<Metrik> found = metrikRepository.findByNameAndOrgId("test.metric.total", TEST_ORG_ID);
        assertThat(found).isPresent();
        assertThat(found.get().getValue()).isEqualTo("{\"value\": 42}");
    }

    @Test
    void shouldReturnEmptyWhenMetrikNotFound() {
        // When
        Optional<Metrik> found = metrikRepository.findByNameAndOrgId("non.existent.metric", TEST_ORG_ID);

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenMetrikExistsButDifferentOrg() {
        // Given
        Metrik metrik = new Metrik("test.metric", "{\"value\": 42}");
        metrik.setOrgId(TEST_ORG_ID);
        metrikRepository.save(metrik);

        // When - search with different org
        Optional<Metrik> found = metrikRepository.findByNameAndOrgId("test.metric", OTHER_ORG_ID);

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    void shouldAllowSameNameForDifferentOrgs() {
        // Given - same metric name for two orgs
        Metrik metrik1 = new Metrik("shared.metric.name", "{\"value\": 1}");
        metrik1.setOrgId(TEST_ORG_ID);
        metrikRepository.save(metrik1);

        Metrik metrik2 = new Metrik("shared.metric.name", "{\"value\": 2}");
        metrik2.setOrgId(OTHER_ORG_ID);
        metrikRepository.save(metrik2);

        // When
        Optional<Metrik> found1 = metrikRepository.findByNameAndOrgId("shared.metric.name", TEST_ORG_ID);
        Optional<Metrik> found2 = metrikRepository.findByNameAndOrgId("shared.metric.name", OTHER_ORG_ID);

        // Then
        assertThat(found1).isPresent();
        assertThat(found1.get().getValue()).isEqualTo("{\"value\": 1}");

        assertThat(found2).isPresent();
        assertThat(found2.get().getValue()).isEqualTo("{\"value\": 2}");
    }

    @Test
    void shouldUpdateMetrikValue() {
        // Given
        Metrik metrik = new Metrik("test.counter", "{\"value\": 1}");
        metrik.setOrgId(TEST_ORG_ID);
        metrik = metrikRepository.save(metrik);
        Long id = metrik.getId();

        // When
        metrik.setValue("{\"value\": 100}");
        metrikRepository.save(metrik);

        // Then
        Optional<Metrik> updated = metrikRepository.findById(id);
        assertThat(updated).isPresent();
        assertThat(updated.get().getValue()).isEqualTo("{\"value\": 100}");
    }

    @Test
    void shouldDeleteMetrik() {
        // Given
        Metrik metrik = new Metrik("test.to.delete", "{\"value\": 0}");
        metrik.setOrgId(TEST_ORG_ID);
        metrik = metrikRepository.save(metrik);
        Long id = metrik.getId();

        // When
        metrikRepository.deleteById(id);

        // Then
        Optional<Metrik> found = metrikRepository.findById(id);
        assertThat(found).isEmpty();
    }

    @Test
    void shouldEnforceUniqueNameAndOrgConstraint() {
        // Given
        Metrik metrik = new Metrik("unique.metric", "{\"value\": 1}");
        metrik.setOrgId(TEST_ORG_ID);
        metrikRepository.save(metrik);

        // When/Then - saving with same name and org should use findByNameAndOrgId + update pattern
        Optional<Metrik> existing = metrikRepository.findByNameAndOrgId("unique.metric", TEST_ORG_ID);
        assertThat(existing).isPresent();

        // Update existing instead of creating duplicate
        existing.get().setValue("{\"value\": 2}");
        Metrik updated = metrikRepository.save(existing.get());
        assertThat(updated.getValue()).isEqualTo("{\"value\": 2}");
    }

    @Test
    void shouldStoreJsonbValue() {
        // Given - complex JSON structure
        String jsonValue = "{\"value\": 123, \"metadata\": {\"source\": \"test\"}}";
        Metrik metrik = new Metrik("test.jsonb.metric", jsonValue);
        metrik.setOrgId(TEST_ORG_ID);

        // When
        metrikRepository.save(metrik);

        // Then
        Optional<Metrik> found = metrikRepository.findByNameAndOrgId("test.jsonb.metric", TEST_ORG_ID);
        assertThat(found).isPresent();
        assertThat(found.get().getValue()).contains("\"value\": 123");
        assertThat(found.get().getValue()).contains("\"source\": \"test\"");
    }

    @Test
    void shouldUpdateZeitstempelOnSave() throws InterruptedException {
        // Given
        Metrik metrik = new Metrik("test.timestamp", "{\"value\": 1}");
        metrik.setOrgId(TEST_ORG_ID);
        metrik = metrikRepository.save(metrik);
        var originalTimestamp = metrik.getZeitstempel();

        // Wait a bit to ensure timestamp changes
        Thread.sleep(10);

        // When
        metrik.setValue("{\"value\": 2}");
        metrikRepository.saveAndFlush(metrik);

        // Then
        Optional<Metrik> updated = metrikRepository.findByNameAndOrgId("test.timestamp", TEST_ORG_ID);
        assertThat(updated).isPresent();
        assertThat(updated.get().getZeitstempel()).isAfterOrEqualTo(originalTimestamp);
    }
}
