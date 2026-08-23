package ch.nacht.repository;

import ch.nacht.entity.NkPosition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for NkPosition entities.
 */
@Repository
public interface NkPositionRepository extends JpaRepository<NkPosition, Long> {

    /**
     * Find all general positions of a billing period, in calculation order.
     *
     * <p>The sort order is not cosmetic: a surcharge is calculated on the sum of all lines with a
     * smaller {@code reihenfolge}, so the calculation service relies on this order.
     *
     * @param abrechnungId Billing period ID
     * @return List of positions
     */
    List<NkPosition> findByAbrechnungIdOrderByReihenfolge(Long abrechnungId);

    /**
     * Delete all positions of a billing period.
     *
     * @param abrechnungId Billing period ID
     */
    void deleteByAbrechnungId(Long abrechnungId);

    /**
     * Check whether a position with the given order number already exists in this billing period.
     * Enforces the unique constraint before the database does, so the user gets a readable message.
     *
     * @param abrechnungId Billing period ID
     * @param reihenfolge Order number
     * @return true if such a position exists
     */
    boolean existsByAbrechnungIdAndReihenfolge(Long abrechnungId, Integer reihenfolge);
}
