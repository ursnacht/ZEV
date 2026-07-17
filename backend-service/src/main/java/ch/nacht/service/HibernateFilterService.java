package ch.nacht.service;

import ch.nacht.exception.NoOrganizationException;
import jakarta.persistence.EntityManager;
import org.hibernate.Filter;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service zum Aktivieren des Hibernate orgFilter für Multi-Tenancy.
 *
 * <p><b>Fail-closed:</b> Kann der Filter nicht aktiviert werden (kein Organisationskontext,
 * Session-Fehler), wird eine {@link IllegalStateException} geworfen und die nachfolgende Query
 * verhindert – niemals still ohne Mandantenfilter weiterlaufen (Cross-Tenant-Leak, OWASP A01).
 */
@Service
public class HibernateFilterService {

    private static final Logger log = LoggerFactory.getLogger(HibernateFilterService.class);

    private final EntityManager entityManager;
    private final OrganizationContextService organizationContextService;

    public HibernateFilterService(EntityManager entityManager,
                                   OrganizationContextService organizationContextService) {
        this.entityManager = entityManager;
        this.organizationContextService = organizationContextService;
    }

    /**
     * Aktiviert den orgFilter für die aktuelle Session anhand des Request-Kontexts (JWT).
     * Muss innerhalb einer Transaktion aufgerufen werden.
     */
    public void enableOrgFilter() {
        enableOrgFilter(organizationContextService.getCurrentOrgId());
    }

    /**
     * Aktiviert bzw. aktualisiert den orgFilter für eine explizit angegebene {@code org_id}.
     * Für Hintergrund-Jobs ohne JWT/Request-Kontext (z.B. MQTT-Aggregation), die den Mandanten
     * aus den Daten ableiten. Muss innerhalb einer Transaktion aufgerufen werden.
     *
     * @throws NoOrganizationException wenn keine {@code org_id} vorliegt (fail-closed)
     * @throws IllegalStateException wenn der Filter nicht aktiviert werden kann
     *         (fail-closed – Query darf nicht ungefiltert laufen)
     */
    public void enableOrgFilter(Long orgId) {
        if (orgId == null) {
            throw new NoOrganizationException(
                    "Keine Organisation im Kontext – orgFilter kann nicht aktiviert werden");
        }
        try {
            Session session = entityManager.unwrap(Session.class);
            Filter filter = session.getEnabledFilter("orgFilter");
            if (filter == null) {
                filter = session.enableFilter("orgFilter");
            }
            filter.setParameter("orgId", orgId);
            log.debug("Hibernate orgFilter aktiviert für org_id: {}", orgId);
        } catch (Exception e) {
            log.error("Konnte orgFilter nicht aktivieren (org_id: {}): {}", orgId, e.getMessage());
            throw new IllegalStateException("orgFilter konnte nicht aktiviert werden", e);
        }
    }
}
