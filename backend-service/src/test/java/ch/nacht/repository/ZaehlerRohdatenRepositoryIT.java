package ch.nacht.repository;

import ch.nacht.AbstractIntegrationTest;
import ch.nacht.entity.Einheit;
import ch.nacht.entity.EinheitTyp;
import ch.nacht.entity.Organisation;
import ch.nacht.entity.ZaehlerRohdaten;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integrationstests für {@link ZaehlerRohdatenRepository} (MQTT-Aggregation) und die
 * MQTT-relevante Auflösung {@link EinheitRepository#findByOrgIdAndMesspunkt}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ZaehlerRohdatenRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private ZaehlerRohdatenRepository rohdatenRepository;

    @Autowired
    private EinheitRepository einheitRepository;

    @Autowired
    private OrganisationRepository organisationRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private Long orgId;
    private Long einheitId;
    private static final String MESSPUNKT = "MP-IT-001";

    @BeforeEach
    void setUp() {
        rohdatenRepository.deleteAll();
        einheitRepository.deleteAll();

        Organisation org = new Organisation();
        org.setKeycloakOrgId(UUID.randomUUID());
        org.setName("Test Organisation");
        org.setErstelltAm(LocalDateTime.now());
        orgId = organisationRepository.save(org).getId();

        Einheit einheit = new Einheit("Wohnung 1", EinheitTyp.CONSUMER);
        einheit.setOrgId(orgId);
        einheit.setMesspunkt(MESSPUNKT);
        einheitId = einheitRepository.save(einheit).getId();
    }

    private ZaehlerRohdaten save(LocalDateTime zeit, String bezug, String einspeisung, boolean verarbeitet) {
        ZaehlerRohdaten r = new ZaehlerRohdaten(orgId, einheitId, zeit,
                new BigDecimal(bezug), new BigDecimal(einspeisung));
        r.setVerarbeitet(verarbeitet);
        r.setEmpfangenAm(LocalDateTime.now());
        return rohdatenRepository.save(r);
    }

    @Test
    void findByEinheitIdAndZeit_Found_ReturnsRow() {
        LocalDateTime zeit = LocalDateTime.of(2026, 1, 1, 10, 0);
        save(zeit, "100.0", "5.0", false);

        Optional<ZaehlerRohdaten> result = rohdatenRepository.findByEinheitIdAndZeit(einheitId, zeit);

        assertThat(result).isPresent();
        assertThat(result.get().getZaehlerstandBezug()).isEqualByComparingTo("100.0");
    }

    @Test
    void findByEinheitIdAndZeit_NotFound_ReturnsEmpty() {
        save(LocalDateTime.of(2026, 1, 1, 10, 0), "100.0", "5.0", false);

        Optional<ZaehlerRohdaten> result =
                rohdatenRepository.findByEinheitIdAndZeit(einheitId, LocalDateTime.of(2026, 1, 1, 9, 0));

        assertThat(result).isEmpty();
    }

    @Test
    void findFirstByZeitLessThanEqual_ReturnsLatestAtOrBefore() {
        save(LocalDateTime.of(2026, 1, 1, 10, 0), "100.0", "5.0", false);
        save(LocalDateTime.of(2026, 1, 1, 10, 5), "110.0", "6.0", false);
        save(LocalDateTime.of(2026, 1, 1, 10, 10), "120.0", "7.0", false);

        Optional<ZaehlerRohdaten> result = rohdatenRepository
                .findFirstByEinheitIdAndZeitLessThanEqualOrderByZeitDesc(
                        einheitId, LocalDateTime.of(2026, 1, 1, 10, 7));

        assertThat(result).isPresent();
        assertThat(result.get().getZeit()).isEqualTo(LocalDateTime.of(2026, 1, 1, 10, 5));
    }

    @Test
    void findFirstByZeitLessThanEqual_NoneBefore_ReturnsEmpty() {
        save(LocalDateTime.of(2026, 1, 1, 10, 0), "100.0", "5.0", false);

        Optional<ZaehlerRohdaten> result = rohdatenRepository
                .findFirstByEinheitIdAndZeitLessThanEqualOrderByZeitDesc(
                        einheitId, LocalDateTime.of(2026, 1, 1, 9, 0));

        assertThat(result).isEmpty();
    }

    @Test
    void findEinheitIdsWithUnverarbeitet_ReturnsOnlyUnprocessed() {
        save(LocalDateTime.of(2026, 1, 1, 10, 0), "100.0", "5.0", true);
        save(LocalDateTime.of(2026, 1, 1, 10, 5), "110.0", "6.0", false);

        List<Long> ids = rohdatenRepository.findEinheitIdsWithUnverarbeitet();

        assertThat(ids).containsExactly(einheitId);
    }

    @Test
    void findEinheitIdsWithUnverarbeitet_AllProcessed_ReturnsEmpty() {
        save(LocalDateTime.of(2026, 1, 1, 10, 0), "100.0", "5.0", true);

        assertThat(rohdatenRepository.findEinheitIdsWithUnverarbeitet()).isEmpty();
    }

    @Test
    void findFirstUnverarbeitet_ReturnsEarliest() {
        save(LocalDateTime.of(2026, 1, 1, 10, 10), "120.0", "7.0", false);
        save(LocalDateTime.of(2026, 1, 1, 10, 0), "100.0", "5.0", false);
        save(LocalDateTime.of(2026, 1, 1, 10, 5), "110.0", "6.0", true); // verarbeitet -> ignoriert

        Optional<ZaehlerRohdaten> result =
                rohdatenRepository.findFirstByEinheitIdAndVerarbeitetFalseOrderByZeitAsc(einheitId);

        assertThat(result).isPresent();
        assertThat(result.get().getZeit()).isEqualTo(LocalDateTime.of(2026, 1, 1, 10, 0));
    }

    @Test
    void existsInIntervall_ReadingWithin_True_OutsideFalse() {
        save(LocalDateTime.of(2026, 1, 1, 10, 5), "110.0", "6.0", false);

        boolean within = rohdatenRepository.existsByEinheitIdAndZeitGreaterThanAndZeitLessThanEqual(
                einheitId, LocalDateTime.of(2026, 1, 1, 10, 0), LocalDateTime.of(2026, 1, 1, 10, 15));
        boolean outside = rohdatenRepository.existsByEinheitIdAndZeitGreaterThanAndZeitLessThanEqual(
                einheitId, LocalDateTime.of(2026, 1, 1, 10, 15), LocalDateTime.of(2026, 1, 1, 10, 30));

        assertThat(within).isTrue();
        assertThat(outside).isFalse();
    }

    @Test
    void markVerarbeitet_MarksRowsUpToBoundary() {
        save(LocalDateTime.of(2026, 1, 1, 10, 0), "100.0", "5.0", false);
        save(LocalDateTime.of(2026, 1, 1, 10, 5), "110.0", "6.0", false);
        LocalDateTime spaeter = LocalDateTime.of(2026, 1, 1, 10, 20);
        save(spaeter, "130.0", "8.0", false);
        LocalDateTime jetzt = LocalDateTime.of(2026, 1, 1, 10, 15);

        int updated = rohdatenRepository.markVerarbeitet(
                einheitId, LocalDateTime.of(2026, 1, 1, 10, 15), jetzt);

        // Bulk-Update umgeht den Persistence-Context -> vor dem Re-Read leeren
        entityManager.flush();
        entityManager.clear();

        assertThat(updated).isEqualTo(2);
        List<ZaehlerRohdaten> alle = rohdatenRepository.findAll();
        long verarbeitet = alle.stream().filter(ZaehlerRohdaten::isVerarbeitet).count();
        assertThat(verarbeitet).isEqualTo(2);
        // Der spätere Stand (10:20) bleibt unverarbeitet
        Optional<ZaehlerRohdaten> spaeterRow = rohdatenRepository.findByEinheitIdAndZeit(einheitId, spaeter);
        assertThat(spaeterRow).isPresent();
        assertThat(spaeterRow.get().isVerarbeitet()).isFalse();
    }

    @Test
    void findAllByOrgIdAndMesspunkt_Found_ReturnsEinheit() {
        List<Einheit> result = einheitRepository.findAllByOrgIdAndMesspunkt(orgId, MESSPUNKT);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(einheitId);
    }

    @Test
    void findAllByOrgIdAndMesspunkt_WrongOrg_ReturnsEmpty() {
        List<Einheit> result = einheitRepository.findAllByOrgIdAndMesspunkt(orgId + 999, MESSPUNKT);

        assertThat(result).isEmpty();
    }
}
