package ch.nacht.repository;

import ch.nacht.entity.Tarif;
import ch.nacht.entity.TarifTyp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

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
     * @return List of overlapping tariffs ordered by validity start date, then by name — mehrere
     *         gleichzeitig gültige Tarife (z.B. Energielieferung und Netznutzung) erscheinen so
     *         auf jeder Rechnung in derselben Reihenfolge
     */
    @Query("SELECT t FROM Tarif t WHERE t.tariftyp = :typ " +
           "AND t.gueltigVon <= :bis AND t.gueltigBis >= :von " +
           "ORDER BY t.gueltigVon, t.bezeichnung")
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
     * Gibt es einen überlappenden Tarif desselben Typs mit <b>derselben Bezeichnung</b>? Für die
     * Typen, bei denen mehrere Tarife nebeneinander gelten dürfen (ZEV, VNB, Grundgebühr) —
     * verglichen ohne Gross-/Kleinschreibung und ohne Leerzeichen am Rand.
     *
     * @param excludeId ID to exclude (use -1 for new tariffs)
     */
    @Query("SELECT COUNT(t) > 0 FROM Tarif t WHERE t.tariftyp = :typ " +
           "AND LOWER(TRIM(t.bezeichnung)) = LOWER(TRIM(:bezeichnung)) " +
           "AND t.id != :excludeId " +
           "AND t.gueltigVon <= :bis AND t.gueltigBis >= :von")
    boolean existsOverlappingTarifMitBezeichnung(
        @Param("typ") TarifTyp typ,
        @Param("bezeichnung") String bezeichnung,
        @Param("von") LocalDate von,
        @Param("bis") LocalDate bis,
        @Param("excludeId") Long excludeId
    );

    /**
     * Alle Tarife in der Reihenfolge der Rechnung (Specs/Tarifverwaltung.md, FR-4).
     */
    List<Tarif> findAllByOrderByReihenfolgeAscIdAsc();

    /** Hoechste Reihenfolge eines Typs; {@code null}, wenn es keinen Tarif dieses Typs gibt. */
    @Query("SELECT MAX(t.reihenfolge) FROM Tarif t WHERE t.tariftyp = :typ")
    Integer findMaxReihenfolgeByTariftyp(@Param("typ") TarifTyp typ);

    /** Hoechste Reihenfolge ueberhaupt; {@code null} ohne Tarife. */
    @Query("SELECT MAX(t.reihenfolge) FROM Tarif t")
    Integer findMaxReihenfolge();

    /**
     * Schiebt alle Tarife hinter {@code ab} um eins nach hinten, um Platz fuer einen neuen zu
     * schaffen. Bulk-Update: Der Hibernate-Filter greift hier NICHT, deshalb die org_id
     * ausdruecklich in der Bedingung.
     */
    @Modifying
    @Query("UPDATE Tarif t SET t.reihenfolge = t.reihenfolge + 1 WHERE t.orgId = :orgId AND t.reihenfolge > :ab")
    int verschiebeReihenfolgeAb(@Param("orgId") Long orgId, @Param("ab") int ab);

    /**
     * Verschiebt die Plaetze {@code von..bis} (inklusive) um {@code delta} - Platz machen beim
     * Verschieben eines Tarifs. Bulk-Update: org_id ausdruecklich, der Hibernate-Filter greift hier
     * nicht.
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Tarif t SET t.reihenfolge = t.reihenfolge + :delta " +
           "WHERE t.orgId = :orgId AND t.reihenfolge BETWEEN :von AND :bis")
    int verschiebeReihenfolgeBereich(@Param("orgId") Long orgId, @Param("von") int von,
                                     @Param("bis") int bis, @Param("delta") int delta);

    /**
     * Setzt den Platz eines einzelnen Tarifs - als Update der einen Spalte statt ueber die Entity.
     * Sonst prueft Hibernate beim Commit die Bean Validation des ganzen Tarifs, und ein Tarif mit
     * einem Altbestand-Wert ausserhalb der heutigen Regeln (auf Hene: Preis 0) liesse sich nicht
     * mehr umsortieren.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Tarif t SET t.reihenfolge = :reihenfolge WHERE t.id = :id AND t.orgId = :orgId")
    int setzeReihenfolge(@Param("orgId") Long orgId, @Param("id") Long id,
                         @Param("reihenfolge") int reihenfolge);

    /**
     * Find all tariffs ordered by type and validity start date (descending).
     *
     * @return List of all tariffs
     */
    List<Tarif> findAllByOrderByTariftypAscGueltigVonDesc();

    /**
     * Find all distinct years that have at least one tariff.
     *
     * @return List of years with tariffs
     */
    @Query("SELECT DISTINCT YEAR(t.gueltigVon) FROM Tarif t " +
           "UNION SELECT DISTINCT YEAR(t.gueltigBis) FROM Tarif t " +
           "ORDER BY 1")
    List<Integer> findDistinctYears();

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
    Optional<Tarif> findFirstById(Long id);
}
