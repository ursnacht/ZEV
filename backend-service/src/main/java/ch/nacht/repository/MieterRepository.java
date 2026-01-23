package ch.nacht.repository;

import ch.nacht.entity.Mieter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Repository for Mieter entities.
 */
@Repository
public interface MieterRepository extends JpaRepository<Mieter, Long> {

    /**
     * Find all tenants ordered by unit ID and lease start date (descending).
     *
     * @return List of all tenants
     */
    List<Mieter> findAllByOrderByEinheitIdAscMietbeginnDesc();

    /**
     * Find all tenants for a specific unit ordered by lease start date (descending).
     *
     * @param einheitId Unit ID
     * @return List of tenants for the unit
     */
    List<Mieter> findByEinheitIdOrderByMietbeginnDesc(Long einheitId);

    /**
     * Check if overlapping lease periods exist for a unit.
     * Two periods overlap if: start1 < end2 AND end1 > start2
     * NULL mietende means open-ended (infinite end date).
     *
     * @param einheitId Unit ID
     * @param mietbeginn Lease start date
     * @param mietende Lease end date (can be null for open-ended)
     * @param excludeId ID to exclude (use -1 for new tenants)
     * @return true if an overlapping tenant exists
     */
    @Query("SELECT COUNT(m) > 0 FROM Mieter m WHERE m.einheitId = :einheitId " +
           "AND m.id != :excludeId " +
           "AND (m.mietende IS NULL OR m.mietende > :mietbeginn) " +
           "AND (:mietende IS NULL OR m.mietbeginn < :mietende)")
    boolean existsOverlappingMieter(
        @Param("einheitId") Long einheitId,
        @Param("mietbeginn") LocalDate mietbeginn,
        @Param("mietende") LocalDate mietende,
        @Param("excludeId") Long excludeId
    );

    /**
     * Check if another tenant without lease end exists for the same unit.
     * Only one tenant per unit can have an open-ended lease (current tenant).
     *
     * @param einheitId Unit ID
     * @param excludeId ID to exclude (use -1 for new tenants)
     * @return true if another tenant without lease end exists
     */
    @Query("SELECT COUNT(m) > 0 FROM Mieter m WHERE m.einheitId = :einheitId " +
           "AND m.id != :excludeId " +
           "AND m.mietende IS NULL")
    boolean existsOtherMieterWithoutMietende(
        @Param("einheitId") Long einheitId,
        @Param("excludeId") Long excludeId
    );

    /**
     * Find tenants for a unit within a quarter (for invoice generation).
     * Returns tenants whose lease period overlaps with the given quarter.
     *
     * @param einheitId Unit ID
     * @param quartalBeginn Quarter start date
     * @param quartalEnde Quarter end date
     * @return List of tenants active during the quarter
     */
    @Query("SELECT m FROM Mieter m WHERE m.einheitId = :einheitId " +
           "AND m.mietbeginn <= :quartalEnde " +
           "AND (m.mietende IS NULL OR m.mietende >= :quartalBeginn) " +
           "ORDER BY m.mietbeginn")
    List<Mieter> findByEinheitIdAndQuartal(
        @Param("einheitId") Long einheitId,
        @Param("quartalBeginn") LocalDate quartalBeginn,
        @Param("quartalEnde") LocalDate quartalEnde
    );
}
