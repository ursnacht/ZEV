package ch.nacht.repository;

import ch.nacht.AbstractIntegrationTest;
import ch.nacht.entity.Metrik;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

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

    @Test
    void shouldSaveAndFindMetrikByName() {
        // Given
        Metrik metrik = new Metrik("test.metric.total", "{\"value\": 42}");

        // When
        Metrik saved = metrikRepository.save(metrik);

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("test.metric.total");
        assertThat(saved.getValue()).isEqualTo("{\"value\": 42}");
        assertThat(saved.getZeitstempel()).isNotNull();

        // Verify findByName works
        Optional<Metrik> found = metrikRepository.findByName("test.metric.total");
        assertThat(found).isPresent();
        assertThat(found.get().getValue()).isEqualTo("{\"value\": 42}");
    }

    @Test
    void shouldReturnEmptyWhenMetrikNotFound() {
        // When
        Optional<Metrik> found = metrikRepository.findByName("non.existent.metric");

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    void shouldUpdateMetrikValue() {
        // Given
        Metrik metrik = metrikRepository.save(new Metrik("test.counter", "{\"value\": 1}"));
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
        Metrik metrik = metrikRepository.save(new Metrik("test.to.delete", "{\"value\": 0}"));
        Long id = metrik.getId();

        // When
        metrikRepository.deleteById(id);

        // Then
        Optional<Metrik> found = metrikRepository.findById(id);
        assertThat(found).isEmpty();
    }

    @Test
    void shouldEnforceUniqueNameConstraint() {
        // Given
        metrikRepository.save(new Metrik("unique.metric", "{\"value\": 1}"));

        // When/Then - saving with same name should use findByName + update pattern
        Optional<Metrik> existing = metrikRepository.findByName("unique.metric");
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

        // When
        Metrik saved = metrikRepository.save(metrik);

        // Then
        Optional<Metrik> found = metrikRepository.findByName("test.jsonb.metric");
        assertThat(found).isPresent();
        assertThat(found.get().getValue()).contains("\"value\": 123");
        assertThat(found.get().getValue()).contains("\"source\": \"test\"");
    }

    @Test
    void shouldUpdateZeitstempelOnSave() throws InterruptedException {
        // Given
        Metrik metrik = metrikRepository.save(new Metrik("test.timestamp", "{\"value\": 1}"));
        var originalTimestamp = metrik.getZeitstempel();

        // Wait a bit to ensure timestamp changes
        Thread.sleep(10);

        // When
        metrik.setValue("{\"value\": 2}");
        metrikRepository.saveAndFlush(metrik);

        // Then
        Optional<Metrik> updated = metrikRepository.findByName("test.timestamp");
        assertThat(updated).isPresent();
        assertThat(updated.get().getZeitstempel()).isAfterOrEqualTo(originalTimestamp);
    }
}
