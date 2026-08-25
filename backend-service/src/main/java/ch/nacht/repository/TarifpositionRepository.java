package ch.nacht.repository;

import ch.nacht.entity.TarifTyp;
import ch.nacht.entity.Tarifposition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.Optional;

/**
 * Repository for Tarifposition entities.
 */
@Repository
public interface TarifpositionRepository extends JpaRepository<Tarifposition, Long> {

    /**
     * Find all positions of a unit, newest quarter first.
     *
     * @param einheitId Unit ID
     * @return List of positions
     */
    @Query("SELECT p FROM Tarifposition p WHERE p.einheit.id = :einheitId "
            + "ORDER BY p.jahr DESC, p.quartal DESC, p.tarif.bezeichnung")
    List<Tarifposition> findByEinheitId(@Param("einheitId") Long einheitId);

    /**
     * Find the positions of several units whose quarter overlaps the given period.
     * Used by the invoice calculation, which passes all units assigned to the tenant.
     *
     * <p>Deliberately <b>overlap</b> and not "quarter fully inside the period": a tenant moving
     * out mid-quarter is billed for a partial period only — with the stricter rule their position
     * would never be billed. Double billing cannot occur because a unit belongs to exactly one
     * tenant over its whole lifetime (on a tenant change the RFID and with it the unit changes).
     *
     * @param einheitIds Unit IDs
     * @param vonJahr Year of the period start
     * @param vonQuartal Quarter of the period start
     * @param bisJahr Year of the period end
     * @param bisQuartal Quarter of the period end
     * @return Positions with a quantity greater than zero, oldest quarter first
     */
    @Query("SELECT p FROM Tarifposition p WHERE p.einheit.id IN :einheitIds "
            + "AND p.menge > 0 "
            + "AND (p.jahr * 4 + p.quartal) >= (:vonJahr * 4 + :vonQuartal) "
            + "AND (p.jahr * 4 + p.quartal) <= (:bisJahr * 4 + :bisQuartal) "
            + "ORDER BY p.jahr, p.quartal, p.tarif.bezeichnung")
    List<Tarifposition> findByEinheitIdsAndQuartalOverlapping(
            @Param("einheitIds") Collection<Long> einheitIds,
            @Param("vonJahr") int vonJahr,
            @Param("vonQuartal") int vonQuartal,
            @Param("bisJahr") int bisJahr,
            @Param("bisQuartal") int bisQuartal
    );

    /**
     * Check whether a position with a tariff of one of the given types already exists for this
     * unit and quarter. Enforces the rule "at most one position per unit, quarter and
     * tariff TYPE" — stricter than the database unique constraint, which only covers the exact
     * same tariff.
     *
     * @param einheitId Unit ID
     * @param jahr Year
     * @param quartal Quarter
     * @param typen Tariff types to check
     * @param excludeId ID to exclude (use -1 for new positions)
     * @return true if such a position exists
     */
    @Query("SELECT COUNT(p) > 0 FROM Tarifposition p WHERE p.einheit.id = :einheitId "
            + "AND p.jahr = :jahr AND p.quartal = :quartal "
            + "AND p.tarif.tariftyp IN :typen "
            + "AND p.id != :excludeId")
    boolean existsByEinheitAndQuartalAndTariftyp(
            @Param("einheitId") Long einheitId,
            @Param("jahr") Integer jahr,
            @Param("quartal") Integer quartal,
            @Param("typen") Set<TarifTyp> typen,
            @Param("excludeId") Long excludeId
    );

    /**
     * Wie oben, aber je <b>Tarif</b> statt je Tariftyp. Für Typen mit mehreren gleichzeitig
     * gültigen Tarifen (ZUSATZ): Dort sollen mehrere Positionen desselben Typs nebeneinander
     * bestehen, solange sie auf verschiedene Tarife zeigen.
     *
     * @param einheitId Unit ID
     * @param jahr Year
     * @param quartal Quarter
     * @param tarifId Tariff to check
     * @param excludeId ID to exclude (use -1 for new positions)
     * @return true if such a position exists
     */
    @Query("SELECT COUNT(p) > 0 FROM Tarifposition p WHERE p.einheit.id = :einheitId "
            + "AND p.jahr = :jahr AND p.quartal = :quartal "
            + "AND p.tarif.id = :tarifId "
            + "AND p.id != :excludeId")
    boolean existsByEinheitAndQuartalAndTarif(
            @Param("einheitId") Long einheitId,
            @Param("jahr") Integer jahr,
            @Param("quartal") Integer quartal,
            @Param("tarifId") Long tarifId,
            @Param("excludeId") Long excludeId
    );

    /**
     * Count positions referencing a given tariff. Used to reject deletion of a referenced tariff.
     *
     * @param tarifId Tariff ID
     * @return Number of positions
     */
    long countByTarifId(Long tarifId);

    /**
     * Count positions of a unit. Used to reject deleting a tenant whose units still carry
     * positions — they would otherwise remain without an invoice recipient.
     *
     * @param einheitId Unit ID
     * @return Number of positions
     */
    long countByEinheitId(Long einheitId);

    /**
     * Findet den Datensatz zur ID — <b>unter dem Mandantenfilter</b>.
     *
     * <p><b>Nicht durch {@code findById} ersetzen.</b> Hibernate wendet {@code @Filter} auf
     * Abfragen an, <b>nicht</b> auf das Laden ueber den Primaerschluessel: {@code findById}
     * liefert auch Datensaetze fremder Mandanten (empirisch belegt in
     * {@code NkAbrechnungRepositoryIT}). Diese abgeleitete Abfrage ist gefiltert und damit der
     * zulaessige Weg, einen Datensatz anhand einer von aussen kommenden ID zu laden. Eine
     * ArchUnit-Regel in {@code ArchitectureTest.SecurityRules} haelt das fest.
     *
     * <p>Ist der Filter nicht eingeschaltet, verhaelt sich die Methode wie {@code findById} —
     * der Wechsel ist also dort verhaltensneutral, wo kein Mandantenkontext gesetzt ist.
     *
     * @param id Technischer Schluessel
     * @return Datensatz des eigenen Mandanten, sonst leer
     */
    Optional<Tarifposition> findFirstById(Long id);
}
