package ch.nacht.service;

import ch.nacht.AbstractIntegrationTest;
import ch.nacht.config.ZeitConfig;
import ch.nacht.entity.Einheit;
import ch.nacht.entity.EinheitTyp;
import ch.nacht.entity.MesspunktEmpfang;
import ch.nacht.entity.MesspunktEmpfangErgebnis;
import ch.nacht.entity.MesspunktEmpfangQuelle;
import ch.nacht.entity.Organisation;
import ch.nacht.entity.ZaehlerRohdaten;
import ch.nacht.repository.EinheitRepository;
import ch.nacht.repository.GeraetezustandRepository;
import ch.nacht.repository.OrganisationRepository;
import ch.nacht.repository.ZaehlerRohdatenRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integrationstest für die <b>Transaktionsgrenze</b> des Empfangsvermerks
 * (Specs/Messpunkt-Empfang.md, FR-1.3 und FR-1.6), hier: <b>der Commit der Ingest-Transaktion
 * scheitert</b>.
 *
 * <p><b>Warum ein Integrationstest.</b> Ob {@code GESPEICHERT} den tatsächlichen Commit spiegelt,
 * entscheidet sich erst in {@code TransactionSynchronization.afterCompletion} — ein Mock hat keinen
 * Transaktions-Proxy und keinen Commit, der scheitern könnte. Und nur gegen echtes PostgreSQL zeigt
 * sich, dass das Festhalten <b>nach</b> einem gescheiterten Commit in eigener Transaktion
 * ({@code REQUIRES_NEW}) tatsächlich committet.
 *
 * <p><b>Wie der Commit scheitert.</b> {@code zaehler_rohdaten.zaehlerstand_bezug} ist
 * {@code DECIMAL(14,4)}. Ein Stand mit elf Vorkommastellen besteht alle Prüfungen des Ingests; der
 * Insert wird erst beim Flush im Commit ausgeführt und scheitert dort mit „numeric field overflow“.
 * {@code messpunkt_empfang} ist bewusst unbeschränkt (Datenmodell) und nimmt den Stand auf.
 *
 * <p><b>Aufbau</b> nach {@code SteuerungServiceTransaktionIT}: {@code @DataJpaTest} mit
 * {@code @Import}, Testmethoden ohne eigene Transaktion ({@code NOT_SUPPORTED}), Aufräumen von Hand.
 * {@link MqttIngestService} und {@link MqttMetrics} tragen {@code @Profile("mqtt")} und kommen
 * deshalb als {@code @Bean} aus der Testkonfiguration — ein {@code @Import} der Klasse würde am
 * Profil scheitern, und das MQTT-Profil zöge die Broker-Konfiguration mit.
 *
 * <p><b>Gegenprobe</b> (25.10.2026): Ohne die Zeile {@code vermerk.verwirf(GRUND_SPEICHERN)} in
 * {@code halteEmpfangFest} scheitert
 * {@link #handle_CommitScheitert_ZeileZeigtSpeichernFehlgeschlagen} — die Zeile zeigt dann
 * {@code GESPEICHERT}, obwohl keine Rohdaten in der Tabelle stehen.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({MesspunktEmpfangService.class, HibernateFilterService.class, ZeitConfig.class,
        MqttIngestServiceTransaktionIT.Konfiguration.class})
class MqttIngestServiceTransaktionIT extends AbstractIntegrationTest {

    private static final String MESSPUNKT = "MP-TX-001";

    @Autowired
    private MqttIngestService mqttIngestService;

    @Autowired
    private OrganisationRepository organisationRepository;

    @Autowired
    private EinheitRepository einheitRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    @MockitoBean
    private SystemmeldungService systemmeldungService;

    @MockitoBean
    private OrganizationContextService organizationContextService;

    private Long orgId;

    @TestConfiguration
    static class Konfiguration {

        @Bean
        MqttMetrics mqttMetrics() {
            return new MqttMetrics(new SimpleMeterRegistry());
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().registerModule(new JavaTimeModule());
        }

        @Bean
        MqttIngestService mqttIngestService(EinheitRepository einheitRepository,
                                            ZaehlerRohdatenRepository rohdatenRepository,
                                            GeraetezustandRepository geraetezustandRepository,
                                            SystemmeldungService systemmeldungService,
                                            ObjectMapper objectMapper,
                                            MqttMetrics mqttMetrics,
                                            MesspunktEmpfangService messpunktEmpfangService) {
            return new MqttIngestService(einheitRepository, rohdatenRepository, geraetezustandRepository,
                    systemmeldungService, objectMapper, mqttMetrics, messpunktEmpfangService);
        }
    }

    @BeforeEach
    void setUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Organisation org = new Organisation();
            org.setKeycloakOrgId(UUID.randomUUID());
            org.setName("Transaktionstest Empfang");
            org.setErstelltAm(LocalDateTime.now());
            orgId = organisationRepository.save(org).getId();

            Einheit einheit = new Einheit("Wohnung TX", EinheitTyp.CONSUMER);
            einheit.setOrgId(orgId);
            einheit.setMesspunkt(MESSPUNKT);
            einheitRepository.save(einheit);
        });
    }

    /** Ohne Testtransaktion bleibt alles stehen - also von Hand und nur die eigenen Zeilen. */
    @AfterEach
    void tearDown() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            entityManager.createNativeQuery("DELETE FROM zev.messpunkt_empfang WHERE org_id = :org")
                    .setParameter("org", orgId).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM zev.zaehler_rohdaten WHERE org_id = :org")
                    .setParameter("org", orgId).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM zev.einheit WHERE org_id = :org")
                    .setParameter("org", orgId).executeUpdate();
            organisationRepository.deleteById(orgId);
        });
    }

    private String topic() {
        return "zev/" + orgId + "/" + MESSPUNKT + "/messwert";
    }

    private static String payload(String bezug) {
        return "{\"timestamp\":\"2026-10-25T10:00:00+01:00\",\"zaehlerstandBezug\":" + bezug
                + ",\"zaehlerstandEinspeisung\":10.0}";
    }

    private List<MesspunktEmpfang> empfangszeilen() {
        return new TransactionTemplate(transactionManager).execute(status -> entityManager.createQuery(
                        "SELECT m FROM MesspunktEmpfang m WHERE m.orgId = :org", MesspunktEmpfang.class)
                .setParameter("org", orgId).getResultList());
    }

    private List<ZaehlerRohdaten> rohdaten() {
        return new TransactionTemplate(transactionManager).execute(status -> entityManager.createQuery(
                        "SELECT r FROM ZaehlerRohdaten r WHERE r.orgId = :org", ZaehlerRohdaten.class)
                .setParameter("org", orgId).getResultList());
    }

    /**
     * Kontrollfall: Gelingt der Commit, ist die Zeile nach dem Ingest <b>committet</b> und zeigt
     * {@code GESPEICHERT} — das Festhalten in {@code afterCompletion} läuft also wirklich in einer
     * eigenen, committenden Transaktion. Die Empfangszeit ist die Backend-Uhr, nicht die Zeit im
     * Payload (FR-1.8).
     */
    @Test
    void handle_CommitGelingt_ZeileGespeichertMitBackendUhr() {
        Instant vorher = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        mqttIngestService.handle(topic(), payload("123.4500"));

        assertThat(rohdaten()).hasSize(1);
        List<MesspunktEmpfang> zeilen = empfangszeilen();
        assertThat(zeilen).hasSize(1);
        MesspunktEmpfang zeile = zeilen.get(0);
        assertThat(zeile.getErgebnis()).isEqualTo(MesspunktEmpfangErgebnis.GESPEICHERT);
        assertThat(zeile.getGrund()).isNull();
        assertThat(zeile.getQuelle()).isEqualTo(MesspunktEmpfangQuelle.PI_GATEWAY);
        assertThat(zeile.getAnzahlMeldungen()).isEqualTo(1);
        assertThat(zeile.getErsterEmpfang()).isEqualTo(zeile.getLetzterEmpfang());
        assertThat(zeile.getZaehlerstandBezug()).isEqualByComparingTo("123.4500");
        // Payload-Zeit 25.10.2026 10:00 - der Vermerk traegt die Backend-Uhr
        assertThat(zeile.getLetzterEmpfang()).isAfterOrEqualTo(vorher.minusSeconds(1))
                .isBeforeOrEqualTo(Instant.now().plusSeconds(1));
    }

    /**
     * Der Kern: Scheitert der Commit, stehen keine Rohdaten in der Tabelle — und die Zeile zeigt
     * {@code UNGUELTIG} / {@code Speichern fehlgeschlagen} statt {@code GESPEICHERT}, mit den
     * gemeldeten Ständen (FR-1.4).
     */
    @Test
    void handle_CommitScheitert_ZeileZeigtSpeichernFehlgeschlagen() {
        // 11 Vorkommastellen: besteht alle Pruefungen, sprengt DECIMAL(14,4) erst beim Commit
        assertThatThrownBy(() -> mqttIngestService.handle(topic(), payload("12345678901.0")))
                .isInstanceOf(RuntimeException.class);

        assertThat(rohdaten()).isEmpty();
        List<MesspunktEmpfang> zeilen = empfangszeilen();
        assertThat(zeilen).hasSize(1);
        MesspunktEmpfang zeile = zeilen.get(0);
        assertThat(zeile.getErgebnis()).isEqualTo(MesspunktEmpfangErgebnis.UNGUELTIG);
        assertThat(zeile.getGrund()).isEqualTo(MqttIngestService.GRUND_SPEICHERN);
        assertThat(zeile.getZaehlerstandBezug()).isEqualByComparingTo(new BigDecimal("12345678901.0"));
        assertThat(zeile.getZaehlerstandEinspeisung()).isEqualByComparingTo("10.0");
    }
}
