package ch.nacht.repository;

import ch.nacht.entity.ZaehlerRohdaten;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ZaehlerRohdatenRepository extends JpaRepository<ZaehlerRohdaten, Long> {

    /** Für Upsert: bestehender Rohdatensatz zu (Einheit, Zeit). */
    Optional<ZaehlerRohdaten> findByEinheitIdAndZeit(Long einheitId, LocalDateTime zeit);

    /** Letzter (jüngster) Zählerstand mit {@code zeit <= bis} – Referenz-/Intervallend-Stand. */
    Optional<ZaehlerRohdaten> findFirstByEinheitIdAndZeitLessThanEqualOrderByZeitDesc(Long einheitId, LocalDateTime bis);

    /** Einheiten mit noch nicht verarbeiteten Rohdaten. */
    @Query("SELECT DISTINCT r.einheitId FROM ZaehlerRohdaten r WHERE r.verarbeitet = false")
    List<Long> findEinheitIdsWithUnverarbeitet();

    /** Früheste noch nicht verarbeitete Messung einer Einheit (für Catch-up). */
    Optional<ZaehlerRohdaten> findFirstByEinheitIdAndVerarbeitetFalseOrderByZeitAsc(Long einheitId);

    /** Gibt es im Intervall (start, ende] eine neue Messung? */
    boolean existsByEinheitIdAndZeitGreaterThanAndZeitLessThanEqual(Long einheitId, LocalDateTime start, LocalDateTime ende);

    /** Markiert alle Rohdaten einer Einheit bis {@code bis} als verarbeitet. */
    @Modifying
    @Query("UPDATE ZaehlerRohdaten r SET r.verarbeitet = true, r.verarbeitetAm = :jetzt "
            + "WHERE r.einheitId = :einheitId AND r.zeit <= :bis AND r.verarbeitet = false")
    int markVerarbeitet(@Param("einheitId") Long einheitId,
                        @Param("bis") LocalDateTime bis,
                        @Param("jetzt") LocalDateTime jetzt);
}
