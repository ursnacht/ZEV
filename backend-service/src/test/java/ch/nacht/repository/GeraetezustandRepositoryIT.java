package ch.nacht.repository;

import ch.nacht.AbstractIntegrationTest;
import ch.nacht.entity.Geraetezustand;
import ch.nacht.entity.Organisation;
import ch.nacht.entity.Zustandsgroesse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integrationstests fuer {@link GeraetezustandRepository#letzterWertJeIntervall} — die
 * <b>native</b> Abfrage der Einspeisesteuerung (Specs/Einspeisesteuerung.md, FR-2a/FR-5b).
 *
 * <p><b>Warum ausgerechnet diese Abfrage einen Integrationstest braucht.</b> Sie ist natives SQL:
 * {@code DISTINCT ON} kennt JPQL nicht. Damit faellt gleich dreierlei weg, worauf sich die uebrigen
 * Abfragen verlassen koennen:
 *
 * <ul>
 *   <li>Der Hibernate-{@code orgFilter} greift <b>nicht</b>. Die {@code org_id} steht deshalb von
 *       Hand in der {@code WHERE}-Bedingung — eine Zusicherung, die niemand ausser einem Test
 *       ueberprueft. Faellt sie weg, liest ein Mandant die Zustaende aller anderen mit, und zwar
 *       lautlos: Die Rueckrechnung liefert weiter Zahlen, nur die falschen.</li>
 *   <li>Das Ergebnis ist ein {@code Object[]} ohne Typinformation. Welche Java-Typen der Treiber
 *       liefert, steht nirgends im Code — {@code SteuerungService.socVerlauf} hatte hier einen
 *       festen Cast auf {@code java.sql.Timestamp}, und der PostgreSQL-Treiber liefert fuer eine
 *       {@code timestamp}-Spalte ein {@link LocalDateTime}. Die Rueckrechnung brach auf der Anlage
 *       mit einer {@code ClassCastException} ab. {@code letzterWertJeIntervall_Rueckgabetypen_...}
 *       nagelt die Typen deshalb ausdruecklich fest.</li>
 *   <li>Die Intervallbildung ({@code date_trunc} + {@code floor(minute/15)}) rechnet die Datenbank,
 *       nicht Java. Ob ein Wert um 10:15:00 zum Intervall 10:00 oder 10:15 gehoert, sagt nur ein
 *       Lauf gegen echtes PostgreSQL.</li>
 * </ul>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class GeraetezustandRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private GeraetezustandRepository geraetezustandRepository;

    @Autowired
    private OrganisationRepository organisationRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private Long orgId;

    /** Zweiter Mandant — nur als Gegenprobe der Mandanten-Isolation. */
    private Long fremdOrgId;

    /** Der Speicher des eigenen Mandanten. */
    private static final Long SPEICHER_ID = 4711L;

    /** Eine zweite Einheit desselben Mandanten — darf nie mitgelesen werden. */
    private static final Long ANDERE_EINHEIT_ID = 4712L;

    private static final String SOC = Zustandsgroesse.SOC.name();

    @BeforeEach
    void setUp() {
        geraetezustandRepository.deleteAll();

        Organisation org = new Organisation();
        org.setKeycloakOrgId(UUID.randomUUID());
        org.setName("Test Organisation");
        org.setErstelltAm(LocalDateTime.now());
        orgId = organisationRepository.save(org).getId();

        Organisation fremd = new Organisation();
        fremd.setKeycloakOrgId(UUID.randomUUID());
        fremd.setName("Fremde Organisation");
        fremd.setErstelltAm(LocalDateTime.now());
        fremdOrgId = organisationRepository.save(fremd).getId();
    }

    // ==================== Mandanten-Isolation ====================

    /**
     * Der haerteste Fall: <b>dieselbe</b> {@code einheit_id}, andere {@code org_id}.
     *
     * <p>{@code geraetezustand.einheit_id} ist ein blankes {@code BIGINT} ohne Relation; zwei
     * Mandanten koennen denselben Wert tragen. Faellt {@code org_id} aus der Bedingung, faellt es
     * hier auf — und nur hier: Mit unterschiedlichen Einheits-Ids liefe der Test auch ohne die
     * Bedingung gruen, weil schon {@code einheit_id} trennt.
     *
     * <p>Der fremde Wert liegt im selben Intervall <b>spaeter</b>. Das ist Absicht: Ohne die
     * {@code org_id}-Bedingung gewaenne er das {@code DISTINCT ON} und das Ergebnis waere 99.0.
     * Bei gleichem Zeitstempel entschiede der Zufall, und der Test waere nur manchmal scharf.
     */
    @Test
    void letzterWertJeIntervall_FremderMandantGleicheEinheit_WirdNichtGelesen() {
        speichere(orgId, SPEICHER_ID, LocalDateTime.of(2026, 3, 1, 10, 5), "40.0");
        speichere(fremdOrgId, SPEICHER_ID, LocalDateTime.of(2026, 3, 1, 10, 10), "99.0");
        flushUndLeere();

        List<Object[]> eigene = abfrage(orgId, SPEICHER_ID, SOC,
                LocalDateTime.of(2026, 3, 1, 10, 0), LocalDateTime.of(2026, 3, 1, 11, 0));

        assertThat(eigene).hasSize(1);
        assertThat((BigDecimal) eigene.get(0)[1]).isEqualByComparingTo("40.0");
    }

    /**
     * Gegenprobe: Der andere Mandant sieht seinen eigenen Wert — und nur den.
     *
     * <p>Die Zeiten sind gegenueber dem vorigen Test <b>vertauscht</b>, damit auch diese Richtung
     * scharf ist: Ohne {@code org_id}-Bedingung gewaenne hier der spaetere eigene Wert 40.0.
     */
    @Test
    void letzterWertJeIntervall_FremderMandant_SiehtNurSeineEigenenWerte() {
        speichere(fremdOrgId, SPEICHER_ID, LocalDateTime.of(2026, 3, 1, 10, 5), "99.0");
        speichere(orgId, SPEICHER_ID, LocalDateTime.of(2026, 3, 1, 10, 10), "40.0");
        flushUndLeere();

        List<Object[]> fremde = abfrage(fremdOrgId, SPEICHER_ID, SOC,
                LocalDateTime.of(2026, 3, 1, 10, 0), LocalDateTime.of(2026, 3, 1, 11, 0));

        assertThat(fremde).hasSize(1);
        assertThat((BigDecimal) fremde.get(0)[1]).isEqualByComparingTo("99.0");
    }

    /** Eine zweite Einheit desselben Mandanten zaehlt ebenso wenig mit — wieder die spaetere. */
    @Test
    void letzterWertJeIntervall_AndereEinheitDesselbenMandanten_WirdNichtGelesen() {
        speichere(orgId, SPEICHER_ID, LocalDateTime.of(2026, 3, 1, 10, 5), "40.0");
        speichere(orgId, ANDERE_EINHEIT_ID, LocalDateTime.of(2026, 3, 1, 10, 10), "77.0");
        flushUndLeere();

        List<Object[]> ergebnis = abfrage(orgId, SPEICHER_ID, SOC,
                LocalDateTime.of(2026, 3, 1, 10, 0), LocalDateTime.of(2026, 3, 1, 11, 0));

        assertThat(ergebnis).hasSize(1);
        assertThat((BigDecimal) ergebnis.get(0)[1]).isEqualByComparingTo("40.0");
    }

    /**
     * Eine nicht erfasste Groesse liefert nichts.
     *
     * <p>Der Parameter ist ein {@code String} — ein nativer Query kennt {@link Zustandsgroesse}
     * nicht. Ein Tippfehler im Aufruf faellt also nicht dem Compiler auf, sondern aeussert sich als
     * leerer Verlauf.
     */
    @Test
    void letzterWertJeIntervall_AndereGroesse_LiefertNichts() {
        speichere(orgId, SPEICHER_ID, LocalDateTime.of(2026, 3, 1, 10, 5), "40.0");
        flushUndLeere();

        assertThat(abfrage(orgId, SPEICHER_ID, "TEMPERATUR",
                LocalDateTime.of(2026, 3, 1, 10, 0), LocalDateTime.of(2026, 3, 1, 11, 0)))
                .isEmpty();
    }

    // ==================== Rueckgabetypen ====================

    /**
     * Der <b>tatsaechliche Laufzeittyp</b> beider Spalten — die Zusicherung, an der
     * {@code SteuerungService.socVerlauf} haengt.
     *
     * <p>Ein fester Cast auf {@code java.sql.Timestamp} stand dort und brach auf der Anlage mit
     * einer {@code ClassCastException}. Seither faengt {@code alsLocalDateTime} beide Faelle ab;
     * dieser Test haelt fest, welcher davon wirklich eintritt. Schiebt ein Treiber- oder
     * Hibernate-Wechsel den Typ, schlaegt er hier fehl statt im viertelstuendlichen Job.
     */
    @Test
    void letzterWertJeIntervall_Rueckgabetypen_BucketIstLocalDateTimeUndWertBigDecimal() {
        speichere(orgId, SPEICHER_ID, LocalDateTime.of(2026, 3, 1, 10, 5), "40.5");
        flushUndLeere();

        List<Object[]> ergebnis = abfrage(orgId, SPEICHER_ID, SOC,
                LocalDateTime.of(2026, 3, 1, 10, 0), LocalDateTime.of(2026, 3, 1, 11, 0));

        assertThat(ergebnis).hasSize(1);
        Object[] zeile = ergebnis.get(0);
        assertThat(zeile).hasSize(2);
        assertThat(zeile[0]).isInstanceOf(LocalDateTime.class);
        assertThat(zeile[1]).isInstanceOf(BigDecimal.class);
        assertThat((LocalDateTime) zeile[0]).isEqualTo(LocalDateTime.of(2026, 3, 1, 10, 0));
        assertThat((BigDecimal) zeile[1]).isEqualByComparingTo("40.5");
    }

    // ==================== Intervallbildung ====================

    /**
     * Mehrere Werte im selben Intervall: der <b>letzte</b> gewinnt.
     *
     * <p>Der Entscheid beschreibt das abgeschlossene Intervall, also zaehlt der Zustand an dessen
     * Ende. Eingefuegt wird bewusst in verdrehter Reihenfolge — die Abfrage darf sich nicht auf die
     * Einfuegereihenfolge oder die Id verlassen, sondern nur auf {@code zeit}.
     */
    @Test
    void letzterWertJeIntervall_MehrereWerteImIntervall_LetzterGewinnt() {
        speichere(orgId, SPEICHER_ID, LocalDateTime.of(2026, 3, 1, 10, 7, 30), "50.0");
        speichere(orgId, SPEICHER_ID, LocalDateTime.of(2026, 3, 1, 10, 14, 30), "55.0");
        speichere(orgId, SPEICHER_ID, LocalDateTime.of(2026, 3, 1, 10, 0), "45.0");
        flushUndLeere();

        List<Object[]> ergebnis = abfrage(orgId, SPEICHER_ID, SOC,
                LocalDateTime.of(2026, 3, 1, 10, 0), LocalDateTime.of(2026, 3, 1, 10, 15));

        assertThat(ergebnis).hasSize(1);
        assertThat((LocalDateTime) ergebnis.get(0)[0]).isEqualTo(LocalDateTime.of(2026, 3, 1, 10, 0));
        assertThat((BigDecimal) ergebnis.get(0)[1]).isEqualByComparingTo("55.0");
    }

    /**
     * Ein Wert genau auf der Intervallgrenze gehoert zum <b>beginnenden</b> Intervall.
     *
     * <p>{@code floor(minute / 15)} schneidet ab: 10:14:59 → 10:00, 10:15:00 → 10:15. Sonst
     * schluege der Zustand des neuen Intervalls dem alten zu und die Rueckrechnung bekaeme den
     * Ladezustand um eine Viertelstunde verschoben.
     */
    @Test
    void letzterWertJeIntervall_WertAufIntervallgrenze_ZaehltZumNeuenIntervall() {
        speichere(orgId, SPEICHER_ID, LocalDateTime.of(2026, 3, 1, 10, 14, 59), "60.0");
        speichere(orgId, SPEICHER_ID, LocalDateTime.of(2026, 3, 1, 10, 15), "61.0");
        speichere(orgId, SPEICHER_ID, LocalDateTime.of(2026, 3, 1, 10, 29, 59), "62.0");
        speichere(orgId, SPEICHER_ID, LocalDateTime.of(2026, 3, 1, 10, 30), "63.0");
        speichere(orgId, SPEICHER_ID, LocalDateTime.of(2026, 3, 1, 10, 45), "64.0");
        flushUndLeere();

        List<Object[]> ergebnis = abfrage(orgId, SPEICHER_ID, SOC,
                LocalDateTime.of(2026, 3, 1, 10, 0), LocalDateTime.of(2026, 3, 1, 11, 0));

        assertThat(ergebnis).hasSize(4);
        assertThat(buckets(ergebnis)).containsExactly(
                LocalDateTime.of(2026, 3, 1, 10, 0),
                LocalDateTime.of(2026, 3, 1, 10, 15),
                LocalDateTime.of(2026, 3, 1, 10, 30),
                LocalDateTime.of(2026, 3, 1, 10, 45));
        assertThat((BigDecimal) ergebnis.get(0)[1]).isEqualByComparingTo("60.0");
        assertThat((BigDecimal) ergebnis.get(1)[1]).isEqualByComparingTo("62.0");
        assertThat((BigDecimal) ergebnis.get(2)[1]).isEqualByComparingTo("63.0");
        assertThat((BigDecimal) ergebnis.get(3)[1]).isEqualByComparingTo("64.0");
    }

    /**
     * Ein Intervall ohne Wert liefert <b>keine Zeile</b> — keine Luecke mit {@code null}.
     *
     * <p>Darauf baut {@code socVerlauf}: Es fuellt eine {@code TreeMap} und schlaegt spaeter mit
     * {@code floorEntry} nach. Eine {@code null}-Zeile wuerde dort den zuletzt bekannten Stand
     * ueberschreiben.
     */
    @Test
    void letzterWertJeIntervall_LeeresIntervall_LiefertKeineZeile() {
        speichere(orgId, SPEICHER_ID, LocalDateTime.of(2026, 3, 1, 10, 5), "40.0");
        // 10:15 und 10:30 bleiben ohne Meldung — der Zaehler war offline
        speichere(orgId, SPEICHER_ID, LocalDateTime.of(2026, 3, 1, 10, 50), "43.0");
        flushUndLeere();

        List<Object[]> ergebnis = abfrage(orgId, SPEICHER_ID, SOC,
                LocalDateTime.of(2026, 3, 1, 10, 0), LocalDateTime.of(2026, 3, 1, 11, 0));

        assertThat(ergebnis).hasSize(2);
        assertThat(buckets(ergebnis)).containsExactly(
                LocalDateTime.of(2026, 3, 1, 10, 0),
                LocalDateTime.of(2026, 3, 1, 10, 45));
    }

    /**
     * Der Zeitraum ist links einschliessend, rechts ausschliessend — dieselbe Konvention wie
     * {@code SteuerentscheidRepository.findByZeitVonBetween}. Ein Wert genau auf {@code bis}
     * gehoert in die naechste Abfrage, sonst kaeme er zweimal vor.
     */
    @Test
    void letzterWertJeIntervall_Zeitraumgrenzen_VonEinschliesslichBisAusschliesslich() {
        speichere(orgId, SPEICHER_ID, LocalDateTime.of(2026, 3, 1, 9, 59, 59), "10.0");
        speichere(orgId, SPEICHER_ID, LocalDateTime.of(2026, 3, 1, 10, 0), "20.0");
        speichere(orgId, SPEICHER_ID, LocalDateTime.of(2026, 3, 1, 10, 59, 59), "30.0");
        speichere(orgId, SPEICHER_ID, LocalDateTime.of(2026, 3, 1, 11, 0), "40.0");
        flushUndLeere();

        List<Object[]> ergebnis = abfrage(orgId, SPEICHER_ID, SOC,
                LocalDateTime.of(2026, 3, 1, 10, 0), LocalDateTime.of(2026, 3, 1, 11, 0));

        assertThat(buckets(ergebnis)).containsExactly(
                LocalDateTime.of(2026, 3, 1, 10, 0),
                LocalDateTime.of(2026, 3, 1, 10, 45));
        assertThat((BigDecimal) ergebnis.get(0)[1]).isEqualByComparingTo("20.0");
        assertThat((BigDecimal) ergebnis.get(1)[1]).isEqualByComparingTo("30.0");
    }

    /** Zeitraum ohne Daten: leere Liste, keine Ausnahme. */
    @Test
    void letzterWertJeIntervall_ZeitraumOhneDaten_LiefertLeereListe() {
        speichere(orgId, SPEICHER_ID, LocalDateTime.of(2026, 3, 1, 10, 5), "40.0");
        flushUndLeere();

        assertThat(abfrage(orgId, SPEICHER_ID, SOC,
                LocalDateTime.of(2026, 3, 2, 0, 0), LocalDateTime.of(2026, 3, 3, 0, 0)))
                .isEmpty();
    }

    /** Ganz ohne Daten ebenso — der erste Lauf auf einer frischen Anlage. */
    @Test
    void letzterWertJeIntervall_KeineDaten_LiefertLeereListe() {
        assertThat(abfrage(orgId, SPEICHER_ID, SOC,
                LocalDateTime.of(2026, 3, 1, 0, 0), LocalDateTime.of(2026, 3, 2, 0, 0)))
                .isEmpty();
    }

    /**
     * Ueber einen ganzen Tag: 96 Intervalle, aufsteigend sortiert.
     *
     * <p>Die Sortierung ist keine Zierde — {@code socVerlauf} fuellt damit eine {@code TreeMap},
     * und die Rueckrechnung laeuft die Intervalle der Reihe nach ab.
     */
    @Test
    void letzterWertJeIntervall_GanzerTag_Liefert96IntervalleAufsteigend() {
        LocalDateTime tagesbeginn = LocalDateTime.of(2026, 3, 1, 0, 0);
        for (int i = 0; i < 96; i++) {
            // zwei Meldungen je Intervall — die spaetere gewinnt
            speichere(orgId, SPEICHER_ID, tagesbeginn.plusMinutes(15L * i + 2), "10.0");
            speichere(orgId, SPEICHER_ID, tagesbeginn.plusMinutes(15L * i + 13), String.valueOf(i));
        }
        flushUndLeere();

        List<Object[]> ergebnis = abfrage(orgId, SPEICHER_ID, SOC,
                tagesbeginn, tagesbeginn.plusDays(1));

        assertThat(ergebnis).hasSize(96);
        assertThat(buckets(ergebnis)).isSorted();
        assertThat(buckets(ergebnis).get(0)).isEqualTo(tagesbeginn);
        assertThat(buckets(ergebnis).get(95)).isEqualTo(LocalDateTime.of(2026, 3, 1, 23, 45));
        assertThat((BigDecimal) ergebnis.get(95)[1]).isEqualByComparingTo("95");
    }

    // ==================== Hilfsmittel ====================

    private List<Object[]> abfrage(Long orgId, Long einheitId, String groesse,
                                   LocalDateTime von, LocalDateTime bis) {
        return geraetezustandRepository.letzterWertJeIntervall(orgId, einheitId, groesse, von, bis);
    }

    private List<LocalDateTime> buckets(List<Object[]> zeilen) {
        return zeilen.stream().map(zeile -> (LocalDateTime) zeile[0]).toList();
    }

    private Geraetezustand speichere(Long orgId, Long einheitId, LocalDateTime zeit, String wert) {
        Geraetezustand z = new Geraetezustand(orgId, einheitId, zeit, Zustandsgroesse.SOC,
                new BigDecimal(wert));
        z.setEmpfangenAm(LocalDateTime.now());
        return geraetezustandRepository.save(z);
    }

    /**
     * Schreibt den Persistence-Context in die Datenbank und leert ihn.
     *
     * <p>Notwendig, weil die native Abfrage an Hibernate vorbei liest: Ungeschriebene Entitaeten
     * saehe sie nicht.
     */
    private void flushUndLeere() {
        entityManager.flush();
        entityManager.clear();
    }
}
