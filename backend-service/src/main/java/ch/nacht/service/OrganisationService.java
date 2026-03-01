package ch.nacht.service;

import ch.nacht.entity.Organisation;
import ch.nacht.repository.OrganisationRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing Organisation entities.
 * Provides auto-provisioning: a new Keycloak organisation is automatically
 * created in the DB on first login.
 */
@Service
public class OrganisationService {

    private static final Logger log = LoggerFactory.getLogger(OrganisationService.class);

    private final OrganisationRepository organisationRepository;

    public OrganisationService(OrganisationRepository organisationRepository) {
        this.organisationRepository = organisationRepository;
    }

    /**
     * Liefert die interne Organisation für eine Keycloak-UUID.
     * Legt sie automatisch an, falls sie noch nicht existiert (Auto-Provisioning).
     * Aktualisiert den Namen, falls er sich geändert hat.
     */
    @Transactional
    public Organisation findOrCreate(UUID keycloakOrgId, String name) {
        Optional<Organisation> existing = organisationRepository.findByKeycloakOrgId(keycloakOrgId);

        if (existing.isPresent()) {
            Organisation org = existing.get();
            if (name != null && !Objects.equals(org.getName(), name)) {
                org.setName(name);
                organisationRepository.save(org);
                log.debug("Organisation-Name aktualisiert: id={}, name={}", org.getId(), name);
            }
            return org;
        }

        Organisation newOrg = new Organisation();
        newOrg.setKeycloakOrgId(keycloakOrgId);
        newOrg.setName(name != null ? name : keycloakOrgId.toString());
        newOrg.setErstelltAm(LocalDateTime.now());
        Organisation saved = organisationRepository.save(newOrg);
        log.info("Neue Organisation auto-provisioniert: keycloak_org_id={}, id={}", keycloakOrgId, saved.getId());
        return saved;
    }
}
