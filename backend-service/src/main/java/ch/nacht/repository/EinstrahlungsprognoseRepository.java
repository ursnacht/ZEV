package ch.nacht.repository;

import ch.nacht.entity.Einstrahlungsprognose;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Zugriff auf die Einstrahlungsprognose (Specs/Ladeplanung.md, FR-2 und FR-5).
 *
 * <p>Die Entity trägt {@code org_id} und {@code @Filter(orgFilter)}: Die Prognose hängt an
 * Standort und Ausrichtung der Anlage. Der Filter ist vor jeder Abfrage zu aktivieren — im Job
 * über {@code enableOrgFilter(orgId)}, weil dort kein Sicherheitskontext existiert.
 */
@Repository
public interface EinstrahlungsprognoseRepository extends JpaRepository<Einstrahlungsprognose, Long> {

    /**
     * Prognose einer Zeitspanne, aufsteigend.
     *
     * @param von Beginn in Ortszeit (einschliesslich)
     * @param bis Ende in Ortszeit (ausschliesslich)
     */
    @Query("SELECT e FROM Einstrahlungsprognose e WHERE e.zeit >= :von AND e.zeit < :bis "
            + "ORDER BY e.zeit")
    List<Einstrahlungsprognose> findByZeitBetween(
        @Param("von") LocalDateTime von,
        @Param("bis") LocalDateTime bis
    );

    /**
     * Schreibt einen Prognosewert; ein vorhandener wird überschrieben.
     *
     * <p><b>Warum ein Upsert:</b> Der Abruf läuft stündlich und liefert jedes Mal dieselben
     * Intervalle mit aktualisierten Werten. Ohne Upsert entstünden je Intervall 24 Datensätze pro
     * Tag, und keiner wüsste, welcher gilt.
     *
     * <p>Der Konfliktschlüssel muss <b>genau</b> dem Unique-Constraint
     * {@code uq_einstrahlungsprognose_org_zeit} entsprechen (V166, und als
     * {@code @UniqueConstraint} an der Entity).
     */
    @Modifying
    @Query(value = """
        INSERT INTO zev.einstrahlungsprognose (org_id, zeit, gti, abgerufen_am)
        VALUES (:orgId, :zeit, :gti, :abgerufenAm)
        ON CONFLICT (org_id, zeit)
        DO UPDATE SET gti          = EXCLUDED.gti,
                      abgerufen_am = EXCLUDED.abgerufen_am
        """, nativeQuery = true)
    void upsert(
        @Param("orgId") Long orgId,
        @Param("zeit") LocalDateTime zeit,
        @Param("gti") BigDecimal gti,
        @Param("abgerufenAm") LocalDateTime abgerufenAm
    );

    /**
     * Summe der vorhergesagten Einstrahlung je Intervall über einen Zeitraum — für den gelernten
     * Umrechnungsfaktor (FR-3). Rückgabe je Zeile: {@code [zeit, gti]}.
     *
     * <p>Nur Intervalle mit {@code gti > 0}: Nachtstunden trügen zum Faktor nichts bei und
     * verwässerten ihn nur.
     */
    @Query("SELECT e.zeit, e.gti FROM Einstrahlungsprognose e "
            + "WHERE e.zeit >= :von AND e.zeit < :bis AND e.gti > 0 ORDER BY e.zeit")
    List<Object[]> findGtiJeIntervall(
        @Param("von") LocalDateTime von,
        @Param("bis") LocalDateTime bis
    );
}
