package ch.nacht.service;

import ch.nacht.AbstractIntegrationTest;
import ch.nacht.dto.PrognosepunktDTO;
import ch.nacht.dto.SteuerKonfigurationDTO;
import ch.nacht.entity.Einheit;
import ch.nacht.entity.EinheitTyp;
import ch.nacht.entity.FeatureFlag;
import ch.nacht.entity.Geraetezustand;
import ch.nacht.entity.Organisation;
import ch.nacht.entity.Steuerentscheid;
import ch.nacht.entity.Zustandsgroesse;
import ch.nacht.repository.EinheitRepository;
import ch.nacht.repository.GeraetezustandRepository;
import ch.nacht.repository.OrganisationRepository;
import ch.nacht.repository.SteuerentscheidRepository;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Integrationstest fuer die <b>Transaktionsgrenze</b> der Schattenrechnung
 * (Specs/Ladeplanung.md, FR-1a, Anhang A.14).
 *
 * <p><b>Warum ein Integrationstest und kein Unit-Test.</b> Der Fehler lag im Zusammenspiel mit
 * Spring: Eine Exception in einer {@code @Transactional}-Bean, die an der Transaktion des Jobs
 * teilnimmt, markiert diese als <i>rollback-only</i> — noch bevor das {@code catch} der
 * Schattenrechnung sie erreicht. Der Commit scheitert dann, und der Entscheid der Kaskade geht
 * verloren. Mit Mockito ist das unsichtbar: Ein Mock hat keinen Transaktions-Proxy.
 *
 * <p><b>Aufbau</b> (Muster fuer Transaktionstests, `.claude/commands/3_backend-tests.md`):
 * <ul>
 *   <li>{@code @DataJpaTest} mit echtem PostgreSQL, {@code @Import} der beteiligten Services —
 *       kein {@code @SpringBootTest}.</li>
 *   <li>{@code @Transactional(propagation = NOT_SUPPORTED)}: Die Testmethode laeuft <b>ohne</b>
 *       eigene Transaktion, sonst nimmt der Service an ihr teil, und der Commit, um den es geht,
 *       findet nie statt. Aufgeraeumt wird deshalb von Hand.</li>
 *   <li>Der Fehler kommt aus einer <b>echten</b> Spring-Bean mit {@code @Transactional}
 *       ({@link WerfendePrognose}), nicht aus einem Mock.</li>
 * </ul>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({SteuerungService.class, SteuerRegelService.class, LadeplanService.class,
        HibernateFilterService.class, SteuerungServiceTransaktionIT.Konfiguration.class})
class SteuerungServiceTransaktionIT extends AbstractIntegrationTest {

    private static final LocalDateTime ZEHN_UHR = LocalDateTime.of(2026, 10, 9, 10, 0);

    @Autowired
    private SteuerungService steuerungService;

    @Autowired
    private SteuerentscheidRepository steuerentscheidRepository;

    @Autowired
    private OrganisationRepository organisationRepository;

    @Autowired
    private EinheitRepository einheitRepository;

    @Autowired
    private GeraetezustandRepository geraetezustandRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    @MockitoBean
    private EinstellungenService einstellungenService;

    @MockitoBean
    private FeatureFlagService featureFlagService;

    @MockitoBean
    private OrganizationContextService organizationContextService;

    private Long orgId;
    private Long speicherId;

    /**
     * Ein Prognose-Service, der in einer echten {@code @Transactional}-Methode wirft — genau die
     * Lage, die eine teilnehmende Transaktion als rollback-only markiert.
     *
     * <p>Die Abhaengigkeiten bleiben {@code null}: Die einzige aufgerufene Methode ist
     * ueberschrieben, und Spring erzeugt den Proxy, ohne den Konstruktor erneut aufzurufen.
     */
    static class WerfendePrognose extends ProduktionsprognoseService {

        WerfendePrognose() {
            super(null, null, null, null, null, null, null);
        }

        @Override
        @Transactional(readOnly = true)
        public List<PrognosepunktDTO> getPrognose(Long orgId, LocalDate datum) {
            throw new IllegalStateException("Prognose kaputt");
        }
    }

