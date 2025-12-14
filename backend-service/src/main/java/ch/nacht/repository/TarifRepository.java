package ch.nacht.repository;

import ch.nacht.entity.Tarif;
import ch.nacht.entity.TarifTyp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Repository for Tarif entities.
 */
@Repository
public interface TarifRepository extends JpaRepository<Tarif, Long> {

    /**
     * Find all tariffs of a specific type that overlap with the given date range.
     * Used for invoice calculation.
     *
     * @param typ Tariff type (ZEV or VNB)
     * @param von Start date (inclusive)
     * @param bis End date (inclusive)
     * @return List of overlapping tariffs ordered by validity start date
     */
    @Query("SELECT t FROM Tarif t WHERE t.tariftyp = :typ " +
           "AND t.gueltigVon <= :bis AND t.gueltigBis >= :von " +
           "ORDER BY t.gueltigVon")
    List<Tarif> findByTariftypAndZeitraumOverlapping(
        @Param("typ") TarifTyp typ,
        @Param("von") LocalDate von,
        @Param("bis") LocalDate bis
    );

    /**
     * Check if an overlapping tariff exists (for validation).
     * Excludes the tariff with the given ID (for updates).
     *
     * @param typ Tariff type
     * @param von Start date
     * @param bis End date
     * @param excludeId ID to exclude (use -1 for new tariffs)
     * @return true if an overlapping tariff exists
     */
    @Query("SELECT COUNT(t) > 0 FROM Tarif t WHERE t.tariftyp = :typ " +
           "AND t.id != :excludeId " +
           "AND t.gueltigVon <= :bis AND t.gueltigBis >= :von")
    boolean existsOverlappingTarif(
        @Param("typ") TarifTyp typ,
        @Param("von") LocalDate von,
        @Param("bis") LocalDate bis,
        @Param("excludeId") Long excludeId
    );

    /**
     * Find all tariffs ordered by type and validity start date (descending).
     *
     * @return List of all tariffs
     */
    List<Tarif> findAllByOrderByTariftypAscGueltigVonDesc();
}
