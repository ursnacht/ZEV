package ch.nacht.repository;

import ch.nacht.entity.Geraetezustand;
import ch.nacht.entity.Zustandsgroesse;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Zugriff auf die Zustandswerte der Geräte (Specs/Gerätezustand.md, FR-5).
 *
 * <p>Die Entity trägt {@code org_id} und {@code @Filter(orgFilter)}. <b>Lesende</b> Aufrufer
 * aktivieren ihn <b>org-explizit</b> ({@code enableOrgFilter(orgId)}): Der erste Nutzniesser ist
 * ein geplanter Job ohne Sicherheitskontext, die parameterlose Variante würfe dort
 * {@code NoOrganizationException}. Der <b>Ingest</b> aktiviert ihn gar nicht — wie schon für
 * {@code zaehler_rohdaten}: Er hat keinen Sicherheitskontext und leitet die {@code org_id} aus dem
 * MQTT-Topic ab.
 */
@Repository
public interface GeraetezustandRepository extends JpaRepository<Geraetezustand, Long> {

    /**
     * Lookup für den Upsert im Ingest — entspricht dem Unique-Schlüssel
     * {@code (einheit_id, zeit, groesse)}.
     *
     * <p>Trifft dieselbe MQTT-Nachricht zweimal ein, wird der vorhandene Wert überschrieben statt
     * ein zweiter angelegt.
     */
    Optional<Geraetezustand> findByEinheitIdAndZeitAndGroesse(
            Long einheitId, LocalDateTime zeit, Zustandsgroesse groesse);

    /**
     * Letzter Wert <b>vor</b> einem Zeitpunkt (FR-5.1) — die Abfrage, die eine Steuerung braucht:
     * „Wie voll war die Batterie am Ende des Intervalls?"
     *
     * <p><b>Echt kleiner</b>, nicht kleiner-gleich: Der Zeitpunkt ist das Intervall<b>ende</b> und
     * damit zugleich der Beginn des nächsten. Ein Wert genau darauf gehört zum folgenden Intervall.
     *
     * <p>Nutzt den Index {@code (einheit_id, groesse, zeit DESC)} — bei rund 525'000 Zeilen pro
     * Jahr und Gerät ist das der Unterschied zwischen wenigen Zeilen und der ganzen Zeitreihe.
     */
    Optional<Geraetezustand> findFirstByEinheitIdAndGroesseAndZeitLessThanOrderByZeitDesc(
            Long einheitId, Zustandsgroesse groesse, LocalDateTime vor);

    /**
     * Werte einer Zeitspanne, aufsteigend (FR-5.2) — für Verlauf und Diagramm.
     *
     * @param von Beginn (einschliesslich)
     * @param bis Ende (<b>ausschliesslich</b>) — dieselben Grenzen wie
     *            {@code SteuerentscheidRepository.findByZeitVonBetween}; sonst fiele ein Wert an
     *            der Tagesgrenze in zwei Abfragen
     */
    List<Geraetezustand> findByEinheitIdAndGroesseAndZeitGreaterThanEqualAndZeitLessThanOrderByZeitAsc(
            Long einheitId, Zustandsgroesse groesse, LocalDateTime von, LocalDateTime bis);

    /**
     * Je <b>15-Minuten-Intervall</b> der letzte Wert einer Grösse — Rückgabe {@code [beginn, wert]}.
     *
     * <p><b>Wofür:</b> Die Rückrechnung wertet bis zu 366 Tage aus. Die Zustandszeitreihe trägt
     * mehrere tausend Werte je Tag (der Zähler meldet alle 30 s); sie vollständig zu laden wäre
     * dort die eigentliche Laufzeit (NFR-1). Diese Abfrage liefert stattdessen 96 Zeilen je Tag —
     * dieselbe Grössenordnung wie die Messwerte.
     *
     * <p><b>Der letzte Wert im Intervall</b>, nicht der erste: Der Entscheid beschreibt das
     * abgeschlossene Intervall, also zählt der Zustand an dessen Ende. Dieselbe Regel wie im Job
     * ({@code socAmIntervallende}).
     *
     * <p><b>Native Query, weil JPQL kein {@code DISTINCT ON} kennt.</b> Damit greift der
     * Hibernate-{@code orgFilter} <b>nicht</b> — {@code org_id} steht deshalb ausdrücklich in der
     * Bedingung. Ohne sie läse ein Mandant die Zustände aller anderen mit.
     *
     * @param orgId     Mandant — <b>zwingend</b>, siehe oben
     * @param einheitId Einheit, deren Zustände gelesen werden
     * @param groesse   Name der Grösse, z.B. {@code SOC}
     * @param von       Beginn in Ortszeit (einschliesslich)
     * @param bis       Ende in Ortszeit (ausschliesslich)
     */
    @Query(value = """
        SELECT DISTINCT ON (bucket) bucket, wert FROM (
            SELECT date_trunc('hour', g.zeit)
                   + floor(extract(minute FROM g.zeit) / 15) * INTERVAL '15 minutes' AS bucket,
                   g.zeit AS zeit,
                   g.wert AS wert
            FROM zev.geraetezustand g
            WHERE g.org_id = :orgId
              AND g.einheit_id = :einheitId
              AND g.groesse = :groesse
              AND g.zeit >= :von
              AND g.zeit < :bis
        ) je_intervall
        ORDER BY bucket, zeit DESC
        """, nativeQuery = true)
    List<Object[]> letzterWertJeIntervall(
        @Param("orgId") Long orgId,
        @Param("einheitId") Long einheitId,
        @Param("groesse") String groesse,
        @Param("von") LocalDateTime von,
        @Param("bis") LocalDateTime bis
    );

}
