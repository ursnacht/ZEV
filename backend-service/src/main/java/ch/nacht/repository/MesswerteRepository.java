package ch.nacht.repository;

import ch.nacht.entity.Einheit;
import ch.nacht.entity.EinheitTyp;
import ch.nacht.entity.Messwerte;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MesswerteRepository extends JpaRepository<Messwerte, Long> {

    @Query("SELECT DISTINCT m.zeit FROM Messwerte m WHERE m.zeit BETWEEN :dateFrom AND :dateTo ORDER BY m.zeit")
    List<LocalDateTime> findDistinctZeitBetween(@Param("dateFrom") LocalDateTime dateFrom, @Param("dateTo") LocalDateTime dateTo);

    @Query("SELECT m FROM Messwerte m WHERE m.zeit = :zeit AND m.einheit.typ = :typ")
    List<Messwerte> findByZeitAndEinheitTyp(@Param("zeit") LocalDateTime zeit, @Param("typ") EinheitTyp typ);

    @Query("SELECT m FROM Messwerte m WHERE m.einheit = :einheit AND m.zeit BETWEEN :dateFrom AND :dateTo ORDER BY m.zeit")
    List<Messwerte> findByEinheitAndZeitBetween(@Param("einheit") Einheit einheit, @Param("dateFrom") LocalDateTime dateFrom, @Param("dateTo") LocalDateTime dateTo);
}
