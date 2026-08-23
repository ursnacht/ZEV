package ch.nacht.repository;

import ch.nacht.AbstractIntegrationTest;
import ch.nacht.entity.Mengeneinheit;
import ch.nacht.entity.NkAbrechnung;
import ch.nacht.entity.NkAkonto;
import ch.nacht.entity.NkPosition;
import ch.nacht.entity.NkPositionsart;
import ch.nacht.entity.NkVerbrauch;
import ch.nacht.entity.NkZusatz;
import ch.nacht.entity.Organisation;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integrationstests der Repositories der Nebenkostenabrechnung
 * ({@code Specs/Nebenkosten/Abrechnung.md}, FR-5).
 *
 * <p>Die fuenf Tabellen sind bewusst in <b>einer</b> Testklasse: Sie teilen sich dieselbe
 * Abrechnung als Fixture, und die interessanten Abfragen — allen voran
 * {@link NkVerbrauchRepository#findByAbrechnungId} mit ihrer Unterabfrage ueber
 * {@code nk_position} — spannen ohnehin ueber mehrere von ihnen.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class NkAbrechnungRepositoryIT extends AbstractIntegrationTest {

    private static final Long MIETER_A = 501L;
    private static final Long MIETER_B = 502L;

    @Autowired
    private NkAbrechnungRepository abrechnungRepository;

    @Autowired
    private NkPositionRepository positionRepository;

    @Autowired
    private NkVerbrauchRepository verbrauchRepository;

    @Autowired
    private NkZusatzRepository zusatzRepository;

    @Autowired
    private NkAkontoRepository akontoRepository;

    @Autowired
    private OrganisationRepository organisationRepository;

    private Long TEST_ORG_ID;

    @BeforeEach
    void setUp() {
        verbrauchRepository.deleteAll();
        zusatzRepository.deleteAll();
        akontoRepository.deleteAll();
        positionRepository.deleteAll();
        abrechnungRepository.deleteAll();

        Organisation org = new Organisation();
        org.setKeycloakOrgId(UUID.fromString("8f1c7a2e-6b4d-4a19-9c5f-2d7e0b3a1c48"));
        org.setName("Nebenkosten Test Organisation");
        org.setErstelltAm(LocalDateTime.now());
        TEST_ORG_ID = organisationRepository.save(org).getId();
    }

    // ==================== NkAbrechnung ====================

    @Test
    void shouldSaveAndFindAbrechnung() {
        NkAbrechnung saved = abrechnungRepository.save(
                createAbrechnung("Nebenkostenabrechnung 2025", LocalDate.of(2025, 1, 1)));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getOrgId()).isEqualTo(TEST_ORG_ID);
        assertThat(saved.isAbgerechnet()).isFalse();
        assertThat(saved.getErstelltAm()).isNotNull();

        Optional<NkAbrechnung> found = abrechnungRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getAnzahlWohnungen()).isEqualTo(9);
    }

    @Test
    void shouldFindAllByOrderByDatumVonDesc() {
        // Akzeptanzkriterium: neuste zuoberst.
        abrechnungRepository.save(createAbrechnung("NK 2023", LocalDate.of(2023, 1, 1)));
        abrechnungRepository.save(createAbrechnung("NK 2025", LocalDate.of(2025, 1, 1)));
        abrechnungRepository.save(createAbrechnung("NK 2024", LocalDate.of(2024, 1, 1)));

        assertThat(abrechnungRepository.findAllByOrderByDatumVonDesc())
                .extracting(NkAbrechnung::getBezeichnung)
                .containsExactly("NK 2025", "NK 2024", "NK 2023");
    }

    @Test
    void shouldSetAbgerechnetFlag() {
        NkAbrechnung abrechnung = abrechnungRepository.save(
                createAbrechnung("NK 2025", LocalDate.of(2025, 1, 1)));

        abrechnung.setAbgerechnet(true);
        abrechnungRepository.saveAndFlush(abrechnung);

        assertThat(abrechnungRepository.findById(abrechnung.getId()).orElseThrow().isAbgerechnet())
                .isTrue();
    }

    // ==================== NkPosition ====================

    @Test
    void shouldFindPositionenInCalculationOrder() {
        // Die Sortierung ist nicht kosmetisch: Ein Zuschlag rechnet auf alle Zeilen davor.
        Long abrechnungId = abrechnung().getId();
        positionRepository.save(zuschlag(abrechnungId, 3, "Verwaltung"));
        positionRepository.save(umlage(abrechnungId, 1, "Allgemeinstrom"));
        positionRepository.save(verbrauch(abrechnungId, 2, "Warmwasser"));

        assertThat(positionRepository.findByAbrechnungIdOrderByReihenfolge(abrechnungId))
                .extracting(NkPosition::getBezeichnung)
                .containsExactly("Allgemeinstrom", "Warmwasser", "Verwaltung");
    }

    @Test
    void shouldStoreArtSpecificFieldsPerPositionsart() {
        Long abrechnungId = abrechnung().getId();
        positionRepository.save(umlage(abrechnungId, 1, "Allgemeinstrom"));
        positionRepository.save(verbrauch(abrechnungId, 2, "Warmwasser"));
        positionRepository.saveAndFlush(zuschlag(abrechnungId, 3, "Verwaltung"));

        List<NkPosition> positionen = positionRepository.findByAbrechnungIdOrderByReihenfolge(abrechnungId);

        assertThat(positionen.get(0).getArt()).isEqualTo(NkPositionsart.UMLAGE);
        assertThat(positionen.get(0).getTotalbetrag()).isEqualByComparingTo("900.00");
        assertThat(positionen.get(0).getGesamtmenge()).isEqualByComparingTo("500.000");
        assertThat(positionen.get(0).getEinheit()).isEqualTo(Mengeneinheit.M3);

        assertThat(positionen.get(1).getBetragProEinheit()).isEqualByComparingTo("3.5000");
        assertThat(positionen.get(1).getTotalbetrag()).isNull();

        assertThat(positionen.get(2).getProzentsatz()).isEqualByComparingTo("5.00");
        assertThat(positionen.get(2).getEinheit()).isNull();
    }

    @Test
    void shouldDetectExistingReihenfolge() {
        Long abrechnungId = abrechnung().getId();
        positionRepository.saveAndFlush(umlage(abrechnungId, 1, "Allgemeinstrom"));

        assertThat(positionRepository.existsByAbrechnungIdAndReihenfolge(abrechnungId, 1)).isTrue();
        assertThat(positionRepository.existsByAbrechnungIdAndReihenfolge(abrechnungId, 2)).isFalse();
    }

    @Test
    void shouldDeletePositionenOfOneAbrechnungOnly() {
        Long ersteId = abrechnung().getId();
        Long zweiteId = abrechnungRepository.save(
                createAbrechnung("NK 2024", LocalDate.of(2024, 1, 1))).getId();
        positionRepository.save(umlage(ersteId, 1, "Allgemeinstrom"));
        positionRepository.saveAndFlush(umlage(zweiteId, 1, "Allgemeinstrom"));

        positionRepository.deleteByAbrechnungId(ersteId);
        positionRepository.flush();

        assertThat(positionRepository.findByAbrechnungIdOrderByReihenfolge(ersteId)).isEmpty();
        assertThat(positionRepository.findByAbrechnungIdOrderByReihenfolge(zweiteId)).hasSize(1);
    }

    // ==================== NkVerbrauch ====================

    @Test
    void shouldFindAlleVerbraeucheOfAbrechnungInOneQuery() {
        // NFR-1: Die Mengen werden in einem Zug geladen, nicht je Position einzeln.
        Long abrechnungId = abrechnung().getId();
        NkPosition warmwasser = positionRepository.save(verbrauch(abrechnungId, 1, "Warmwasser"));
        NkPosition heizung = positionRepository.save(verbrauch(abrechnungId, 2, "Heizung"));

        verbrauchRepository.save(menge(warmwasser.getId(), MIETER_A, "12.500"));
        verbrauchRepository.save(menge(warmwasser.getId(), MIETER_B, "8.000"));
        verbrauchRepository.saveAndFlush(menge(heizung.getId(), MIETER_A, "300.000"));

        assertThat(verbrauchRepository.findByAbrechnungId(abrechnungId)).hasSize(3);
        assertThat(verbrauchRepository.findByPositionId(warmwasser.getId())).hasSize(2);
    }

    @Test
    void shouldNotFindVerbraeucheOfOtherAbrechnung() {
        Long ersteId = abrechnung().getId();
        Long zweiteId = abrechnungRepository.save(
                createAbrechnung("NK 2024", LocalDate.of(2024, 1, 1))).getId();
        NkPosition fremd = positionRepository.save(verbrauch(zweiteId, 1, "Warmwasser"));
        verbrauchRepository.saveAndFlush(menge(fremd.getId(), MIETER_A, "12.500"));

        assertThat(verbrauchRepository.findByAbrechnungId(ersteId)).isEmpty();
        assertThat(verbrauchRepository.findByAbrechnungId(zweiteId)).hasSize(1);
    }

    @Test
    void shouldDeleteVerbraeucheOfOnePositionOnly() {
        Long abrechnungId = abrechnung().getId();
        NkPosition warmwasser = positionRepository.save(verbrauch(abrechnungId, 1, "Warmwasser"));
        NkPosition heizung = positionRepository.save(verbrauch(abrechnungId, 2, "Heizung"));
        verbrauchRepository.save(menge(warmwasser.getId(), MIETER_A, "12.500"));
        verbrauchRepository.saveAndFlush(menge(heizung.getId(), MIETER_A, "300.000"));

        verbrauchRepository.deleteByPositionId(warmwasser.getId());
        verbrauchRepository.flush();

        assertThat(verbrauchRepository.findByPositionId(warmwasser.getId())).isEmpty();
        assertThat(verbrauchRepository.findByPositionId(heizung.getId())).hasSize(1);
    }

    @Test
    void shouldCountVerbraeucheByMieter() {
        // Grundlage des Loeschschutzes in MieterService.deleteMieter (FR-5).
        Long abrechnungId = abrechnung().getId();
        NkPosition warmwasser = positionRepository.save(verbrauch(abrechnungId, 1, "Warmwasser"));
        verbrauchRepository.saveAndFlush(menge(warmwasser.getId(), MIETER_A, "12.500"));

        assertThat(verbrauchRepository.countByMieterId(MIETER_A)).isEqualTo(1);
        assertThat(verbrauchRepository.countByMieterId(MIETER_B)).isZero();
    }

    // ==================== NkZusatz ====================

    @Test
    void shouldFindZusaetzeGroupedByMieterAndReihenfolge() {
        Long abrechnungId = abrechnung().getId();
        zusatzRepository.save(zusatz(abrechnungId, MIETER_B, 1, "Schluessel"));
        zusatzRepository.save(zusatz(abrechnungId, MIETER_A, 2, "Sauna"));
        zusatzRepository.saveAndFlush(zusatz(abrechnungId, MIETER_A, 1, "Waschkarte"));

        assertThat(zusatzRepository.findByAbrechnungIdOrderByMieterIdAscReihenfolgeAsc(abrechnungId))
                .extracting(NkZusatz::getBezeichnung)
                .containsExactly("Waschkarte", "Sauna", "Schluessel");

        assertThat(zusatzRepository.findByAbrechnungIdAndMieterIdOrderByReihenfolge(abrechnungId, MIETER_A))
                .extracting(NkZusatz::getBezeichnung)
                .containsExactly("Waschkarte", "Sauna");
    }

    @Test
    void shouldCountZusaetzeByMieter() {
        Long abrechnungId = abrechnung().getId();
        zusatzRepository.saveAndFlush(zusatz(abrechnungId, MIETER_A, 1, "Sauna"));

        assertThat(zusatzRepository.countByMieterId(MIETER_A)).isEqualTo(1);
        assertThat(zusatzRepository.countByMieterId(MIETER_B)).isZero();
    }

    @Test
    void shouldDeleteZusaetzeOfAbrechnung() {
        Long abrechnungId = abrechnung().getId();
        zusatzRepository.saveAndFlush(zusatz(abrechnungId, MIETER_A, 1, "Sauna"));

        zusatzRepository.deleteByAbrechnungId(abrechnungId);
        zusatzRepository.flush();

        assertThat(zusatzRepository.findByAbrechnungIdOrderByMieterIdAscReihenfolgeAsc(abrechnungId))
                .isEmpty();
    }

    // ==================== NkAkonto ====================

    @Test
    void shouldFindAkontoOfAbrechnungAndMieter() {
        Long abrechnungId = abrechnung().getId();
        akontoRepository.save(akonto(abrechnungId, MIETER_A, "4.50", "150.00", "-50.00"));
        akontoRepository.saveAndFlush(akonto(abrechnungId, MIETER_B, "12.00", "200.00", "0.00"));

        assertThat(akontoRepository.findByAbrechnungId(abrechnungId)).hasSize(2);

        NkAkonto gefunden = akontoRepository
                .findByAbrechnungIdAndMieterId(abrechnungId, MIETER_A).orElseThrow();
        // Angebrochene Monate brauchen die zwei Nachkommastellen von NUMERIC(5,2).
        assertThat(gefunden.getAnzahlMonate()).isEqualByComparingTo("4.50");
        // Der Korrekturbetrag darf als einziges Feld negativ sein.
        assertThat(gefunden.getKorrektur()).isEqualByComparingTo("-50.00");
    }

    @Test
    void shouldReturnEmptyWhenAkontoNotRecorded() {
        Long abrechnungId = abrechnung().getId();

        assertThat(akontoRepository.findByAbrechnungIdAndMieterId(abrechnungId, MIETER_A)).isEmpty();
    }

    @Test
    void shouldCountAkontoByMieter() {
        Long abrechnungId = abrechnung().getId();
        akontoRepository.saveAndFlush(akonto(abrechnungId, MIETER_A, "12.00", "150.00", "0.00"));

        assertThat(akontoRepository.countByMieterId(MIETER_A)).isEqualTo(1);
        assertThat(akontoRepository.countByMieterId(MIETER_B)).isZero();
    }

    @Test
    void shouldDeleteAkontoOfAbrechnung() {
        Long abrechnungId = abrechnung().getId();
        akontoRepository.saveAndFlush(akonto(abrechnungId, MIETER_A, "12.00", "150.00", "0.00"));

        akontoRepository.deleteByAbrechnungId(abrechnungId);
        akontoRepository.flush();

        assertThat(akontoRepository.findByAbrechnungId(abrechnungId)).isEmpty();
    }

    // ==================== Testdaten ====================

    private NkAbrechnung abrechnung() {
        return abrechnungRepository.save(createAbrechnung("NK 2025", LocalDate.of(2025, 1, 1)));
    }

    private NkAbrechnung createAbrechnung(String bezeichnung, LocalDate von) {
        NkAbrechnung abrechnung = new NkAbrechnung();
        abrechnung.setOrgId(TEST_ORG_ID);
        abrechnung.setBezeichnung(bezeichnung);
        abrechnung.setDatumVon(von);
        abrechnung.setDatumBis(von.plusYears(1).minusDays(1));
        abrechnung.setAnzahlWohnungen(9);
        return abrechnung;
    }

    private NkPosition umlage(Long abrechnungId, int reihenfolge, String bezeichnung) {
        NkPosition position = basisPosition(abrechnungId, reihenfolge, bezeichnung, NkPositionsart.UMLAGE);
        position.setEinheit(Mengeneinheit.M3);
        position.setTotalbetrag(new BigDecimal("900.00"));
        position.setGesamtmenge(new BigDecimal("500.000"));
        return position;
    }

    private NkPosition verbrauch(Long abrechnungId, int reihenfolge, String bezeichnung) {
        NkPosition position = basisPosition(abrechnungId, reihenfolge, bezeichnung, NkPositionsart.VERBRAUCH);
        position.setEinheit(Mengeneinheit.KWH);
        position.setBetragProEinheit(new BigDecimal("3.5000"));
        return position;
    }

    private NkPosition zuschlag(Long abrechnungId, int reihenfolge, String bezeichnung) {
        NkPosition position = basisPosition(abrechnungId, reihenfolge, bezeichnung, NkPositionsart.ZUSCHLAG);
        position.setProzentsatz(new BigDecimal("5.00"));
        return position;
    }

    private NkPosition basisPosition(Long abrechnungId, int reihenfolge, String bezeichnung,
                                     NkPositionsart art) {
        NkPosition position = new NkPosition();
        position.setOrgId(TEST_ORG_ID);
        position.setAbrechnungId(abrechnungId);
        position.setArt(art);
        position.setBezeichnung(bezeichnung);
        position.setReihenfolge(reihenfolge);
        return position;
    }

    private NkVerbrauch menge(Long positionId, Long mieterId, String menge) {
        NkVerbrauch verbrauch = new NkVerbrauch(positionId, mieterId, new BigDecimal(menge));
        verbrauch.setOrgId(TEST_ORG_ID);
        return verbrauch;
    }

    private NkZusatz zusatz(Long abrechnungId, Long mieterId, int reihenfolge, String bezeichnung) {
        NkZusatz zusatz = new NkZusatz();
        zusatz.setOrgId(TEST_ORG_ID);
        zusatz.setAbrechnungId(abrechnungId);
        zusatz.setMieterId(mieterId);
        zusatz.setReihenfolge(reihenfolge);
        zusatz.setBezeichnung(bezeichnung);
        zusatz.setEinheit(Mengeneinheit.STUECK);
        zusatz.setMenge(new BigDecimal("4.000"));
        zusatz.setBetragProEinheit(new BigDecimal("6.5000"));
        return zusatz;
    }

    private NkAkonto akonto(Long abrechnungId, Long mieterId, String monate,
                            String proMonat, String korrektur) {
        NkAkonto akonto = new NkAkonto();
        akonto.setOrgId(TEST_ORG_ID);
        akonto.setAbrechnungId(abrechnungId);
        akonto.setMieterId(mieterId);
        akonto.setAnzahlMonate(new BigDecimal(monate));
        akonto.setBetragProMonat(new BigDecimal(proMonat));
        akonto.setKorrektur(new BigDecimal(korrektur));
        return akonto;
    }
}
