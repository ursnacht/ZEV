package ch.nacht.repository;

import ch.nacht.entity.Preiszeitreihe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository der Preiszeitreihe (Specs/Preiszeitreihe.md).
 *
 * <p>Die Entity traegt bewusst <b>kein</b> {@code org_id} und keinen {@code @Filter} — die Preise
 * gelten mandantenuebergreifend (FR-2). Ein {@code findById} kommt hier deshalb nicht vor: Die
 * ArchUnit-Regel {@code servicesMustNotUseFindByIdOnFilteredRepositories} fuehrt nur die
 * ungefilterten Repositories in ihrer Whitelist, und die Zugriffe dieses Features brauchen es nicht
 * (Bereichsabfrage und Upsert ueber {@code zeit_von}).
 */
@Repository
public interface PreiszeitreiheRepository extends JpaRepository<Preiszeitreihe, Long> {

    /**
     * Alle Werte einer Zeitspanne, aufsteigend.
     *
     * <p>Die obere Grenze ist <b>exklusiv</b>: Der Aufrufer uebergibt den Beginn des Folgetags in
     * UTC. Waere sie einschliesslich, fiele der Wert an der Tagesgrenze in zwei Abfragen — oder,
     * bei einer Korrektur um eine Viertelstunde, in keine.
     *
     * @param von Beginn der Spanne in UTC (einschliesslich)
     * @param bis Ende der Spanne in UTC (ausschliesslich)
     * @return Werte nach {@code zeit_von} sortiert
     */
    @Query("SELECT p FROM Preiszeitreihe p WHERE p.zeitVon >= :von AND p.zeitVon < :bis "
            + "ORDER BY p.zeitVon")
    List<Preiszeitreihe> findByZeitraum(
        @Param("von") LocalDateTime von,
        @Param("bis") LocalDateTime bis
    );

    /**
     * Schreibt einen Viertelstundenwert und ueberschreibt einen bestehenden desselben Beginns.
     *
     * <p>Der Konfliktschluessel muss <b>genau</b> dem Unique-Constraint
     * {@code uq_preiszeitreihe_zeit_von} (V129) entsprechen, sonst scheitert jeder Aufruf mit
     * „no unique or exclusion constraint matching the ON CONFLICT specification".
     *
     * <p>Damit ist ein erneuter Abruf desselben Tages idempotent, und eine Preiskorrektur der
     * Quelle ersetzt den alten Wert. Der Aufrufer schreibt in aufsteigender {@code zeit_von}-
     * Reihenfolge (siehe {@code PreiszeitreiheService}): Job und Schaltflaeche nehmen die Sperren
     * dann in derselben Reihenfolge und koennen sich nicht verklemmen.
     *
     * @param zeitVon     Intervallbeginn in UTC
     * @param zeitBis     Intervallende in UTC
     * @param preis       Einspeisepreis in CHF/kWh
     * @param publikation Publikationszeitpunkt der Quelle in UTC, darf {@code null} sein
     */
    @Modifying
    @Query(value = """
        INSERT INTO zev.preiszeitreihe (zeit_von, zeit_bis, preis, publikation, aktualisiert_am)
        VALUES (:zeitVon, :zeitBis, :preis, :publikation, now())
        ON CONFLICT (zeit_von)
        DO UPDATE SET zeit_bis = EXCLUDED.zeit_bis,
                      preis = EXCLUDED.preis,
                      publikation = EXCLUDED.publikation,
                      aktualisiert_am = now()
        """, nativeQuery = true)
    void upsert(
        @Param("zeitVon") LocalDateTime zeitVon,
        @Param("zeitBis") LocalDateTime zeitBis,
        @Param("preis") BigDecimal preis,
        @Param("publikation") LocalDateTime publikation
    );
}
