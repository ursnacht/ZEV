package ch.nacht.repository;

import ch.nacht.entity.Debitor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Debitor entities.
 */
@Repository
public interface DebitorRepository extends JpaRepository<Debitor, Long> {

    /**
     * Find all debitor entries with datum_von within the given range.
     * Hibernate org filter must be enabled before calling this method.
     *
     * @param von Start date (inclusive)
     * @param bis End date (inclusive)
     * @return List of debitor entries ordered by datum_von
     */
    @Query("SELECT d FROM Debitor d WHERE d.datumVon >= :von AND d.datumVon <= :bis ORDER BY d.datumVon, d.mieterId")
    List<Debitor> findByDatumVonBetween(
        @Param("von") LocalDate von,
        @Param("bis") LocalDate bis
    );

    /**
     * Upsert a debitor entry by unique key (mieter_id, datum_von, org_id).
     * Updates betrag and datum_bis only if zahldatum is not yet set.
     *
     * @param mieterId FK to mieter
     * @param betrag   Invoice amount in CHF
     * @param datumVon Start of billing period
     * @param datumBis End of billing period
     * @param orgId    Organisation ID
     */
    @Modifying
    @Query(value = """
        INSERT INTO zev.debitor (mieter_id, betrag, datum_von, datum_bis, zahldatum, org_id)
        VALUES (:mieterId, :betrag, :datumVon, :datumBis, NULL, :orgId)
        ON CONFLICT (mieter_id, datum_von, org_id)
        DO UPDATE SET betrag = EXCLUDED.betrag, datum_bis = EXCLUDED.datum_bis
        WHERE zev.debitor.zahldatum IS NULL
        """, nativeQuery = true)
    void upsert(
        @Param("mieterId") Long mieterId,
        @Param("betrag") BigDecimal betrag,
        @Param("datumVon") LocalDate datumVon,
        @Param("datumBis") LocalDate datumBis,
        @Param("orgId") Long orgId
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
    Optional<Debitor> findFirstById(Long id);
}
