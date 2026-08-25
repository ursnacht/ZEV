package ch.nacht.repository;

import ch.nacht.entity.Einheit;
import ch.nacht.entity.EinheitTyp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EinheitRepository extends JpaRepository<Einheit, Long> {
    List<Einheit> findAllByOrderByNameAsc();

    /**
     * Auflösung der Einheiten über (org_id, messpunkt) – explizite Mandantenprüfung
     * für den MQTT-Ingest (ohne Request-Scope / orgFilter). Mehrere Treffer sind zulässig:
     * die Bilanz-Typen BEZUG/RUECKLIEFERUNG dürfen denselben Messpunkt teilen, die Meldung
     * wird dann beim Ingest auf die Einheiten aufgeteilt (Register-Projektion).
     */
    List<Einheit> findAllByOrgIdAndMesspunkt(Long orgId, String messpunkt);

    /** Erste Einheit eines Typs, z.B. die Bilanz-Einheit (max. eine je Mandant; orgFilter muss aktiv sein). */
    Optional<Einheit> findFirstByTyp(EinheitTyp typ);

    /** Eindeutigkeit der Bilanz-Typen je Mandant (orgFilter muss aktiv sein). */
    boolean existsByTyp(EinheitTyp typ);

    /** Eindeutigkeit der Bilanz-Typen je Mandant beim Update (orgFilter muss aktiv sein). */
    boolean existsByTypAndIdNot(EinheitTyp typ, Long id);

    /**
     * Anzahl Einheiten eines Typs, die als Wohnung an der Nebenkostenabrechnung teilnehmen
     * (orgFilter muss aktiv sein).
     *
     * <p>Belegt dort die „Anzahl Wohnungen" vor ({@code CONSUMER}). Massgebend ist allein das
     * Kennzeichen {@code nebenkostenRelevant} — nicht die Mieterzuordnung: Ein Messpunkt wie
     * Allgemeinstrom kann durchaus einem Mieter zugeordnet sein, nämlich dem Eigentümer.
     *
     * <p>Bewusst <b>ohne</b> Zeitraum: Eine Wohnung, die im Abrechnungszeitraum leer stand, ist
     * trotzdem eine Wohnung und gehört in den Nenner — genau darauf beruht der Leerstandsanteil
     * (FR-2).
     *
     * <p>Der Wert bleibt ein <b>Vorschlag</b>: Der Nenner wird an der Abrechnung erfasst und ist
     * überschreibbar.
     *
     * @param typ Einheitentyp
     * @return Anzahl teilnehmender Einheiten dieses Typs
     */
    long countByTypAndNebenkostenRelevantTrue(EinheitTyp typ);

    /**
     * Prüft, ob eine <b>andere</b> Ladestations-Einheit dieselbe RFID (`messpunkt`) trägt.
     * Nur für {@code LADESTATION}: Die Bilanz-Typen dürfen sich einen Messpunkt teilen, deshalb
     * ist die Eindeutigkeit auf diesen Typ eingeschränkt (orgFilter muss aktiv sein).
     *
     * @param messpunkt RFID der Ladestation
     * @param excludeId Eigene ID beim Update, {@code -1} beim Anlegen
     * @return true, wenn die RFID bereits einer anderen Ladestation gehört
     */
    @Query("SELECT COUNT(e) > 0 FROM Einheit e WHERE e.messpunkt = :messpunkt "
            + "AND e.typ = ch.nacht.entity.EinheitTyp.LADESTATION "
            + "AND e.id != :excludeId")
    boolean existsLadestationWithMesspunkt(
        @Param("messpunkt") String messpunkt,
        @Param("excludeId") Long excludeId
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
    Optional<Einheit> findFirstById(Long id);
}
