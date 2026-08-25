package ch.nacht.repository;

import ch.nacht.AbstractIntegrationTest;
import ch.nacht.entity.Mengeneinheit;
import ch.nacht.entity.Mieter;
import ch.nacht.entity.NkAbrechnung;
import ch.nacht.entity.NkZusatz;
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
 * Integrationstests der Tabellenzusicherungen von {@code zev.nk_zusatz}
 * ({@code Specs/Nebenkosten/Abrechnung.md}, FR-3 und FR-5).
 *
 * <p>{@link NkAbrechnungRepositoryIT} deckt die Ableitungsmethoden dieses Repositories ab. Hier
 * geht es um das, was nur die Datenbank durchsetzt: die je Mieter eindeutige Reihenfolge, die
 * erlaubten Mengeneinheiten, die nicht-negativen Werte, das kaskadierende Loeschen, den
 * Loeschschutz des Mieters und den Mandantenfilter.
 *
 * <p>Zum Nachlegen der Zusicherungen und zur Pflege siehe {@link NkPositionRepositoryIT} —
 * dieselbe Begruendung und dieselbe Fallstrick-Warnung gelten hier.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class NkZusatzRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private NkZusatzRepository zusatzRepository;

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

    /** Die Reihenfolge ist je Mieter eindeutig; jede nativ eingefuegte Zeile braucht eine neue. */
    private int naechsteReihenfolge;

    @BeforeEach
    void setUp() {
        zusatzRepository.deleteAllInBatch();
        abrechnungRepository.deleteAllInBatch();
        mieterRepository.deleteAllInBatch();
        entityManager.flush();

        angleichAnFlywaySchema();

        TEST_ORG_ID = saveOrganisation("Nebenkosten Zusatz Test",
                UUID.fromString("6e9f2a5b-8c13-4e46-9f75-0a2b4c6d8e15"));
        FREMD_ORG_ID = saveOrganisation("Nebenkosten Zusatz Fremd",
                UUID.fromString("7f0a3b6c-9d24-4f57-8a86-1b3c5d7e9f26"));

        mieterAId = saveMieter(TEST_ORG_ID, "Mieter A").getId();
        mieterBId = saveMieter(TEST_ORG_ID, "Mieter B").getId();
        fremdMieterId = saveMieter(FREMD_ORG_ID, "Fremder Mieter").getId();

        abrechnungId = saveAbrechnung(TEST_ORG_ID, "NK 2025").getId();
        fremdAbrechnungId = saveAbrechnung(FREMD_ORG_ID, "NK 2025 fremd").getId();
        naechsteReihenfolge = 100;
    }

    // ==================== uq_nk_zusatz_reihenfolge ====================

    @Test
    void shouldRejectDuplicateReihenfolgeForSameMieter() {
        zusatzRepository.saveAndFlush(zusatz(abrechnungId, mieterAId, TEST_ORG_ID, 1, "Waschkarte"));

        assertVerletzt("uq_nk_zusatz_reihenfolge", () -> zusatzRepository
                .saveAndFlush(zusatz(abrechnungId, mieterAId, TEST_ORG_ID, 1, "Sauna")));
    }

    @Test
    void shouldAllowSameReihenfolgeForAnotherMieter() {
        // Die Nummer ist je Mieter eindeutig, nicht je Abrechnung: Zwei Mieter duerfen ihre
        // Zusatzposition an derselben Stelle des gemeinsamen Nummernraums haben.
        zusatzRepository.saveAndFlush(zusatz(abrechnungId, mieterAId, TEST_ORG_ID, 1, "Waschkarte"));
        zusatzRepository.saveAndFlush(zusatz(abrechnungId, mieterBId, TEST_ORG_ID, 1, "Sauna"));

        assertThat(zusatzRepository.findByAbrechnungIdOrderByMieterIdAscReihenfolgeAsc(abrechnungId))
                .hasSize(2);
        assertThat(zusatzRepository.findByAbrechnungIdAndMieterIdOrderByReihenfolge(
                abrechnungId, mieterAId)).hasSize(1);
    }

    // ==================== ck_nk_zusatz_einheit / ck_nk_zusatz_werte ====================

    @Test
    void shouldAcceptChfAsEinheit() {
        // V121: eine Zusatzposition kann ein reiner Betrag sein (Schadenersatz, Weiterverrechnung).
        zusatzRepository.saveAndFlush(zusatz(abrechnungId, mieterAId, TEST_ORG_ID, 1, "Schaden",
                Mengeneinheit.CHF, "1.000", "250.0000"));
        entityManager.clear();

        assertThat(zusatzRepository.findByAbrechnungIdOrderByMieterIdAscReihenfolgeAsc(abrechnungId))
                .singleElement()
                .extracting(NkZusatz::getEinheit)
                .isEqualTo(Mengeneinheit.CHF);
    }

    @Test
    void shouldRejectUnknownEinheit() {
        assertVerletzt("ck_nk_zusatz_einheit",
                () -> insertZusatz(mieterAId, "'LITER'", "4.000", "6.5000"));
    }

    @Test
    void shouldRejectNegativeMengeInDatabase() {
        assertVerletzt("ck_nk_zusatz_werte",
                () -> insertZusatz(mieterAId, "'STUECK'", "-0.001", "6.5000"));
    }

    @Test
    void shouldRejectNegativeBetragProEinheitInDatabase() {
        assertVerletzt("ck_nk_zusatz_werte",
                () -> insertZusatz(mieterAId, "'STUECK'", "4.000", "-0.0001"));
    }

    @Test
    void shouldRejectNegativeMengeByBeanValidation() {
        NkZusatz zusatz = zusatz(abrechnungId, mieterAId, TEST_ORG_ID, 1, "Sauna");
        zusatz.setMenge(new BigDecimal("-0.001"));

        assertThatThrownBy(() -> zusatzRepository.saveAndFlush(zusatz))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("Menge must not be negative");
    }

    @Test
    void shouldAcceptZeroWerte() {
        // Eine Zeile mit Menge 0 dokumentiert, dass eine Leistung erfasst, aber nicht bezogen
        // wurde — sie muss speicherbar bleiben.
        zusatzRepository.saveAndFlush(zusatz(abrechnungId, mieterAId, TEST_ORG_ID, 1, "Sauna",
                Mengeneinheit.STUECK, "0.000", "0.0000"));

        assertThat(zusatzRepository.findByAbrechnungIdAndMieterIdOrderByReihenfolge(
                abrechnungId, mieterAId)).hasSize(1);
    }

    // ==================== Fremdschluessel ====================

    @Test
    void shouldCascadeDeleteZusaetzeWhenAbrechnungDeleted() {
        zusatzRepository.saveAndFlush(zusatz(abrechnungId, mieterAId, TEST_ORG_ID, 1, "Sauna"));

        fuehreAus("DELETE FROM zev.nk_abrechnung WHERE id = " + abrechnungId);
        entityManager.clear();

        assertThat(zusatzRepository.findByAbrechnungIdOrderByMieterIdAscReihenfolgeAsc(abrechnungId))
                .isEmpty();
    }

    @Test
    void shouldRejectDeletingMieterReferencedByZusatz() {
        // Loeschschutz: Ohne den Mieter waere die abgeschlossene Abrechnung nicht mehr
        // reproduzierbar — die Betraege sind nirgends gespeichert.
        zusatzRepository.saveAndFlush(zusatz(abrechnungId, mieterAId, TEST_ORG_ID, 1, "Sauna"));

        assertVerletzt("fk_nk_zusatz_mieter",
                () -> fuehreAus("DELETE FROM zev.mieter WHERE id = " + mieterAId));
    }

    @Test
    void shouldAllowDeletingMieterWithoutZusatz() {
        zusatzRepository.saveAndFlush(zusatz(abrechnungId, mieterAId, TEST_ORG_ID, 1, "Sauna"));

        fuehreAus("DELETE FROM zev.mieter WHERE id = " + mieterBId);
        // Der Mieter liegt aus dem Aufbau noch im Persistence Context; ohne clear() antwortete
        // findById aus dem Cache und nicht aus der Datenbank.
        entityManager.clear();

        assertThat(mieterRepository.findById(mieterBId)).isEmpty();
    }

    @Test
    void shouldRejectZusatzForUnknownAbrechnung() {
        assertVerletzt("fk_nk_zusatz_abrechnung", () -> zusatzRepository
                .saveAndFlush(zusatz(999999L, mieterAId, TEST_ORG_ID, 1, "Sauna")));
    }

    // ==================== Mandantenfilter ====================

    @Test
    void shouldNotSeeZusaetzeOfOtherOrgWhenOrgFilterEnabled() {
        zusatzRepository.saveAndFlush(zusatz(abrechnungId, mieterAId, TEST_ORG_ID, 1, "Sauna"));
        zusatzRepository.saveAndFlush(
                zusatz(fremdAbrechnungId, fremdMieterId, FREMD_ORG_ID, 1, "Fremde Sauna"));
        entityManager.clear();

        aktiviereOrgFilter(TEST_ORG_ID);

        assertThat(zusatzRepository.findByAbrechnungIdOrderByMieterIdAscReihenfolgeAsc(abrechnungId))
                .hasSize(1);
        assertThat(zusatzRepository
                .findByAbrechnungIdOrderByMieterIdAscReihenfolgeAsc(fremdAbrechnungId)).isEmpty();
        assertThat(zusatzRepository.findByAbrechnungIdAndMieterIdOrderByReihenfolge(
                fremdAbrechnungId, fremdMieterId)).isEmpty();
        assertThat(zusatzRepository.countByMieterId(fremdMieterId)).isZero();
        assertThat(zusatzRepository.findAll()).hasSize(1);
    }

    @Test
    void shouldDeleteOnlyZusaetzeOfOwnOrgWhenOrgFilterEnabled() {
        zusatzRepository.saveAndFlush(
                zusatz(fremdAbrechnungId, fremdMieterId, FREMD_ORG_ID, 1, "Fremde Sauna"));
        entityManager.clear();

        aktiviereOrgFilter(TEST_ORG_ID);
        zusatzRepository.deleteByAbrechnungId(fremdAbrechnungId);
        entityManager.flush();
        entityManager.clear();

        aktiviereOrgFilter(FREMD_ORG_ID);
        assertThat(zusatzRepository
                .findByAbrechnungIdOrderByMieterIdAscReihenfolgeAsc(fremdAbrechnungId)).hasSize(1);
    }

    // ==================== Angleich an das Flyway-Schema ====================

    /**
     * Legt die Zusicherungen von {@code zev.nk_zusatz} an, die nur in den Migrationen stehen:
     * V118 (Fremdschluessel, Wertebereiche, Eindeutigkeit) und V121 (Einheit inkl. CHF).
     */
    private void angleichAnFlywaySchema() {
        fuehreAus("ALTER TABLE zev.nk_zusatz DROP CONSTRAINT IF EXISTS fk_nk_zusatz_abrechnung");
        fuehreAus("ALTER TABLE zev.nk_zusatz ADD CONSTRAINT fk_nk_zusatz_abrechnung"
                + " FOREIGN KEY (abrechnung_id) REFERENCES zev.nk_abrechnung(id) ON DELETE CASCADE");

        fuehreAus("ALTER TABLE zev.nk_zusatz DROP CONSTRAINT IF EXISTS fk_nk_zusatz_mieter");
        fuehreAus("ALTER TABLE zev.nk_zusatz ADD CONSTRAINT fk_nk_zusatz_mieter"
                + " FOREIGN KEY (mieter_id) REFERENCES zev.mieter(id) ON DELETE RESTRICT");

        fuehreAus("ALTER TABLE zev.nk_zusatz DROP CONSTRAINT IF EXISTS ck_nk_zusatz_einheit");
        fuehreAus("ALTER TABLE zev.nk_zusatz ADD CONSTRAINT ck_nk_zusatz_einheit"
                + " CHECK (einheit IN ('KWH', 'MONAT', 'STUECK', 'M3', 'CHF'))");

        fuehreAus("ALTER TABLE zev.nk_zusatz DROP CONSTRAINT IF EXISTS ck_nk_zusatz_werte");
        fuehreAus("ALTER TABLE zev.nk_zusatz ADD CONSTRAINT ck_nk_zusatz_werte"
                + " CHECK (menge >= 0 AND betrag_pro_einheit >= 0)");

        fuehreAus("ALTER TABLE zev.nk_zusatz DROP CONSTRAINT IF EXISTS uq_nk_zusatz_reihenfolge");
        fuehreAus("ALTER TABLE zev.nk_zusatz ADD CONSTRAINT uq_nk_zusatz_reihenfolge"
                + " UNIQUE (abrechnung_id, mieter_id, reihenfolge, org_id)");
    }

    // ==================== Testdaten ====================

    /** Fuegt eine Zeile mit nativem SQL ein — an der Bean Validation der Entity vorbei. */
    private void insertZusatz(Long mieter, String einheit, String menge, String betragProEinheit) {
        fuehreAus("INSERT INTO zev.nk_zusatz (id, org_id, abrechnung_id, mieter_id, reihenfolge,"
                + " bezeichnung, einheit, menge, betrag_pro_einheit)"
                + " VALUES (nextval('zev.nk_zusatz_seq'), " + TEST_ORG_ID + ", " + abrechnungId
                + ", " + mieter + ", " + naechsteReihenfolge++ + ", 'Constraint-Test', " + einheit
                + ", " + menge + ", " + betragProEinheit + ")");
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

    private NkZusatz zusatz(Long abrechnung, Long mieter, Long orgId, int reihenfolge,
                            String bezeichnung) {
        return zusatz(abrechnung, mieter, orgId, reihenfolge, bezeichnung, Mengeneinheit.STUECK,
                "4.000", "6.5000");
    }

    private NkZusatz zusatz(Long abrechnung, Long mieter, Long orgId, int reihenfolge,
                            String bezeichnung, Mengeneinheit einheit, String menge,
                            String betragProEinheit) {
        NkZusatz zusatz = new NkZusatz();
        zusatz.setOrgId(orgId);
        zusatz.setAbrechnungId(abrechnung);
        zusatz.setMieterId(mieter);
        zusatz.setReihenfolge(reihenfolge);
        zusatz.setBezeichnung(bezeichnung);
        zusatz.setEinheit(einheit);
        zusatz.setMenge(new BigDecimal(menge));
        zusatz.setBetragProEinheit(new BigDecimal(betragProEinheit));
        return zusatz;
    }
}
