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
     * Alle Forderungen, deren Zeitraum sich mit dem gegebenen um <b>mindestens einen Tag</b>
     * ueberschneidet (Specs/Debitorkontrolle.md, FR-1).
     *
     * <p>Die Bedingung ist {@code datum_von <= bis AND datum_bis >= von} — beide Grenzen
     * einschliesslich. Eine Forderung, die am ersten Tag des gewaehlten Zeitraums endet,
     * ueberschneidet ihn um genau einen Tag und wird angezeigt.
     *
     * <p><b>Warum nicht mehr {@code datum_von} im Zeitraum:</b> Eine Nebenkostenabrechnung laeuft
     * ueber ein ganzes Jahr. Ihre Forderung begann im Januar und war damit in jedem Quartal
     * ausser dem ersten unsichtbar — obwohl sie offen ist und den ganzen Zeitraum betrifft. Wer im
     * Q3 nach offenen Forderungen sah, uebersah sie.
     *
     * <p>Hibernate org filter must be enabled before calling this method.
     *
     * @param von Start des gewaehlten Zeitraums (einschliesslich)
     * @param bis Ende des gewaehlten Zeitraums (einschliesslich)
     * @return Forderungen mit Ueberschneidung, nach {@code datum_von} sortiert
     */
    @Query("SELECT d FROM Debitor d WHERE d.datumVon <= :bis AND d.datumBis >= :von "
            + "ORDER BY d.datumVon, d.mieterId")
    List<Debitor> findByZeitraumUeberschneidung(
        @Param("von") LocalDate von,
        @Param("bis") LocalDate bis
    );

    /**
     * Upsert a debitor entry by unique key (mieter_id, datum_von, herkunft, org_id).
     * Updates betrag and datum_bis only if zahldatum is not yet set.
     *
     * <p>Die {@code herkunft} gehoert in den Konfliktschluessel: Eine NK-Jahresabrechnung und die
     * ZEV-Quartalsrechnung Q1 haben denselben {@code datum_von}, und ohne sie wuerde die eine
     * Buchung die andere ueberschreiben. Der Schluessel muss <b>genau</b> dem Unique-Constraint
     * {@code uq_debitor_mieter_von_herkunft_org} entsprechen (V126), sonst scheitert jeder Aufruf
     * mit „no unique or exclusion constraint matching the ON CONFLICT specification".
     *
     * @param mieterId FK to mieter
     * @param betrag   Invoice amount in CHF
     * @param datumVon Start of billing period
     * @param datumBis End of billing period
     * @param orgId    Organisation ID
     * @param herkunft {@code ZEV} oder {@code NK} — der Name des Enum-Werts, weil eine native
     *                 Abfrage keinen Enum-Konverter durchlaeuft
     */
    @Modifying
    @Query(value = """
        INSERT INTO zev.debitor (mieter_id, betrag, datum_von, datum_bis, zahldatum, org_id, herkunft)
        VALUES (:mieterId, :betrag, :datumVon, :datumBis, NULL, :orgId, :herkunft)
        ON CONFLICT (mieter_id, datum_von, herkunft, org_id)
        DO UPDATE SET betrag = EXCLUDED.betrag, datum_bis = EXCLUDED.datum_bis
        WHERE zev.debitor.zahldatum IS NULL
        """, nativeQuery = true)
    void upsert(
        @Param("mieterId") Long mieterId,
        @Param("betrag") BigDecimal betrag,
        @Param("datumVon") LocalDate datumVon,
        @Param("datumBis") LocalDate datumBis,
        @Param("orgId") Long orgId,
        @Param("herkunft") String herkunft
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
