package ch.nacht.repository;

import ch.nacht.entity.Steuerentscheid;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Zugriff auf die Steuerentscheide der Einspeisesteuerung
 * (Specs/Einspeisesteuerung.md, FR-3).
 *
 * <p>Die Entity trägt {@code org_id} und {@code @Filter(orgFilter)}: Entscheide sind
 * anlagenspezifisch. Der Filter ist vor jeder Abfrage zu aktivieren — im Job über
 * {@code enableOrgFilter(orgId)}, weil dort kein Sicherheitskontext existiert.
 */
@Repository
public interface SteuerentscheidRepository extends JpaRepository<Steuerentscheid, Long> {

    /**
     * Entscheide einer Zeitspanne, aufsteigend.
     *
     * @param von Beginn in Ortszeit (einschliesslich)
     * @param bis Ende in Ortszeit (ausschliesslich)
     * @return Entscheide nach {@code zeitVon} sortiert
     */
    @Query("SELECT s FROM Steuerentscheid s WHERE s.zeitVon >= :von AND s.zeitVon < :bis "
            + "ORDER BY s.zeitVon")
    List<Steuerentscheid> findByZeitVonBetween(
        @Param("von") LocalDateTime von,
        @Param("bis") LocalDateTime bis
    );

    /**
     * Der Entscheid eines bestimmten Intervalls; leer, wenn keiner vorliegt.
     *
     * <p><b>Wofür:</b> Die Hysterese von {@code SOC_TIEF} braucht den Zustand des <b>Vor</b>-
     * intervalls — galt dort die Freigabe, liegt die Grenze höher. Bewusst das exakte
     * Vorintervall und nicht „der letzte davor": Nach einer Lücke von Stunden oder Tagen sägte
     * ein alter Entscheid eine Freigabe fort, die längst nicht mehr gilt. Fehlt das Vorintervall,
     * beginnt die Kette neu — ohne erweiterte Grenze, also auf der sicheren Seite.
     *
     * @param zeitVon Beginn des Intervalls in Ortszeit
     */
    Optional<Steuerentscheid> findByZeitVon(LocalDateTime zeitVon);

    /**
     * Schreibt einen Entscheid; ein vorhandener wird überschrieben.
     *
     * <p><b>Warum ein Upsert und kein {@code save}:</b> Treffen Messwerte verspätet ein, wertet der
     * nächste Lauf dasselbe Intervall erneut aus. Ohne Upsert entstünde ein zweiter Datensatz für
     * denselben Zeitpunkt, und die Tagesansicht zeigte zwei widersprüchliche Entscheide.
     *
     * <p>Der Konfliktschlüssel muss <b>genau</b> dem Unique-Constraint
     * {@code uq_steuerentscheid_org_zeit} (V145) entsprechen, sonst scheitert jeder Aufruf mit
     * {@code there is no unique or exclusion constraint matching the ON CONFLICT specification}.
     *
     * <p>Die Enum-Werte kommen als {@code String} — ein nativer Query kennt die Java-Enums nicht;
     * die CHECK-Constraints der Tabelle prüfen sie datenbankseitig.
     */
    @Modifying
    @Query(value = """
        INSERT INTO zev.steuerentscheid (org_id, zeit_von, preis, preis_tief_rest, produktion,
                                         verbrauch, bezug, ruecklieferung, soc,
                                         speicher_ladung, speicher_entladung, ueberschuss,
                                         regel, batterieladung, einspeisung, schwellwert,
                                         speicherwert, soc_minimum, soc_hysterese, erstellt_am)
        VALUES (:orgId, :zeitVon, :preis, :preisTiefRest, :produktion, :verbrauch, :bezug,
                :ruecklieferung, :soc, :speicherLadung, :speicherEntladung, :ueberschuss,
                :regel, :batterieladung, :einspeisung, :schwellwert, :speicherwert,
                now())
        ON CONFLICT (org_id, zeit_von)
        DO UPDATE SET preis           = EXCLUDED.preis,
                      preis_tief_rest = EXCLUDED.preis_tief_rest,
                      produktion      = EXCLUDED.produktion,
                      verbrauch       = EXCLUDED.verbrauch,
                      bezug           = EXCLUDED.bezug,
                      ruecklieferung  = EXCLUDED.ruecklieferung,
                      soc             = EXCLUDED.soc,
                      speicher_ladung    = EXCLUDED.speicher_ladung,
                      speicher_entladung = EXCLUDED.speicher_entladung,
                      ueberschuss     = EXCLUDED.ueberschuss,
                      regel           = EXCLUDED.regel,
                      batterieladung  = EXCLUDED.batterieladung,
                      einspeisung     = EXCLUDED.einspeisung,
                      schwellwert     = EXCLUDED.schwellwert,
                      speicherwert    = EXCLUDED.speicherwert,
                      soc_minimum     = EXCLUDED.soc_minimum,
                      soc_hysterese   = EXCLUDED.soc_hysterese,
                      erstellt_am     = now()
        """, nativeQuery = true)
    void upsert(
        @Param("orgId") Long orgId,
        @Param("zeitVon") LocalDateTime zeitVon,
        @Param("preis") BigDecimal preis,
        @Param("preisTiefRest") BigDecimal preisTiefRest,
        @Param("produktion") BigDecimal produktion,
        @Param("verbrauch") BigDecimal verbrauch,
        @Param("bezug") BigDecimal bezug,
        @Param("ruecklieferung") BigDecimal ruecklieferung,
        @Param("soc") BigDecimal soc,
        @Param("speicherLadung") BigDecimal speicherLadung,
        @Param("speicherEntladung") BigDecimal speicherEntladung,
        @Param("ueberschuss") BigDecimal ueberschuss,
        @Param("regel") String regel,
        @Param("batterieladung") String batterieladung,
        @Param("einspeisung") String einspeisung,
        @Param("schwellwert") BigDecimal schwellwert,
        @Param("speicherwert") BigDecimal speicherwert,
        @Param("socMinimum") BigDecimal socMinimum,
        @Param("socHysterese") BigDecimal socHysterese
    );
}
