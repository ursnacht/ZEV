package ch.nacht.repository;

import ch.nacht.entity.MeldungLevel;
import ch.nacht.entity.Systemmeldung;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Systemmeldung entities.
 *
 * <p>Die Listen-Abfragen laufen im Request-Scope (Hibernate-{@code orgFilter} aktiv, FR-1.11);
 * die Dedup-/Auto-Resolve-Methoden sind <b>org-explizit</b> für den Hintergrund-Lauf ohne
 * Request-Kontext (MQTT-Auto-Lauf). {@code Slice} liefert {@code hasNext()} → {@code hatMehr}
 * ohne teure Count-Abfrage.
 */
@Repository
public interface SystemmeldungRepository extends JpaRepository<Systemmeldung, Long> {

    // --- Liste (request-scoped, orgFilter aktiv); Sortierung via Pageable ---

    @Query("SELECT s FROM Systemmeldung s WHERE "
            + "(:erledigt IS NULL OR s.erledigt = :erledigt) "
            + "AND (:kategorie IS NULL OR s.kategorie = :kategorie) "
            + "AND (:level IS NULL OR s.level = :level)")
    Slice<Systemmeldung> findByFilter(@Param("erledigt") Boolean erledigt,
                                      @Param("kategorie") String kategorie,
                                      @Param("level") MeldungLevel level,
                                      Pageable pageable);

    // Sortierung nach Schweregrad (ERROR > WARN > INFO) – aufsteigend
    @Query("SELECT s FROM Systemmeldung s WHERE "
            + "(:erledigt IS NULL OR s.erledigt = :erledigt) "
            + "AND (:kategorie IS NULL OR s.kategorie = :kategorie) "
            + "AND (:level IS NULL OR s.level = :level) "
            + "ORDER BY CASE WHEN s.level = ch.nacht.entity.MeldungLevel.ERROR THEN 3 "
            + "WHEN s.level = ch.nacht.entity.MeldungLevel.WARN THEN 2 ELSE 1 END ASC, s.zuletztAufgetreten DESC")
    Slice<Systemmeldung> findByFilterOrderByLevelAsc(@Param("erledigt") Boolean erledigt,
                                                     @Param("kategorie") String kategorie,
                                                     @Param("level") MeldungLevel level,
                                                     Pageable pageable);

    // Sortierung nach Schweregrad (ERROR > WARN > INFO) – absteigend
    @Query("SELECT s FROM Systemmeldung s WHERE "
            + "(:erledigt IS NULL OR s.erledigt = :erledigt) "
            + "AND (:kategorie IS NULL OR s.kategorie = :kategorie) "
            + "AND (:level IS NULL OR s.level = :level) "
            + "ORDER BY CASE WHEN s.level = ch.nacht.entity.MeldungLevel.ERROR THEN 3 "
            + "WHEN s.level = ch.nacht.entity.MeldungLevel.WARN THEN 2 ELSE 1 END DESC, s.zuletztAufgetreten DESC")
    Slice<Systemmeldung> findByFilterOrderByLevelDesc(@Param("erledigt") Boolean erledigt,
                                                      @Param("kategorie") String kategorie,
                                                      @Param("level") MeldungLevel level,
                                                      Pageable pageable);

    /** Vorhandene Kategorien des Mandanten (für den Kategorie-Filter). */
    @Query("SELECT DISTINCT s.kategorie FROM Systemmeldung s ORDER BY s.kategorie")
    List<String> findDistinctKategorien();

    // --- Org-explizit (Hintergrund-Lauf ohne Request-Kontext) ---

    /** Dedup-Lookup: offener Eintrag desselben Keys im Mandanten. */
    Optional<Systemmeldung> findByOrgIdAndMeldungKeyAndErledigtFalse(Long orgId, String meldungKey);

    /** Prüft, ob ein offener Eintrag desselben Keys im Mandanten existiert (Reopen-Konflikt). */
    boolean existsByOrgIdAndMeldungKeyAndErledigtFalse(Long orgId, String meldungKey);

    /** Auto-Resolve: offene Einträge des Keys im Mandanten automatisch auf erledigt setzen. */
    @Modifying
    @Query("UPDATE Systemmeldung s SET s.erledigt = true, s.erledigtAm = :jetzt, s.erledigtAutomatisch = true "
            + "WHERE s.orgId = :orgId AND s.meldungKey = :meldungKey AND s.erledigt = false")
    int autoResolve(@Param("orgId") Long orgId,
                    @Param("meldungKey") String meldungKey,
                    @Param("jetzt") LocalDateTime jetzt);

    /**
     * Löscht alle erledigten Meldungen <b>eines Mandanten</b> (benutzerausgelöste Aufräumaktion).
     *
     * <p>{@code org_id} steht bewusst <b>explizit</b> in der Bedingung: Hibernate-{@code @Filter}
     * greift bei Bulk-{@code DELETE}-JPQL <b>nicht</b>, ein Verlass auf {@code orgFilter} würde
     * hier mandantenübergreifend löschen.
     */
    @Modifying
    @Query("DELETE FROM Systemmeldung s WHERE s.erledigt = true AND s.orgId = :orgId")
    int deleteErledigtByOrgId(@Param("orgId") Long orgId);

    // --- Retention (mandantenübergreifend) ---

    @Modifying
    @Query("DELETE FROM Systemmeldung s WHERE s.erledigt = true AND s.erledigtAm < :cutoff")
    int deleteErledigtOlderThan(@Param("cutoff") LocalDateTime cutoff);

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
    Optional<Systemmeldung> findFirstById(Long id);
}
