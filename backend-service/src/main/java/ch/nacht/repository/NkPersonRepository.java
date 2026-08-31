package ch.nacht.repository;

import ch.nacht.entity.NkPerson;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for NkPerson entities (persons per apartment of a tenant).
 */
@Repository
public interface NkPersonRepository extends JpaRepository<NkPerson, Long> {

    /**
     * Find all person counts of a billing period.
     *
     * @param abrechnungId Billing period ID
     * @return List of person counts
     */
    List<NkPerson> findByAbrechnungId(Long abrechnungId);

    /**
     * Delete all person counts of a billing period.
     *
     * @param abrechnungId Billing period ID
     */
    void deleteByAbrechnungId(Long abrechnungId);

    /**
     * Count entries referencing a tenant. Used to reject deleting a tenant who appears in a
     * billing period.
     *
     * @param mieterId Tenant ID
     * @return Number of entries
     */
    long countByMieterId(Long mieterId);
}
