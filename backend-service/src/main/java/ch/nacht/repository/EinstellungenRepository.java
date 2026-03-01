package ch.nacht.repository;

import ch.nacht.entity.Einstellungen;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for Einstellungen entities.
 */
@Repository
public interface EinstellungenRepository extends JpaRepository<Einstellungen, Long> {

    /**
     * Find settings by organization ID.
     *
     * @param orgId The organization (tenant) ID
     * @return Optional containing the settings if found
     */
    Optional<Einstellungen> findByOrgId(Long orgId);
}
