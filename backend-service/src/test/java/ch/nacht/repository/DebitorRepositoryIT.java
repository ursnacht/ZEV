package ch.nacht.repository;

import ch.nacht.AbstractIntegrationTest;
import ch.nacht.entity.Debitor;
import ch.nacht.entity.Debitorherkunft;
import ch.nacht.entity.Mieter;
import ch.nacht.entity.Organisation;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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
 * Integrationstests für {@link DebitorRepository} mit Testcontainers.
 *
 * <p>Beide Abfragen dieses Repositories sind von Hand geschrieben und liessen sich bisher nur
 * über die Oberfläche prüfen:
 * <ul>
 *   <li>{@code findByDatumVonBetween} filtert ausschliesslich über {@code datum_von} — ein
 *       Eintrag, dessen Zeitraum in den Filter hineinragt, aber davor beginnt, erscheint
 *       <b>nicht</b>. Diese Kante bestimmt, was die Debitorenkontrolle anzeigt.</li>
 *   <li>{@code upsert} ist natives SQL mit {@code ON CONFLICT}. Seine {@code WHERE
 *       zahldatum IS NULL}-Klausel ist der Schutz davor, dass ein erneuter Rechnungslauf einen
 *       bereits bezahlten Eintrag überschreibt — der teuerste denkbare Fehler dieser Tabelle.
 *       Der Ausdruck ist für Hibernate undurchsichtig; nur eine echte Postgres-Instanz zeigt,
 *       ob er greift.</li>
 * </ul>
 *
 * <p><b>Zum Org-Filter:</b> {@code @DataJpaTest} kennt keinen {@code HibernateFilterService},
 * der Filter ist also standardmässig aus und die Abfragen liefern mandantenübergreifend. Hier
 * stand, das Einschalten sei "Aufgabe des Service und dort getestet" — das traf nicht zu: Die
 * Service-Tests mocken das Repository und können eine Hibernate-Zusicherung grundsätzlich nicht
 * prüfen. Der Abschnitt „Mandantenfilter" unten schaltet ihn deshalb selbst ein
 * ({@code aktiviereOrgFilter}) und prüft die Trennung dort, wo sie entsteht.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class DebitorRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private DebitorRepository debitorRepository;

    @Autowired
    private MieterRepository mieterRepository;

    @Autowired
    private OrganisationRepository organisationRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private Long TEST_ORG_ID;
    private Long ANDERE_ORG_ID;
    private Long mieterAId;
    private Long mieterBId;

    @BeforeEach
    void setUp() {
        angleichAnFlywaySchema();
        debitorRepository.deleteAll();
        mieterRepository.deleteAll();

        TEST_ORG_ID = saveOrganisation("Debitor Test Organisation",
                UUID.fromString("b7c5e4a1-3d29-4f18-9a6b-2c8d0e1f3a5b"));
        ANDERE_ORG_ID = saveOrganisation("Debitor Fremd Organisation",
                UUID.fromString("c8d6f5b2-4e3a-5029-8b7c-3d9e1f2a4b6c"));

        mieterAId = saveMieter("Mieter A").getId();
        mieterBId = saveMieter("Mieter B").getId();
    }

    @AfterEach
    void tearDown() {
        debitorRepository.deleteAll();
        mieterRepository.deleteAll();
    }

    /**
     * Ergänzt am Mapping-Schema die eine Eigenschaft, die {@code upsert} braucht und die nur in
     * {@code V55__Create_Debitor_Table.sql} steht: den Spalten-Default auf {@code debitor_seq}.
     *
     * <p>Die Integrationstests laufen mit {@code ddl-auto=create-drop}, das Schema entsteht also
     * aus dem Mapping und nicht aus Flyway. Hibernate vergibt die ID selbst und legt deshalb
     * keinen Default an — das native INSERT der Upsert-Abfrage liefert aber keine ID mit und
     * liefe ohne diesen Angleich in eine NOT-NULL-Verletzung. Produktiv existiert der Default
     * seit V55; die Abfrage hängt also an einer Zusicherung des Schemas, nicht der Entity.
     *
     * <p>DDL ist in Postgres transaktional: Die Änderung verschwindet mit dem Rollback des Tests.
     */
    private void angleichAnFlywaySchema() {
        entityManager.createNativeQuery(
                "ALTER TABLE zev.debitor ALTER COLUMN id SET DEFAULT nextval('zev.debitor_seq')")
                .executeUpdate();
    }

    private Long saveOrganisation(String name, UUID keycloakOrgId) {
        Organisation org = new Organisation();
        org.setKeycloakOrgId(keycloakOrgId);
        org.setName(name);
        org.setErstelltAm(LocalDateTime.now());
        return organisationRepository.save(org).getId();
    }

    // ==================== Mandantenfilter ====================

    /** Gegenprobe: Ohne Filter sind beide Mandanten sichtbar — sonst waere der Test darunter wertlos. */
    @Test
    void shouldSeeAllOrgsWhenOrgFilterDisabled() {
        saveDebitor(mieterAId, "100.00", LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28), null);
        debitorFuerOrg(ANDERE_ORG_ID, "999.00", LocalDate.of(2026, 2, 1));
        syncMitDatenbank();

        assertThat(debitorRepository.findByDatumVonBetween(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))).hasSize(2);
    }

    @Test
    void shouldNotSeeDebitorOfOtherOrgWhenOrgFilterEnabled() {
        Debitor eigener = saveDebitor(mieterAId, "100.00",
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28), null);
        Debitor fremder = debitorFuerOrg(ANDERE_ORG_ID, "999.00", LocalDate.of(2026, 2, 1));
        syncMitDatenbank();

        aktiviereOrgFilter(entityManager, TEST_ORG_ID);

        assertThat(debitorRepository.findByDatumVonBetween(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)))
                .extracting(Debitor::getBetrag)
                .singleElement()
                .satisfies(betrag -> assertThat(betrag).isEqualByComparingTo("100.00"));
        assertThat(debitorRepository.findFirstById(eigener.getId())).isPresent();
        assertThat(debitorRepository.findFirstById(fremder.getId())).isEmpty();
    }

    /**
     * Die Forderung eines fremden Mandanten darf durch {@code upsert} nicht ueberschrieben werden.
     *
     * <p>Der Unique-Key ist {@code (mieter_id, datum_von, org_id)} — die {@code org_id} gehoert
     * also zum Schluessel. Die Abfrage ist natives SQL und sieht den Mandantenfilter nicht; sie
     * ist allein deshalb sicher, weil ihr die {@code orgId} als Parameter uebergeben wird.
     */
    @Test
    void upsertDarfFremdeForderungNichtUeberschreiben() {
        Debitor fremder = debitorFuerOrg(ANDERE_ORG_ID, "999.00", LocalDate.of(2026, 2, 1));
        syncMitDatenbank();

        debitorRepository.upsert(fremder.getMieterId(), new BigDecimal("1.00"),
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28), TEST_ORG_ID, Debitorherkunft.ZEV.name());
        syncMitDatenbank();

        aktiviereOrgFilter(entityManager, ANDERE_ORG_ID);
        assertThat(debitorRepository.findFirstById(fremder.getId()).orElseThrow().getBetrag())
                .isEqualByComparingTo("999.00");
    }

    private Debitor debitorFuerOrg(Long orgId, String betrag, LocalDate von) {
        Debitor debitor = new Debitor();
        debitor.setOrgId(orgId);
        debitor.setMieterId(mieterBId);
        debitor.setBetrag(new BigDecimal(betrag));
        debitor.setDatumVon(von);
        debitor.setDatumBis(von.plusMonths(1));
        return debitorRepository.save(debitor);
    }

    private Mieter saveMieter(String name) {
        Mieter mieter = new Mieter();
        mieter.setOrgId(TEST_ORG_ID);
        mieter.setName(name);
        mieter.setStrasse("Teststrasse 1");
        mieter.setPlz("8000");
        mieter.setOrt("Zürich");
        mieter.setMietbeginn(LocalDate.of(2020, 1, 1));
        return mieterRepository.save(mieter);
    }

    private Debitor saveDebitor(Long mieterId, String betrag, LocalDate von, LocalDate bis, LocalDate zahldatum) {
        Debitor debitor = new Debitor();
        debitor.setOrgId(TEST_ORG_ID);
        debitor.setMieterId(mieterId);
        debitor.setBetrag(new BigDecimal(betrag));
        debitor.setDatumVon(von);
        debitor.setDatumBis(bis);
        debitor.setZahldatum(zahldatum);
        return debitorRepository.save(debitor);
    }

    /**
     * Schreibt Anstehendes in die Datenbank und leert den Persistence-Context.
     * Ohne das sähe die native Upsert-Abfrage die noch nicht geschriebenen Zeilen nicht — und
     * das anschliessende Lesen läge auf dem Stand vor dem Upsert.
     */
    private void syncMitDatenbank() {
        entityManager.flush();
        entityManager.clear();
    }

    // ==================== findByDatumVonBetween ====================

    @Test
    void shouldFindDebitorenWithinRange() {
        saveDebitor(mieterAId, "100.00", LocalDate.of(2026, 2, 15), LocalDate.of(2026, 3, 31), null);

        List<Debitor> result = debitorRepository.findByDatumVonBetween(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getBetrag()).isEqualByComparingTo("100.00");
    }

    @Test
    void shouldIncludeBothRangeBoundaries() {
        // Beide Grenzen sind einschliessend - sonst fiele der erste bzw. letzte Tag des
        // gewaehlten Zeitraums aus der Debitorenkontrolle heraus
        saveDebitor(mieterAId, "10.00", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), null);
        saveDebitor(mieterBId, "20.00", LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 30), null);

        List<Debitor> result = debitorRepository.findByDatumVonBetween(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31));

        assertThat(result).hasSize(2);
    }

    @Test
    void shouldExcludeDebitorenOutsideRange() {
        saveDebitor(mieterAId, "10.00", LocalDate.of(2025, 12, 31), LocalDate.of(2026, 1, 31), null);
        saveDebitor(mieterBId, "20.00", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30), null);

        List<Debitor> result = debitorRepository.findByDatumVonBetween(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31));

        assertThat(result).isEmpty();
    }

    @Test
    void shouldFilterOnDatumVonOnly_NotOnOverlap() {
        // Ein Eintrag vom 01.12.2025 bis 28.02.2026 ragt in den Filter hinein, beginnt aber
        // davor - er erscheint bewusst NICHT. Der Filter ist ein Stichtagsfilter auf datum_von,
        // keine Ueberschneidungspruefung.
        saveDebitor(mieterAId, "10.00", LocalDate.of(2025, 12, 1), LocalDate.of(2026, 2, 28), null);

        List<Debitor> result = debitorRepository.findByDatumVonBetween(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31));

        assertThat(result).isEmpty();
    }

    @Test
    void shouldOrderByDatumVonThenMieterId() {
        saveDebitor(mieterBId, "30.00", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), null);
        saveDebitor(mieterBId, "10.00", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), null);
        saveDebitor(mieterAId, "20.00", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), null);

        List<Debitor> result = debitorRepository.findByDatumVonBetween(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31));

        assertThat(result).extracting(Debitor::getBetrag)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("10.00"), new BigDecimal("20.00"), new BigDecimal("30.00"));
        // Bei gleichem Datum entscheidet die Mieter-ID: Mieter A vor Mieter B
        assertThat(result.get(1).getMieterId()).isEqualTo(mieterAId);
        assertThat(result.get(2).getMieterId()).isEqualTo(mieterBId);
    }

    @Test
    void shouldReturnEmptyListWhenNothingMatches() {
        assertThat(debitorRepository.findByDatumVonBetween(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31))).isEmpty();
    }

    @Test
    void shouldKeepZahldatumAndTwoDecimals() {
        saveDebitor(mieterAId, "1234.55", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31),
                LocalDate.of(2026, 4, 15));
        syncMitDatenbank();

        List<Debitor> result = debitorRepository.findByDatumVonBetween(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getBetrag()).isEqualByComparingTo("1234.55");
        assertThat(result.get(0).getZahldatum()).isEqualTo(LocalDate.of(2026, 4, 15));
    }

    // ==================== upsert ====================

    @Test
    void shouldInsertNewDebitorViaUpsert() {
        debitorRepository.upsert(mieterAId, new BigDecimal("250.00"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), TEST_ORG_ID, Debitorherkunft.ZEV.name());
        syncMitDatenbank();

        List<Debitor> result = debitorRepository.findByDatumVonBetween(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMieterId()).isEqualTo(mieterAId);
        assertThat(result.get(0).getBetrag()).isEqualByComparingTo("250.00");
        assertThat(result.get(0).getDatumBis()).isEqualTo(LocalDate.of(2026, 3, 31));
        // Ein neu erzeugter Eintrag ist offen
        assertThat(result.get(0).getZahldatum()).isNull();
    }

    @Test
    void shouldUpdateBetragAndDatumBisOnConflictWhenUnpaid() {
        // Zweiter Rechnungslauf fuer denselben Zeitraum: Der offene Eintrag wird nachgefuehrt,
        // statt dass ein zweiter entsteht
        saveDebitor(mieterAId, "100.00", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 28), null);
        syncMitDatenbank();

        debitorRepository.upsert(mieterAId, new BigDecimal("175.50"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), TEST_ORG_ID, Debitorherkunft.ZEV.name());
        syncMitDatenbank();

        List<Debitor> result = debitorRepository.findByDatumVonBetween(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getBetrag()).isEqualByComparingTo("175.50");
        assertThat(result.get(0).getDatumBis()).isEqualTo(LocalDate.of(2026, 3, 31));
    }

    @Test
    void shouldNotOverwriteAlreadyPaidDebitor() {
        // Der eigentliche Schutz: Ist der Eintrag bezahlt, laesst ein erneuter Rechnungslauf
        // ihn unveraendert - weder Betrag noch Zeitraum noch Zahldatum duerfen kippen
        saveDebitor(mieterAId, "100.00", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31),
                LocalDate.of(2026, 4, 10));
        syncMitDatenbank();

        debitorRepository.upsert(mieterAId, new BigDecimal("999.00"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30), TEST_ORG_ID, Debitorherkunft.ZEV.name());
        syncMitDatenbank();

        List<Debitor> result = debitorRepository.findByDatumVonBetween(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getBetrag()).isEqualByComparingTo("100.00");
        assertThat(result.get(0).getDatumBis()).isEqualTo(LocalDate.of(2026, 3, 31));
        assertThat(result.get(0).getZahldatum()).isEqualTo(LocalDate.of(2026, 4, 10));
    }

    @Test
    void shouldCreateSeparateEntriesForDifferentPeriods() {
        // Der Schluessel enthaelt datum_von: Ein neues Quartal ergibt einen neuen Eintrag
        debitorRepository.upsert(mieterAId, new BigDecimal("100.00"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), TEST_ORG_ID, Debitorherkunft.ZEV.name());
        debitorRepository.upsert(mieterAId, new BigDecimal("200.00"),
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30), TEST_ORG_ID, Debitorherkunft.ZEV.name());
        syncMitDatenbank();

        List<Debitor> result = debitorRepository.findByDatumVonBetween(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        assertThat(result).hasSize(2);
        assertThat(result).extracting(Debitor::getDatumVon)
                .containsExactly(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 1));
    }

    @Test
    void shouldCreateSeparateEntriesForDifferentMieter() {
        debitorRepository.upsert(mieterAId, new BigDecimal("100.00"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), TEST_ORG_ID, Debitorherkunft.ZEV.name());
        debitorRepository.upsert(mieterBId, new BigDecimal("200.00"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), TEST_ORG_ID, Debitorherkunft.ZEV.name());
        syncMitDatenbank();

        List<Debitor> result = debitorRepository.findByDatumVonBetween(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31));

        assertThat(result).hasSize(2);
        assertThat(result).extracting(Debitor::getMieterId)
                .containsExactly(mieterAId, mieterBId);
    }

    @Test
    void shouldKeepEntriesOfDifferentOrganisationsApart() {
        // org_id gehoert zum Schluessel: Derselbe Mieter und Zeitraum in einem anderen Mandanten
        // ergibt einen eigenen Eintrag statt eines Konflikts
        debitorRepository.upsert(mieterAId, new BigDecimal("100.00"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), TEST_ORG_ID, Debitorherkunft.ZEV.name());
        debitorRepository.upsert(mieterAId, new BigDecimal("200.00"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), ANDERE_ORG_ID, Debitorherkunft.ZEV.name());
        syncMitDatenbank();

        // Ohne aktiven Org-Filter sind beide sichtbar - das Trennen ist Aufgabe des Service
        List<Debitor> result = debitorRepository.findByDatumVonBetween(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31));

        assertThat(result).hasSize(2);
        assertThat(result).extracting(Debitor::getOrgId)
                .containsExactlyInAnyOrder(TEST_ORG_ID, ANDERE_ORG_ID);
    }

    @Test
    void shouldUpsertRepeatedlyWithoutCreatingDuplicates() {
        // Mehrere Laeufe hintereinander duerfen die Tabelle nicht aufblaehen
        for (int lauf = 1; lauf <= 3; lauf++) {
            debitorRepository.upsert(mieterAId, new BigDecimal(lauf * 100 + ".00"),
                    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), TEST_ORG_ID, Debitorherkunft.ZEV.name());
            syncMitDatenbank();
        }

        List<Debitor> result = debitorRepository.findByDatumVonBetween(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getBetrag()).isEqualByComparingTo("300.00");
    }

    // ==================== Herkunft im Unique-Key ====================
    // Specs/Nebenkosten/RechnungenGenerieren.md FR-5

    /**
     * <b>Der Kern der Migration V126:</b> Dieselbe {@code (mieter_id, datum_von)} mit
     * verschiedener Herkunft ergibt <b>zwei</b> Forderungen.
     *
     * <p>Eine NK-Jahresabrechnung 01.01.–31.12. und die ZEV-Quartalsrechnung Q1 haben denselben
     * {@code datum_von}. Ohne die Herkunft im Schluessel haette die NK-Buchung die ZEV-Forderung
     * desselben Mieters stillschweigend ueberschrieben — kein Fehler, keine Meldung, eine
     * Forderung weg.
     */
    @Test
    void upsertMitVerschiedenerHerkunft_ErzeugtZweiForderungen() {
        debitorRepository.upsert(mieterAId, new BigDecimal("250.00"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), TEST_ORG_ID,
                Debitorherkunft.ZEV.name());
        debitorRepository.upsert(mieterAId, new BigDecimal("812.35"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), TEST_ORG_ID,
                Debitorherkunft.NK.name());
        syncMitDatenbank();

        List<Debitor> result = debitorRepository.findByDatumVonBetween(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1));

        assertThat(result).hasSize(2);
        assertThat(result).extracting(Debitor::getHerkunft)
                .containsExactlyInAnyOrder(Debitorherkunft.ZEV, Debitorherkunft.NK);
        assertThat(result).extracting(Debitor::getBetrag)
                .containsExactlyInAnyOrder(new BigDecimal("250.00"), new BigDecimal("812.35"));
    }

    /** Ein NK-Lauf aktualisiert seine eigene Forderung und laesst die ZEV-Forderung unberuehrt. */
    @Test
    void upsertNK_LaesstZEVForderungUnberuehrt() {
        debitorRepository.upsert(mieterAId, new BigDecimal("250.00"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), TEST_ORG_ID,
                Debitorherkunft.ZEV.name());
        debitorRepository.upsert(mieterAId, new BigDecimal("100.00"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), TEST_ORG_ID,
                Debitorherkunft.NK.name());
        syncMitDatenbank();

        // Zweiter NK-Lauf mit geaendertem Betrag
        debitorRepository.upsert(mieterAId, new BigDecimal("199.95"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), TEST_ORG_ID,
                Debitorherkunft.NK.name());
        syncMitDatenbank();

        List<Debitor> result = debitorRepository.findByDatumVonBetween(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1));

        assertThat(result).hasSize(2);
        assertThat(result).filteredOn(d -> d.getHerkunft() == Debitorherkunft.ZEV)
                .singleElement()
                .extracting(Debitor::getBetrag).isEqualTo(new BigDecimal("250.00"));
        assertThat(result).filteredOn(d -> d.getHerkunft() == Debitorherkunft.NK)
                .singleElement()
                .extracting(Debitor::getBetrag).isEqualTo(new BigDecimal("199.95"));
    }

    /**
     * <b>Regression des ZEV-Pfads.</b> Die {@code ON CONFLICT}-Klausel muss genau dem neuen
     * Unique-Constraint entsprechen. Bliebe sie auf {@code (mieter_id, datum_von, org_id)},
     * scheiterte <b>jeder</b> Upsert mit „no unique or exclusion constraint matching the ON
     * CONFLICT specification" — auch der ZEV-seitige, der mit dieser Aenderung nichts zu tun hat.
     */
    @Test
    void upsertZEV_FunktioniertNachDemSchluesseltausch() {
        debitorRepository.upsert(mieterAId, new BigDecimal("100.00"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), TEST_ORG_ID,
                Debitorherkunft.ZEV.name());
        debitorRepository.upsert(mieterAId, new BigDecimal("175.50"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), TEST_ORG_ID,
                Debitorherkunft.ZEV.name());
        syncMitDatenbank();

        List<Debitor> result = debitorRepository.findByDatumVonBetween(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getBetrag()).isEqualByComparingTo("175.50");
        assertThat(result.get(0).getHerkunft()).isEqualTo(Debitorherkunft.ZEV);
    }

    /** Eine bezahlte NK-Forderung wird von einem erneuten Lauf nicht angetastet. */
    @Test
    void upsertNK_BezahlteForderung_BleibtUnveraendert() {
        debitorRepository.upsert(mieterAId, new BigDecimal("100.00"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), TEST_ORG_ID,
                Debitorherkunft.NK.name());
        syncMitDatenbank();

        Debitor bezahlt = debitorRepository.findByDatumVonBetween(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1)).get(0);
        bezahlt.setZahldatum(LocalDate.of(2027, 1, 20));
        debitorRepository.save(bezahlt);
        syncMitDatenbank();

        debitorRepository.upsert(mieterAId, new BigDecimal("999.00"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), TEST_ORG_ID,
                Debitorherkunft.NK.name());
        syncMitDatenbank();

        List<Debitor> result = debitorRepository.findByDatumVonBetween(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getBetrag()).isEqualByComparingTo("100.00");
        assertThat(result.get(0).getZahldatum()).isEqualTo(LocalDate.of(2027, 1, 20));
    }

    /** Ein ueber die Entity gespeicherter Eintrag traegt ohne Zutun {@code ZEV}. */
    @Test
    void saveOhneHerkunft_TraegtZEV() {
        Debitor debitor = new Debitor();
        debitor.setOrgId(TEST_ORG_ID);
        debitor.setMieterId(mieterAId);
        debitor.setBetrag(new BigDecimal("50.00"));
        debitor.setDatumVon(LocalDate.of(2026, 7, 1));
        debitor.setDatumBis(LocalDate.of(2026, 9, 30));

        Debitor gespeichert = debitorRepository.save(debitor);
        syncMitDatenbank();

        assertThat(gespeichert.getHerkunft()).isEqualTo(Debitorherkunft.ZEV);
    }
}
