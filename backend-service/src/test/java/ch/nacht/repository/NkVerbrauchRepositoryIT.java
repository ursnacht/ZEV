package ch.nacht.repository;

import ch.nacht.AbstractIntegrationTest;
import ch.nacht.entity.Mengeneinheit;
import ch.nacht.entity.Mieter;
import ch.nacht.entity.NkAbrechnung;
import ch.nacht.entity.NkPosition;
import ch.nacht.entity.NkPositionsart;
import ch.nacht.entity.NkVerbrauch;
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
 * Integrationstests der Tabellenzusicherungen von {@code zev.nk_verbrauch}
 * ({@code Specs/Nebenkosten/Abrechnung.md}, FR-2 und FR-5).
 *
 * <p>{@link NkAbrechnungRepositoryIT} deckt die Ableitungsmethoden und die Unterabfrage von
 * {@link NkVerbrauchRepository#findByAbrechnungId} ab. Hier geht es um das, was nur die Datenbank
 * durchsetzt: die eine Zeile je Position und Mieter, die nicht-negative Menge, das kaskadierende
 * Loeschen ueber zwei Stufen, den Loeschschutz des Mieters und den Mandantenfilter.
 *
 * <p>Zum Nachlegen der Zusicherungen und zur Pflege siehe
 * {@link NkPositionRepositoryIT} — dieselbe Begruendung und dieselbe Fallstrick-Warnung gelten hier.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class NkVerbrauchRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private NkVerbrauchRepository verbrauchRepository;

    @Autowired
    private NkPositionRepository positionRepository;

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
    private Long positionId;
    private Long fremdPositionId;
    private Long mieterAId;
    private Long mieterBId;

    @BeforeEach
    void setUp() {
        verbrauchRepository.deleteAllInBatch();
        positionRepository.deleteAllInBatch();
        abrechnungRepository.deleteAllInBatch();
        mieterRepository.deleteAllInBatch();
        entityManager.flush();

        angleichAnFlywaySchema();

        TEST_ORG_ID = saveOrganisation("Nebenkosten Verbrauch Test",
                UUID.fromString("4c7d0e3f-6a91-4c24-9d53-8e0f2a4b6c93"));
        FREMD_ORG_ID = saveOrganisation("Nebenkosten Verbrauch Fremd",
                UUID.fromString("5d8e1f4a-7b02-4d35-8e64-9f1a3b5c7d04"));

        mieterAId = saveMieter(TEST_ORG_ID, "Mieter A").getId();
        mieterBId = saveMieter(TEST_ORG_ID, "Mieter B").getId();

        abrechnungId = saveAbrechnung(TEST_ORG_ID, "NK 2025").getId();
        fremdAbrechnungId = saveAbrechnung(FREMD_ORG_ID, "NK 2025 fremd").getId();
        positionId = savePosition(abrechnungId, TEST_ORG_ID, 1, "Warmwasser").getId();
        fremdPositionId = savePosition(fremdAbrechnungId, FREMD_ORG_ID, 1, "Warmwasser").getId();
    }

    // ==================== uq_nk_verbrauch ====================

    @Test
    void shouldRejectSecondMengeForSamePositionAndMieter() {
        // Zwei Mengen desselben Mieters zur selben Position waeren nicht summierbar, sondern
        // widerspruechlich — die Anzeige zeigt genau ein Eingabefeld.
        verbrauchRepository.saveAndFlush(menge(positionId, mieterAId, TEST_ORG_ID, "12.500"));

        assertVerletzt("uq_nk_verbrauch", () -> verbrauchRepository
                .saveAndFlush(menge(positionId, mieterAId, TEST_ORG_ID, "8.000")));
    }

    @Test
    void shouldAllowSameMieterOnAnotherPosition() {
        Long heizung = savePosition(abrechnungId, TEST_ORG_ID, 2, "Heizung").getId();
        verbrauchRepository.saveAndFlush(menge(positionId, mieterAId, TEST_ORG_ID, "12.500"));
        verbrauchRepository.saveAndFlush(menge(heizung, mieterAId, TEST_ORG_ID, "300.000"));

        assertThat(verbrauchRepository.findByAbrechnungId(abrechnungId)).hasSize(2);
        assertThat(verbrauchRepository.countByMieterId(mieterAId)).isEqualTo(2);
    }

    // ==================== ck_nk_verbrauch_menge ====================

    @Test
    void shouldRejectNegativeMengeInDatabase() {
        assertVerletzt("ck_nk_verbrauch_menge",
                () -> insertMenge(positionId, mieterAId, "-0.001"));
    }

    @Test
    void shouldRejectNegativeMengeByBeanValidation() {
        // Vor der Datenbank greift die Bean Validation der Entity — sie liefert die Meldung, die
        // der Anwender zu sehen bekommt.
        assertThatThrownBy(() -> verbrauchRepository
                .saveAndFlush(menge(positionId, mieterAId, TEST_ORG_ID, "-0.001")))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("Menge must not be negative");
    }

    @Test
    void shouldAcceptZeroMenge() {
        // Eine erfasste 0 ist eine bewusste Aussage und muss von "nicht erfasst" unterscheidbar
        // bleiben; beide ergeben denselben Betrag, aber nicht dieselbe Anzeige.
        verbrauchRepository.saveAndFlush(menge(positionId, mieterAId, TEST_ORG_ID, "0.000"));
        entityManager.clear();

        assertThat(verbrauchRepository.findByPositionId(positionId)).hasSize(1);
        assertThat(verbrauchRepository.findByPositionId(positionId).getFirst().getMenge())
                .isEqualByComparingTo("0");
    }

    @Test
    void shouldRoundMengeToThreeDecimals() {
        // NUMERIC(12,3): Der vierte Dezimal wird gerundet, nicht abgewiesen. Bei einer
        // ANTEIL-Position steht hier ein Prozentsatz — die Kontrollsumme rechnet mit dem
        // gerundeten Wert weiter.
        verbrauchRepository.saveAndFlush(menge(positionId, mieterAId, TEST_ORG_ID, "12.5678"));
        entityManager.clear();

        assertThat(verbrauchRepository.findByPositionId(positionId).getFirst().getMenge())
                .isEqualByComparingTo("12.568");
    }

    // ==================== fk_nk_verbrauch_position (ON DELETE CASCADE) ====================

    @Test
    void shouldCascadeDeleteVerbraeucheWhenPositionDeleted() {
        verbrauchRepository.saveAndFlush(menge(positionId, mieterAId, TEST_ORG_ID, "12.500"));

        fuehreAus("DELETE FROM zev.nk_position WHERE id = " + positionId);
        entityManager.clear();

        assertThat(verbrauchRepository.findByPositionId(positionId)).isEmpty();
    }

    @Test
    void shouldCascadeDeleteVerbraeucheOverTwoLevelsWhenAbrechnungDeleted() {
        // nk_abrechnung -> nk_position -> nk_verbrauch: Erst diese Kette macht das Loeschen einer
        // Abrechnung ohne Aufraeumcode im Service moeglich.
        verbrauchRepository.saveAndFlush(menge(positionId, mieterAId, TEST_ORG_ID, "12.500"));

        fuehreAus("DELETE FROM zev.nk_abrechnung WHERE id = " + abrechnungId);
        entityManager.clear();

        assertThat(verbrauchRepository.findByAbrechnungId(abrechnungId)).isEmpty();
        assertThat(verbrauchRepository.count()).isZero();
    }

    @Test
    void shouldRejectVerbrauchForUnknownPosition() {
        assertVerletzt("fk_nk_verbrauch_position", () -> verbrauchRepository
                .saveAndFlush(menge(999999L, mieterAId, TEST_ORG_ID, "12.500")));
    }

    // ==================== fk_nk_verbrauch_mieter (ON DELETE RESTRICT) ====================

    @Test
    void shouldRejectDeletingMieterReferencedByVerbrauch() {
        // Loeschschutz: Die Betraege einer Abrechnung werden nicht gespeichert, sondern jederzeit
        // neu gerechnet. Ohne den Mieter waere eine abgeschlossene Abrechnung nicht mehr
        // reproduzierbar.
        verbrauchRepository.saveAndFlush(menge(positionId, mieterAId, TEST_ORG_ID, "12.500"));

        assertVerletzt("fk_nk_verbrauch_mieter",
                () -> fuehreAus("DELETE FROM zev.mieter WHERE id = " + mieterAId));
    }

    @Test
    void shouldAllowDeletingMieterWithoutVerbrauch() {
        verbrauchRepository.saveAndFlush(menge(positionId, mieterAId, TEST_ORG_ID, "12.500"));

        fuehreAus("DELETE FROM zev.mieter WHERE id = " + mieterBId);
        // Der Mieter liegt aus dem Aufbau noch im Persistence Context; ohne clear() antwortete
        // findById aus dem Cache und nicht aus der Datenbank.
        entityManager.clear();

        assertThat(mieterRepository.findById(mieterBId)).isEmpty();
    }

    @Test
    void shouldRejectVerbrauchForUnknownMieter() {
        assertVerletzt("fk_nk_verbrauch_mieter", () -> verbrauchRepository
                .saveAndFlush(menge(positionId, 999999L, TEST_ORG_ID, "12.500")));
    }

    // ==================== Mandantenfilter ====================

    @Test
    void shouldNotSeeVerbraeucheOfOtherOrgWhenOrgFilterEnabled() {
        Long fremdMieterId = saveMieter(FREMD_ORG_ID, "Fremder Mieter").getId();
        verbrauchRepository.saveAndFlush(menge(positionId, mieterAId, TEST_ORG_ID, "12.500"));
        verbrauchRepository.saveAndFlush(
                menge(fremdPositionId, fremdMieterId, FREMD_ORG_ID, "99.000"));
        entityManager.clear();

        aktiviereOrgFilter(TEST_ORG_ID);

        assertThat(verbrauchRepository.findByAbrechnungId(abrechnungId)).hasSize(1);
        assertThat(verbrauchRepository.findByAbrechnungId(fremdAbrechnungId)).isEmpty();
        assertThat(verbrauchRepository.findByPositionId(fremdPositionId)).isEmpty();
        // countByMieterId traegt den Loeschschutz: Zaehlte es mandantenuebergreifend, liesse sich
        // aus dem Zaehlerstand auf Daten eines anderen Mandanten schliessen.
        assertThat(verbrauchRepository.countByMieterId(fremdMieterId)).isZero();
        assertThat(verbrauchRepository.findAll()).hasSize(1);
    }

    @Test
    void shouldDeleteOnlyVerbraeucheOfOwnOrgWhenOrgFilterEnabled() {
        Long fremdMieterId = saveMieter(FREMD_ORG_ID, "Fremder Mieter").getId();
        verbrauchRepository.saveAndFlush(
                menge(fremdPositionId, fremdMieterId, FREMD_ORG_ID, "99.000"));
        entityManager.clear();

        aktiviereOrgFilter(TEST_ORG_ID);
        verbrauchRepository.deleteByPositionId(fremdPositionId);
        entityManager.flush();
        entityManager.clear();

        aktiviereOrgFilter(FREMD_ORG_ID);
        assertThat(verbrauchRepository.findByPositionId(fremdPositionId)).hasSize(1);
    }

    // ==================== Angleich an das Flyway-Schema ====================

    /**
     * Legt die Zusicherungen aus V118 an, die nur in der Migration stehen: die beiden
     * Fremdschluessel mit ihren unterschiedlichen Loeschregeln, den Wertebereich der Menge und die
     * Eindeutigkeit je Position und Mieter. Der Fremdschluessel von {@code nk_position} auf
     * {@code nk_abrechnung} kommt dazu, weil die Kaskade zwei Stufen tief laeuft.
     */
    private void angleichAnFlywaySchema() {
        fuehreAus("ALTER TABLE zev.nk_position DROP CONSTRAINT IF EXISTS fk_nk_position_abrechnung");
        fuehreAus("ALTER TABLE zev.nk_position ADD CONSTRAINT fk_nk_position_abrechnung"
                + " FOREIGN KEY (abrechnung_id) REFERENCES zev.nk_abrechnung(id) ON DELETE CASCADE");

        fuehreAus("ALTER TABLE zev.nk_verbrauch DROP CONSTRAINT IF EXISTS fk_nk_verbrauch_position");
        fuehreAus("ALTER TABLE zev.nk_verbrauch ADD CONSTRAINT fk_nk_verbrauch_position"
                + " FOREIGN KEY (position_id) REFERENCES zev.nk_position(id) ON DELETE CASCADE");

        fuehreAus("ALTER TABLE zev.nk_verbrauch DROP CONSTRAINT IF EXISTS fk_nk_verbrauch_mieter");
        fuehreAus("ALTER TABLE zev.nk_verbrauch ADD CONSTRAINT fk_nk_verbrauch_mieter"
                + " FOREIGN KEY (mieter_id) REFERENCES zev.mieter(id) ON DELETE RESTRICT");

        fuehreAus("ALTER TABLE zev.nk_verbrauch DROP CONSTRAINT IF EXISTS ck_nk_verbrauch_menge");
        fuehreAus("ALTER TABLE zev.nk_verbrauch ADD CONSTRAINT ck_nk_verbrauch_menge"
                + " CHECK (menge >= 0)");

        fuehreAus("ALTER TABLE zev.nk_verbrauch DROP CONSTRAINT IF EXISTS uq_nk_verbrauch");
        fuehreAus("ALTER TABLE zev.nk_verbrauch ADD CONSTRAINT uq_nk_verbrauch"
                + " UNIQUE (position_id, mieter_id, org_id)");
    }

    // ==================== Testdaten ====================

    /** Fuegt eine Zeile mit nativem SQL ein — an der Bean Validation der Entity vorbei. */
    private void insertMenge(Long position, Long mieter, String menge) {
        fuehreAus("INSERT INTO zev.nk_verbrauch (id, org_id, position_id, mieter_id, menge)"
                + " VALUES (nextval('zev.nk_verbrauch_seq'), " + TEST_ORG_ID + ", " + position
                + ", " + mieter + ", " + menge + ")");
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

    private NkPosition savePosition(Long abrechnung, Long orgId, int reihenfolge, String bezeichnung) {
        NkPosition position = new NkPosition();
        position.setOrgId(orgId);
        position.setAbrechnungId(abrechnung);
        position.setArt(NkPositionsart.VERBRAUCH);
        position.setBezeichnung(bezeichnung);
        position.setReihenfolge(reihenfolge);
        position.setEinheit(Mengeneinheit.KWH);
        position.setBetragProEinheit(new BigDecimal("3.5000"));
        return positionRepository.saveAndFlush(position);
    }

    private NkVerbrauch menge(Long position, Long mieter, Long orgId, String menge) {
        NkVerbrauch verbrauch = new NkVerbrauch(position, mieter, new BigDecimal(menge));
        verbrauch.setOrgId(orgId);
        return verbrauch;
    }
}
