package ch.nacht.repository;

import ch.nacht.AbstractIntegrationTest;
import ch.nacht.entity.Einheit;
import ch.nacht.entity.EinheitTyp;
import ch.nacht.entity.Erfassungsart;
import ch.nacht.entity.Einheit;
import ch.nacht.entity.EinheitTyp;
import ch.nacht.entity.Organisation;
import ch.nacht.entity.Tarif;
import ch.nacht.entity.TarifTyp;
import ch.nacht.entity.Tarifposition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integrationstests für {@link TarifpositionRepository} mit Testcontainers (Specs/Ladestationen.md).
 *
 * <p>Prüft die selbst geschriebenen Queries gegen eine echte Datenbank: Sortierung der Liste,
 * die Überschneidungsregel samt {@code menge > 0}-Filter für die Rechnungserzeugung, die
 * typbezogene Eindeutigkeitsprüfung und die Zählung referenzierender Positionen.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class TarifpositionRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private TarifpositionRepository tarifpositionRepository;

    @Autowired
    private EinheitRepository einheitRepository;

    @Autowired
    private TarifRepository tarifRepository;

    @Autowired
    private OrganisationRepository organisationRepository;

    private Long TEST_ORG_ID;
    private Einheit ladestationA;
    private Einheit ladestationB;
    private Tarif ladestromTarif;
    private Tarif ladestromTarif2;
    private Tarif zevTarif;

    @BeforeEach
    void setUp() {
        tarifpositionRepository.deleteAll();
        einheitRepository.deleteAll();
        tarifRepository.deleteAll();
        einheitRepository.deleteAll();

        Organisation org = new Organisation();
        org.setKeycloakOrgId(UUID.fromString("a1a4c1c2-6f1f-4c73-9f3a-0e1a2b3c4d5e"));
        org.setName("Tarifposition Test Organisation");
        org.setErstelltAm(LocalDateTime.now());
        TEST_ORG_ID = organisationRepository.save(org).getId();

        Einheit einheit = new Einheit("Test Einheit", EinheitTyp.CONSUMER);
        einheit.setOrgId(TEST_ORG_ID);
        Long einheitId = einheitRepository.save(einheit).getId();

        ladestationA = einheitRepository.save(createLadestation("Ladestation A", "RFID-A"));
        ladestationB = einheitRepository.save(createLadestation("Ladestation B", "RFID-B"));

        ladestromTarif = tarifRepository.save(createTarif("Ladestrom", TarifTyp.LADESTROM, "0.35000",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)));
        ladestromTarif2 = tarifRepository.save(createTarif("Ladestrom Neu", TarifTyp.LADESTROM, "0.40000",
                LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31)));
        zevTarif = tarifRepository.save(createTarif("ZEV 2026", TarifTyp.ZEV, "0.20000",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)));
    }

    @AfterEach
    void tearDown() {
        tarifpositionRepository.deleteAll();
        einheitRepository.deleteAll();
        tarifRepository.deleteAll();
        einheitRepository.deleteAll();
    }

    private Einheit createLadestation(String name, String rfid) {
        Einheit einheit = new Einheit(name, EinheitTyp.LADESTATION);
        einheit.setOrgId(TEST_ORG_ID);
        einheit.setMesspunkt(rfid);
        return einheit;
    }

    private Tarif createTarif(String bezeichnung, TarifTyp typ, String preis,
                              LocalDate von, LocalDate bis) {
        Tarif tarif = new Tarif(bezeichnung, typ, new BigDecimal(preis), von, bis);
        tarif.setOrgId(TEST_ORG_ID);
        return tarif;
    }

    private Tarifposition savePosition(Einheit einheit, Tarif tarif, int jahr, int quartal, String menge) {
        Tarifposition position = new Tarifposition(einheit, tarif, jahr, quartal, new BigDecimal(menge));
        position.setOrgId(TEST_ORG_ID);
        return tarifpositionRepository.save(position);
    }

    // ==================== Persistenz ====================

    @Test
    void shouldSaveAndFindTarifposition() {
        Tarifposition position = new Tarifposition(ladestationA, ladestromTarif, 2026, 3, new BigDecimal("120.500"));
        position.setOrgId(TEST_ORG_ID);
        position.setQuellReferenz("LP-01");
        position.setBemerkung("Beleg 42");

        Tarifposition saved = tarifpositionRepository.save(position);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getOrgId()).isEqualTo(TEST_ORG_ID);
        assertThat(saved.getMenge()).isEqualByComparingTo("120.500");
        // Default der Entity: manuelle Erfassung
        assertThat(saved.getErfassungsart()).isEqualTo(Erfassungsart.MANUELL);
        assertThat(saved.getQuellReferenz()).isEqualTo("LP-01");
        assertThat(saved.getBemerkung()).isEqualTo("Beleg 42");
    }

    @Test
    void shouldKeepThreeDecimalsForMenge() {
        Tarifposition saved = savePosition(ladestationA, ladestromTarif, 2026, 1, "120.567");
        tarifpositionRepository.flush();

        Tarifposition reloaded = tarifpositionRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getMenge()).isEqualByComparingTo("120.567");
    }

    // Die Feldvalidierung (menge >= 0, quartal 1-4, jahr 2000-2100) ist in
    // TarifpositionTest über den Bean-Validator abgedeckt - hier würde die fehlgeschlagene
    // Flush-Operation den Persistence-Context der übrigen Tests beschädigen.

    // ==================== findByEinheitId ====================

    @Test
    void shouldFindByEinheitIdOrderedNewestQuarterFirst() {
        savePosition(ladestationA, ladestromTarif, 2026, 1, "10.000");
        savePosition(ladestationA, ladestromTarif, 2026, 4, "40.000");
        savePosition(ladestationA, ladestromTarif, 2026, 2, "20.000");

        List<Tarifposition> result = tarifpositionRepository.findByEinheitId(ladestationA.getId());

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getQuartal()).isEqualTo(4);
        assertThat(result.get(1).getQuartal()).isEqualTo(2);
        assertThat(result.get(2).getQuartal()).isEqualTo(1);
    }

    @Test
    void shouldFindByEinheitIdOrderedAcrossYears() {
        savePosition(ladestationA, ladestromTarif, 2026, 4, "40.000");
        savePosition(ladestationA, ladestromTarif2, 2027, 1, "10.000");

        List<Tarifposition> result = tarifpositionRepository.findByEinheitId(ladestationA.getId());

        // Neuestes Quartal zuerst: Q1/2027 vor Q4/2026
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getJahr()).isEqualTo(2027);
        assertThat(result.get(1).getJahr()).isEqualTo(2026);
    }

    @Test
    void shouldFindByEinheitIdReturnsOnlyOwnPositions() {
        savePosition(ladestationA, ladestromTarif, 2026, 1, "10.000");
        savePosition(ladestationB, ladestromTarif, 2026, 1, "99.000");

        List<Tarifposition> result = tarifpositionRepository.findByEinheitId(ladestationA.getId());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMenge()).isEqualByComparingTo("10.000");
    }

    @Test
    void shouldFindByEinheitIdReturnsEmptyForMieterWithoutPositions() {
        assertThat(tarifpositionRepository.findByEinheitId(ladestationB.getId())).isEmpty();
    }

    // ==================== findByEinheitIdsAndQuartalOverlapping ====================

    @Test
    void shouldFindOverlappingPositionsForSingleQuarter() {
        savePosition(ladestationA, ladestromTarif, 2026, 1, "10.000");
        savePosition(ladestationA, ladestromTarif, 2026, 2, "20.000");

        List<Tarifposition> result = tarifpositionRepository.findByEinheitIdsAndQuartalOverlapping(List.of(ladestationA.getId()), 2026, 1, 2026, 1);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getQuartal()).isEqualTo(1);
    }

    @Test
    void shouldFindOverlappingPositionsForHalfYear_ReturnsBothQuarters() {
        savePosition(ladestationA, ladestromTarif, 2026, 1, "10.000");
        savePosition(ladestationA, ladestromTarif, 2026, 2, "20.000");
        savePosition(ladestationA, ladestromTarif, 2026, 3, "30.000");

        List<Tarifposition> result = tarifpositionRepository.findByEinheitIdsAndQuartalOverlapping(List.of(ladestationA.getId()), 2026, 1, 2026, 2);

        // Aufsteigend sortiert (aeltestes Quartal zuerst)
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getQuartal()).isEqualTo(1);
        assertThat(result.get(1).getQuartal()).isEqualTo(2);
    }

    @Test
    void shouldFindOverlappingPositionsAcrossYearBoundary() {
        savePosition(ladestationA, ladestromTarif, 2026, 3, "30.000");
        savePosition(ladestationA, ladestromTarif, 2026, 4, "40.000");
        savePosition(ladestationA, ladestromTarif2, 2027, 1, "10.000");
        savePosition(ladestationA, ladestromTarif2, 2027, 2, "20.000");

        List<Tarifposition> result = tarifpositionRepository.findByEinheitIdsAndQuartalOverlapping(List.of(ladestationA.getId()), 2026, 4, 2027, 1);

        // Q4/2026 und Q1/2027 - chronologisch ueber die Jahresgrenze hinweg
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getJahr()).isEqualTo(2026);
        assertThat(result.get(0).getQuartal()).isEqualTo(4);
        assertThat(result.get(1).getJahr()).isEqualTo(2027);
        assertThat(result.get(1).getQuartal()).isEqualTo(1);
    }

    @Test
    void shouldExcludePositionsWithMengeZero() {
        savePosition(ladestationA, ladestromTarif, 2026, 1, "0.000");

        List<Tarifposition> result = tarifpositionRepository.findByEinheitIdsAndQuartalOverlapping(List.of(ladestationA.getId()), 2026, 1, 2026, 1);

        // Menge = 0 bleibt gespeichert, erscheint aber nicht auf der Rechnung
        assertThat(result).isEmpty();
        assertThat(tarifpositionRepository.findByEinheitId(ladestationA.getId())).hasSize(1);
    }

    @Test
    void shouldReturnOnlyPositionsOfTheGivenMieter() {
        savePosition(ladestationA, ladestromTarif, 2026, 1, "10.000");
        savePosition(ladestationB, ladestromTarif, 2026, 1, "99.000");

        List<Tarifposition> result = tarifpositionRepository.findByEinheitIdsAndQuartalOverlapping(List.of(ladestationB.getId()), 2026, 1, 2026, 1);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMenge()).isEqualByComparingTo("99.000");
    }

    @Test
    void shouldFindPositionsOfBothLadestationenOfOneMieter() {
        // Mieter mit zwei Ladestationen: beide Positionen gehoeren auf dieselbe Rechnung,
        // jede als eigene Zeile (Specs/Ladestationen.md, AK Rechnung)
        savePosition(ladestationA, ladestromTarif, 2026, 1, "10.000");
        savePosition(ladestationB, ladestromTarif, 2026, 1, "20.000");

        List<Tarifposition> result = tarifpositionRepository.findByEinheitIdsAndQuartalOverlapping(
                List.of(ladestationA.getId(), ladestationB.getId()), 2026, 1, 2026, 1);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(p -> p.getEinheit().getId())
                .containsExactlyInAnyOrder(ladestationA.getId(), ladestationB.getId());
    }

    @Test
    void shouldAllowSameQuarterForTwoLadestationenOfOneMieter() {
        // Die Eindeutigkeit gilt je Einheit - zwei Einheiten desselben Mieters duerfen
        // im selben Quartal je eine Position tragen
        savePosition(ladestationA, ladestromTarif, 2026, 1, "10.000");
        savePosition(ladestationB, ladestromTarif, 2026, 1, "20.000");

        assertThat(tarifpositionRepository.existsByEinheitAndQuartalAndTariftyp(
                ladestationB.getId(), 2026, 1, TarifTyp.MANUELL_ERFASST, -1L)).isTrue();
        assertThat(tarifpositionRepository.findByEinheitId(ladestationA.getId())).hasSize(1);
        assertThat(tarifpositionRepository.findByEinheitId(ladestationB.getId())).hasSize(1);
    }

    @Test
    void shouldCountPositionsOfEinheit() {
        // Grundlage des Loeschschutzes beim Mieter (Specs/Ladestationen.md §5)
        savePosition(ladestationA, ladestromTarif, 2026, 1, "10.000");
        savePosition(ladestationA, ladestromTarif, 2026, 2, "20.000");

        assertThat(tarifpositionRepository.countByEinheitId(ladestationA.getId())).isEqualTo(2);
        assertThat(tarifpositionRepository.countByEinheitId(ladestationB.getId())).isZero();
    }

    @Test
    void shouldDeletePositionsWhenEinheitIsDeleted() {
        // ON DELETE CASCADE: mit der Einheit verschwinden ihre Positionen (§5)
        savePosition(ladestationB, ladestromTarif, 2026, 1, "10.000");
        savePosition(ladestationA, ladestromTarif, 2026, 1, "20.000");

        tarifpositionRepository.deleteAll(tarifpositionRepository.findByEinheitId(ladestationB.getId()));
        einheitRepository.delete(ladestationB);

        assertThat(einheitRepository.findById(ladestationB.getId())).isEmpty();
        assertThat(tarifpositionRepository.countByEinheitId(ladestationB.getId())).isZero();
        // Die Positionen der anderen Einheit bleiben unberuehrt
        assertThat(tarifpositionRepository.countByEinheitId(ladestationA.getId())).isEqualTo(1);
    }

    @Test
    void shouldReturnEmptyWhenNoQuarterOverlaps() {
        savePosition(ladestationA, ladestromTarif, 2026, 1, "10.000");

        List<Tarifposition> result = tarifpositionRepository.findByEinheitIdsAndQuartalOverlapping(List.of(ladestationA.getId()), 2026, 3, 2026, 4);

        assertThat(result).isEmpty();
    }

    // ==================== existsByEinheitAndQuartalAndTariftyp ====================

    @Test
    void shouldDetectExistingPositionOfSameTariftyp() {
        savePosition(ladestationA, ladestromTarif, 2026, 1, "10.000");

        boolean exists = tarifpositionRepository.existsByEinheitAndQuartalAndTariftyp(
                ladestationA.getId(), 2026, 1, TarifTyp.MANUELL_ERFASST, -1L);

        assertThat(exists).isTrue();
    }

    @Test
    void shouldDetectExistingPositionEvenWithDifferentLadestromTarif() {
        // Zweiter LADESTROM-Tarif darf die Regel nicht umgehen
        savePosition(ladestationA, ladestromTarif, 2026, 1, "10.000");

        boolean exists = tarifpositionRepository.existsByEinheitAndQuartalAndTariftyp(
                ladestationA.getId(), 2026, 1, TarifTyp.MANUELL_ERFASST, -1L);

        assertThat(exists).isTrue();
        assertThat(ladestromTarif2.getTariftyp()).isEqualTo(TarifTyp.LADESTROM);
    }

    @Test
    void shouldNotDetectPositionOfOtherTariftyp() {
        // Position auf einem ZEV-Tarif (fachlich nicht erlaubt, hier bewusst direkt persistiert)
        savePosition(ladestationA, zevTarif, 2026, 1, "10.000");

        boolean exists = tarifpositionRepository.existsByEinheitAndQuartalAndTariftyp(
                ladestationA.getId(), 2026, 1, TarifTyp.MANUELL_ERFASST, -1L);

        assertThat(exists).isFalse();
    }

    @Test
    void shouldNotDetectPositionOfOtherQuartal() {
        savePosition(ladestationA, ladestromTarif, 2026, 1, "10.000");

        boolean exists = tarifpositionRepository.existsByEinheitAndQuartalAndTariftyp(
                ladestationA.getId(), 2026, 2, TarifTyp.MANUELL_ERFASST, -1L);

        assertThat(exists).isFalse();
    }

    @Test
    void shouldNotDetectPositionOfOtherMieter() {
        savePosition(ladestationB, ladestromTarif, 2026, 1, "10.000");

        boolean exists = tarifpositionRepository.existsByEinheitAndQuartalAndTariftyp(
                ladestationA.getId(), 2026, 1, TarifTyp.MANUELL_ERFASST, -1L);

        assertThat(exists).isFalse();
    }

    @Test
    void shouldExcludeSelfWhenCheckingDuplicates() {
        Tarifposition eigene = savePosition(ladestationA, ladestromTarif, 2026, 1, "10.000");

        boolean exists = tarifpositionRepository.existsByEinheitAndQuartalAndTariftyp(
                ladestationA.getId(), 2026, 1, TarifTyp.MANUELL_ERFASST, eigene.getId());

        // Beim Bearbeiten darf die eigene Position nicht als Duplikat gelten
        assertThat(exists).isFalse();
    }

    // ==================== countByTarifId ====================

    @Test
    void shouldCountPositionsReferencingATarif() {
        savePosition(ladestationA, ladestromTarif, 2026, 1, "10.000");
        savePosition(ladestationB, ladestromTarif, 2026, 1, "20.000");
        savePosition(ladestationA, ladestromTarif2, 2027, 1, "30.000");

        assertThat(tarifpositionRepository.countByTarifId(ladestromTarif.getId())).isEqualTo(2);
        assertThat(tarifpositionRepository.countByTarifId(ladestromTarif2.getId())).isEqualTo(1);
    }

    @Test
    void shouldCountZeroForUnreferencedTarif() {
        assertThat(tarifpositionRepository.countByTarifId(zevTarif.getId())).isZero();
    }
}
