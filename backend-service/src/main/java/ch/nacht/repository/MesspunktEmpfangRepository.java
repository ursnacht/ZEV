package ch.nacht.repository;

import ch.nacht.entity.MesspunktEmpfang;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Zugriff auf den Empfang je Messpunkt (Specs/Messpunkt-Empfang.md).
 *
 * <p>Nur {@code MesspunktEmpfangService} greift hierauf zu (ArchUnit, NFR-2). Die beiden
 * schreibenden Abfragen sind <b>native</b> und laufen damit am Hibernate-{@code orgFilter} vorbei:
 * Der Upsert nennt {@code org_id} ausdrücklich, das Aufräumen läuft bewusst über alle Mandanten.
 */
@Repository
public interface MesspunktEmpfangRepository extends JpaRepository<MesspunktEmpfang, Long> {

    /**
     * Hält eine Meldung fest: legt die Zeile an oder aktualisiert sie (FR-1.1).
     *
     * <p><b>Kein Lesen vor dem Schreiben</b> — {@code ON CONFLICT} entscheidet in der Datenbank.
     * Bei einem Konflikt verfällt der gezogene Sequenzwert; das ist hinnehmbar. Überschrieben
     * werden Zeitpunkt, Quelle, Stände, Ergebnis, Grund und Payload: Die Zeile zeigt immer genau die
     * letzte Meldung (FR-1.4, FR-1.9). {@code erster_empfang} bleibt, der Zähler steigt um 1.
     */
    @Modifying
    @Query(value = """
        INSERT INTO zev.messpunkt_empfang (id, org_id, messpunkt, quelle, erster_empfang,
            letzter_empfang, anzahl_meldungen, zaehlerstand_bezug, zaehlerstand_einspeisung,
            ergebnis, grund, letzte_meldung)
        VALUES (nextval('zev.messpunkt_empfang_seq'), :orgId, :messpunkt, :quelle, :zeitpunkt,
            :zeitpunkt, 1, :bezug, :einspeisung, :ergebnis, :grund, :letzteMeldung)
        ON CONFLICT (org_id, messpunkt) DO UPDATE SET
            quelle = EXCLUDED.quelle,
            letzter_empfang = EXCLUDED.letzter_empfang,
            anzahl_meldungen = zev.messpunkt_empfang.anzahl_meldungen + 1,
            zaehlerstand_bezug = EXCLUDED.zaehlerstand_bezug,
            zaehlerstand_einspeisung = EXCLUDED.zaehlerstand_einspeisung,
            ergebnis = EXCLUDED.ergebnis,
            grund = EXCLUDED.grund,
            letzte_meldung = EXCLUDED.letzte_meldung
        """, nativeQuery = true)
    void upsert(@Param("orgId") Long orgId,
                @Param("messpunkt") String messpunkt,
                @Param("quelle") String quelle,
                @Param("zeitpunkt") Instant zeitpunkt,
                @Param("bezug") BigDecimal bezug,
                @Param("einspeisung") BigDecimal einspeisung,
                @Param("ergebnis") String ergebnis,
                @Param("grund") String grund,
                @Param("letzteMeldung") String letzteMeldung);

    /** Alle Zeilen des Mandanten (gefiltert über den {@code orgFilter} des Aufrufers). */
    List<MesspunktEmpfang> findAllByOrderByLetzterEmpfangDesc();

    /**
     * Einzelzugriff <b>unter dem Mandantenfilter</b> — anders als {@code findById}, das als
     * Primärschlüssel-Zugriff am Filter vorbeiläuft (Muster {@code TarifRepository.findFirstById}).
     *
     * @param id Technischer Schlüssel
     * @return Zeile des eigenen Mandanten, sonst leer
     */
    Optional<MesspunktEmpfang> findFirstById(Long id);

    /**
     * Löscht Zeilen ohne Einheit, deren letzter Empfang vor der Grenze liegt (FR-3.2).
     *
     * <p><b>Über alle Mandanten</b>: Der Job hat keinen Sicherheitskontext. „Ohne Einheit“ heisst:
     * Keine Einheit desselben Mandanten ausser einer Ladestation trägt den Messpunkt — dieselbe
     * Regel wie im Ingest. Zeilen einer {@code org_id} ohne Mandanten haben nie eine Einheit und
     * fallen hier ebenfalls weg.
     *
     * @param grenze Zeilen mit {@code letzter_empfang} <b>strikt</b> davor werden gelöscht
     * @return Anzahl gelöschter Zeilen
     */
    @Modifying
    @Query(value = """
        DELETE FROM zev.messpunkt_empfang m
        WHERE m.letzter_empfang < :grenze
          AND NOT EXISTS (
              SELECT 1 FROM zev.einheit e
              WHERE e.org_id = m.org_id
                AND e.messpunkt = m.messpunkt
                AND e.typ <> 'LADESTATION')
        """, nativeQuery = true)
    int loescheVerwaisteVor(@Param("grenze") Instant grenze);
}
