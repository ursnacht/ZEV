package ch.nacht.repository;

import ch.nacht.entity.NkZusatz;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for NkZusatz entities.
 */
@Repository
public interface NkZusatzRepository extends JpaRepository<NkZusatz, Long> {

    /**
     * Find all tenant-specific positions of a billing period, in calculation order.
     *
     * @param abrechnungId Billing period ID
     * @return List of positions
     */
    List<NkZusatz> findByAbrechnungIdOrderByMieterIdAscReihenfolgeAsc(Long abrechnungId);

    /**
     * Find the tenant-specific positions of a single tenant, in calculation order.
     *
     * @param abrechnungId Billing period ID
     * @param mieterId Tenant ID
     * @return List of positions
     */
    List<NkZusatz> findByAbrechnungIdAndMieterIdOrderByReihenfolge(Long abrechnungId, Long mieterId);

    /**
     * Delete all tenant-specific positions of a billing period.
     *
     * @param abrechnungId Billing period ID
     */
    void deleteByAbrechnungId(Long abrechnungId);

    /**
     * Count positions referencing a tenant. Used to reject deleting a tenant who appears in a
     * billing period.
     *
     * @param mieterId Tenant ID
     * @return Number of positions
     */
    long countByMieterId(Long mieterId);
}
