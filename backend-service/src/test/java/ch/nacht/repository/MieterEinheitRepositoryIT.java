package ch.nacht.repository;

import ch.nacht.AbstractIntegrationTest;
import ch.nacht.entity.Einheit;
import ch.nacht.entity.EinheitTyp;
import ch.nacht.entity.Mieter;
import ch.nacht.entity.MieterEinheit;
import ch.nacht.entity.Organisation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integrationstests für {@link MieterEinheitRepository} mit Testcontainers
 * (Specs/Ladestationen.md).
 *
 * <p>Die Tabelle {@code mieter_einheit} löst die frühere Spalte {@code mieter.einheit_id} ab: Ein
 * Mieter kann mehreren Einheiten zugeordnet sein — Wohnung und Ladestation(en) —, ein Nutzer ohne
 * Wohnung nur einer Ladestation. Geprüft werden die Abfragen, die Rechnungserzeugung
 * ({@code findEinheitIdsByMieterId}), Mieterliste ({@code findByMieterIdIn}), Neuschreiben der
 * Zuordnungen ({@code deleteByMieterId}) und Löschschutz ({@code countByEinheitId}) tragen.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class MieterEinheitRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private MieterEinheitRepository mieterEinheitRepository;

    @Autowired
    private MieterRepository mieterRepository;

    @Autowired
    private EinheitRepository einheitRepository;

    @Autowired
    private OrganisationRepository organisationRepository;

    private Long TEST_ORG_ID;
    private Einheit wohnung;
    private Einheit ladestationA;
    private Einheit ladestationB;

    @BeforeEach
    void setUp() {
        mieterEinheitRepository.deleteAll();
        mieterRepository.deleteAll();
        einheitRepository.deleteAll();

        Organisation org = new Organisation();
        org.setKeycloakOrgId(UUID.fromString("f4b1d0a7-6c62-4f6e-8b1e-2c9a5d7e3f10"));
        org.setName("MieterEinheit Test Organisation");
        org.setErstelltAm(LocalDateTime.now());
        TEST_ORG_ID = organisationRepository.save(org).getId();

        wohnung = einheitRepository.save(createEinheit("Wohnung A", EinheitTyp.CONSUMER, null));
        ladestationA = einheitRepository.save(
                createEinheit("Ladestation A", EinheitTyp.LADESTATION, "RFID-A"));
        ladestationB = einheitRepository.save(
                createEinheit("Ladestation B", EinheitTyp.LADESTATION, "RFID-B"));
    }

    @AfterEach
    void tearDown() {
        mieterEinheitRepository.deleteAll();
        mieterRepository.deleteAll();
        einheitRepository.deleteAll();
    }

    private Einheit createEinheit(String name, EinheitTyp typ, String messpunkt) {
        Einheit einheit = new Einheit(name, typ);
        einheit.setOrgId(TEST_ORG_ID);
        einheit.setMesspunkt(messpunkt);
        return einheit;
    }

    private Mieter saveMieter(String name) {
        Mieter mieter = new Mieter();
        mieter.setOrgId(TEST_ORG_ID);
        mieter.setName(name);
        mieter.setStrasse("Teststrasse 1");
        mieter.setPlz("8000");
        mieter.setOrt("Zürich");
        mieter.setMietbeginn(LocalDate.of(2026, 1, 1));
        return mieterRepository.save(mieter);
    }

    private void zuordnen(Mieter mieter, Einheit... einheiten) {
        for (Einheit einheit : einheiten) {
            mieterEinheitRepository.save(new MieterEinheit(TEST_ORG_ID, mieter.getId(), einheit.getId()));
        }
    }

    // ==================== Persistenz ====================

    @Test
    void shouldSaveAndFindZuordnung() {
        Mieter mieter = saveMieter("Max Mustermann");

        MieterEinheit saved = mieterEinheitRepository.save(
                new MieterEinheit(TEST_ORG_ID, mieter.getId(), wohnung.getId()));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getOrgId()).isEqualTo(TEST_ORG_ID);
        assertThat(saved.getMieterId()).isEqualTo(mieter.getId());
        assertThat(saved.getEinheitId()).isEqualTo(wohnung.getId());
    }

    @Test
    void shouldAssignSeveralEinheitenToOneMieter() {
        // Wohnung + zwei Ladestationen: alles auf einer Rechnung
        Mieter mieter = saveMieter("Mit Wohnung und Ladestationen");
        zuordnen(mieter, wohnung, ladestationA, ladestationB);

        assertThat(mieterEinheitRepository.findByMieterId(mieter.getId())).hasSize(3);
    }

    @Test
    void shouldAssignOnlyLadestationToMieterWithoutWohnung() {
        // Nutzer ohne Wohnung
        Mieter nutzer = saveMieter("Nutzer ohne Wohnung");
        zuordnen(nutzer, ladestationA);

        assertThat(mieterEinheitRepository.findEinheitIdsByMieterId(nutzer.getId()))
                .containsExactly(ladestationA.getId());
    }

    @Test
    void shouldAssignOneEinheitToSeveralMieterOverTime() {
        // Mieterwechsel an der Wohnung: beide Mietverhaeltnisse zeigen auf dieselbe Einheit
        Mieter vormieter = saveMieter("Vormieter");
        Mieter nachmieter = saveMieter("Nachmieter");
        zuordnen(vormieter, wohnung);
        zuordnen(nachmieter, wohnung);

        assertThat(mieterEinheitRepository.countByEinheitId(wohnung.getId())).isEqualTo(2);
    }

    // ==================== findEinheitIdsByMieterId ====================

    @Test
    void shouldFindEinheitIdsOrderedByEinheitId() {
        Mieter mieter = saveMieter("Sortiert");
        // Bewusst in umgekehrter Reihenfolge zugeordnet
        zuordnen(mieter, ladestationB, ladestationA, wohnung);

        List<Long> result = mieterEinheitRepository.findEinheitIdsByMieterId(mieter.getId());

        // Stabile Reihenfolge fuer Formular und Liste
        assertThat(result).containsExactly(wohnung.getId(), ladestationA.getId(), ladestationB.getId());
    }

    @Test
    void shouldReturnEmptyEinheitIdsForMieterWithoutZuordnung() {
        Mieter mieter = saveMieter("Ohne Zuordnung");

        assertThat(mieterEinheitRepository.findEinheitIdsByMieterId(mieter.getId())).isEmpty();
    }

    @Test
    void shouldReturnOnlyEinheitIdsOfTheGivenMieter() {
        Mieter erster = saveMieter("Erster");
        Mieter zweiter = saveMieter("Zweiter");
        zuordnen(erster, wohnung);
        zuordnen(zweiter, ladestationA, ladestationB);

        assertThat(mieterEinheitRepository.findEinheitIdsByMieterId(erster.getId()))
                .containsExactly(wohnung.getId());
        assertThat(mieterEinheitRepository.findEinheitIdsByMieterId(zweiter.getId()))
                .containsExactly(ladestationA.getId(), ladestationB.getId());
    }

    // ==================== findByMieterIdIn ====================

    @Test
    void shouldFindZuordnungenOfSeveralMieterInOneQuery() {
        Mieter erster = saveMieter("Erster");
        Mieter zweiter = saveMieter("Zweiter");
        zuordnen(erster, wohnung, ladestationA);
        zuordnen(zweiter, ladestationB);

        List<MieterEinheit> result = mieterEinheitRepository.findByMieterIdIn(
                List.of(erster.getId(), zweiter.getId()));

        assertThat(result).hasSize(3);
        assertThat(result).extracting(MieterEinheit::getMieterId)
                .containsOnly(erster.getId(), zweiter.getId());
    }

    @Test
    void shouldReturnEmptyForUnknownMieterIds() {
        assertThat(mieterEinheitRepository.findByMieterIdIn(List.of(999999L))).isEmpty();
    }

    // ==================== deleteByMieterId ====================

    @Test
    void shouldDeleteOnlyZuordnungenOfTheGivenMieter() {
        // Beim Speichern werden die Zuordnungen komplett neu geschrieben
        Mieter erster = saveMieter("Erster");
        Mieter zweiter = saveMieter("Zweiter");
        zuordnen(erster, wohnung, ladestationA);
        zuordnen(zweiter, ladestationB);

        mieterEinheitRepository.deleteByMieterId(erster.getId());
        mieterEinheitRepository.flush();

        assertThat(mieterEinheitRepository.findByMieterId(erster.getId())).isEmpty();
        assertThat(mieterEinheitRepository.findByMieterId(zweiter.getId())).hasSize(1);
    }

    @Test
    void shouldKeepEinheitenWhenZuordnungenAreDeleted() {
        // Ohne Positionen verschwinden nur die Zuordnungen, die Einheiten bleiben bestehen (§5)
        Mieter mieter = saveMieter("Ausziehend");
        zuordnen(mieter, wohnung, ladestationA);

        mieterEinheitRepository.deleteByMieterId(mieter.getId());
        mieterEinheitRepository.flush();

        assertThat(einheitRepository.findById(wohnung.getId())).isPresent();
        assertThat(einheitRepository.findById(ladestationA.getId())).isPresent();
    }

    // ==================== countByEinheitId (Loeschschutz der Einheit) ====================

    @Test
    void shouldCountMieterAssignedToAnEinheit() {
        Mieter erster = saveMieter("Erster");
        Mieter zweiter = saveMieter("Zweiter");
        zuordnen(erster, ladestationA);
        zuordnen(zweiter, ladestationA);

        assertThat(mieterEinheitRepository.countByEinheitId(ladestationA.getId())).isEqualTo(2);
    }

    @Test
    void shouldCountZeroForEinheitWithoutZuordnung() {
        assertThat(mieterEinheitRepository.countByEinheitId(ladestationB.getId())).isZero();
    }
}
