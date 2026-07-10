package ch.nacht.service;

import jakarta.persistence.EntityManager;
import org.hibernate.Filter;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service zum Aktivieren des Hibernate orgFilter für Multi-Tenancy.
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
     */
    public void enableOrgFilter(Long orgId) {
        if (orgId == null) {
            return;
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
            log.warn("Konnte orgFilter nicht aktivieren: {}", e.getMessage());
        }
    }
}
