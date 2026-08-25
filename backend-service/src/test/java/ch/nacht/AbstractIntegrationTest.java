package ch.nacht;

import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base class for integration tests using Testcontainers.
 * Uses a singleton PostgreSQL container shared across all integration tests.
 * The container is started once and reused for all test classes.
 */
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer postgres;

    static {
        postgres = new PostgreSQLContainer("postgres:16-alpine")
                .withDatabaseName("zev")
                .withUsername("test")
                .withPassword("test")
                .withInitScript("create-schema.sql");
        postgres.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.flyway.enabled", () -> "false");
    }

    /**
     * Schaltet den Mandantenfilter {@code orgFilter} fuer die laufende Session ein.
     *
     * <p>Im Betrieb tut das {@code HibernateFilterService.enableOrgFilter()} zu Beginn jeder
     * Service-Methode. Ein Repository-Test hat keinen Service davor, also muss er den Filter
     * selbst setzen — sonst prueft er die Abfrage <b>ungefiltert</b> und ist gegen eine
     * Mandanten-Vermischung blind.
     *
     * <p>Der Filter greift auf {@code SELECT}, und damit auch auf die abgeleiteten
     * {@code deleteBy...}-Methoden: Spring Data liest die Treffer zuerst und loescht dann.
     *
     * <p>Bewusst statisch und mit uebergebenem {@link EntityManager}: Von dieser Klasse erben
     * auch Tests ohne JPA-Kontext ({@code ActuatorSecurityIT},
     * {@code SecurityConfigContextLoadIT}), bei denen ein injizierter EntityManager den
     * Kontextaufbau brechen wuerde.
     *
     * @param entityManager EntityManager der Testklasse
     * @param orgId Mandant, dessen Daten sichtbar sein sollen
     */
    protected static void aktiviereOrgFilter(EntityManager entityManager, Long orgId) {
        entityManager.unwrap(Session.class).enableFilter("orgFilter").setParameter("orgId", orgId);
    }
}
