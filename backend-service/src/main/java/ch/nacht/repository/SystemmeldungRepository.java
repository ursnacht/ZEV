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

    // --- Retention (mandantenübergreifend) ---

    @Modifying
    @Query("DELETE FROM Systemmeldung s WHERE s.erledigt = true AND s.erledigtAm < :cutoff")
    int deleteErledigtOlderThan(@Param("cutoff") LocalDateTime cutoff);
}
