package ch.nacht.repository;

import ch.nacht.entity.MieterEinheit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * Repository for the tenant-to-unit assignment.
 */
@Repository
public interface MieterEinheitRepository extends JpaRepository<MieterEinheit, Long> {

    /**
     * Assignments of one tenant.
     *
     * @param mieterId Tenant ID
     * @return Assignments
     */
    List<MieterEinheit> findByMieterId(Long mieterId);

    /**
     * Assignments of several tenants — used to fill the unit list of a whole result set with a
     * single query instead of one per tenant.
     *
     * @param mieterIds Tenant IDs
     * @return Assignments
     */
    List<MieterEinheit> findByMieterIdIn(Collection<Long> mieterIds);

    /**
     * Delete all assignments of one tenant (before writing the new set).
     *
     * @param mieterId Tenant ID
     */
    void deleteByMieterId(Long mieterId);

    /**
     * Count the tenants assigned to a unit — the unit must not be deleted while assignments exist.
     *
     * @param einheitId Unit ID
     * @return Number of assignments
     */
    long countByEinheitId(Long einheitId);

    /**
     * Unit IDs of one tenant, ordered — keeps the form and list output stable.
     *
     * @param mieterId Tenant ID
     * @return Unit IDs
     */
    @Query("SELECT me.einheitId FROM MieterEinheit me WHERE me.mieterId = :mieterId ORDER BY me.einheitId")
    List<Long> findEinheitIdsByMieterId(@Param("mieterId") Long mieterId);
}
