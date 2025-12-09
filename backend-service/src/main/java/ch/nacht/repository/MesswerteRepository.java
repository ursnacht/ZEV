package ch.nacht.repository;

import ch.nacht.entity.Einheit;
import ch.nacht.entity.EinheitTyp;
import ch.nacht.entity.Messwerte;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MesswerteRepository extends JpaRepository<Messwerte, Long> {

    @Query("SELECT DISTINCT m.zeit FROM Messwerte m WHERE m.zeit BETWEEN :dateFrom AND :dateTo ORDER BY m.zeit")
    List<LocalDateTime> findDistinctZeitBetween(@Param("dateFrom") LocalDateTime dateFrom, @Param("dateTo") LocalDateTime dateTo);

    @Query("SELECT m FROM Messwerte m WHERE m.zeit = :zeit AND m.einheit.typ = :typ")
    List<Messwerte> findByZeitAndEinheitTyp(@Param("zeit") LocalDateTime zeit, @Param("typ") EinheitTyp typ);

    @Query("SELECT m FROM Messwerte m WHERE m.einheit = :einheit AND m.zeit BETWEEN :dateFrom AND :dateTo ORDER BY m.zeit")
    List<Messwerte> findByEinheitAndZeitBetween(@Param("einheit") Einheit einheit, @Param("dateFrom") LocalDateTime dateFrom, @Param("dateTo") LocalDateTime dateTo);

    // Statistik-Abfragen
    @Query("SELECT MAX(m.zeit) FROM Messwerte m")
    Optional<LocalDateTime> findMaxZeit();

    @Query("SELECT DISTINCT CAST(m.zeit AS LocalDate) FROM Messwerte m WHERE m.zeit >= :dateFrom AND m.zeit < :dateTo ORDER BY CAST(m.zeit AS LocalDate)")
    List<LocalDate> findDistinctDatesInRange(@Param("dateFrom") LocalDateTime dateFrom, @Param("dateTo") LocalDateTime dateTo);

    @Query("SELECT COALESCE(SUM(m.total), 0) FROM Messwerte m WHERE m.einheit.typ = :typ AND m.zeit >= :dateFrom AND m.zeit < :dateTo")
    Double sumTotalByEinheitTypAndZeitBetween(@Param("typ") EinheitTyp typ, @Param("dateFrom") LocalDateTime dateFrom, @Param("dateTo") LocalDateTime dateTo);

    @Query("SELECT COALESCE(SUM(m.zev), 0) FROM Messwerte m WHERE m.einheit.typ = :typ AND m.zeit >= :dateFrom AND m.zeit < :dateTo")
    Double sumZevByEinheitTypAndZeitBetween(@Param("typ") EinheitTyp typ, @Param("dateFrom") LocalDateTime dateFrom, @Param("dateTo") LocalDateTime dateTo);

    @Query("SELECT COALESCE(SUM(m.zevCalculated), 0) FROM Messwerte m WHERE m.einheit.typ = :typ AND m.zeit >= :dateFrom AND m.zeit < :dateTo")
    Double sumZevCalculatedByEinheitTypAndZeitBetween(@Param("typ") EinheitTyp typ, @Param("dateFrom") LocalDateTime dateFrom, @Param("dateTo") LocalDateTime dateTo);

    @Query("SELECT DISTINCT m.einheit FROM Messwerte m WHERE m.zeit >= :dateFrom AND m.zeit < :dateTo")
    List<Einheit> findDistinctEinheitenInRange(@Param("dateFrom") LocalDateTime dateFrom, @Param("dateTo") LocalDateTime dateTo);

    @Query("SELECT m FROM Messwerte m WHERE CAST(m.zeit AS LocalDate) = :date")
    List<Messwerte> findByDate(@Param("date") LocalDate date);
}
