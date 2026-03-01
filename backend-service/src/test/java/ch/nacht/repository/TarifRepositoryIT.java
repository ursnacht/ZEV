package ch.nacht.repository;

import ch.nacht.AbstractIntegrationTest;
import ch.nacht.entity.Organisation;
import ch.nacht.entity.Tarif;
import ch.nacht.entity.TarifTyp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for TarifRepository using Testcontainers.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class TarifRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private TarifRepository tarifRepository;

    @Autowired
    private OrganisationRepository organisationRepository;

    private Long TEST_ORG_ID;

    @BeforeEach
    void setUp() {
        tarifRepository.deleteAll();
        Organisation org = new Organisation();
        org.setKeycloakOrgId(UUID.fromString("c2c9ba74-de18-4491-9489-8185629edd93"));
        org.setName("Test Organisation");
        org.setErstelltAm(LocalDateTime.now());
        TEST_ORG_ID = organisationRepository.save(org).getId();
    }

    private Tarif createTarif(String bezeichnung, TarifTyp typ, BigDecimal preis, LocalDate von, LocalDate bis) {
        Tarif tarif = new Tarif(bezeichnung, typ, preis, von, bis);
        tarif.setOrgId(TEST_ORG_ID);
        return tarif;
    }

    @Test
    void shouldSaveAndFindTarif() {
        // Given
        Tarif tarif = createTarif(
            "ZEV Tarif 2024",
            TarifTyp.ZEV,
            new BigDecimal("0.20000"),
            LocalDate.of(2024, 1, 1),
            LocalDate.of(2024, 12, 31)
        );

        // When
        Tarif saved = tarifRepository.save(tarif);

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getBezeichnung()).isEqualTo("ZEV Tarif 2024");
        assertThat(saved.getTariftyp()).isEqualTo(TarifTyp.ZEV);
        assertThat(saved.getPreis()).isEqualByComparingTo(new BigDecimal("0.20000"));
        assertThat(saved.getGueltigVon()).isEqualTo(LocalDate.of(2024, 1, 1));
        assertThat(saved.getGueltigBis()).isEqualTo(LocalDate.of(2024, 12, 31));
        assertThat(saved.getOrgId()).isEqualTo(TEST_ORG_ID);

        Optional<Tarif> found = tarifRepository.findById(saved.getId());
        assertThat(found).isPresent();
    }

    @Test
    void shouldFindAllByOrderByTariftypAscGueltigVonDesc() {
        // Given
        tarifRepository.save(createTarif(
            "VNB 2024", TarifTyp.VNB, new BigDecimal("0.34"),
            LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)
        ));
        tarifRepository.save(createTarif(
            "ZEV 2023", TarifTyp.ZEV, new BigDecimal("0.19"),
            LocalDate.of(2023, 1, 1), LocalDate.of(2023, 12, 31)
        ));
        tarifRepository.save(createTarif(
            "ZEV 2024", TarifTyp.ZEV, new BigDecimal("0.20"),
            LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)
        ));

        // When
        List<Tarif> result = tarifRepository.findAllByOrderByTariftypAscGueltigVonDesc();

        // Then
        assertThat(result).hasSize(3);
        // VNB comes before ZEV (alphabetically)
        assertThat(result.get(0).getTariftyp()).isEqualTo(TarifTyp.VNB);
        // ZEV 2024 before ZEV 2023 (descending by gueltigVon)
        assertThat(result.get(1).getBezeichnung()).isEqualTo("ZEV 2024");
        assertThat(result.get(2).getBezeichnung()).isEqualTo("ZEV 2023");
    }

    @Test
    void shouldFindByTariftypAndZeitraumOverlapping_ExactMatch() {
        // Given
        Tarif tarif = tarifRepository.save(createTarif(
            "ZEV 2024", TarifTyp.ZEV, new BigDecimal("0.20"),
            LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)
        ));

        // When - exact match
        List<Tarif> result = tarifRepository.findByTariftypAndZeitraumOverlapping(
            TarifTyp.ZEV,
            LocalDate.of(2024, 1, 1),
            LocalDate.of(2024, 12, 31)
        );

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(tarif.getId());
    }

    @Test
    void shouldFindByTariftypAndZeitraumOverlapping_PartialOverlap() {
        // Given
        tarifRepository.save(createTarif(
            "ZEV 2024", TarifTyp.ZEV, new BigDecimal("0.20"),
            LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)
        ));

        // When - query only March
        List<Tarif> result = tarifRepository.findByTariftypAndZeitraumOverlapping(
            TarifTyp.ZEV,
            LocalDate.of(2024, 3, 1),
            LocalDate.of(2024, 3, 31)
        );

        // Then
        assertThat(result).hasSize(1);
    }

    @Test
    void shouldFindByTariftypAndZeitraumOverlapping_NoMatch_WrongType() {
        // Given
        tarifRepository.save(createTarif(
            "ZEV 2024", TarifTyp.ZEV, new BigDecimal("0.20"),
            LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)
        ));

        // When - query VNB type
        List<Tarif> result = tarifRepository.findByTariftypAndZeitraumOverlapping(
            TarifTyp.VNB,
            LocalDate.of(2024, 1, 1),
            LocalDate.of(2024, 12, 31)
        );

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void shouldFindByTariftypAndZeitraumOverlapping_NoMatch_OutOfRange() {
        // Given
        tarifRepository.save(createTarif(
            "ZEV 2024", TarifTyp.ZEV, new BigDecimal("0.20"),
            LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)
        ));

        // When - query 2023 (before tarif validity)
        List<Tarif> result = tarifRepository.findByTariftypAndZeitraumOverlapping(
            TarifTyp.ZEV,
            LocalDate.of(2023, 1, 1),
            LocalDate.of(2023, 12, 31)
        );

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void shouldFindByTariftypAndZeitraumOverlapping_MultipleTarifs() {
        // Given
        tarifRepository.save(createTarif(
            "ZEV H1 2024", TarifTyp.ZEV, new BigDecimal("0.19"),
            LocalDate.of(2024, 1, 1), LocalDate.of(2024, 6, 30)
        ));
        tarifRepository.save(createTarif(
            "ZEV H2 2024", TarifTyp.ZEV, new BigDecimal("0.21"),
            LocalDate.of(2024, 7, 1), LocalDate.of(2024, 12, 31)
        ));

        // When - query spans both tarifs
        List<Tarif> result = tarifRepository.findByTariftypAndZeitraumOverlapping(
            TarifTyp.ZEV,
            LocalDate.of(2024, 1, 1),
            LocalDate.of(2024, 12, 31)
        );

        // Then
        assertThat(result).hasSize(2);
        // Should be ordered by gueltigVon
        assertThat(result.get(0).getBezeichnung()).isEqualTo("ZEV H1 2024");
        assertThat(result.get(1).getBezeichnung()).isEqualTo("ZEV H2 2024");
    }

    @Test
    void shouldExistsOverlappingTarif_ReturnTrue_WhenOverlapExists() {
        // Given
        tarifRepository.save(createTarif(
            "ZEV 2024", TarifTyp.ZEV, new BigDecimal("0.20"),
            LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)
        ));

        // When
        boolean exists = tarifRepository.existsOverlappingTarif(
            TarifTyp.ZEV,
            LocalDate.of(2024, 6, 1),
            LocalDate.of(2024, 8, 31),
            -1L  // new tarif (no ID to exclude)
        );

        // Then
        assertThat(exists).isTrue();
    }

    @Test
    void shouldExistsOverlappingTarif_ReturnFalse_WhenNoOverlap() {
        // Given
        tarifRepository.save(createTarif(
            "ZEV 2024", TarifTyp.ZEV, new BigDecimal("0.20"),
            LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)
        ));

        // When - check 2025 (no overlap)
        boolean exists = tarifRepository.existsOverlappingTarif(
            TarifTyp.ZEV,
            LocalDate.of(2025, 1, 1),
            LocalDate.of(2025, 12, 31),
            -1L
        );

        // Then
        assertThat(exists).isFalse();
    }

    @Test
    void shouldExistsOverlappingTarif_ExcludeOwnId() {
        // Given
        Tarif tarif = tarifRepository.save(createTarif(
            "ZEV 2024", TarifTyp.ZEV, new BigDecimal("0.20"),
            LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)
        ));

        // When - exclude own ID (simulates update)
        boolean exists = tarifRepository.existsOverlappingTarif(
            TarifTyp.ZEV,
            LocalDate.of(2024, 1, 1),
            LocalDate.of(2024, 12, 31),
            tarif.getId()
        );

        // Then
        assertThat(exists).isFalse();
    }

    @Test
    void shouldExistsOverlappingTarif_DifferentType_NoConflict() {
        // Given
        tarifRepository.save(createTarif(
            "ZEV 2024", TarifTyp.ZEV, new BigDecimal("0.20"),
            LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)
        ));

        // When - check VNB type (no conflict)
        boolean exists = tarifRepository.existsOverlappingTarif(
            TarifTyp.VNB,
            LocalDate.of(2024, 1, 1),
            LocalDate.of(2024, 12, 31),
            -1L
        );

        // Then
        assertThat(exists).isFalse();
    }

    @Test
    void shouldDeleteTarif() {
        // Given
        Tarif tarif = tarifRepository.save(createTarif(
            "ZEV 2024", TarifTyp.ZEV, new BigDecimal("0.20"),
            LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)
        ));
        Long id = tarif.getId();

        // When
        tarifRepository.deleteById(id);

        // Then
        Optional<Tarif> found = tarifRepository.findById(id);
        assertThat(found).isEmpty();
    }

    @Test
    void shouldUpdateTarif() {
        // Given
        Tarif tarif = tarifRepository.save(createTarif(
            "ZEV 2024", TarifTyp.ZEV, new BigDecimal("0.20"),
            LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)
        ));

        // When
        tarif.setBezeichnung("ZEV 2024 Updated");
        tarif.setPreis(new BigDecimal("0.22000"));
        tarifRepository.save(tarif);

        // Then
        Optional<Tarif> updated = tarifRepository.findById(tarif.getId());
        assertThat(updated).isPresent();
        assertThat(updated.get().getBezeichnung()).isEqualTo("ZEV 2024 Updated");
        assertThat(updated.get().getPreis()).isEqualByComparingTo(new BigDecimal("0.22000"));
    }
}
