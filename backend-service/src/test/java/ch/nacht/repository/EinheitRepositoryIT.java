package ch.nacht.repository;

import ch.nacht.AbstractIntegrationTest;
import ch.nacht.entity.Einheit;
import ch.nacht.entity.EinheitTyp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for EinheitRepository using Testcontainers.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class EinheitRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private EinheitRepository einheitRepository;

    private static final UUID TEST_ORG_ID = UUID.fromString("c2c9ba74-de18-4491-9489-8185629edd93");

    @BeforeEach
    void setUp() {
        einheitRepository.deleteAll();
    }

    private Einheit createEinheit(String name, EinheitTyp typ) {
        Einheit einheit = new Einheit(name, typ);
        einheit.setOrgId(TEST_ORG_ID);
        return einheit;
    }

    @Test
    void shouldSaveAndFindEinheit() {
        // Given
        Einheit einheit = createEinheit("Test Consumer", EinheitTyp.CONSUMER);

        // When
        Einheit saved = einheitRepository.save(einheit);

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("Test Consumer");
        assertThat(saved.getTyp()).isEqualTo(EinheitTyp.CONSUMER);
        assertThat(saved.getOrgId()).isEqualTo(TEST_ORG_ID);

        // Verify we can find it
        Optional<Einheit> found = einheitRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Test Consumer");
    }

    @Test
    void shouldFindAllByOrderByNameAsc() {
        // Given
        einheitRepository.save(createEinheit("Zebra", EinheitTyp.CONSUMER));
        einheitRepository.save(createEinheit("Alpha", EinheitTyp.PRODUCER));
        einheitRepository.save(createEinheit("Beta", EinheitTyp.CONSUMER));

        // When
        List<Einheit> result = einheitRepository.findAllByOrderByNameAsc();

        // Then
        assertThat(result).hasSize(3);
        assertThat(result.get(0).getName()).isEqualTo("Alpha");
        assertThat(result.get(1).getName()).isEqualTo("Beta");
        assertThat(result.get(2).getName()).isEqualTo("Zebra");
    }

    @Test
    void shouldDeleteEinheit() {
        // Given
        Einheit einheit = einheitRepository.save(createEinheit("To Delete", EinheitTyp.CONSUMER));
        Long id = einheit.getId();

        // When
        einheitRepository.deleteById(id);

        // Then
        Optional<Einheit> found = einheitRepository.findById(id);
        assertThat(found).isEmpty();
    }

    @Test
    void shouldUpdateEinheit() {
        // Given
        Einheit einheit = einheitRepository.save(createEinheit("Original Name", EinheitTyp.CONSUMER));
        Long id = einheit.getId();

        // When
        einheit.setName("Updated Name");
        einheit.setTyp(EinheitTyp.PRODUCER);
        einheitRepository.save(einheit);

        // Then
        Optional<Einheit> updated = einheitRepository.findById(id);
        assertThat(updated).isPresent();
        assertThat(updated.get().getName()).isEqualTo("Updated Name");
        assertThat(updated.get().getTyp()).isEqualTo(EinheitTyp.PRODUCER);
    }

    @Test
    void shouldEnforceNotNullConstraints() {
        // Given
        Einheit einheit = createEinheit("Valid Name", EinheitTyp.CONSUMER);

        // When
        Einheit saved = einheitRepository.save(einheit);

        // Then
        assertThat(saved.getId()).isNotNull();
    }
}
