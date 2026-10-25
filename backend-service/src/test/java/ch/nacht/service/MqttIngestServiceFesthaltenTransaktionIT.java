package ch.nacht.service;

import ch.nacht.AbstractIntegrationTest;
import ch.nacht.config.ZeitConfig;
import ch.nacht.entity.Einheit;
import ch.nacht.entity.EinheitTyp;
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

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Integrationstest für die <b>Transaktionsgrenze</b> des Empfangsvermerks
 * (Specs/Messpunkt-Empfang.md, FR-1.6), hier: <b>das Festhalten scheitert</b>. Die Rohdaten der
 * Meldung müssen trotzdem gespeichert sein — eine Anzeige darf die Messung nie kosten.
 *
 * <p><b>Warum ein Integrationstest.</b> Ein werfendes Festhalten im selben Transaktionsschritt
 * setzte die Ingest-Transaktion auf rollback-only, und der Commit scheiterte mit
 * {@code UnexpectedRollbackException} — obwohl das {@code catch} den Fehler abfängt. Mit Mockito
 * ist das unsichtbar.
 *
 * <p><b>Der werfende Service</b> ({@link WerfenderEmpfangService}) ist eine echte Bean, deren
 * {@code halteFest} in einer <b>teilnehmenden</b> Transaktion ({@code REQUIRED}) wirft — die
 * ungünstigste Lage: Die Rohdaten sollen nicht davon abhängen, dass das Festhalten selbst eine
 * eigene Transaktion öffnet. Getrennt von {@code MqttIngestServiceTransaktionIT}, weil dort der
 * echte Service committen muss.
 *
 * <p><b>Gegenprobe</b> (25.10.2026): Ruft {@code halteEmpfangFest} den Service direkt in der
 * Ingest-Transaktion auf statt in {@code afterCompletion}, scheitert der Test — {@code handle}
 * wirft {@code UnexpectedRollbackException}, und die Rohdaten fehlen.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({HibernateFilterService.class, ZeitConfig.class,
        MqttIngestServiceFesthaltenTransaktionIT.Konfiguration.class})
class MqttIngestServiceFesthaltenTransaktionIT extends AbstractIntegrationTest {

    private static final String MESSPUNKT = "MP-TX-002";

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

    /**
     * Ein Empfangs-Service, der in einer echten {@code @Transactional}-Methode wirft.
     *
     * <p>Die Abhängigkeiten bleiben {@code null}: Die einzige aufgerufene Methode ist
     * überschrieben.
     */
    static class WerfenderEmpfangService extends MesspunktEmpfangService {

        WerfenderEmpfangService() {
            super(null, null, null, Clock.systemUTC());
        }

        @Override
        @Transactional
        public void halteFest(Meldung meldung) {
            throw new IllegalStateException("Festhalten kaputt");
        }
    }

    @TestConfiguration
    static class Konfiguration {

        @Bean
        MesspunktEmpfangService messpunktEmpfangService() {
            return new WerfenderEmpfangService();
        }

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
            org.setName("Transaktionstest Festhalten");
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

    @Test
    void handle_FesthaltenWirft_RohdatenSindTrotzdemGespeichert() {
        String topic = "zev/" + orgId + "/" + MESSPUNKT + "/messwert";
        String payload = "{\"timestamp\":\"2026-10-25T10:00:00+01:00\",\"zaehlerstandBezug\":123.45,"
                + "\"zaehlerstandEinspeisung\":10.0}";

        assertThatCode(() -> mqttIngestService.handle(topic, payload)).doesNotThrowAnyException();

        List<ZaehlerRohdaten> rohdaten = new TransactionTemplate(transactionManager).execute(
                status -> entityManager.createQuery(
                                "SELECT r FROM ZaehlerRohdaten r WHERE r.orgId = :org", ZaehlerRohdaten.class)
                        .setParameter("org", orgId).getResultList());
        assertThat(rohdaten).hasSize(1);
        assertThat(rohdaten.get(0).getZaehlerstandBezug()).isEqualByComparingTo("123.45");
        assertThat(rohdaten.get(0).getZeit()).isEqualTo(LocalDateTime.of(2026, 10, 25, 10, 0));
    }
}