    @TestConfiguration
    static class Konfiguration {

        @Bean
        ProduktionsprognoseService produktionsprognoseService() {
            return new WerfendePrognose();
        }
    }

    @BeforeEach
    void setUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Organisation org = new Organisation();
            org.setKeycloakOrgId(UUID.randomUUID());
            org.setName("Transaktionstest");
            org.setErstelltAm(LocalDateTime.now());
            orgId = organisationRepository.save(org).getId();

            Einheit speicher = new Einheit("Batterie", EinheitTyp.SPEICHER);
            speicher.setOrgId(orgId);
            speicherId = einheitRepository.save(speicher).getId();

            geraetezustandRepository.save(new Geraetezustand(orgId, speicherId,
                    ZEHN_UHR.plusMinutes(14), Zustandsgroesse.SOC, new BigDecimal("50.0")));

            // Das Schema stammt von Hibernate; der Default der Id fehlt dort, der native Upsert
            // nennt die Spalte aber nicht (siehe SteuerentscheidRepositoryIT).
            entityManager.createNativeQuery("ALTER TABLE zev.steuerentscheid "
                    + "ALTER COLUMN id SET DEFAULT nextval('zev.steuerentscheid_seq')")
                    .executeUpdate();
        });

        // Alle Voraussetzungen der Schattenrechnung erfuellt - damit sie bis zur Prognose kommt
        SteuerKonfigurationDTO konfiguration = new SteuerKonfigurationDTO();
        konfiguration.setBatteriekapazitaet(new BigDecimal("40.8"));
        konfiguration.setBreitengrad(new BigDecimal("47.0"));
        konfiguration.setLaengengrad(new BigDecimal("7.5"));
        konfiguration.setAzimut(BigDecimal.ZERO);
        konfiguration.setNeigung(new BigDecimal("30"));
        when(einstellungenService.getSteuerKonfiguration(orgId)).thenReturn(konfiguration);
        when(featureFlagService.isEnabled(orgId, FeatureFlag.EINSPEISESTEUERUNG)).thenReturn(true);
        when(organizationContextService.getCurrentOrgId()).thenReturn(orgId);
    }

    /** Ohne Testtransaktion bleibt alles stehen - also von Hand und nur die eigenen Zeilen. */
    @AfterEach
    void tearDown() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            entityManager.createNativeQuery("DELETE FROM zev.steuerentscheid WHERE org_id = :org")
                    .setParameter("org", orgId).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM zev.geraetezustand WHERE org_id = :org")
                    .setParameter("org", orgId).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM zev.einheit WHERE org_id = :org")
                    .setParameter("org", orgId).executeUpdate();
            organisationRepository.deleteById(orgId);
        });
    }

    /**
     * Der Kern: Wirft die Prognose in ihrer Transaktion, wird der Entscheid der Kaskade trotzdem
     * <b>committet</b> — mit leeren Schattenwerten. Vor der Isolierung (A.14) scheiterte hier der
     * Commit mit {@code UnexpectedRollbackException}, und es stand kein Entscheid in der Tabelle.
     */
    @Test
    void werteIntervallAus_PrognoseWirftInIhrerTransaktion_EntscheidIstCommittet() {
        steuerungService.werteIntervallAus(orgId, ZEHN_UHR);

        List<Steuerentscheid> entscheide = new TransactionTemplate(transactionManager).execute(
                status -> entityManager.createQuery(
                                "SELECT s FROM Steuerentscheid s WHERE s.orgId = :org",
                                Steuerentscheid.class)
                        .setParameter("org", orgId).getResultList());

        assertThat(entscheide).hasSize(1);
        Steuerentscheid entscheid = entscheide.get(0);
        assertThat(entscheid.getZeitVon()).isEqualTo(ZEHN_UHR);
        assertThat(entscheid.getBatterieladung()).isNotNull();
        assertThat(entscheid.getLadeplanBatterieladung()).isNull();
        assertThat(entscheid.getRang()).isNull();
    }
}
