package ch.nacht.repository;

import ch.nacht.entity.NkAkonto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for NkAkonto entities.
 */
@Repository
public interface NkAkontoRepository extends JpaRepository<NkAkonto, Long> {

    /**
     * Find all prepayment entries of a billing period.
     *
     * @param abrechnungId Billing period ID
     * @return List of prepayment entries
     */
    List<NkAkonto> findByAbrechnungId(Long abrechnungId);

    /**
     * Find the prepayment entry of a single tenant. At most one exists per billing period and
     * tenant (unique constraint {@code uq_nk_akonto}).
     *
     * @param abrechnungId Billing period ID
     * @param mieterId Tenant ID
     * @return The entry, if recorded
     */
    Optional<NkAkonto> findByAbrechnungIdAndMieterId(Long abrechnungId, Long mieterId);

    /**
     * Delete all prepayment entries of a billing period.
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
