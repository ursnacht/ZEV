package ch.nacht.repository;

import ch.nacht.entity.Organisation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Organisation entities.
 */
public interface OrganisationRepository extends JpaRepository<Organisation, Long> {

    Optional<Organisation> findByKeycloakOrgId(UUID keycloakOrgId);
}
