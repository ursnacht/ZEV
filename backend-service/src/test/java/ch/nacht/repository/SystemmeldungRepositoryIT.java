package ch.nacht.repository;

import ch.nacht.AbstractIntegrationTest;
import ch.nacht.entity.MeldungLevel;
import ch.nacht.entity.Organisation;
import ch.nacht.entity.Systemmeldung;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integrationstests des {@link SystemmeldungRepository} ({@code Specs/Systemmeldungen.md}, FR-2).
 *
 * <p>Geprüft wird, was nur gegen eine echte Datenbank prüfbar ist: die sieben {@code @Query}-
 * Methoden samt Filterkombinationen und Schweregrad-Sortierung, die Paginierung über
 * {@code Slice.hasNext()}, der Mandantenfilter — und vor allem die beiden Stellen, an denen der
 * Filter <b>nicht</b> greift und die Isolation deshalb explizit in der Abfrage stehen muss:
 * Bulk-{@code UPDATE} (Auto-Resolve) und Bulk-{@code DELETE} (Aufräumen).
 *
 * <p><b>Warum die Zusicherungen hier von Hand angelegt werden:</b> Die Integrationstests laufen mit
 * {@code ddl-auto=create-drop} und abgeschaltetem Flyway (siehe {@link AbstractIntegrationTest}),
 * das Schema entsteht also aus dem Mapping. Der UNIQUE-<b>Teil</b>-Index
 * {@code (org_id, meldung_key) WHERE erledigt = FALSE} lässt sich in JPA gar nicht ausdrücken und
 * existiert nur in {@code V86}. Er wird deshalb in {@link #angleichAnFlywaySchema()} nachgezogen,
 * wortgleich zur Migration; dasselbe Muster verwendet {@code DebitorRepositoryIT}. DDL ist in
 * Postgres transaktional, die Änderung verschwindet mit dem Rollback des Tests.
 *
 * <p><b>Achtung bei Änderungen:</b> Wird der Index in einer neuen Migration angepasst, ist die
 * Kopie unten mitzuziehen — sonst prüft der Test weiter die alte Regel.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class SystemmeldungRepositoryIT extends AbstractIntegrationTest {

    private static final String KAT_BILANZ = "SYSTEMMELDUNG_KATEGORIE_BILANZMODELL";
    private static final String KAT_MQTT = "SYSTEMMELDUNG_KATEGORIE_MQTT";
    private static final String KEY_BILANZ = "BILANZMODELL_KEINE_BILANZDATEN";
    private static final String KEY_AUSFALL = "MQTT_ZAEHLER_AUSFALL";

    @Autowired
    private SystemmeldungRepository systemmeldungRepository;

    @Autowired
    private OrganisationRepository organisationRepository;

    @Autowired
    private EntityManager entityManager;

    private Long TEST_ORG_ID;

    /** Zweiter Mandant — nur als Gegenprobe der Isolation. */
    private Long FREMD_ORG_ID;

    @BeforeEach
    void setUp() {
        systemmeldungRepository.deleteAll();
        angleichAnFlywaySchema();

        TEST_ORG_ID = speichereOrg("Systemmeldung Test Organisation",
                UUID.fromString("4c8e1f92-7a35-4d60-b8e1-9f2c5a3d7b04"));
        FREMD_ORG_ID = speichereOrg("Fremde Organisation",
                UUID.fromString("8a1d4b73-2e69-4c05-9f37-6b0e2d8a5c19"));
    }

    // ==================== findByFilter: Filterkombinationen ====================

    @Test
    void shouldReturnAllWhenNoFilterIsSet() {
        speichere(MeldungLevel.ERROR, KAT_BILANZ, KEY_BILANZ, false);
        speichere(MeldungLevel.INFO, KAT_MQTT, KEY_AUSFALL, true);

        assertThat(seite(null, null, null)).hasSize(2);
    }

    @Test
    void shouldFilterByErledigt() {
        speichere(MeldungLevel.ERROR, KAT_BILANZ, KEY_BILANZ, false);
        speichere(MeldungLevel.INFO, KAT_MQTT, KEY_AUSFALL, true);

        assertThat(seite(false, null, null)).extracting(Systemmeldung::isErledigt)
                .containsExactly(false);
        assertThat(seite(true, null, null)).extracting(Systemmeldung::isErledigt)
                .containsExactly(true);
    }

    @Test
    void shouldFilterByKategorie() {
        speichere(MeldungLevel.ERROR, KAT_BILANZ, KEY_BILANZ, false);
        speichere(MeldungLevel.INFO, KAT_MQTT, KEY_AUSFALL, false);

        assertThat(seite(null, KAT_MQTT, null)).extracting(Systemmeldung::getKategorie)
                .containsExactly(KAT_MQTT);
    }

    @Test
    void shouldFilterByLevel() {
        speichere(MeldungLevel.ERROR, KAT_BILANZ, KEY_BILANZ, false);
        speichere(MeldungLevel.WARN, KAT_MQTT, KEY_AUSFALL, false);

        assertThat(seite(null, null, MeldungLevel.WARN)).extracting(Systemmeldung::getLevel)
                .containsExactly(MeldungLevel.WARN);
    }

    @Test
    void shouldCombineAllThreeFilters() {
        speichere(MeldungLevel.ERROR, KAT_BILANZ, KEY_BILANZ, false);
        speichere(MeldungLevel.WARN, KAT_MQTT, KEY_AUSFALL, false);
        speichere(MeldungLevel.WARN, KAT_MQTT, "MQTT_ZAEHLER_LUECKE", true);

        // erledigt=false UND Kategorie MQTT UND Level WARN trifft genau eine Zeile.
        assertThat(seite(false, KAT_MQTT, MeldungLevel.WARN))
                .extracting(Systemmeldung::getMeldungKey)
                .containsExactly(KEY_AUSFALL);
    }

    // ==================== Paginierung: hatMehr ohne Count ====================

    /**
     * {@code Slice.hasNext()} ist das {@code hatMehr}-Flag aus FR-1.12 — es entsteht daraus, dass
     * Spring Data eine Zeile mehr liest als angefordert, und ersetzt die teure Count-Abfrage.
     */
    @Test
    void shouldReportHasNextWithoutCountQuery() {
        for (int i = 1; i <= 3; i++) {
            speichere(MeldungLevel.INFO, KAT_MQTT, "KEY_" + i, true);
        }

        Slice<Systemmeldung> ersteSeite = systemmeldungRepository.findByFilter(
                null, null, null, PageRequest.of(0, 2, Sort.by("meldungKey")));
        assertThat(ersteSeite.getContent()).hasSize(2);
        assertThat(ersteSeite.hasNext()).isTrue();

        Slice<Systemmeldung> zweiteSeite = systemmeldungRepository.findByFilter(
                null, null, null, PageRequest.of(1, 2, Sort.by("meldungKey")));
        assertThat(zweiteSeite.getContent()).hasSize(1);
        assertThat(zweiteSeite.hasNext()).isFalse();
    }

    @Test
    void shouldReturnEmptySliceBeyondTheLastPage() {
        speichere(MeldungLevel.INFO, KAT_MQTT, KEY_AUSFALL, true);

        Slice<Systemmeldung> seite = systemmeldungRepository.findByFilter(
                null, null, null, PageRequest.of(5, 50, Sort.by("meldungKey")));

        assertThat(seite.getContent()).isEmpty();
        assertThat(seite.hasNext()).isFalse();
    }

    // ==================== Sortierung nach Schweregrad ====================

    /**
     * Level wird nach <b>Schweregrad</b> sortiert, nicht alphabetisch.
     *
     * <p>Alphabetisch ergäbe ERROR, INFO, WARN — eine Reihenfolge, in der WARN hinter INFO steht
     * und die niemand erwartet. Deshalb die {@code CASE}-Abfragen im Repository.
     */
    @Test
    void shouldSortByLevelSeverityAscending() {
        speichere(MeldungLevel.ERROR, KAT_BILANZ, "K_ERROR", true);
        speichere(MeldungLevel.INFO, KAT_MQTT, "K_INFO", true);
        speichere(MeldungLevel.WARN, KAT_MQTT, "K_WARN", true);

        Slice<Systemmeldung> seite = systemmeldungRepository.findByFilterOrderByLevelAsc(
                null, null, null, PageRequest.of(0, 50));

        assertThat(seite.getContent()).extracting(Systemmeldung::getLevel)
                .containsExactly(MeldungLevel.INFO, MeldungLevel.WARN, MeldungLevel.ERROR);
    }

    @Test
    void shouldSortByLevelSeverityDescending() {
        speichere(MeldungLevel.ERROR, KAT_BILANZ, "K_ERROR", true);
        speichere(MeldungLevel.INFO, KAT_MQTT, "K_INFO", true);
        speichere(MeldungLevel.WARN, KAT_MQTT, "K_WARN", true);

        Slice<Systemmeldung> seite = systemmeldungRepository.findByFilterOrderByLevelDesc(
                null, null, null, PageRequest.of(0, 50));

        assertThat(seite.getContent()).extracting(Systemmeldung::getLevel)
                .containsExactly(MeldungLevel.ERROR, MeldungLevel.WARN, MeldungLevel.INFO);
    }

    @Test
    void shouldSortByZuletztAufgetretenWithinTheSameLevel() {
        // Zweitkriterium der Level-Abfragen: neuste zuoberst.
        Systemmeldung alt = speichere(MeldungLevel.WARN, KAT_MQTT, "K_ALT", true);
        alt.setZuletztAufgetreten(LocalDateTime.of(2024, 1, 1, 0, 0));
        Systemmeldung neu = speichere(MeldungLevel.WARN, KAT_MQTT, "K_NEU", true);
        neu.setZuletztAufgetreten(LocalDateTime.of(2024, 6, 1, 0, 0));
        systemmeldungRepository.saveAllAndFlush(List.of(alt, neu));
        entityManager.clear();

        Slice<Systemmeldung> seite = systemmeldungRepository.findByFilterOrderByLevelAsc(
                null, null, null, PageRequest.of(0, 50));

        assertThat(seite.getContent()).extracting(Systemmeldung::getMeldungKey)
                .containsExactly("K_NEU", "K_ALT");
    }

    // ==================== findDistinctKategorien ====================

    @Test
    void shouldReturnDistinctKategorienSorted() {
        speichere(MeldungLevel.ERROR, KAT_MQTT, "K_1", true);
        speichere(MeldungLevel.ERROR, KAT_MQTT, "K_2", true);
        speichere(MeldungLevel.ERROR, KAT_BILANZ, "K_3", true);

        assertThat(systemmeldungRepository.findDistinctKategorien())
                .containsExactly(KAT_BILANZ, KAT_MQTT);
    }

    // ==================== Dedup-Lookup ====================

    @Test
    void shouldFindOnlyOpenEntryForDedup() {
        speichere(MeldungLevel.ERROR, KAT_BILANZ, KEY_BILANZ, true);

        // Ein erledigter Eintrag darf den Dedup-Lookup nicht beantworten — sonst entstünde bei
        // einem erneuten Fehler kein neuer offener Eintrag (FR-1.8).
        assertThat(systemmeldungRepository
                .findByOrgIdAndMeldungKeyAndErledigtFalse(TEST_ORG_ID, KEY_BILANZ)).isEmpty();
        assertThat(systemmeldungRepository
                .existsByOrgIdAndMeldungKeyAndErledigtFalse(TEST_ORG_ID, KEY_BILANZ)).isFalse();

        speichere(MeldungLevel.ERROR, KAT_BILANZ, KEY_BILANZ, false);

        assertThat(systemmeldungRepository
                .findByOrgIdAndMeldungKeyAndErledigtFalse(TEST_ORG_ID, KEY_BILANZ)).isPresent();
        assertThat(systemmeldungRepository
                .existsByOrgIdAndMeldungKeyAndErledigtFalse(TEST_ORG_ID, KEY_BILANZ)).isTrue();
    }

    @Test
    void shouldScopeDedupLookupToTheGivenOrg() {
        speichereFuerOrg(FREMD_ORG_ID, MeldungLevel.ERROR, KAT_BILANZ, KEY_BILANZ, false);

        // Org-explizit: der Eintrag des anderen Mandanten darf den eigenen Dedup nicht auslösen.
        assertThat(systemmeldungRepository
                .findByOrgIdAndMeldungKeyAndErledigtFalse(TEST_ORG_ID, KEY_BILANZ)).isEmpty();
        assertThat(systemmeldungRepository
                .findByOrgIdAndMeldungKeyAndErledigtFalse(FREMD_ORG_ID, KEY_BILANZ)).isPresent();
    }

    // ==================== UNIQUE-Teil-Index: die Dedup-Invariante ====================

    /**
     * Zwei <b>offene</b> Einträge mit demselben Key im selben Mandanten sind unmöglich.
     *
     * <p>Das ist die Invariante, auf der die Deduplizierung beruht — und der Grund, warum
     * Audit-Einträge direkt als erledigt gespeichert werden (FR-1.11).
     */
    @Test
    void shouldRejectASecondOpenEntryWithTheSameKey() {
        speichere(MeldungLevel.ERROR, KAT_BILANZ, KEY_BILANZ, false);

        assertThatThrownBy(() -> speichere(MeldungLevel.WARN, KAT_MQTT, KEY_BILANZ, false))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** Erledigte Einträge sind vom Teil-Index ausgenommen — mehrere davon sind erlaubt. */
    @Test
    void shouldAllowSeveralResolvedEntriesWithTheSameKey() {
        speichere(MeldungLevel.INFO, KAT_MQTT, KEY_AUSFALL, true);
        speichere(MeldungLevel.INFO, KAT_MQTT, KEY_AUSFALL, true);
        speichere(MeldungLevel.INFO, KAT_MQTT, KEY_AUSFALL, true);

        assertThat(systemmeldungRepository.findAll()).hasSize(3);
    }

    /** Derselbe Key darf bei zwei Mandanten gleichzeitig offen sein — der Index trägt die org_id. */
    @Test
    void shouldAllowTheSameOpenKeyInAnotherOrg() {
        speichere(MeldungLevel.ERROR, KAT_BILANZ, KEY_BILANZ, false);
        speichereFuerOrg(FREMD_ORG_ID, MeldungLevel.ERROR, KAT_BILANZ, KEY_BILANZ, false);

        assertThat(systemmeldungRepository.findAll()).hasSize(2);
    }

    /** Ein offener neben einem erledigten Eintrag desselben Keys ist der Normalfall nach FR-1.8. */
    @Test
    void shouldAllowOneOpenNextToAResolvedEntry() {
        speichere(MeldungLevel.ERROR, KAT_BILANZ, KEY_BILANZ, true);
        speichere(MeldungLevel.ERROR, KAT_BILANZ, KEY_BILANZ, false);

        assertThat(systemmeldungRepository.findAll()).hasSize(2);
    }

    // ==================== autoResolve: Bulk-UPDATE, org-explizit ====================

    @Test
    void shouldAutoResolveOpenEntriesOfTheKey() {
        speichere(MeldungLevel.ERROR, KAT_BILANZ, KEY_BILANZ, false);
        LocalDateTime jetzt = LocalDateTime.of(2024, 6, 1, 12, 0);

        int anzahl = systemmeldungRepository.autoResolve(TEST_ORG_ID, KEY_BILANZ, jetzt);
        entityManager.flush();
        entityManager.clear();

        assertThat(anzahl).isEqualTo(1);
        Systemmeldung aufgeloest = systemmeldungRepository.findAll().getFirst();
        assertThat(aufgeloest.isErledigt()).isTrue();
        assertThat(aufgeloest.getErledigtAm()).isEqualTo(jetzt);
        assertThat(aufgeloest.isErledigtAutomatisch()).isTrue();
    }

    @Test
    void shouldNotAutoResolveOtherKeys() {
        speichere(MeldungLevel.ERROR, KAT_BILANZ, KEY_BILANZ, false);
        speichere(MeldungLevel.WARN, KAT_MQTT, KEY_AUSFALL, false);

        int anzahl = systemmeldungRepository.autoResolve(
                TEST_ORG_ID, KEY_BILANZ, LocalDateTime.of(2024, 6, 1, 12, 0));
        entityManager.flush();
        entityManager.clear();

        assertThat(anzahl).isEqualTo(1);
        assertThat(systemmeldungRepository.findAll())
                .filteredOn(m -> !m.isErledigt())
                .extracting(Systemmeldung::getMeldungKey)
                .containsExactly(KEY_AUSFALL);
    }

    /**
     * Auto-Resolve darf niemals über Mandantengrenzen wirken.
     *
     * <p>Der Hibernate-Filter greift bei Bulk-{@code UPDATE} nicht — die {@code org_id} steht
     * deshalb explizit in der Abfrage. Genau das prüft dieser Test: der Eintrag des anderen
     * Mandanten bleibt offen.
     */
    @Test
    void shouldNotAutoResolveEntriesOfAnotherOrg() {
        speichereFuerOrg(FREMD_ORG_ID, MeldungLevel.ERROR, KAT_BILANZ, KEY_BILANZ, false);

        int anzahl = systemmeldungRepository.autoResolve(
                TEST_ORG_ID, KEY_BILANZ, LocalDateTime.of(2024, 6, 1, 12, 0));
        entityManager.flush();
        entityManager.clear();

        assertThat(anzahl).isZero();
        assertThat(systemmeldungRepository.findAll()).singleElement()
                .satisfies(m -> assertThat(m.isErledigt()).isFalse());
    }

    // ==================== deleteErledigtByOrgId: Bulk-DELETE, org-explizit ====================

    @Test
    void shouldDeleteOnlyResolvedEntries() {
        speichere(MeldungLevel.ERROR, KAT_BILANZ, KEY_BILANZ, false);
        speichere(MeldungLevel.INFO, KAT_MQTT, KEY_AUSFALL, true);

        int anzahl = systemmeldungRepository.deleteErledigtByOrgId(TEST_ORG_ID);
        entityManager.flush();
        entityManager.clear();

        assertThat(anzahl).isEqualTo(1);
        assertThat(systemmeldungRepository.findAll()).singleElement()
                .satisfies(m -> assertThat(m.getMeldungKey()).isEqualTo(KEY_BILANZ));
    }

    /**
     * Das Aufräumen darf keine Meldungen eines anderen Mandanten löschen.
     *
     * <p>Der kritischste Test dieser Klasse: Hibernate-{@code @Filter} greift bei
     * Bulk-{@code DELETE}-JPQL <b>nicht</b>. Ohne die explizite {@code org_id} in der Bedingung
     * löschte ein Klick auf „Erledigte Meldungen löschen" mandantenübergreifend — unwiederbringlich
     * und ohne jede Spur.
     */
    @Test
    void shouldNotDeleteResolvedEntriesOfAnotherOrg() {
        speichere(MeldungLevel.INFO, KAT_MQTT, KEY_AUSFALL, true);
        speichereFuerOrg(FREMD_ORG_ID, MeldungLevel.INFO, KAT_MQTT, KEY_AUSFALL, true);

        int anzahl = systemmeldungRepository.deleteErledigtByOrgId(TEST_ORG_ID);
        entityManager.flush();
        entityManager.clear();

        assertThat(anzahl).isEqualTo(1);
        assertThat(systemmeldungRepository.findAll()).singleElement()
                .satisfies(m -> assertThat(m.getOrgId()).isEqualTo(FREMD_ORG_ID));
    }

    // ==================== Retention: mandantenübergreifend, nur Erledigte ====================

    @Test
    void shouldDeleteResolvedEntriesOlderThanTheCutoff() {
        Systemmeldung alt = speichere(MeldungLevel.INFO, KAT_MQTT, "K_ALT", true);
        alt.setErledigtAm(LocalDateTime.of(2024, 1, 1, 0, 0));
        Systemmeldung neu = speichere(MeldungLevel.INFO, KAT_MQTT, "K_NEU", true);
        neu.setErledigtAm(LocalDateTime.of(2024, 12, 1, 0, 0));
        systemmeldungRepository.saveAllAndFlush(List.of(alt, neu));

        int anzahl = systemmeldungRepository.deleteErledigtOlderThan(LocalDateTime.of(2024, 6, 1, 0, 0));
        entityManager.flush();
        entityManager.clear();

        assertThat(anzahl).isEqualTo(1);
        assertThat(systemmeldungRepository.findAll()).extracting(Systemmeldung::getMeldungKey)
                .containsExactly("K_NEU");
    }

    /**
     * Offene Einträge werden von der Retention <b>nie</b> gelöscht (FR-1.10) — auch wenn sie alt
     * sind. Ein offenes Problem verschwindet nicht durch Zeitablauf.
     */
    @Test
    void shouldNeverDeleteOpenEntriesRegardlessOfAge() {
        Systemmeldung offen = speichere(MeldungLevel.ERROR, KAT_BILANZ, KEY_BILANZ, false);
        offen.setErstmalsAufgetreten(LocalDateTime.of(2020, 1, 1, 0, 0));
        offen.setZuletztAufgetreten(LocalDateTime.of(2020, 1, 1, 0, 0));
        systemmeldungRepository.saveAndFlush(offen);

        int anzahl = systemmeldungRepository.deleteErledigtOlderThan(LocalDateTime.now());
        entityManager.flush();
        entityManager.clear();

        assertThat(anzahl).isZero();
        assertThat(systemmeldungRepository.findAll()).hasSize(1);
    }

    /** Die Retention läuft bewusst mandantenübergreifend (FR-1.10) — sie ist ein Systemjob. */
    @Test
    void shouldApplyRetentionAcrossOrgs() {
        Systemmeldung eigene = speichere(MeldungLevel.INFO, KAT_MQTT, "K_EIGEN", true);
        eigene.setErledigtAm(LocalDateTime.of(2024, 1, 1, 0, 0));
        Systemmeldung fremde = speichereFuerOrg(FREMD_ORG_ID, MeldungLevel.INFO, KAT_MQTT, "K_FREMD", true);
        fremde.setErledigtAm(LocalDateTime.of(2024, 1, 1, 0, 0));
        systemmeldungRepository.saveAllAndFlush(List.of(eigene, fremde));

        int anzahl = systemmeldungRepository.deleteErledigtOlderThan(LocalDateTime.of(2024, 6, 1, 0, 0));
        entityManager.flush();
        entityManager.clear();

        assertThat(anzahl).isEqualTo(2);
        assertThat(systemmeldungRepository.findAll()).isEmpty();
    }

    // ==================== Mandantenfilter ====================

    /** Gegenprobe: Ohne Filter sind beide Mandanten sichtbar — sonst wäre der Test darunter wertlos. */
    @Test
    void shouldSeeAllOrgsWhenOrgFilterDisabled() {
        speichere(MeldungLevel.ERROR, KAT_BILANZ, KEY_BILANZ, false);
        speichereFuerOrg(FREMD_ORG_ID, MeldungLevel.WARN, KAT_MQTT, KEY_AUSFALL, false);
        entityManager.clear();

        assertThat(seite(null, null, null)).hasSize(2);
        assertThat(systemmeldungRepository.findDistinctKategorien()).hasSize(2);
    }

    @Test
    void shouldNotSeeSystemmeldungOfOtherOrgWhenOrgFilterEnabled() {
        Systemmeldung eigene = speichere(MeldungLevel.ERROR, KAT_BILANZ, KEY_BILANZ, false);
        Systemmeldung fremde = speichereFuerOrg(
                FREMD_ORG_ID, MeldungLevel.WARN, KAT_MQTT, KEY_AUSFALL, false);
        entityManager.clear();

        aktiviereOrgFilter(entityManager, TEST_ORG_ID);

        assertThat(seite(null, null, null)).extracting(Systemmeldung::getMeldungKey)
                .containsExactly(KEY_BILANZ);
        assertThat(systemmeldungRepository.findDistinctKategorien()).containsExactly(KAT_BILANZ);
        assertThat(systemmeldungRepository.findFirstById(eigene.getId())).isPresent();
        assertThat(systemmeldungRepository.findFirstById(fremde.getId())).isEmpty();
        assertThat(systemmeldungRepository.existsById(fremde.getId())).isFalse();
    }

    // ==================== Angleich an das Flyway-Schema ====================

    /**
     * Legt den UNIQUE-Teil-Index aus {@code V86} an.
     *
     * <p>Ein Teil-Index ({@code WHERE erledigt = FALSE}) lässt sich in JPA nicht ausdrücken; er
     * existiert deshalb ausschliesslich in der Migration und fehlt im Mapping-Schema der Tests.
     */
    private void angleichAnFlywaySchema() {
        entityManager.createNativeQuery(
                "DROP INDEX IF EXISTS zev.uk_systemmeldung_offen").executeUpdate();
        entityManager.createNativeQuery(
                "CREATE UNIQUE INDEX uk_systemmeldung_offen"
                        + " ON zev.systemmeldung (org_id, meldung_key) WHERE erledigt = FALSE")
                .executeUpdate();
    }

    // ==================== Testdaten ====================

    private Long speichereOrg(String name, UUID keycloakOrgId) {
        Organisation org = new Organisation();
        org.setKeycloakOrgId(keycloakOrgId);
        org.setName(name);
        org.setErstelltAm(LocalDateTime.now());
        return organisationRepository.save(org).getId();
    }

    private Systemmeldung speichere(MeldungLevel level, String kategorie, String key, boolean erledigt) {
        return speichereFuerOrg(TEST_ORG_ID, level, kategorie, key, erledigt);
    }

    private Systemmeldung speichereFuerOrg(Long orgId, MeldungLevel level, String kategorie,
                                           String key, boolean erledigt) {
        LocalDateTime jetzt = LocalDateTime.of(2024, 3, 1, 10, 0);
        Systemmeldung meldung = new Systemmeldung(level, kategorie, key, "p", jetzt, jetzt);
        meldung.setOrgId(orgId);
        if (erledigt) {
            meldung.setErledigt(true);
            meldung.setErledigtAm(jetzt);
        }
        return systemmeldungRepository.saveAndFlush(meldung);
    }

    /** Kurzform für die Standard-Listenabfrage. */
    private List<Systemmeldung> seite(Boolean erledigt, String kategorie, MeldungLevel level) {
        Pageable pageable = PageRequest.of(0, 50, Sort.by("meldungKey"));
        return systemmeldungRepository.findByFilter(erledigt, kategorie, level, pageable).getContent();
    }
}
