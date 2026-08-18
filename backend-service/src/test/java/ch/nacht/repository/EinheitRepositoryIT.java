package ch.nacht.repository;

import ch.nacht.AbstractIntegrationTest;
import ch.nacht.entity.Einheit;
import ch.nacht.entity.EinheitTyp;
import ch.nacht.entity.Organisation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
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

    @Autowired
    private OrganisationRepository organisationRepository;

    private Long TEST_ORG_ID;

    @BeforeEach
    void setUp() {
        einheitRepository.deleteAll();
        Organisation org = new Organisation();
        org.setKeycloakOrgId(UUID.fromString("c2c9ba74-de18-4491-9489-8185629edd93"));
        org.setName("Test Organisation");
        org.setErstelltAm(LocalDateTime.now());
        TEST_ORG_ID = organisationRepository.save(org).getId();
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

    // ==================== Ladestationen (Specs/Ladestationen.md) ====================

    private Einheit createEinheit(String name, EinheitTyp typ, String messpunkt) {
        Einheit einheit = createEinheit(name, typ);
        einheit.setMesspunkt(messpunkt);
        return einheit;
    }

    @Test
    void shouldSaveLadestationWithRfidInMesspunkt() {
        Einheit saved = einheitRepository.save(
                createEinheit("Ladestation 1", EinheitTyp.LADESTATION, "RFID-0123456789"));

        assertThat(saved.getTyp()).isEqualTo(EinheitTyp.LADESTATION);
        assertThat(saved.getMesspunkt()).isEqualTo("RFID-0123456789");
    }

    @Test
    void shouldSaveLadestationWithMesspunktOfMaximumLength() {
        // messpunkt ist VARCHAR(50) - laut Spec genuegt das fuer eine RFID
        String rfid = "R".repeat(50);

        Einheit saved = einheitRepository.save(
                createEinheit("Ladestation Max", EinheitTyp.LADESTATION, rfid));

        assertThat(saved.getMesspunkt()).hasSize(50);
    }

    @Test
    void shouldDetectOtherLadestationWithSameMesspunkt() {
        einheitRepository.save(createEinheit("Ladestation 1", EinheitTyp.LADESTATION, "RFID-001"));

        // Beim Anlegen (excludeId = -1) kollidiert die RFID
        assertThat(einheitRepository.existsLadestationWithMesspunkt("RFID-001", -1L)).isTrue();
    }

    @Test
    void shouldNotDetectUnusedMesspunkt() {
        einheitRepository.save(createEinheit("Ladestation 1", EinheitTyp.LADESTATION, "RFID-001"));

        assertThat(einheitRepository.existsLadestationWithMesspunkt("RFID-002", -1L)).isFalse();
    }

    @Test
    void shouldExcludeSelfWhenCheckingLadestationMesspunkt() {
        Einheit ladestation = einheitRepository.save(
                createEinheit("Ladestation 1", EinheitTyp.LADESTATION, "RFID-001"));

        // Beim Update darf die eigene RFID nicht mit sich selbst kollidieren
        assertThat(einheitRepository.existsLadestationWithMesspunkt("RFID-001", ladestation.getId()))
                .isFalse();
    }

    @Test
    void shouldIgnoreMesspunktOfOtherEinheitTypen() {
        // BEZUG und RUECKLIEFERUNG teilen sich bewusst einen Messpunkt (Register-Projektion),
        // deshalb ist die Eindeutigkeit auf LADESTATION beschraenkt
        einheitRepository.save(createEinheit("Bezug", EinheitTyp.BEZUG, "BILANZ-1"));
        einheitRepository.save(createEinheit("Ruecklieferung", EinheitTyp.RUECKLIEFERUNG, "BILANZ-1"));
        einheitRepository.save(createEinheit("Wohnung A", EinheitTyp.CONSUMER, "MP-001"));

        assertThat(einheitRepository.existsLadestationWithMesspunkt("BILANZ-1", -1L)).isFalse();
        assertThat(einheitRepository.existsLadestationWithMesspunkt("MP-001", -1L)).isFalse();
        // Der Ingest-Pfad bleibt unveraendert: beide Bilanz-Einheiten sind auflösbar
        assertThat(einheitRepository.findAllByOrgIdAndMesspunkt(TEST_ORG_ID, "BILANZ-1")).hasSize(2);
    }

    @Test
    void shouldFindLadestationByOrgIdAndMesspunkt() {
        // Das Repository filtert Ladestationen NICHT aus - das macht der MqttIngestService,
        // damit eine RFID nie versehentlich Messwerte erhält
        einheitRepository.save(createEinheit("Ladestation 1", EinheitTyp.LADESTATION, "RFID-001"));

        List<Einheit> result = einheitRepository.findAllByOrgIdAndMesspunkt(TEST_ORG_ID, "RFID-001");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTyp()).isEqualTo(EinheitTyp.LADESTATION);
    }

    @Test
    void shouldNotCountLadestationAsBilanzTyp() {
        // LADESTATION ist kein Bilanz-Typ: mehrere Ladestationen je Mandant sind zulaessig
        einheitRepository.save(createEinheit("Ladestation 1", EinheitTyp.LADESTATION, "RFID-001"));
        einheitRepository.save(createEinheit("Ladestation 2", EinheitTyp.LADESTATION, "RFID-002"));

        assertThat(einheitRepository.existsByTyp(EinheitTyp.BEZUG)).isFalse();
        assertThat(einheitRepository.findAllByOrderByNameAsc()).hasSize(2);
    }

    @Test
    void shouldSortLadestationenTogetherWithOtherEinheiten() {
        // Ladestationen werden in den Auswahllisten nicht ausgeblendet (FR-3)
        einheitRepository.save(createEinheit("Wohnung A", EinheitTyp.CONSUMER, null));
        einheitRepository.save(createEinheit("Ladestation 1", EinheitTyp.LADESTATION, "RFID-001"));

        assertThat(einheitRepository.findAllByOrderByNameAsc())
                .extracting(Einheit::getName)
                .containsExactly("Ladestation 1", "Wohnung A");
    }
}
