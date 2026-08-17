package ch.nacht.repository;

import ch.nacht.AbstractIntegrationTest;
import ch.nacht.entity.Einheit;
import ch.nacht.entity.EinheitTyp;
import ch.nacht.entity.Mieter;
import ch.nacht.entity.Organisation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for MieterRepository using Testcontainers.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class MieterRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private MieterRepository mieterRepository;

    @Autowired
    private EinheitRepository einheitRepository;

    @Autowired
    private OrganisationRepository organisationRepository;

    private Long TEST_ORG_ID;
    private Long einheitId;

    @BeforeEach
    void setUp() {
        mieterRepository.deleteAll();
        einheitRepository.deleteAll();

        Organisation org = new Organisation();
        org.setKeycloakOrgId(UUID.fromString("d3d8cb85-ef29-4592-a59a-9296740fee04"));
        org.setName("Mieter Test Organisation");
        org.setErstelltAm(LocalDateTime.now());
        TEST_ORG_ID = organisationRepository.save(org).getId();

        Einheit einheit = new Einheit("Test Einheit", EinheitTyp.CONSUMER);
        einheit.setOrgId(TEST_ORG_ID);
        einheitId = einheitRepository.save(einheit).getId();
    }

    @AfterEach
    void tearDown() {
        mieterRepository.deleteAll();
        einheitRepository.deleteAll();
    }

    private Mieter createMieter(String name, LocalDate mietbeginn, LocalDate mietende) {
        Mieter mieter = new Mieter();
        mieter.setOrgId(TEST_ORG_ID);
        mieter.setName(name);
        mieter.setStrasse("Teststrasse 1");
        mieter.setPlz("8000");
        mieter.setOrt("Zürich");
        mieter.setMietbeginn(mietbeginn);
        mieter.setMietende(mietende);
        mieter.setEinheitId(einheitId);
        return mieter;
    }

    @Test
    void shouldSaveAndFindMieter() {
        // Given
        Mieter mieter = createMieter("Max Mustermann", LocalDate.of(2024, 1, 1), null);

        // When
        Mieter saved = mieterRepository.save(mieter);

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("Max Mustermann");
        assertThat(saved.getMietbeginn()).isEqualTo(LocalDate.of(2024, 1, 1));
        assertThat(saved.getMietende()).isNull();
        assertThat(saved.getEinheitId()).isEqualTo(einheitId);
        assertThat(saved.getOrgId()).isEqualTo(TEST_ORG_ID);
    }

    @Test
    void shouldFindAllByOrderByEinheitIdAscMietbeginnDesc() {
        // Given – two mieter on the same Einheit, one starts later
        mieterRepository.save(createMieter("Erster Mieter", LocalDate.of(2023, 1, 1), LocalDate.of(2023, 12, 31)));
        mieterRepository.save(createMieter("Zweiter Mieter", LocalDate.of(2024, 1, 1), null));

        // When
        List<Mieter> result = mieterRepository.findAllByOrderByEinheitIdAscMietbeginnDesc();

        // Then
        assertThat(result).hasSize(2);
        // Within the same einheitId, ordered by mietbeginn desc → newest first
        assertThat(result.get(0).getMietbeginn()).isEqualTo(LocalDate.of(2024, 1, 1));
        assertThat(result.get(1).getMietbeginn()).isEqualTo(LocalDate.of(2023, 1, 1));
    }

    @Test
    void shouldFindByEinheitIdOrderByMietbeginnDesc() {
        // Given
        mieterRepository.save(createMieter("Alt", LocalDate.of(2022, 6, 1), LocalDate.of(2023, 5, 31)));
        mieterRepository.save(createMieter("Neu", LocalDate.of(2023, 6, 1), null));

        // When
        List<Mieter> result = mieterRepository.findByEinheitIdOrderByMietbeginnDesc(einheitId);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("Neu");
        assertThat(result.get(1).getName()).isEqualTo("Alt");
    }

    @Test
    void shouldFindByEinheitIdOrderByMietbeginnDesc_ReturnsEmptyForOtherEinheit() {
        // Given
        mieterRepository.save(createMieter("Mieter A", LocalDate.of(2024, 1, 1), null));

        // When – search for a non-existent einheit
        List<Mieter> result = mieterRepository.findByEinheitIdOrderByMietbeginnDesc(99999L);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void shouldDetectOverlappingMieterOpenEnded_WhenOpenEndedExists() {
        // Given – a current open-ended tenant
        mieterRepository.save(createMieter("Aktueller Mieter", LocalDate.of(2024, 1, 1), null));

        // When – new tenant starting any date (open-ended) → overlap exists
        boolean overlaps = mieterRepository.existsOverlappingMieterOpenEnded(
                einheitId, LocalDate.of(2024, 6, 1), -1L);

        // Then
        assertThat(overlaps).isTrue();
    }

    @Test
    void shouldNotDetectOverlappingMieterOpenEnded_WhenPreviousTenantEnded() {
        // Given – a past tenant who already moved out before the new one starts
        mieterRepository.save(createMieter("Alter Mieter", LocalDate.of(2023, 1, 1), LocalDate.of(2023, 12, 31)));

        // When – new open-ended tenant starting after old one ended → no overlap
        boolean overlaps = mieterRepository.existsOverlappingMieterOpenEnded(
                einheitId, LocalDate.of(2024, 1, 1), -1L);

        // Then
        assertThat(overlaps).isFalse();
    }

    @Test
    void shouldExcludeSelfWhenCheckingOpenEndedOverlap() {
        // Given – existing open-ended mieter
        Mieter existing = mieterRepository.save(createMieter("Selbst", LocalDate.of(2024, 1, 1), null));

        // When – check overlap excluding itself (edit scenario)
        boolean overlaps = mieterRepository.existsOverlappingMieterOpenEnded(
                einheitId, existing.getMietbeginn(), existing.getId());

        // Then – should not report overlap against itself
        assertThat(overlaps).isFalse();
    }

    @Test
    void shouldDetectOverlappingMieterBounded_WhenPeriodsOverlap() {
        // Given – existing tenant 2024-01-01 to 2024-12-31
        mieterRepository.save(createMieter("Bestehender Mieter",
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)));

        // When – new bounded tenant overlapping the middle
        boolean overlaps = mieterRepository.existsOverlappingMieterBounded(
                einheitId,
                LocalDate.of(2024, 6, 1),
                LocalDate.of(2025, 3, 31),
                -1L);

        // Then
        assertThat(overlaps).isTrue();
    }

    @Test
    void shouldNotDetectOverlappingMieterBounded_WhenPeriodsAdjacent() {
        // Given – existing tenant ends 2023-12-31
        mieterRepository.save(createMieter("Vorheriger Mieter",
                LocalDate.of(2023, 1, 1), LocalDate.of(2023, 12, 31)));

        // When – new bounded tenant starts exactly on 2024-01-01 (adjacent, not overlapping)
        boolean overlaps = mieterRepository.existsOverlappingMieterBounded(
                einheitId,
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 12, 31),
                -1L);

        // Then
        assertThat(overlaps).isFalse();
    }

    @Test
    void shouldDetectOtherMieterWithoutMietende_WhenOpenEndedExists() {
        // Given – existing open-ended tenant
        mieterRepository.save(createMieter("Offener Mieter", LocalDate.of(2024, 1, 1), null));

        // When – check for another open-ended tenant on the same unit
        boolean exists = mieterRepository.existsOtherMieterWithoutMietende(einheitId, -1L);

        // Then
        assertThat(exists).isTrue();
    }

    @Test
    void shouldNotDetectOtherMieterWithoutMietende_WhenAllTenantsHaveMietende() {
        // Given – all tenants have an end date
        mieterRepository.save(createMieter("Abgeschlossen", LocalDate.of(2023, 1, 1), LocalDate.of(2023, 12, 31)));

        // When
        boolean exists = mieterRepository.existsOtherMieterWithoutMietende(einheitId, -1L);

        // Then
        assertThat(exists).isFalse();
    }

    @Test
    void shouldExcludeSelfWhenCheckingOtherMieterWithoutMietende() {
        // Given – existing open-ended mieter
        Mieter existing = mieterRepository.save(createMieter("Selbst offen", LocalDate.of(2024, 1, 1), null));

        // When – check excluding itself
        boolean exists = mieterRepository.existsOtherMieterWithoutMietende(einheitId, existing.getId());

        // Then – should not count itself
        assertThat(exists).isFalse();
    }

    @Test
    void shouldFindByEinheitIdAndQuartal_WhenMieterOverlapsQuartal() {
        // Given – tenant active during Q1 2024
        mieterRepository.save(createMieter("Quartal Mieter",
                LocalDate.of(2024, 1, 15), LocalDate.of(2024, 6, 30)));

        // When – query for Q1 2024 (Jan–Mar)
        List<Mieter> result = mieterRepository.findByEinheitIdAndQuartal(
                einheitId,
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 3, 31));

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Quartal Mieter");
    }

    @Test
    void shouldFindByEinheitIdAndQuartal_WhenMieterIsOpenEndedAndStartsBeforeQuartal() {
        // Given – open-ended tenant who started before the quarter
        mieterRepository.save(createMieter("Dauerhaft",
                LocalDate.of(2023, 6, 1), null));

        // When – query for Q2 2024
        List<Mieter> result = mieterRepository.findByEinheitIdAndQuartal(
                einheitId,
                LocalDate.of(2024, 4, 1),
                LocalDate.of(2024, 6, 30));

        // Then – open-ended tenant must be included
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMietende()).isNull();
    }

    @Test
    void shouldNotFindByEinheitIdAndQuartal_WhenMieterEndedBeforeQuartal() {
        // Given – tenant who ended before the quarter started
        mieterRepository.save(createMieter("Abgelaufen",
                LocalDate.of(2022, 1, 1), LocalDate.of(2022, 12, 31)));

        // When – query for Q1 2024
        List<Mieter> result = mieterRepository.findByEinheitIdAndQuartal(
                einheitId,
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 3, 31));

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void shouldFindByEinheitIdAndQuartal_OrderedByMietbeginn() {
        // Given – two tenants within the same quarter
        mieterRepository.save(createMieter("Späterer Mieter",
                LocalDate.of(2024, 2, 1), LocalDate.of(2024, 3, 15)));
        mieterRepository.save(createMieter("Früherer Mieter",
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31)));

        // When
        List<Mieter> result = mieterRepository.findByEinheitIdAndQuartal(
                einheitId,
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 3, 31));

        // Then – ordered by mietbeginn ascending
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("Früherer Mieter");
        assertThat(result.get(1).getName()).isEqualTo("Späterer Mieter");
    }
}
