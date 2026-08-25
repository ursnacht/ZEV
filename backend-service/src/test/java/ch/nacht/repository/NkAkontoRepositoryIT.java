package ch.nacht.repository;

import ch.nacht.AbstractIntegrationTest;
import ch.nacht.entity.Mieter;
import ch.nacht.entity.NkAbrechnung;
import ch.nacht.entity.NkAkonto;
import ch.nacht.entity.Organisation;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.validation.ConstraintViolationException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integrationstests der Tabellenzusicherungen von {@code zev.nk_akonto}
 * ({@code Specs/Nebenkosten/Abrechnung.md}, FR-4 und FR-5).
 *
 * <p>{@link NkAbrechnungRepositoryIT} deckt die Ableitungsmethoden dieses Repositories ab. Hier
 * geht es um das, was nur die Datenbank durchsetzt: die eine Zeile je Abrechnung und Mieter — sie
 * ist die Zusicherung, auf die sich das {@code Optional} von
 * {@link NkAkontoRepository#findByAbrechnungIdAndMieterId} stuetzt —, die nicht-negativen Werte
 * bei gleichzeitig erlaubter negativer Korrektur, das kaskadierende Loeschen, den Loeschschutz des
 * Mieters und den Mandantenfilter.
 *
 * <p>Zum Nachlegen der Zusicherungen und zur Pflege siehe {@link NkPositionRepositoryIT} —
 * dieselbe Begruendung und dieselbe Fallstrick-Warnung gelten hier.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class NkAkontoRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private NkAkontoRepository akontoRepository;

    @Autowired
    private NkAbrechnungRepository abrechnungRepository;

    @Autowired
    private MieterRepository mieterRepository;

    @Autowired
    private OrganisationRepository organisationRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private Long TEST_ORG_ID;
    private Long FREMD_ORG_ID;
    private Long abrechnungId;
    private Long fremdAbrechnungId;
    private Long mieterAId;
    private Long mieterBId;
    private Long fremdMieterId;

    @BeforeEach
    void setUp() {
        akontoRepository.deleteAllInBatch();
        abrechnungRepository.deleteAllInBatch();
        mieterRepository.deleteAllInBatch();
        entityManager.flush();

        angleichAnFlywaySchema();

        TEST_ORG_ID = saveOrganisation("Nebenkosten Akonto Test",
                UUID.fromString("8a1b4c7d-0e35-4a68-9b97-2c4d6e8f0a37"));
        FREMD_ORG_ID = saveOrganisation("Nebenkosten Akonto Fremd",
                UUID.fromString("9b2c5d8e-1f46-4b79-8ca8-3d5e7f9a1b48"));

        mieterAId = saveMieter(TEST_ORG_ID, "Mieter A").getId();
        mieterBId = saveMieter(TEST_ORG_ID, "Mieter B").getId();
        fremdMieterId = saveMieter(FREMD_ORG_ID, "Fremder Mieter").getId();

        abrechnungId = saveAbrechnung(TEST_ORG_ID, "NK 2025").getId();
        fremdAbrechnungId = saveAbrechnung(FREMD_ORG_ID, "NK 2025 fremd").getId();
    }

    // ==================== uq_nk_akonto ====================

    @Test
    void shouldRejectSecondAkontoForSameMieter() {
        // findByAbrechnungIdAndMieterId liefert ein Optional — waere eine zweite Zeile moeglich,
        // wuerde Spring Data dort mit einer NonUniqueResultException abbrechen.
        akontoRepository.saveAndFlush(akonto(abrechnungId, mieterAId, TEST_ORG_ID,
                "12.00", "150.00", "0.00"));

        assertVerletzt("uq_nk_akonto", () -> akontoRepository.saveAndFlush(
                akonto(abrechnungId, mieterAId, TEST_ORG_ID, "6.00", "150.00", "0.00")));
    }

    @Test
    void shouldAllowAkontoForAnotherMieterAndAnotherAbrechnung() {
        Long zweiteId = saveAbrechnung(TEST_ORG_ID, "NK 2024").getId();
        akontoRepository.saveAndFlush(akonto(abrechnungId, mieterAId, TEST_ORG_ID,
                "12.00", "150.00", "0.00"));
        akontoRepository.saveAndFlush(akonto(abrechnungId, mieterBId, TEST_ORG_ID,
                "12.00", "200.00", "0.00"));
        akontoRepository.saveAndFlush(akonto(zweiteId, mieterAId, TEST_ORG_ID,
                "12.00", "140.00", "0.00"));

        assertThat(akontoRepository.findByAbrechnungId(abrechnungId)).hasSize(2);
        assertThat(akontoRepository.findByAbrechnungId(zweiteId)).hasSize(1);
        assertThat(akontoRepository.countByMieterId(mieterAId)).isEqualTo(2);
    }

    // ==================== ck_nk_akonto_werte ====================

    @Test
    void shouldAllowNegativeKorrektur() {
        // Die Korrektur ist bewusst vom CHECK ausgenommen: Sie zieht eine Zahlung ab, die im
        // Vorjahr schon beruecksichtigt wurde.
        akontoRepository.saveAndFlush(akonto(abrechnungId, mieterAId, TEST_ORG_ID,
                "12.00", "150.00", "-50.00"));
        entityManager.clear();

        assertThat(akontoRepository.findByAbrechnungIdAndMieterId(abrechnungId, mieterAId)
                .orElseThrow().getKorrektur()).isEqualByComparingTo("-50.00");
    }

    @Test
    void shouldRejectNegativeAnzahlMonateInDatabase() {
        assertVerletzt("ck_nk_akonto_werte",
                () -> insertAkonto(mieterAId, "-0.01", "150.00", "0.00"));
    }

    @Test
    void shouldRejectNegativeBetragProMonatInDatabase() {
        assertVerletzt("ck_nk_akonto_werte",
                () -> insertAkonto(mieterAId, "12.00", "-0.01", "0.00"));
    }

    @Test
    void shouldRejectNegativeAnzahlMonateByBeanValidation() {
        assertThatThrownBy(() -> akontoRepository.saveAndFlush(
                akonto(abrechnungId, mieterAId, TEST_ORG_ID, "-0.01", "150.00", "0.00")))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("Anzahl Monate must not be negative");
    }

    @Test
    void shouldAcceptZeroMonate() {
        // Ein Mieter, der im Zeitraum nie eingezogen ist, hat 0 Monate — die Zeile muss trotzdem
        // speicherbar sein, sonst laesst sich eine halb erfasste Abrechnung nicht zwischenspeichern.
        akontoRepository.saveAndFlush(akonto(abrechnungId, mieterAId, TEST_ORG_ID,
                "0.00", "0.00", "0.00"));

        assertThat(akontoRepository.findByAbrechnungId(abrechnungId)).hasSize(1);
    }

    @Test
    void shouldKeepTwoDecimalsOfAnzahlMonate() {
        // NUMERIC(5,2): Angebrochene Monate brauchen die zwei Nachkommastellen; die dritte wird
        // gerundet, nicht abgewiesen.
        akontoRepository.saveAndFlush(akonto(abrechnungId, mieterAId, TEST_ORG_ID,
                "4.567", "150.00", "0.00"));
        entityManager.clear();

        assertThat(akontoRepository.findByAbrechnungIdAndMieterId(abrechnungId, mieterAId)
                .orElseThrow().getAnzahlMonate()).isEqualByComparingTo("4.57");
    }

    // ==================== Fremdschluessel ====================

    @Test
    void shouldCascadeDeleteAkontoWhenAbrechnungDeleted() {
        akontoRepository.saveAndFlush(akonto(abrechnungId, mieterAId, TEST_ORG_ID,
                "12.00", "150.00", "0.00"));

        fuehreAus("DELETE FROM zev.nk_abrechnung WHERE id = " + abrechnungId);
        entityManager.clear();

        assertThat(akontoRepository.findByAbrechnungId(abrechnungId)).isEmpty();
    }

    @Test
    void shouldRejectDeletingMieterReferencedByAkonto() {
        // Loeschschutz: Ohne den Mieter waere die abgeschlossene Abrechnung nicht mehr
        // reproduzierbar — die Betraege sind nirgends gespeichert.
        akontoRepository.saveAndFlush(akonto(abrechnungId, mieterAId, TEST_ORG_ID,
                "12.00", "150.00", "0.00"));

        assertVerletzt("fk_nk_akonto_mieter",
                () -> fuehreAus("DELETE FROM zev.mieter WHERE id = " + mieterAId));
    }

    @Test
    void shouldAllowDeletingMieterWithoutAkonto() {
        akontoRepository.saveAndFlush(akonto(abrechnungId, mieterAId, TEST_ORG_ID,
                "12.00", "150.00", "0.00"));

        fuehreAus("DELETE FROM zev.mieter WHERE id = " + mieterBId);
        // Der Mieter liegt aus dem Aufbau noch im Persistence Context; ohne clear() antwortete
        // findById aus dem Cache und nicht aus der Datenbank.
        entityManager.clear();

        assertThat(mieterRepository.findById(mieterBId)).isEmpty();
    }

    @Test
    void shouldRejectAkontoForUnknownAbrechnung() {
        assertVerletzt("fk_nk_akonto_abrechnung", () -> akontoRepository.saveAndFlush(
                akonto(999999L, mieterAId, TEST_ORG_ID, "12.00", "150.00", "0.00")));
    }

    // ==================== Mandantenfilter ====================

    @Test
    void shouldNotSeeAkontoOfOtherOrgWhenOrgFilterEnabled() {
        akontoRepository.saveAndFlush(akonto(abrechnungId, mieterAId, TEST_ORG_ID,
                "12.00", "150.00", "0.00"));
        akontoRepository.saveAndFlush(akonto(fremdAbrechnungId, fremdMieterId, FREMD_ORG_ID,
                "12.00", "999.00", "0.00"));
        entityManager.clear();

        aktiviereOrgFilter(TEST_ORG_ID);

        assertThat(akontoRepository.findByAbrechnungId(abrechnungId)).hasSize(1);
        assertThat(akontoRepository.findByAbrechnungId(fremdAbrechnungId)).isEmpty();
        assertThat(akontoRepository.findByAbrechnungIdAndMieterId(fremdAbrechnungId, fremdMieterId))
                .isEmpty();
        assertThat(akontoRepository.countByMieterId(fremdMieterId)).isZero();
        assertThat(akontoRepository.findAll()).hasSize(1);
    }

    @Test
    void shouldDeleteOnlyAkontoOfOwnOrgWhenOrgFilterEnabled() {
        akontoRepository.saveAndFlush(akonto(fremdAbrechnungId, fremdMieterId, FREMD_ORG_ID,
                "12.00", "999.00", "0.00"));
        entityManager.clear();

        aktiviereOrgFilter(TEST_ORG_ID);
        akontoRepository.deleteByAbrechnungId(fremdAbrechnungId);
        entityManager.flush();
        entityManager.clear();

        aktiviereOrgFilter(FREMD_ORG_ID);
        assertThat(akontoRepository.findByAbrechnungId(fremdAbrechnungId)).hasSize(1);
    }

    // ==================== Angleich an das Flyway-Schema ====================

    /** Legt die Zusicherungen von {@code zev.nk_akonto} aus V118 an. */
    private void angleichAnFlywaySchema() {
        fuehreAus("ALTER TABLE zev.nk_akonto DROP CONSTRAINT IF EXISTS fk_nk_akonto_abrechnung");
        fuehreAus("ALTER TABLE zev.nk_akonto ADD CONSTRAINT fk_nk_akonto_abrechnung"
                + " FOREIGN KEY (abrechnung_id) REFERENCES zev.nk_abrechnung(id) ON DELETE CASCADE");

        fuehreAus("ALTER TABLE zev.nk_akonto DROP CONSTRAINT IF EXISTS fk_nk_akonto_mieter");
        fuehreAus("ALTER TABLE zev.nk_akonto ADD CONSTRAINT fk_nk_akonto_mieter"
                + " FOREIGN KEY (mieter_id) REFERENCES zev.mieter(id) ON DELETE RESTRICT");

        fuehreAus("ALTER TABLE zev.nk_akonto DROP CONSTRAINT IF EXISTS ck_nk_akonto_werte");
        fuehreAus("ALTER TABLE zev.nk_akonto ADD CONSTRAINT ck_nk_akonto_werte"
                + " CHECK (anzahl_monate >= 0 AND betrag_pro_monat >= 0)");

        fuehreAus("ALTER TABLE zev.nk_akonto DROP CONSTRAINT IF EXISTS uq_nk_akonto");
        fuehreAus("ALTER TABLE zev.nk_akonto ADD CONSTRAINT uq_nk_akonto"
                + " UNIQUE (abrechnung_id, mieter_id, org_id)");
    }

    // ==================== Testdaten ====================

    /** Fuegt eine Zeile mit nativem SQL ein — an der Bean Validation der Entity vorbei. */
    private void insertAkonto(Long mieter, String monate, String proMonat, String korrektur) {
        fuehreAus("INSERT INTO zev.nk_akonto (id, org_id, abrechnung_id, mieter_id, anzahl_monate,"
                + " betrag_pro_monat, korrektur)"
                + " VALUES (nextval('zev.nk_akonto_seq'), " + TEST_ORG_ID + ", " + abrechnungId
                + ", " + mieter + ", " + monate + ", " + proMonat + ", " + korrektur + ")");
    }

    private void fuehreAus(String sql) {
        entityManager.createNativeQuery(sql).executeUpdate();
    }

    private void aktiviereOrgFilter(Long orgId) {
        entityManager.unwrap(Session.class).enableFilter("orgFilter").setParameter("orgId", orgId);
    }

    /**
     * Prueft, dass die Datenbank den Schreibzugriff mit der genannten Zusicherung abweist. Der
     * Name wird mitgeprueft: Sonst bestaetigte der Test auch eine Ablehnung aus anderem Grund.
     */
    private void assertVerletzt(String constraintName, ThrowingCallable schreibzugriff) {
        assertThatThrownBy(schreibzugriff)
                .rootCause()
                .hasMessageContaining(constraintName);
    }

    private Long saveOrganisation(String name, UUID keycloakOrgId) {
        Organisation org = new Organisation();
        org.setKeycloakOrgId(keycloakOrgId);
        org.setName(name);
        org.setErstelltAm(LocalDateTime.now());
        return organisationRepository.saveAndFlush(org).getId();
    }

    private Mieter saveMieter(Long orgId, String name) {
        Mieter mieter = new Mieter();
        mieter.setOrgId(orgId);
        mieter.setName(name);
        mieter.setStrasse("Teststrasse 1");
        mieter.setPlz("8000");
        mieter.setOrt("Zuerich");
        mieter.setMietbeginn(LocalDate.of(2020, 1, 1));
        return mieterRepository.saveAndFlush(mieter);
    }

    private NkAbrechnung saveAbrechnung(Long orgId, String bezeichnung) {
        NkAbrechnung abrechnung = new NkAbrechnung();
        abrechnung.setOrgId(orgId);
        abrechnung.setBezeichnung(bezeichnung);
        abrechnung.setDatumVon(LocalDate.of(2025, 1, 1));
        abrechnung.setDatumBis(LocalDate.of(2025, 12, 31));
        abrechnung.setAnzahlWohnungen(9);
        return abrechnungRepository.saveAndFlush(abrechnung);
    }

    private NkAkonto akonto(Long abrechnung, Long mieter, Long orgId, String monate,
                            String proMonat, String korrektur) {
        NkAkonto akonto = new NkAkonto();
        akonto.setOrgId(orgId);
        akonto.setAbrechnungId(abrechnung);
        akonto.setMieterId(mieter);
        akonto.setAnzahlMonate(new BigDecimal(monate));
        akonto.setBetragProMonat(new BigDecimal(proMonat));
        akonto.setKorrektur(new BigDecimal(korrektur));
        return akonto;
    }
}
