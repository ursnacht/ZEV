package ch.nacht.repository;

import ch.nacht.AbstractIntegrationTest;
import ch.nacht.entity.Einheit;
import ch.nacht.entity.EinheitTyp;
import ch.nacht.entity.Messwerte;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Integration tests for MesswerteRepository using Testcontainers.
 * Tests the statistics queries used by StatistikService.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class MesswerteRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private MesswerteRepository messwerteRepository;

    @Autowired
    private EinheitRepository einheitRepository;

    private static final UUID TEST_ORG_ID = UUID.fromString("c2c9ba74-de18-4491-9489-8185629edd93");

    private Einheit producer;
    private Einheit consumer1;
    private Einheit consumer2;

    @BeforeEach
    void setUp() {
        // Clean up
        messwerteRepository.deleteAll();
        einheitRepository.deleteAll();

        // Create test units with org_id
        producer = createEinheit("Solaranlage", EinheitTyp.PRODUCER);
        producer = einheitRepository.save(producer);
        consumer1 = createEinheit("Wohnung A", EinheitTyp.CONSUMER);
        consumer1 = einheitRepository.save(consumer1);
        consumer2 = createEinheit("Wohnung B", EinheitTyp.CONSUMER);
        consumer2 = einheitRepository.save(consumer2);
    }

    private Einheit createEinheit(String name, EinheitTyp typ) {
        Einheit einheit = new Einheit(name, typ);
        einheit.setOrgId(TEST_ORG_ID);
        return einheit;
    }

    @Test
    void shouldFindMaxZeit() {
        // Given
        LocalDateTime time1 = LocalDateTime.of(2024, 1, 15, 10, 0);
        LocalDateTime time2 = LocalDateTime.of(2024, 1, 20, 14, 30);
        LocalDateTime time3 = LocalDateTime.of(2024, 1, 10, 8, 0);

        messwerteRepository.save(createMesswerte(time1, producer, -100.0, -80.0, null));
        messwerteRepository.save(createMesswerte(time2, consumer1, 50.0, 40.0, 40.0));
        messwerteRepository.save(createMesswerte(time3, consumer2, 30.0, 25.0, 25.0));

        // When
        Optional<LocalDateTime> maxZeit = messwerteRepository.findMaxZeit();

        // Then
        assertThat(maxZeit).isPresent();
        assertThat(maxZeit.get()).isEqualTo(time2);
    }

    @Test
    void shouldFindDistinctDatesInRange() {
        // Given
        LocalDateTime day1Morning = LocalDateTime.of(2024, 1, 15, 8, 0);
        LocalDateTime day1Evening = LocalDateTime.of(2024, 1, 15, 18, 0);
        LocalDateTime day2 = LocalDateTime.of(2024, 1, 16, 12, 0);
        LocalDateTime day3 = LocalDateTime.of(2024, 1, 17, 10, 0);

        messwerteRepository.save(createMesswerte(day1Morning, producer, -100.0, -80.0, null));
        messwerteRepository.save(createMesswerte(day1Evening, producer, -50.0, -40.0, null));
        messwerteRepository.save(createMesswerte(day2, consumer1, 60.0, 50.0, 50.0));
        messwerteRepository.save(createMesswerte(day3, consumer1, 70.0, 55.0, 55.0));

        // When
        LocalDateTime from = LocalDateTime.of(2024, 1, 15, 0, 0);
        LocalDateTime to = LocalDateTime.of(2024, 1, 18, 0, 0);
        List<LocalDate> dates = messwerteRepository.findDistinctDatesInRange(from, to);

        // Then
        assertThat(dates).hasSize(3);
        assertThat(dates).containsExactly(
                LocalDate.of(2024, 1, 15),
                LocalDate.of(2024, 1, 16),
                LocalDate.of(2024, 1, 17)
        );
    }

    @Test
    void shouldSumTotalByEinheitTyp() {
        // Given
        LocalDateTime time1 = LocalDateTime.of(2024, 1, 15, 10, 0);
        LocalDateTime time2 = LocalDateTime.of(2024, 1, 15, 11, 0);

        // Producer values are negative
        messwerteRepository.save(createMesswerte(time1, producer, -100.0, -80.0, null));
        messwerteRepository.save(createMesswerte(time2, producer, -150.0, -120.0, null));

        // Consumer values are positive
        messwerteRepository.save(createMesswerte(time1, consumer1, 60.0, 50.0, 50.0));
        messwerteRepository.save(createMesswerte(time2, consumer1, 40.0, 30.0, 30.0));
        messwerteRepository.save(createMesswerte(time1, consumer2, 50.0, 40.0, 40.0));

        LocalDateTime from = LocalDateTime.of(2024, 1, 15, 0, 0);
        LocalDateTime to = LocalDateTime.of(2024, 1, 16, 0, 0);

        // When
        Double producerTotal = messwerteRepository.sumTotalByEinheitTypAndZeitBetween(EinheitTyp.PRODUCER, from, to);
        Double consumerTotal = messwerteRepository.sumTotalByEinheitTypAndZeitBetween(EinheitTyp.CONSUMER, from, to);

        // Then
        assertThat(producerTotal).isCloseTo(-250.0, within(0.001));
        assertThat(consumerTotal).isCloseTo(150.0, within(0.001));
    }

    @Test
    void shouldSumZevByEinheitTyp() {
        // Given
        LocalDateTime time1 = LocalDateTime.of(2024, 1, 15, 10, 0);
        LocalDateTime time2 = LocalDateTime.of(2024, 1, 15, 11, 0);

        messwerteRepository.save(createMesswerte(time1, producer, -100.0, -80.0, null));
        messwerteRepository.save(createMesswerte(time2, producer, -150.0, -120.0, null));
        messwerteRepository.save(createMesswerte(time1, consumer1, 60.0, 50.0, 50.0));
        messwerteRepository.save(createMesswerte(time2, consumer2, 70.0, 60.0, 60.0));

        LocalDateTime from = LocalDateTime.of(2024, 1, 15, 0, 0);
        LocalDateTime to = LocalDateTime.of(2024, 1, 16, 0, 0);

        // When
        Double producerZev = messwerteRepository.sumZevByEinheitTypAndZeitBetween(EinheitTyp.PRODUCER, from, to);
        Double consumerZev = messwerteRepository.sumZevByEinheitTypAndZeitBetween(EinheitTyp.CONSUMER, from, to);

        // Then
        assertThat(producerZev).isCloseTo(-200.0, within(0.001));
        assertThat(consumerZev).isCloseTo(110.0, within(0.001));
    }

    @Test
    void shouldSumZevCalculatedByEinheitTyp() {
        // Given
        LocalDateTime time1 = LocalDateTime.of(2024, 1, 15, 10, 0);
        LocalDateTime time2 = LocalDateTime.of(2024, 1, 15, 11, 0);

        // Producer has no zevCalculated
        messwerteRepository.save(createMesswerte(time1, producer, -100.0, -80.0, null));
        // Consumers have zevCalculated
        messwerteRepository.save(createMesswerte(time1, consumer1, 60.0, 50.0, 48.0));
        messwerteRepository.save(createMesswerte(time2, consumer1, 40.0, 35.0, 33.0));
        messwerteRepository.save(createMesswerte(time1, consumer2, 50.0, 40.0, 39.0));

        LocalDateTime from = LocalDateTime.of(2024, 1, 15, 0, 0);
        LocalDateTime to = LocalDateTime.of(2024, 1, 16, 0, 0);

        // When
        Double consumerZevCalc = messwerteRepository.sumZevCalculatedByEinheitTypAndZeitBetween(EinheitTyp.CONSUMER, from, to);

        // Then
        assertThat(consumerZevCalc).isCloseTo(120.0, within(0.001)); // 48 + 33 + 39
    }

    @Test
    void shouldFindDistinctEinheitenInRange() {
        // Given
        LocalDateTime time1 = LocalDateTime.of(2024, 1, 15, 10, 0);
        LocalDateTime time2 = LocalDateTime.of(2024, 1, 16, 10, 0);

        // Only producer and consumer1 have data in range
        messwerteRepository.save(createMesswerte(time1, producer, -100.0, -80.0, null));
        messwerteRepository.save(createMesswerte(time2, consumer1, 60.0, 50.0, 50.0));
        // consumer2 has no data

        LocalDateTime from = LocalDateTime.of(2024, 1, 15, 0, 0);
        LocalDateTime to = LocalDateTime.of(2024, 1, 17, 0, 0);

        // When
        List<Einheit> einheiten = messwerteRepository.findDistinctEinheitenInRange(from, to);

        // Then
        assertThat(einheiten).hasSize(2);
        assertThat(einheiten).extracting(Einheit::getName)
                .containsExactlyInAnyOrder("Solaranlage", "Wohnung A");
    }

    @Test
    void shouldSumTotalByEinheit() {
        // Given
        LocalDateTime time1 = LocalDateTime.of(2024, 1, 15, 10, 0);
        LocalDateTime time2 = LocalDateTime.of(2024, 1, 15, 11, 0);
        LocalDateTime time3 = LocalDateTime.of(2024, 1, 15, 12, 0);

        messwerteRepository.save(createMesswerte(time1, consumer1, 100.0, 80.0, 80.0));
        messwerteRepository.save(createMesswerte(time2, consumer1, 150.0, 120.0, 120.0));
        messwerteRepository.save(createMesswerte(time3, consumer1, 50.0, 40.0, 40.0));

        // Consumer2 data should not be included
        messwerteRepository.save(createMesswerte(time1, consumer2, 200.0, 160.0, 160.0));

        LocalDateTime from = LocalDateTime.of(2024, 1, 15, 0, 0);
        LocalDateTime to = LocalDateTime.of(2024, 1, 16, 0, 0);

        // When
        Double consumer1Total = messwerteRepository.sumTotalByEinheitAndZeitBetween(consumer1, from, to);
        Double consumer2Total = messwerteRepository.sumTotalByEinheitAndZeitBetween(consumer2, from, to);

        // Then
        assertThat(consumer1Total).isCloseTo(300.0, within(0.001)); // 100 + 150 + 50
        assertThat(consumer2Total).isCloseTo(200.0, within(0.001));
    }

    @Test
    void shouldSumZevByEinheit() {
        // Given
        LocalDateTime time1 = LocalDateTime.of(2024, 1, 15, 10, 0);
        LocalDateTime time2 = LocalDateTime.of(2024, 1, 15, 11, 0);

        messwerteRepository.save(createMesswerte(time1, producer, -100.0, -80.0, null));
        messwerteRepository.save(createMesswerte(time2, producer, -150.0, -120.0, null));

        LocalDateTime from = LocalDateTime.of(2024, 1, 15, 0, 0);
        LocalDateTime to = LocalDateTime.of(2024, 1, 16, 0, 0);

        // When
        Double producerZev = messwerteRepository.sumZevByEinheitAndZeitBetween(producer, from, to);

        // Then
        assertThat(producerZev).isCloseTo(-200.0, within(0.001)); // -80 + -120
    }

    @Test
    void shouldSumZevCalculatedByEinheit() {
        // Given
        LocalDateTime time1 = LocalDateTime.of(2024, 1, 15, 10, 0);
        LocalDateTime time2 = LocalDateTime.of(2024, 1, 15, 11, 0);

        messwerteRepository.save(createMesswerte(time1, consumer1, 100.0, 80.0, 75.0));
        messwerteRepository.save(createMesswerte(time2, consumer1, 150.0, 120.0, 118.0));

        LocalDateTime from = LocalDateTime.of(2024, 1, 15, 0, 0);
        LocalDateTime to = LocalDateTime.of(2024, 1, 16, 0, 0);

        // When
        Double consumer1ZevCalc = messwerteRepository.sumZevCalculatedByEinheitAndZeitBetween(consumer1, from, to);

        // Then
        assertThat(consumer1ZevCalc).isCloseTo(193.0, within(0.001)); // 75 + 118
    }

    @Test
    void shouldReturnZeroWhenNoDataInRange() {
        // Given - no data saved
        LocalDateTime from = LocalDateTime.of(2024, 1, 15, 0, 0);
        LocalDateTime to = LocalDateTime.of(2024, 1, 16, 0, 0);

        // When
        Double producerTotal = messwerteRepository.sumTotalByEinheitTypAndZeitBetween(EinheitTyp.PRODUCER, from, to);
        Double consumerZev = messwerteRepository.sumZevByEinheitTypAndZeitBetween(EinheitTyp.CONSUMER, from, to);
        Double einheitTotal = messwerteRepository.sumTotalByEinheitAndZeitBetween(consumer1, from, to);

        // Then - COALESCE should return 0
        assertThat(producerTotal).isEqualTo(0.0);
        assertThat(consumerZev).isEqualTo(0.0);
        assertThat(einheitTotal).isEqualTo(0.0);
    }

    @Test
    void shouldRespectDateRangeBoundaries() {
        // Given
        LocalDateTime beforeRange = LocalDateTime.of(2024, 1, 14, 23, 59);
        LocalDateTime inRange = LocalDateTime.of(2024, 1, 15, 12, 0);
        LocalDateTime afterRange = LocalDateTime.of(2024, 1, 16, 0, 1);

        messwerteRepository.save(createMesswerte(beforeRange, consumer1, 100.0, 80.0, 80.0));
        messwerteRepository.save(createMesswerte(inRange, consumer1, 200.0, 160.0, 160.0));
        messwerteRepository.save(createMesswerte(afterRange, consumer1, 300.0, 240.0, 240.0));

        LocalDateTime from = LocalDateTime.of(2024, 1, 15, 0, 0);
        LocalDateTime to = LocalDateTime.of(2024, 1, 16, 0, 0);

        // When
        Double total = messwerteRepository.sumTotalByEinheitAndZeitBetween(consumer1, from, to);

        // Then - only inRange value should be included
        assertThat(total).isCloseTo(200.0, within(0.001));
    }

    @Test
    void shouldFindByDate() {
        // Given
        LocalDateTime day1Morning = LocalDateTime.of(2024, 1, 15, 8, 0);
        LocalDateTime day1Evening = LocalDateTime.of(2024, 1, 15, 20, 0);
        LocalDateTime day2 = LocalDateTime.of(2024, 1, 16, 12, 0);

        messwerteRepository.save(createMesswerte(day1Morning, producer, -100.0, -80.0, null));
        messwerteRepository.save(createMesswerte(day1Evening, consumer1, 60.0, 50.0, 50.0));
        messwerteRepository.save(createMesswerte(day2, consumer2, 70.0, 55.0, 55.0));

        // When
        List<Messwerte> day1Data = messwerteRepository.findByDate(LocalDate.of(2024, 1, 15));
        List<Messwerte> day2Data = messwerteRepository.findByDate(LocalDate.of(2024, 1, 16));

        // Then
        assertThat(day1Data).hasSize(2);
        assertThat(day2Data).hasSize(1);
    }

    private Messwerte createMesswerte(LocalDateTime zeit, Einheit einheit, Double total, Double zev, Double zevCalculated) {
        Messwerte m = new Messwerte(zeit, total, zev, einheit);
        m.setOrgId(TEST_ORG_ID);
        m.setZevCalculated(zevCalculated);
        return m;
    }
}
