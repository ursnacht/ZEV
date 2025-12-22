package ch.nacht.service;

import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

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
     * Aktiviert den orgFilter für die aktuelle Session.
     * Muss innerhalb einer Transaktion aufgerufen werden.
     */
    public void enableOrgFilter() {
        UUID orgId = organizationContextService.getCurrentOrgId();
        if (orgId != null) {
            try {
                Session session = entityManager.unwrap(Session.class);
                if (session.getEnabledFilter("orgFilter") == null) {
                    session.enableFilter("orgFilter").setParameter("orgId", orgId);
                    log.debug("Hibernate orgFilter aktiviert für org_id: {}", orgId);
                }
            } catch (Exception e) {
                log.warn("Konnte orgFilter nicht aktivieren: {}", e.getMessage());
            }
        }
    }
}
