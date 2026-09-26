package ch.nacht.repository;

import ch.nacht.AbstractIntegrationTest;
import ch.nacht.entity.Einstrahlungsprognose;
import ch.nacht.entity.Organisation;
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
 * Integrationstests fuer {@link EinstrahlungsprognoseRepository} — den <b>nativen</b> Upsert der
 * Einstrahlungsprognose und die beiden Leseabfragen (Specs/Ladeplanung.md, FR-2 und FR-3).
 *
 * <p><b>Warum es diese Klasse braucht.</b> Der Upsert ist natives SQL: Kein Compiler liest ihn,
 * und die Service-Unit-Tests mocken das Repository weg. Beim gleichartigen Upsert des
 * Steuerentscheids kamen einmal Spalten in die Zielliste, die Platzhalter aber nicht in
 * {@code VALUES} — der Job brach seither jeden Lauf ab, waehrend alle Tests gruen blieben
 * ({@link SteuerentscheidRepositoryIT} beschreibt den Fall). Hier wird dasselbe abgedeckt: dass
 * der Befehl gegen echtes PostgreSQL <b>laeuft</b>, dass der zweite Aufruf denselben Datensatz
 * <b>ueberschreibt</b>, und dass der Konfliktschluessel die {@code org_id} wirklich enthaelt.
 *
 * <p><b>Und die Rueckgabetypen von {@code findGtiJeIntervall}.</b> Der aufrufende Service castet
 * fest auf {@code LocalDateTime} und {@code BigDecimal}. Ein solcher Cast flog schon einmal erst
 * im Betrieb auf, weil eine native Query {@code java.sql.Timestamp} lieferte. Hier wird er
 * deshalb gegen die echte Datenbank festgenagelt.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class EinstrahlungsprognoseRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private EinstrahlungsprognoseRepository prognoseRepository;

    @Autowired
    private OrganisationRepository organisationRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private Long orgId;

    /** Zweiter Mandant — der Konfliktschluessel enthaelt {@code org_id}, das ist zu zeigen. */
    private Long fremdOrgId;

    /** Intervall<b>beginn</b> in Ortszeit — derselbe Bezug wie {@code steuerentscheid.zeit_von}. */
    private static final LocalDateTime INTERVALL = LocalDateTime.of(2026, 9, 25, 13, 15);

    private static final LocalDateTime ABGERUFEN_AM = LocalDateTime.of(2026, 9, 25, 6, 0);

    @BeforeEach
    void setUp() {
        prognoseRepository.deleteAll();

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

        ergaenzeIdDefault();
    }

    /**
     * Holt die eine Eigenschaft der echten Tabelle nach, die der Upsert braucht.
     *
     * <p>Das Schema dieses Tests stammt von Hibernate ({@code ddl-auto=create-drop}, Flyway ist
     * hier abgeschaltet). Der <b>Default auf {@code id}</b> fehlt dort: Das native {@code INSERT}
     * nennt die Spalte nicht, weil die echte Tabelle (V166) {@code DEFAULT nextval(...)} traegt —
     * die Entity dagegen zieht ihre Id ueber {@code @GeneratedValue(SEQUENCE)} in Java, weshalb
     * Hibernate die Spalte ohne Default erzeugt.
     *
     * <p>Der <b>Unique-Constraint</b> {@code uq_einstrahlungsprognose_org_zeit} wird <b>nicht</b>
     * nachgezogen: Er steht als {@code @UniqueConstraint} an der Entity und kommt damit aus
     * derselben Quelle wie das uebrige Schema. Zoege ihn der Test selbst nach, koennte er nicht
     * bezeugen, dass der Konfliktschluessel des Upserts zu einem Constraint passt, den die
     * Anwendung wirklich kennt — er stellte genau die Bedingung her, die er pruefen soll.
     *
     * <p>Die DDL laeuft innerhalb der Testtransaktion und wird mit ihr zurueckgerollt.
     */
    private void ergaenzeIdDefault() {
        entityManager.createNativeQuery(
                "ALTER TABLE zev.einstrahlungsprognose "
                        + "ALTER COLUMN id SET DEFAULT nextval('zev.einstrahlungsprognose_seq')")
                .executeUpdate();
    }

    // ==================== Einfuegen ====================

    /** Der erste Aufruf legt den Datensatz an und fuellt alle vier Wertspalten. */
    @Test
    void upsert_NeuesIntervall_SchreibtAlleSpalten() {
        prognoseRepository.upsert(orgId, INTERVALL, new BigDecimal("812.40"), ABGERUFEN_AM);
        leereContext();

        List<Einstrahlungsprognose> alle = prognoseRepository.findAll();
        assertThat(alle).hasSize(1);
        Einstrahlungsprognose gespeichert = alle.get(0);
        assertThat(gespeichert.getId()).isNotNull();
        assertThat(gespeichert.getOrgId()).isEqualTo(orgId);
        assertThat(gespeichert.getZeit()).isEqualTo(INTERVALL);
        assertThat(gespeichert.getGti()).isEqualByComparingTo("812.40");
        assertThat(gespeichert.getAbgerufenAm()).isEqualTo(ABGERUFEN_AM);
    }

    /**
     * Die Ortszeit kommt unveraendert zurueck — keine Zonenrechnung auf dem Weg durch die
     * Datenbank.
     *
     * <p>{@code zeit} ist Ortszeit ohne Zone ({@code TIMESTAMP}, nicht {@code TIMESTAMPTZ}).
     * Rechnete irgendeine Schicht die Zeitzone der JVM hinein, laege die ganze Prognose ein bis
     * zwei Stunden daneben — und saehe weiterhin plausibel aus.
     */
    @Test
    void upsert_Ortszeit_KommtUnveraendertZurueck() {
        // 25.10.2026 ist der Tag der Zeitumstellung - 02:30 gibt es an diesem Tag zweimal
        LocalDateTime umstellungstag = LocalDateTime.of(2026, 10, 25, 2, 30);
        prognoseRepository.upsert(orgId, umstellungstag, new BigDecimal("0.00"), ABGERUFEN_AM);
        leereContext();

        assertThat(prognoseRepository.findAll().get(0).getZeit()).isEqualTo(umstellungstag);
    }

    // ==================== Ueberschreiben ====================

    /**
     * Der zweite Aufruf auf dasselbe {@code (org_id, zeit)} <b>ueberschreibt</b> — kein zweiter
     * Datensatz, dieselbe Id, neue Werte.
     *
     * <p>Genau dafuer gibt es den Upsert: Der Abruf laeuft stuendlich und liefert jedes Mal
     * dieselben Intervalle mit aktualisierten Werten. Ohne Upsert entstuenden je Intervall 24
     * Datensaetze pro Tag, und keiner wuesste, welcher gilt.
     *
     * <p>Geprueft werden <b>beide</b> Wertspalten: Fehlte {@code abgerufen_am} im
     * {@code DO UPDATE SET}, bliebe still der alte Zeitpunkt stehen — und damit die Auskunft
     * darueber, ob die Prognose noch frisch ist. Der Fehler zeigte sich erst im zweiten Lauf.
     */
    @Test
    void upsert_GleichesIntervallZweimal_UeberschreibtMitGleicherId() {
        prognoseRepository.upsert(orgId, INTERVALL, new BigDecimal("812.40"), ABGERUFEN_AM);
        leereContext();
        Long idNachErstemLauf = prognoseRepository.findAll().get(0).getId();

        LocalDateTime spaeter = ABGERUFEN_AM.plusHours(3);
        prognoseRepository.upsert(orgId, INTERVALL, new BigDecimal("640.10"), spaeter);
        leereContext();

        List<Einstrahlungsprognose> alle = prognoseRepository.findAll();
        assertThat(alle).hasSize(1);
        Einstrahlungsprognose gespeichert = alle.get(0);
        assertThat(gespeichert.getId()).isEqualTo(idNachErstemLauf);
        assertThat(gespeichert.getGti()).isEqualByComparingTo("640.10");
        assertThat(gespeichert.getAbgerufenAm()).isEqualTo(spaeter);
    }

    /**
     * Ein anderes Intervall desselben Mandanten ist <b>kein</b> Konflikt.
     *
     * <p>Sonst haette der Job nach dem zweiten Durchlauf genau einen Prognosewert statt 192.
     */
    @Test
    void upsert_AnderesIntervall_LegtZweitenDatensatzAn() {
        prognoseRepository.upsert(orgId, INTERVALL, new BigDecimal("812.40"), ABGERUFEN_AM);
        prognoseRepository.upsert(orgId, INTERVALL.plusMinutes(15), new BigDecimal("795.10"),
                ABGERUFEN_AM);
        leereContext();

        assertThat(prognoseRepository.findAll()).hasSize(2);
    }

    /**
     * Dasselbe Intervall bei einem <b>anderen Mandanten</b> ergibt einen eigenen Datensatz.
     *
     * <p>Der Schluessel ist {@code (org_id, zeit)}, nicht {@code zeit} allein. Waere er es,
     * ueberschrieben sich zwei Anlagen gegenseitig ihre Prognose — sie laufen zur selben Zeit,
     * stehen aber an verschiedenen Orten und sind verschieden ausgerichtet. Die zweite Anlage
     * rechnete dann mit der Einstrahlung der ersten.
     */
    @Test
    void upsert_GleichesIntervallAndererMandant_LegtEigenenDatensatzAn() {
        prognoseRepository.upsert(orgId, INTERVALL, new BigDecimal("812.40"), ABGERUFEN_AM);
        prognoseRepository.upsert(fremdOrgId, INTERVALL, new BigDecimal("120.00"), ABGERUFEN_AM);
        leereContext();

        List<Einstrahlungsprognose> alle = prognoseRepository.findAll();
        assertThat(alle).hasSize(2);
        assertThat(alle).extracting(Einstrahlungsprognose::getOrgId)
                .containsExactlyInAnyOrder(orgId, fremdOrgId);
        assertThat(alle).filteredOn(e -> e.getOrgId().equals(orgId))
                .singleElement()
                .extracting(Einstrahlungsprognose::getGti)
                .satisfies(gti -> assertThat((BigDecimal) gti).isEqualByComparingTo("812.40"));
        assertThat(alle).filteredOn(e -> e.getOrgId().equals(fremdOrgId))
                .singleElement()
                .extracting(Einstrahlungsprognose::getGti)
                .satisfies(gti -> assertThat((BigDecimal) gti).isEqualByComparingTo("120.00"));
    }

    // ==================== findByZeitBetween ====================

    /**
     * Die Tagesabfrage ist <b>halboffen</b>: {@code von} einschliesslich, {@code bis}
     * ausschliesslich — und sortiert aufsteigend.
     *
     * <p>Mit {@code <=} am Ende truege das erste Intervall des Folgetags in die Tagesansicht und
     * erschiene dort als 97. Punkt.
     */
    @Test
    void findByZeitBetween_GrenzenSindHalboffenUndSortiert() {
        LocalDateTime von = LocalDateTime.of(2026, 9, 25, 0, 0);
        LocalDateTime bis = LocalDateTime.of(2026, 9, 26, 0, 0);
        prognoseRepository.upsert(orgId, bis, new BigDecimal("4.00"), ABGERUFEN_AM);       // draussen
        prognoseRepository.upsert(orgId, von.minusMinutes(15), new BigDecimal("1.00"), ABGERUFEN_AM);
        prognoseRepository.upsert(orgId, bis.minusMinutes(15), new BigDecimal("3.00"), ABGERUFEN_AM);
        prognoseRepository.upsert(orgId, von, new BigDecimal("2.00"), ABGERUFEN_AM);
        leereContext();

        List<Einstrahlungsprognose> werte = prognoseRepository.findByZeitBetween(von, bis);

        assertThat(werte).extracting(Einstrahlungsprognose::getZeit)
                .containsExactly(von, bis.minusMinutes(15));
    }

    /**
     * Der Mandantenfilter greift auf dem Lesen — der Upsert schreibt org-explizit, gelesen wird
     * unter Filter.
     *
     * <p>Ohne diese Zusicherung zeigte die Tagesansicht die Prognose fremder Anlagen, und der
     * gelernte Umrechnungsfaktor lernte aus fremder Einstrahlung.
     */
    @Test
    void findByZeitBetween_MitOrgFilter_SiehtNurDieEigenePrognose() {
        prognoseRepository.upsert(orgId, INTERVALL, new BigDecimal("812.40"), ABGERUFEN_AM);
        prognoseRepository.upsert(fremdOrgId, INTERVALL, new BigDecimal("120.00"), ABGERUFEN_AM);
        leereContext();

        aktiviereOrgFilter(entityManager, orgId);

        List<Einstrahlungsprognose> werte = prognoseRepository.findByZeitBetween(
                INTERVALL.minusHours(1), INTERVALL.plusHours(1));
        assertThat(werte).singleElement()
                .extracting(Einstrahlungsprognose::getOrgId).isEqualTo(orgId);
    }

    // ==================== findGtiJeIntervall ====================

    /**
     * Nur Intervalle mit {@code gti > 0} — Nachtstunden bleiben draussen.
     *
     * <p>Sie truegen zum gelernten Faktor nichts bei (0 geteilt durch nichts), zaehlten aber in
     * die Mindestzahl von 150 Punkten. Ein Mandant haette den Faktor damit nach zwei Tagen statt
     * nach dreien — und er waere aus zu wenigen hellen Intervallen geschaetzt.
     */
    @Test
    void findGtiJeIntervall_LiefertNurHelleIntervalle() {
        prognoseRepository.upsert(orgId, INTERVALL, new BigDecimal("812.40"), ABGERUFEN_AM);
        prognoseRepository.upsert(orgId, INTERVALL.plusMinutes(15), new BigDecimal("0.00"),
                ABGERUFEN_AM);
        prognoseRepository.upsert(orgId, INTERVALL.plusMinutes(30), new BigDecimal("0.01"),
                ABGERUFEN_AM);
        leereContext();

        List<Object[]> zeilen = prognoseRepository.findGtiJeIntervall(
                INTERVALL, INTERVALL.plusHours(1));

        assertThat(zeilen).hasSize(2);
        assertThat(zeilen).extracting(z -> z[0])
                .containsExactly(INTERVALL, INTERVALL.plusMinutes(30));
    }

    /**
     * <b>Die Rueckgabetypen.</b> {@code zeile[0]} ist ein {@link LocalDateTime}, {@code zeile[1]}
     * ein {@link BigDecimal} — genau die Typen, auf die
     * {@code ProduktionsprognoseService.umrechnungsfaktor} fest castet.
     *
     * <p>Ein fester Cast auf {@code java.sql.Timestamp} in einer anderen Query flog erst im
     * Betrieb auf; ein Cast auf {@code LocalDateTime} flaege genauso auf, wenn die Abfrage einmal
     * nativ wuerde. Die Zusicherung steht deshalb hier, wo sie gegen echtes PostgreSQL geprueft
     * wird — kein Compiler prueft sie.
     */
    @Test
    void findGtiJeIntervall_LiefertLocalDateTimeUndBigDecimal() {
        prognoseRepository.upsert(orgId, INTERVALL, new BigDecimal("812.40"), ABGERUFEN_AM);
        leereContext();

        Object[] zeile = prognoseRepository.findGtiJeIntervall(
                INTERVALL, INTERVALL.plusHours(1)).get(0);

        assertThat(zeile[0]).isInstanceOf(LocalDateTime.class).isEqualTo(INTERVALL);
        assertThat(zeile[1]).isInstanceOf(BigDecimal.class);
        assertThat((BigDecimal) zeile[1]).isEqualByComparingTo("812.40");
    }

    /**
     * Auch die Lernabfrage sieht unter Filter nur den eigenen Mandanten.
     *
     * <p>Sie laeuft im Job ohne Sicherheitskontext. Lernte sie aus fremder Einstrahlung, waere der
     * Faktor einer Anlage von der Ausrichtung einer anderen bestimmt — und niemand saehe es der
     * Zahl an.
     */
    @Test
    void findGtiJeIntervall_MitOrgFilter_SiehtNurDieEigenePrognose() {
        prognoseRepository.upsert(orgId, INTERVALL, new BigDecimal("812.40"), ABGERUFEN_AM);
        prognoseRepository.upsert(fremdOrgId, INTERVALL, new BigDecimal("120.00"), ABGERUFEN_AM);
        leereContext();

        aktiviereOrgFilter(entityManager, orgId);

        List<Object[]> zeilen = prognoseRepository.findGtiJeIntervall(
                INTERVALL, INTERVALL.plusHours(1));

        assertThat(zeilen).hasSize(1);
        assertThat((BigDecimal) zeilen.get(0)[1]).isEqualByComparingTo("812.40");
    }

    // ==================== Hilfsmittel ====================

    /**
     * Schreibt Ausstehendes und leert den Persistence-Context.
     *
     * <p>Der Upsert geht als natives {@code INSERT} an Hibernate vorbei: Ohne Leeren lieferte ein
     * anschliessendes Lesen die Entity aus dem Cache und damit den Stand <b>vor</b> dem
     * Ueberschreiben.
     */
    private void leereContext() {
        entityManager.flush();
        entityManager.clear();
    }
}
