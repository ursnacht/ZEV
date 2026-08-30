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

    /** Für Upsert aus der MQTT-Aggregation (ein Messwert je Einheit + Zeitpunkt). */
    Optional<Messwerte> findByEinheitAndZeit(Einheit einheit, LocalDateTime zeit);

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

    /**
     * Anzahl der 15-Min-Zeitpunkte, für die ein Messwert dieses Einheiten-Typs vorliegt.
     *
     * <p>Gebraucht für die <b>gemessenen</b> Kennzahlen: Der Netzbezug der BEZUG-Einheit taugt nur
     * dann als Nenner-Grundlage, wenn er den Monat lückenlos abdeckt. Fehlen einzelne Intervalle
     * (Bilanz-Lücken, FR-2.5), ist die Summe zu klein und der daraus gerechnete Autarkiegrad zu
     * optimistisch – die tages- und einheitengenaue Vollständigkeitsprüfung sieht solche Lücken
     * innerhalb eines Tages nicht.
     */
    @Query("SELECT COUNT(DISTINCT m.zeit) FROM Messwerte m WHERE m.einheit.typ = :typ AND m.zeit >= :dateFrom AND m.zeit < :dateTo")
    long countDistinctZeitByEinheitTypAndZeitBetween(@Param("typ") EinheitTyp typ, @Param("dateFrom") LocalDateTime dateFrom, @Param("dateTo") LocalDateTime dateTo);

    /**
     * Aggregiert je 15-Min-Intervall (zeit) die Beträge der vier Bilanz-Komponenten für die
     * Batterie-Kennzahlen (Spec Statistik-Kennzahlen.md, Stufe 2). Rückgabe je Zeile:
     * {@code [zeit, produktion, verbrauch, bezug, ruecklieferung]} (alle als Betrag/positiv).
     * JPQL → der Hibernate-orgFilter greift (Mandanten-Isolation).
     */
    @Query("SELECT m.zeit, "
            + "COALESCE(SUM(CASE WHEN m.einheit.typ = ch.nacht.entity.EinheitTyp.PRODUCER THEN ABS(m.total) ELSE 0 END), 0), "
            + "COALESCE(SUM(CASE WHEN m.einheit.typ = ch.nacht.entity.EinheitTyp.CONSUMER THEN m.total ELSE 0 END), 0), "
            + "COALESCE(SUM(CASE WHEN m.einheit.typ = ch.nacht.entity.EinheitTyp.BEZUG THEN m.total ELSE 0 END), 0), "
            + "COALESCE(SUM(CASE WHEN m.einheit.typ = ch.nacht.entity.EinheitTyp.RUECKLIEFERUNG THEN ABS(m.total) ELSE 0 END), 0) "
            + "FROM Messwerte m WHERE m.zeit >= :dateFrom AND m.zeit < :dateTo GROUP BY m.zeit ORDER BY m.zeit")
    List<Object[]> sumBilanzKomponentenPerZeitBetween(@Param("dateFrom") LocalDateTime dateFrom, @Param("dateTo") LocalDateTime dateTo);

    @Query("SELECT m FROM Messwerte m WHERE CAST(m.zeit AS LocalDate) = :date")
    List<Messwerte> findByDate(@Param("date") LocalDate date);

    // Summen pro Einheit für Statistik
    @Query("SELECT COALESCE(SUM(m.total), 0) FROM Messwerte m WHERE m.einheit = :einheit AND m.zeit >= :dateFrom AND m.zeit < :dateTo")
    Double sumTotalByEinheitAndZeitBetween(@Param("einheit") Einheit einheit, @Param("dateFrom") LocalDateTime dateFrom, @Param("dateTo") LocalDateTime dateTo);

    @Query("SELECT COALESCE(SUM(m.zev), 0) FROM Messwerte m WHERE m.einheit = :einheit AND m.zeit >= :dateFrom AND m.zeit < :dateTo")
    Double sumZevByEinheitAndZeitBetween(@Param("einheit") Einheit einheit, @Param("dateFrom") LocalDateTime dateFrom, @Param("dateTo") LocalDateTime dateTo);

    @Query("SELECT COALESCE(SUM(m.zevCalculated), 0) FROM Messwerte m WHERE m.einheit = :einheit AND m.zeit >= :dateFrom AND m.zeit < :dateTo")
    Double sumZevCalculatedByEinheitAndZeitBetween(@Param("einheit") Einheit einheit, @Param("dateFrom") LocalDateTime dateFrom, @Param("dateTo") LocalDateTime dateTo);
}
