package ch.nacht.repository;

import ch.nacht.AbstractIntegrationTest;
import ch.nacht.entity.Mengeneinheit;
import ch.nacht.entity.NkAbrechnung;
import ch.nacht.entity.NkPosition;
import ch.nacht.entity.NkPositionsart;
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
 * Integrationstests der Tabellenzusicherungen von {@code zev.nk_position}
 * ({@code Specs/Nebenkosten/Abrechnung.md}, FR-2 und FR-5).
 *
 * <p>{@link NkAbrechnungRepositoryIT} deckt die Ableitungsmethoden dieses Repositories ab. Hier
 * geht es um das, was <b>nur</b> die Datenbank durchsetzt und ein Service-Unit-Test deshalb nicht
 * sehen kann: den art-abhaengigen CHECK {@code ck_nk_position_felder}, die Wertebereiche, die
 * eindeutige Reihenfolge, das kaskadierende Loeschen und den Mandantenfilter.
 *
 * <p><b>Warum die Zusicherungen hier von Hand angelegt werden:</b> Die Integrationstests laufen
 * mit {@code ddl-auto=create-drop} und abgeschaltetem Flyway (siehe
 * {@link AbstractIntegrationTest}), das Schema entsteht also aus dem Mapping. Die Entity kennt
 * weder {@code uniqueConstraints} noch {@code @Check} — die Zusicherungen stehen ausschliesslich
 * in den Migrationen. Sie werden deshalb in {@link #angleichAnFlywaySchema()} nachgezogen,
 * wortgleich zu V118/V121/V125; dasselbe Muster verwendet {@code DebitorRepositoryIT}. DDL ist in
 * Postgres transaktional, die Aenderung verschwindet mit dem Rollback des Tests.
 *
 * <p><b>Achtung bei Aenderungen:</b> Wird eine dieser Zusicherungen in einer neuen Migration
 * angepasst, ist die Kopie unten mitzuziehen — sonst prueft der Test weiter die alte Regel.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class NkPositionRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private NkPositionRepository positionRepository;

    @Autowired
    private NkAbrechnungRepository abrechnungRepository;

    @Autowired
    private OrganisationRepository organisationRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private Long TEST_ORG_ID;
    private Long FREMD_ORG_ID;
    private Long abrechnungId;
    private Long fremdAbrechnungId;

    /** Die Reihenfolge ist eindeutig; jede nativ eingefuegte Zeile braucht eine neue. */
    private int naechsteReihenfolge;

    @BeforeEach
    void setUp() {
        positionRepository.deleteAllInBatch();
        abrechnungRepository.deleteAllInBatch();
        entityManager.flush();

        angleichAnFlywaySchema();

        TEST_ORG_ID = saveOrganisation("Nebenkosten Position Test",
                UUID.fromString("2a5b8c1d-4e7f-4a02-9b31-6c8d0e2f4a71"));
        FREMD_ORG_ID = saveOrganisation("Nebenkosten Position Fremd",
                UUID.fromString("3b6c9d2e-5f80-4b13-8c42-7d9e1f3a5b82"));

        abrechnungId = saveAbrechnung(TEST_ORG_ID, "NK 2025").getId();
        fremdAbrechnungId = saveAbrechnung(FREMD_ORG_ID, "NK 2025 fremd").getId();
        naechsteReihenfolge = 100;
    }

    // ==================== ck_nk_position_art / ck_nk_position_felder ====================

    @Test
    void shouldAcceptAllFourPositionsarten() {
        // ANTEIL kam erst mit V125 dazu; ohne diesen Test faellt eine vergessene
        // Constraint-Erweiterung erst beim Speichern in der Oberflaeche auf.
        insertPosition("UMLAGE", "'M3'", "900.00", "500.000", "NULL", "NULL");
        insertPosition("VERBRAUCH", "'KWH'", "NULL", "NULL", "3.5000", "NULL");
        insertPosition("ZUSCHLAG", "NULL", "NULL", "NULL", "NULL", "5.00");
        insertPosition("ANTEIL", "NULL", "1200.00", "NULL", "NULL", "NULL");

        assertThat(positionRepository.findByAbrechnungIdOrderByReihenfolge(abrechnungId))
                .extracting(NkPosition::getArt)
                .containsExactly(NkPositionsart.UMLAGE, NkPositionsart.VERBRAUCH,
                        NkPositionsart.ZUSCHLAG, NkPositionsart.ANTEIL);
    }

    @Test
    void shouldRejectUnknownPositionsart() {
        assertVerletzt("ck_nk_position_art",
                () -> insertPosition("PAUSCHALE", "NULL", "900.00", "NULL", "NULL", "NULL"));
    }

    @Test
    void shouldRejectUmlageWithoutTotalbetrag() {
        assertVerletzt("ck_nk_position_felder",
                () -> insertPosition("UMLAGE", "'M3'", "NULL", "500.000", "NULL", "NULL"));
    }

    @Test
    void shouldRejectVerbrauchWithTotalbetrag() {
        // Eine VERBRAUCH-Zeile mit Totalbetrag waere nicht berechenbar: Es bliebe offen, ob der
        // Betrag verteilt oder aus Menge mal Preis gebildet wird.
        assertVerletzt("ck_nk_position_felder",
                () -> insertPosition("VERBRAUCH", "'KWH'", "900.00", "NULL", "3.5000", "NULL"));
    }

    @Test
    void shouldRejectZuschlagWithEinheit() {
        // Ein Zuschlag rechnet in Prozent; eine Mengeneinheit hat dort keine Bedeutung.
        assertVerletzt("ck_nk_position_felder",
                () -> insertPosition("ZUSCHLAG", "'KWH'", "NULL", "NULL", "NULL", "5.00"));
    }

    @Test
    void shouldRejectAnteilWithProzentsatzAtPosition() {
        // Der Prozentsatz einer ANTEIL-Position steht je Mieter in nk_verbrauch.menge, nicht an
        // der Position — sonst waere sie von ZUSCHLAG nicht mehr zu unterscheiden.
        assertVerletzt("ck_nk_position_felder",
                () -> insertPosition("ANTEIL", "NULL", "1200.00", "NULL", "NULL", "50.00"));
    }

    @Test
    void shouldRejectAnteilWithoutTotalbetrag() {
        assertVerletzt("ck_nk_position_felder",
                () -> insertPosition("ANTEIL", "NULL", "NULL", "NULL", "NULL", "NULL"));
    }

    // ==================== ck_nk_position_einheit / _prozent / _mengen ====================

    @Test
    void shouldAcceptChfAsEinheit() {
        // V121: fuer Umlagen, deren verteilte Groesse selbst ein Betrag ist (Gruenabfuhr, Praemien).
        insertPosition("UMLAGE", "'CHF'", "900.00", "900.000", "NULL", "NULL");

        assertThat(positionRepository.findByAbrechnungIdOrderByReihenfolge(abrechnungId))
                .singleElement()
                .extracting(NkPosition::getEinheit)
                .isEqualTo(Mengeneinheit.CHF);
    }

    @Test
    void shouldRejectUnknownEinheit() {
        assertVerletzt("ck_nk_position_einheit",
                () -> insertPosition("UMLAGE", "'LITER'", "900.00", "NULL", "NULL", "NULL"));
    }

    @Test
    void shouldRejectProzentsatzAboveHundred() {
        assertVerletzt("ck_nk_position_prozent",
                () -> insertPosition("ZUSCHLAG", "NULL", "NULL", "NULL", "NULL", "100.01"));
    }

    @Test
    void shouldRejectNegativeTotalbetrag() {
        assertVerletzt("ck_nk_position_mengen",
                () -> insertPosition("UMLAGE", "'M3'", "-1.00", "NULL", "NULL", "NULL"));
    }

    @Test
    void shouldRejectNegativeBetragProEinheitByBeanValidation() {
        // Vor der Datenbank greift die Bean Validation der Entity — sie liefert die Meldung, die
        // der Anwender zu sehen bekommt.
        NkPosition position = verbrauch(abrechnungId, TEST_ORG_ID, 1, "Warmwasser");
        position.setBetragProEinheit(new BigDecimal("-0.0001"));

        assertThatThrownBy(() -> positionRepository.saveAndFlush(position))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("Betrag pro Einheit must not be negative");
    }

    // ==================== uq_nk_position_reihenfolge ====================

    @Test
    void shouldRejectDuplicateReihenfolgeWithinAbrechnung() {
        // Zwei Positionen mit derselben Nummer machten die Zuschlagskaskade nicht-deterministisch.
        positionRepository.saveAndFlush(umlage(abrechnungId, TEST_ORG_ID, 1, "Allgemeinstrom"));

        assertVerletzt("uq_nk_position_reihenfolge", () -> positionRepository
                .saveAndFlush(umlage(abrechnungId, TEST_ORG_ID, 1, "Regenwasser")));
    }

    @Test
    void shouldAllowSameReihenfolgeInAnotherAbrechnung() {
        Long zweiteId = saveAbrechnung(TEST_ORG_ID, "NK 2024").getId();
        positionRepository.saveAndFlush(umlage(abrechnungId, TEST_ORG_ID, 1, "Allgemeinstrom"));
        positionRepository.saveAndFlush(umlage(zweiteId, TEST_ORG_ID, 1, "Allgemeinstrom"));

        assertThat(positionRepository.findByAbrechnungIdOrderByReihenfolge(abrechnungId)).hasSize(1);
        assertThat(positionRepository.findByAbrechnungIdOrderByReihenfolge(zweiteId)).hasSize(1);
    }

    // ==================== fk_nk_position_abrechnung (ON DELETE CASCADE) ====================

    @Test
    void shouldCascadeDeletePositionenWhenAbrechnungDeleted() {
        positionRepository.saveAndFlush(umlage(abrechnungId, TEST_ORG_ID, 1, "Allgemeinstrom"));
        positionRepository.saveAndFlush(verbrauch(abrechnungId, TEST_ORG_ID, 2, "Warmwasser"));

        fuehreAus("DELETE FROM zev.nk_abrechnung WHERE id = " + abrechnungId);
        entityManager.clear();

        assertThat(positionRepository.findByAbrechnungIdOrderByReihenfolge(abrechnungId)).isEmpty();
    }

    @Test
    void shouldRejectPositionForUnknownAbrechnung() {
        assertVerletzt("fk_nk_position_abrechnung", () -> positionRepository
                .saveAndFlush(umlage(999999L, TEST_ORG_ID, 1, "Allgemeinstrom")));
    }

    // ==================== Mandantenfilter ====================

    @Test
    void shouldNotSeePositionenOfOtherOrgWhenOrgFilterEnabled() {
        positionRepository.saveAndFlush(umlage(abrechnungId, TEST_ORG_ID, 1, "Allgemeinstrom"));
        positionRepository.saveAndFlush(umlage(fremdAbrechnungId, FREMD_ORG_ID, 1, "Allgemeinstrom"));
        entityManager.clear();

        aktiviereOrgFilter(TEST_ORG_ID);

        assertThat(positionRepository.findByAbrechnungIdOrderByReihenfolge(abrechnungId)).hasSize(1);
        // Die Abrechnung des anderen Mandanten existiert, ihre Positionen sind aber unsichtbar.
        assertThat(positionRepository.findByAbrechnungIdOrderByReihenfolge(fremdAbrechnungId)).isEmpty();
        assertThat(positionRepository.existsByAbrechnungIdAndReihenfolge(fremdAbrechnungId, 1)).isFalse();
        assertThat(positionRepository.findAll()).hasSize(1);
    }

    @Test
    void shouldDeleteOnlyPositionenOfOwnOrgWhenOrgFilterEnabled() {
        // deleteByAbrechnungId erhaelt die abrechnungId aus dem Request. Faellt der Filter aus,
        // loescht ein untergeschobener Fremdschluessel die Positionen eines anderen Mandanten.
        positionRepository.saveAndFlush(umlage(abrechnungId, TEST_ORG_ID, 1, "Allgemeinstrom"));
        positionRepository.saveAndFlush(umlage(fremdAbrechnungId, FREMD_ORG_ID, 1, "Allgemeinstrom"));
        entityManager.clear();

        aktiviereOrgFilter(TEST_ORG_ID);
        positionRepository.deleteByAbrechnungId(fremdAbrechnungId);
        entityManager.flush();
        entityManager.clear();

        aktiviereOrgFilter(FREMD_ORG_ID);
        assertThat(positionRepository.findByAbrechnungIdOrderByReihenfolge(fremdAbrechnungId))
                .hasSize(1);
    }

    // ==================== Angleich an das Flyway-Schema ====================

    /**
     * Legt die Zusicherungen von {@code zev.nk_position} an, die nur in den Migrationen stehen:
     * V118 (Fremdschluessel, Wertebereiche, eindeutige Reihenfolge), V121 (Einheit inkl. CHF) und
     * V125 (Positionsart inkl. ANTEIL).
     */
    private void angleichAnFlywaySchema() {
        fuehreAus("ALTER TABLE zev.nk_position DROP CONSTRAINT IF EXISTS fk_nk_position_abrechnung");
        fuehreAus("ALTER TABLE zev.nk_position ADD CONSTRAINT fk_nk_position_abrechnung"
                + " FOREIGN KEY (abrechnung_id) REFERENCES zev.nk_abrechnung(id) ON DELETE CASCADE");

        fuehreAus("ALTER TABLE zev.nk_position DROP CONSTRAINT IF EXISTS ck_nk_position_art");
        fuehreAus("ALTER TABLE zev.nk_position ADD CONSTRAINT ck_nk_position_art"
                + " CHECK (art IN ('UMLAGE', 'VERBRAUCH', 'ZUSCHLAG', 'ANTEIL'))");

        fuehreAus("ALTER TABLE zev.nk_position DROP CONSTRAINT IF EXISTS ck_nk_position_einheit");
        fuehreAus("ALTER TABLE zev.nk_position ADD CONSTRAINT ck_nk_position_einheit"
                + " CHECK (einheit IS NULL OR einheit IN ('KWH', 'MONAT', 'STUECK', 'M3', 'CHF'))");

        fuehreAus("ALTER TABLE zev.nk_position DROP CONSTRAINT IF EXISTS ck_nk_position_prozent");
        fuehreAus("ALTER TABLE zev.nk_position ADD CONSTRAINT ck_nk_position_prozent"
                + " CHECK (prozentsatz IS NULL OR (prozentsatz >= 0 AND prozentsatz <= 100))");

        fuehreAus("ALTER TABLE zev.nk_position DROP CONSTRAINT IF EXISTS ck_nk_position_mengen");
        fuehreAus("ALTER TABLE zev.nk_position ADD CONSTRAINT ck_nk_position_mengen CHECK ("
                + " (totalbetrag IS NULL OR totalbetrag >= 0)"
                + " AND (gesamtmenge IS NULL OR gesamtmenge >= 0)"
                + " AND (betrag_pro_einheit IS NULL OR betrag_pro_einheit >= 0))");

        fuehreAus("ALTER TABLE zev.nk_position DROP CONSTRAINT IF EXISTS ck_nk_position_felder");
        fuehreAus("ALTER TABLE zev.nk_position ADD CONSTRAINT ck_nk_position_felder CHECK ("
                + " (art = 'UMLAGE'"
                + "     AND totalbetrag IS NOT NULL AND einheit IS NOT NULL"
                + "     AND betrag_pro_einheit IS NULL AND prozentsatz IS NULL)"
                + " OR (art = 'VERBRAUCH'"
                + "     AND betrag_pro_einheit IS NOT NULL AND einheit IS NOT NULL"
                + "     AND totalbetrag IS NULL AND gesamtmenge IS NULL AND prozentsatz IS NULL)"
                + " OR (art = 'ZUSCHLAG'"
                + "     AND prozentsatz IS NOT NULL"
                + "     AND totalbetrag IS NULL AND gesamtmenge IS NULL"
                + "     AND betrag_pro_einheit IS NULL AND einheit IS NULL)"
                + " OR (art = 'ANTEIL'"
                + "     AND totalbetrag IS NOT NULL"
                + "     AND einheit IS NULL AND gesamtmenge IS NULL"
                + "     AND betrag_pro_einheit IS NULL AND prozentsatz IS NULL))");

        fuehreAus("ALTER TABLE zev.nk_position DROP CONSTRAINT IF EXISTS uq_nk_position_reihenfolge");
        fuehreAus("ALTER TABLE zev.nk_position ADD CONSTRAINT uq_nk_position_reihenfolge"
                + " UNIQUE (abrechnung_id, reihenfolge, org_id)");
    }

    // ==================== Testdaten ====================

    /**
     * Fuegt eine Zeile mit nativem SQL ein — die art-abhaengigen CHECKs pruefen Kombinationen,
     * welche die Bean Validation der Entity gar nicht erst durchliesse.
     */
    private void insertPosition(String art, String einheit, String totalbetrag, String gesamtmenge,
                                String betragProEinheit, String prozentsatz) {
        fuehreAus("INSERT INTO zev.nk_position (id, org_id, abrechnung_id, art, bezeichnung,"
                + " reihenfolge, einheit, totalbetrag, gesamtmenge, betrag_pro_einheit, prozentsatz)"
                + " VALUES (nextval('zev.nk_position_seq'), " + TEST_ORG_ID + ", " + abrechnungId
                + ", '" + art + "', 'Constraint-Test', " + naechsteReihenfolge++ + ", " + einheit
                + ", " + totalbetrag + ", " + gesamtmenge + ", " + betragProEinheit + ", "
                + prozentsatz + ")");
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

    private NkAbrechnung saveAbrechnung(Long orgId, String bezeichnung) {
        NkAbrechnung abrechnung = new NkAbrechnung();
        abrechnung.setOrgId(orgId);
        abrechnung.setBezeichnung(bezeichnung);
        abrechnung.setDatumVon(LocalDate.of(2025, 1, 1));
        abrechnung.setDatumBis(LocalDate.of(2025, 12, 31));
        abrechnung.setAnzahlWohnungen(9);
        return abrechnungRepository.saveAndFlush(abrechnung);
    }

    private NkPosition umlage(Long abrechnung, Long orgId, int reihenfolge, String bezeichnung) {
        NkPosition position = basisPosition(abrechnung, orgId, reihenfolge, bezeichnung,
                NkPositionsart.UMLAGE);
        position.setEinheit(Mengeneinheit.M3);
        position.setTotalbetrag(new BigDecimal("900.00"));
        return position;
    }

    private NkPosition verbrauch(Long abrechnung, Long orgId, int reihenfolge, String bezeichnung) {
        NkPosition position = basisPosition(abrechnung, orgId, reihenfolge, bezeichnung,
                NkPositionsart.VERBRAUCH);
        position.setEinheit(Mengeneinheit.KWH);
        position.setBetragProEinheit(new BigDecimal("3.5000"));
        return position;
    }

    private NkPosition basisPosition(Long abrechnung, Long orgId, int reihenfolge,
                                     String bezeichnung, NkPositionsart art) {
        NkPosition position = new NkPosition();
        position.setOrgId(orgId);
        position.setAbrechnungId(abrechnung);
        position.setArt(art);
        position.setBezeichnung(bezeichnung);
        position.setReihenfolge(reihenfolge);
        return position;
    }
}
