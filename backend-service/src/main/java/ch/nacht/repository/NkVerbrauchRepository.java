package ch.nacht.repository;

import ch.nacht.entity.NkVerbrauch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for NkVerbrauch entities.
 */
@Repository
public interface NkVerbrauchRepository extends JpaRepository<NkVerbrauch, Long> {

    /**
     * Find all recorded quantities of a whole billing period, across all its positions.
     * The calculation service loads them in one go and groups them itself — one query instead of
     * one per position.
     *
     * @param abrechnungId Billing period ID
     * @return List of quantities
     */
    @Query("SELECT v FROM NkVerbrauch v WHERE v.positionId IN "
            + "(SELECT p.id FROM NkPosition p WHERE p.abrechnungId = :abrechnungId)")
    List<NkVerbrauch> findByAbrechnungId(@Param("abrechnungId") Long abrechnungId);

    /**
     * Find the recorded quantities of a single position.
     *
     * @param positionId Position ID
     * @return List of quantities
     */
    List<NkVerbrauch> findByPositionId(Long positionId);

    /**
     * Delete all quantities of a position. Used when a position changes its type and its
     * quantities lose their meaning.
     *
     * @param positionId Position ID
     */
    void deleteByPositionId(Long positionId);

    /**
     * Count quantities referencing a tenant. Used to reject deleting a tenant who appears in a
     * billing period — the database would refuse it anyway (ON DELETE RESTRICT), but without a
     * readable message.
     *
     * @param mieterId Tenant ID
     * @return Number of quantities
     */
    long countByMieterId(Long mieterId);
}
