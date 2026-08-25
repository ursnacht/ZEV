package ch.nacht.repository;

import ch.nacht.entity.Mieter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Mieter entities.
 *
 * <p>Der Bezug zur Einheit läuft seit {@code V105} über die Zuordnungstabelle
 * {@code mieter_einheit} (ein Mieter kann mehreren Einheiten zugeordnet sein). Die Abfragen
 * verbinden deshalb explizit mit {@code MieterEinheit}; ein Mieter gilt als „Mieter der Einheit",
 * sobald eine Zuordnung existiert. Der Mietzeitraum bleibt am Mieter und gilt für alle seine
 * Einheiten.
 */
@Repository
public interface MieterRepository extends JpaRepository<Mieter, Long> {

    /**
     * Find all tenants ordered by name and lease start date (descending).
     * Vorher nach Einheit sortiert — die gibt es am Mieter nicht mehr; die Liste sortiert im
     * Frontend ohnehin clientseitig.
     *
     * @return List of all tenants
     */
    List<Mieter> findAllByOrderByNameAscMietbeginnDesc();

    /**
     * Find all tenants assigned to a specific unit, ordered by lease start date (descending).
     *
     * @param einheitId Unit ID
     * @return List of tenants for the unit
     */
    @Query("SELECT m FROM Mieter m JOIN MieterEinheit me ON me.mieterId = m.id "
           + "WHERE me.einheitId = :einheitId ORDER BY m.mietbeginn DESC")
    List<Mieter> findByEinheitIdOrderByMietbeginnDesc(@Param("einheitId") Long einheitId);

    /**
     * Check if overlapping lease periods exist for a unit (open-ended new lease).
     * Used when the new tenant has no mietende (current tenant).
     *
     * @param einheitId Unit ID
     * @param mietbeginn Lease start date
     * @param excludeId ID to exclude (use -1 for new tenants)
     * @return true if an overlapping tenant exists
     */
    @Query("SELECT COUNT(m) > 0 FROM Mieter m JOIN MieterEinheit me ON me.mieterId = m.id "
           + "WHERE me.einheitId = :einheitId "
           + "AND m.id != :excludeId "
           + "AND (m.mietende IS NULL OR m.mietende > :mietbeginn)")
    boolean existsOverlappingMieterOpenEnded(
        @Param("einheitId") Long einheitId,
        @Param("mietbeginn") LocalDate mietbeginn,
        @Param("excludeId") Long excludeId
    );

    /**
     * Check if overlapping lease periods exist for a unit (bounded new lease).
     * Used when the new tenant has a specific mietende.
     *
     * @param einheitId Unit ID
     * @param mietbeginn Lease start date
     * @param mietende Lease end date
     * @param excludeId ID to exclude (use -1 for new tenants)
     * @return true if an overlapping tenant exists
     */
    @Query("SELECT COUNT(m) > 0 FROM Mieter m JOIN MieterEinheit me ON me.mieterId = m.id "
           + "WHERE me.einheitId = :einheitId "
           + "AND m.id != :excludeId "
           + "AND (m.mietende IS NULL OR m.mietende > :mietbeginn) "
           + "AND m.mietbeginn < :mietende")
    boolean existsOverlappingMieterBounded(
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
    @Query("SELECT COUNT(m) > 0 FROM Mieter m JOIN MieterEinheit me ON me.mieterId = m.id "
           + "WHERE me.einheitId = :einheitId "
           + "AND m.id != :excludeId "
           + "AND m.mietende IS NULL")
    boolean existsOtherMieterWithoutMietende(
        @Param("einheitId") Long einheitId,
        @Param("excludeId") Long excludeId
    );

    /**
     * Find all tenants whose lease period overlaps the given period, regardless of unit
     * (Specs/Nebenkosten/Abrechnung.md, FR-1). These are the tenants a billing period covers.
     *
     * <p>Deliberately <b>without</b> a join on {@code MieterEinheit}: a tenant without a unit
     * still appears in the billing period — with zero days and a note — instead of vanishing
     * silently. Only the days determine the amount, and those come from the units.
     *
     * @param von Period start
     * @param bis Period end
     * @return Tenants active during the period, by name
     */
    @Query("SELECT m FROM Mieter m WHERE m.mietbeginn <= :bis "
           + "AND (m.mietende IS NULL OR m.mietende >= :von) "
           + "ORDER BY m.name, m.mietbeginn")
    List<Mieter> findByZeitraumOverlapping(
        @Param("von") LocalDate von,
        @Param("bis") LocalDate bis
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
    @Query("SELECT m FROM Mieter m JOIN MieterEinheit me ON me.mieterId = m.id "
           + "WHERE me.einheitId = :einheitId "
           + "AND m.mietbeginn <= :quartalEnde "
           + "AND (m.mietende IS NULL OR m.mietende >= :quartalBeginn) "
           + "ORDER BY m.mietbeginn")
    List<Mieter> findByEinheitIdAndQuartal(
        @Param("einheitId") Long einheitId,
        @Param("quartalBeginn") LocalDate quartalBeginn,
        @Param("quartalEnde") LocalDate quartalEnde
    );

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
    Optional<Mieter> findFirstById(Long id);
}
