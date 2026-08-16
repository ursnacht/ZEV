package ch.nacht.repository;

import ch.nacht.entity.TarifTyp;
import ch.nacht.entity.Tarifposition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

/**
 * Repository for Tarifposition entities.
 */
@Repository
public interface TarifpositionRepository extends JpaRepository<Tarifposition, Long> {

    /**
     * Find all positions of a tenant, newest quarter first.
     *
     * @param mieterId Tenant ID
     * @return List of positions
     */
    @Query("SELECT p FROM Tarifposition p WHERE p.mieter.id = :mieterId "
            + "ORDER BY p.jahr DESC, p.quartal DESC, p.tarif.bezeichnung")
    List<Tarifposition> findByMieterId(@Param("mieterId") Long mieterId);

    /**
     * Find the positions of a tenant whose quarter overlaps the given period.
     * Used by the invoice calculation.
     *
     * <p>Deliberately <b>overlap</b> and not "quarter fully inside the period": a tenant moving
     * out mid-quarter is billed for a partial period only — with the stricter rule their position
     * would never be billed. Double billing cannot occur because each position belongs to exactly
     * one tenant.
     *
     * @param mieterId Tenant ID
     * @param vonJahr Year of the period start
     * @param vonQuartal Quarter of the period start
     * @param bisJahr Year of the period end
     * @param bisQuartal Quarter of the period end
     * @return Positions with a quantity greater than zero, oldest quarter first
     */
    @Query("SELECT p FROM Tarifposition p WHERE p.mieter.id = :mieterId "
            + "AND p.menge > 0 "
            + "AND (p.jahr * 4 + p.quartal) >= (:vonJahr * 4 + :vonQuartal) "
            + "AND (p.jahr * 4 + p.quartal) <= (:bisJahr * 4 + :bisQuartal) "
            + "ORDER BY p.jahr, p.quartal, p.tarif.bezeichnung")
    List<Tarifposition> findByMieterIdAndQuartalOverlapping(
            @Param("mieterId") Long mieterId,
            @Param("vonJahr") int vonJahr,
            @Param("vonQuartal") int vonQuartal,
            @Param("bisJahr") int bisJahr,
            @Param("bisQuartal") int bisQuartal
    );

    /**
     * Check whether a position with a tariff of one of the given types already exists for this
     * tenant and quarter. Enforces the rule "at most one position per tenant, quarter and
     * tariff TYPE" — stricter than the database unique constraint, which only covers the exact
     * same tariff.
     *
     * @param mieterId Tenant ID
     * @param jahr Year
     * @param quartal Quarter
     * @param typen Tariff types to check
     * @param excludeId ID to exclude (use -1 for new positions)
     * @return true if such a position exists
     */
    @Query("SELECT COUNT(p) > 0 FROM Tarifposition p WHERE p.mieter.id = :mieterId "
            + "AND p.jahr = :jahr AND p.quartal = :quartal "
            + "AND p.tarif.tariftyp IN :typen "
            + "AND p.id != :excludeId")
    boolean existsByMieterAndQuartalAndTariftyp(
            @Param("mieterId") Long mieterId,
            @Param("jahr") Integer jahr,
            @Param("quartal") Integer quartal,
            @Param("typen") Set<TarifTyp> typen,
            @Param("excludeId") Long excludeId
    );

    /**
     * Count positions referencing a given tariff. Used to reject deletion of a referenced tariff.
     *
     * @param tarifId Tariff ID
     * @return Number of positions
     */
    long countByTarifId(Long tarifId);
}
