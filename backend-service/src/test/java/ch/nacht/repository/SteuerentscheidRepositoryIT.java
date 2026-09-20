package ch.nacht.repository;

import ch.nacht.AbstractIntegrationTest;
import ch.nacht.entity.Organisation;
import ch.nacht.entity.Steuerentscheid;
import ch.nacht.entity.Steuerregel;
import ch.nacht.entity.Steuerzustand;
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
 * Integrationstests fuer {@link SteuerentscheidRepository#upsert} — den <b>nativen</b> Upsert der
 * Einspeisesteuerung (Specs/Einspeisesteuerung.md, FR-3).
 *
 * <p><b>Was hier geprueft wird und warum.</b> Der Upsert ist natives SQL mit 19 Zielspalten und
 * {@code ON CONFLICT (org_id, zeit_von) DO UPDATE}. Kein Compiler liest ihn, und die
 * Service-Unit-Tests mocken das Repository weg. Beim Ergaenzen von {@code soc_minimum} und
 * {@code soc_hysterese} kamen die Spalten in die Zielliste, die Platzhalter aber nicht in
 * {@code VALUES} — der Job auf der Anlage brach seither jeden Lauf mit
 * {@code INSERT has more target columns than expressions} ab, waehrend alle Tests gruen blieben.
 *
 * <p>{@link SteuerentscheidUpsertQueryTest} deckt seither die abzaehlbare Haelfte ab: Spalten
 * gegen Werte, jede Spalte im {@code DO UPDATE SET}. Diese Klasse deckt die andere Haelfte — dass
 * der Befehl gegen echtes PostgreSQL <b>laeuft</b>, dass der zweite Aufruf denselben Datensatz
 * <b>ueberschreibt</b> statt einen zweiten anzulegen, und dass dabei wirklich <b>jeder</b> Wert
 * neu gesetzt wird. Eine im {@code DO UPDATE SET} vertauschte oder vergessene Spalte ist still:
 * Der erste Lauf schriebe richtig, erst der zweite liesse einen alten Wert stehen.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class SteuerentscheidRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private SteuerentscheidRepository steuerentscheidRepository;

    @Autowired
    private OrganisationRepository organisationRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private Long orgId;

    /** Zweiter Mandant — der Konfliktschluessel enthaelt {@code org_id}, das ist zu zeigen. */
    private Long fremdOrgId;

    private static final LocalDateTime INTERVALL = LocalDateTime.of(2026, 3, 1, 10, 15);

    @BeforeEach
    void setUp() {
        steuerentscheidRepository.deleteAll();

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
     * hier abgeschaltet) und nicht aus den Migrationen. Der <b>Default auf {@code id}</b> fehlt
     * dort: Das native {@code INSERT} nennt die Spalte nicht, weil die echte Tabelle
     * {@code DEFAULT nextval(...)} traegt — die Entity dagegen zieht ihre Id ueber
     * {@code @GeneratedValue(SEQUENCE)} in Java, weshalb Hibernate die Spalte ohne Default erzeugt.
     *
     * <p>Den <b>Unique-Constraint</b> {@code uq_steuerentscheid_org_zeit} zog dieser Test zuerst
     * ebenfalls selbst nach. Er steht jetzt als {@code @UniqueConstraint} an der Entity und kommt
     * damit aus derselben Quelle wie das uebrige Schema. Das ist mehr als Bequemlichkeit: Solange
     * der Test ihn selbst anlegte, konnte er nicht bezeugen, dass der Konfliktschluessel des
     * Upserts zu einem Constraint passt, den die Anwendung wirklich kennt — er stellte genau die
     * Bedingung her, die er pruefen sollte.
     *
     * <p>Die DDL laeuft innerhalb der Testtransaktion und wird mit ihr zurueckgerollt.
     */
    private void ergaenzeIdDefault() {
        entityManager.createNativeQuery(
                "ALTER TABLE zev.steuerentscheid "
                        + "ALTER COLUMN id SET DEFAULT nextval('zev.steuerentscheid_seq')")
                .executeUpdate();
    }

    // ==================== Einfuegen ====================

    /**
     * Der erste Aufruf legt den Datensatz an — und fuellt <b>alle</b> Spalten.
     *
     * <p>Geprueft wird jede einzeln, mit einem eigenen Wert je Spalte: Waeren zwei Platzhalter in
     * {@code VALUES} vertauscht, faellt das nur so auf. Gleiche Werte in mehreren Spalten haetten
     * die Vertauschung verdeckt.
     */
    @Test
    void upsert_NeuesIntervall_SchreibtAlleSpalten() {
        Werte werte = Werte.erste();

        upsert(orgId, INTERVALL, werte);
        leereContext();

        List<Steuerentscheid> alle = steuerentscheidRepository.findAll();
        assertThat(alle).hasSize(1);
        Steuerentscheid gespeichert = alle.get(0);
        assertThat(gespeichert.getId()).isNotNull();
        assertThat(gespeichert.getOrgId()).isEqualTo(orgId);
        assertThat(gespeichert.getZeitVon()).isEqualTo(INTERVALL);
        assertThat(gespeichert.getErstelltAm()).isNotNull();
        pruefe(gespeichert, werte);
    }

    /**
     * Die neun nullbaren Spalten bleiben leer, wenn nichts vorliegt.
     *
     * <p>Das ist der Normalfall einer Anlage <b>ohne Speicher</b> und ohne Preisreihe: {@code soc},
     * {@code speicher_ladung}, {@code speicher_entladung}, {@code soc_minimum},
     * {@code soc_hysterese}, {@code preis}, {@code preis_tief_rest}, {@code bezug} und
     * {@code ruecklieferung} sind dann leer. Ein nativer Query bindet {@code null} nicht von
     * selbst richtig — ohne ableitbaren Typ quittiert PostgreSQL mit
     * {@code could not determine data type of parameter}.
     */
    @Test
    void upsert_OhneSpeicherUndPreis_SchreibtNullSpaltenAlsNull() {
        upsert(orgId, INTERVALL, Werte.nurPflichtfelder());
        leereContext();

        Steuerentscheid gespeichert = steuerentscheidRepository.findByZeitVon(INTERVALL).orElseThrow();
        assertThat(gespeichert.getPreis()).isNull();
        assertThat(gespeichert.getPreisTiefRest()).isNull();
        assertThat(gespeichert.getBezug()).isNull();
        assertThat(gespeichert.getRuecklieferung()).isNull();
        assertThat(gespeichert.getSoc()).isNull();
        assertThat(gespeichert.getSpeicherLadung()).isNull();
        assertThat(gespeichert.getSpeicherEntladung()).isNull();
        assertThat(gespeichert.getSocMinimum()).isNull();
        assertThat(gespeichert.getSocHysterese()).isNull();
        // Pflichtfelder stehen trotzdem
        assertThat(gespeichert.getProduktion()).isEqualByComparingTo("0.000");
        assertThat(gespeichert.getRegel()).isEqualTo(Steuerregel.KEIN_UEBERSCHUSS);
    }

    // ==================== Ueberschreiben ====================

    /**
     * Der zweite Aufruf auf dasselbe Intervall <b>ueberschreibt</b> — kein zweiter Datensatz.
     *
     * <p>Genau dafuer gibt es den Upsert: Treffen Messwerte verspaetet ein, wertet der naechste
     * Lauf dasselbe Intervall erneut aus. Mit {@code save} staenden danach zwei widersprechende
     * Entscheide fuer 10:15 in der Tagesansicht.
     *
     * <p>Und alle 17 Wertspalten tragen danach die neuen Zahlen. Eine im {@code DO UPDATE SET}
     * vergessene Spalte behielte still den alten Wert; deshalb hat jede Spalte in der zweiten
     * Fassung einen anderen Wert als in der ersten.
     */
    @Test
    void upsert_GleichesIntervallZweimal_UeberschreibtUndAktualisiertAlleSpalten() {
        upsert(orgId, INTERVALL, Werte.erste());
        leereContext();
        Long idNachErstemLauf = steuerentscheidRepository.findByZeitVon(INTERVALL).orElseThrow().getId();

        Werte zweite = Werte.zweite();
        upsert(orgId, INTERVALL, zweite);
        leereContext();

        List<Steuerentscheid> alle = steuerentscheidRepository.findAll();
        assertThat(alle).hasSize(1);
        Steuerentscheid gespeichert = alle.get(0);
        // Derselbe Datensatz, nicht ein neuer: ON CONFLICT DO UPDATE behaelt die Id
        assertThat(gespeichert.getId()).isEqualTo(idNachErstemLauf);
        pruefe(gespeichert, zweite);
    }

    /**
     * Auch der Weg zurueck nach {@code null} muss ankommen.
     *
     * <p>Faellt der Speicher aus, liefert der naechste Lauf fuer dasselbe Intervall keinen
     * Ladezustand mehr. Stuende {@code soc} nicht im {@code DO UPDATE SET}, bliebe der alte Wert
     * stehen — ein Ladezustand, den nie jemand gemessen hat.
     */
    @Test
    void upsert_ZweiterLaufOhneSpeicherwerte_SetztSpaltenAufNullZurueck() {
        upsert(orgId, INTERVALL, Werte.erste());
        leereContext();

        upsert(orgId, INTERVALL, Werte.nurPflichtfelder());
        leereContext();

        Steuerentscheid gespeichert = steuerentscheidRepository.findByZeitVon(INTERVALL).orElseThrow();
        assertThat(gespeichert.getSoc()).isNull();
        assertThat(gespeichert.getSpeicherLadung()).isNull();
        assertThat(gespeichert.getSpeicherEntladung()).isNull();
        assertThat(gespeichert.getSocMinimum()).isNull();
        assertThat(gespeichert.getSocHysterese()).isNull();
        assertThat(gespeichert.getPreis()).isNull();
        assertThat(gespeichert.getPreisTiefRest()).isNull();
        assertThat(gespeichert.getBezug()).isNull();
        assertThat(gespeichert.getRuecklieferung()).isNull();
    }

    // ==================== Konfliktschluessel ====================

    /**
     * Ein anderes Intervall desselben Mandanten ist <b>kein</b> Konflikt.
     *
     * <p>Sonst haette der Job nach dem zweiten Durchlauf genau einen Entscheid statt 96 je Tag.
     */
    @Test
    void upsert_AnderesIntervall_LegtZweitenDatensatzAn() {
        upsert(orgId, INTERVALL, Werte.erste());
        upsert(orgId, INTERVALL.plusMinutes(15), Werte.zweite());
        leereContext();

        assertThat(steuerentscheidRepository.findAll()).hasSize(2);
        assertThat(steuerentscheidRepository.findByZeitVon(INTERVALL)).isPresent();
        assertThat(steuerentscheidRepository.findByZeitVon(INTERVALL.plusMinutes(15))).isPresent();
    }

    /**
     * Derselbe Zeitpunkt bei einem <b>anderen Mandanten</b> ist ebenfalls kein Konflikt.
     *
     * <p>Der Schluessel ist {@code (org_id, zeit_von)}, nicht {@code zeit_von} allein. Waere er es,
     * ueberschrieben sich zwei Anlagen gegenseitig ihre Entscheide — sie laufen zur selben Zeit.
     */
    @Test
    void upsert_GleichesIntervallAndererMandant_LegtEigenenDatensatzAn() {
        upsert(orgId, INTERVALL, Werte.erste());
        upsert(fremdOrgId, INTERVALL, Werte.zweite());
        leereContext();

        List<Steuerentscheid> alle = steuerentscheidRepository.findAll();
        assertThat(alle).hasSize(2);
        assertThat(alle).extracting(Steuerentscheid::getOrgId)
                .containsExactlyInAnyOrder(orgId, fremdOrgId);

        Steuerentscheid eigener = alle.stream()
                .filter(s -> s.getOrgId().equals(orgId)).findFirst().orElseThrow();
        Steuerentscheid fremder = alle.stream()
                .filter(s -> s.getOrgId().equals(fremdOrgId)).findFirst().orElseThrow();
        pruefe(eigener, Werte.erste());
        pruefe(fremder, Werte.zweite());
    }

    /**
     * Der Mandantenfilter greift auf dem <b>Lesen</b> — der Upsert schreibt org-explizit, gelesen
     * wird hinterher unter Filter.
     *
     * <p>Der Job hat keinen Sicherheitskontext und aktiviert den Filter selbst
     * ({@code enableOrgFilter(orgId)}). Ohne diese Zusicherung zeigte die Tagesansicht die
     * Entscheide fremder Anlagen.
     */
    @Test
    void findByZeitVon_MitOrgFilter_SiehtNurDenEigenenEntscheid() {
        upsert(orgId, INTERVALL, Werte.erste());
        upsert(fremdOrgId, INTERVALL, Werte.zweite());
        leereContext();

        aktiviereOrgFilter(entityManager, orgId);

        assertThat(steuerentscheidRepository.findAll()).hasSize(1);
        Steuerentscheid sichtbar = steuerentscheidRepository.findByZeitVon(INTERVALL).orElseThrow();
        assertThat(sichtbar.getOrgId()).isEqualTo(orgId);
        assertThat(steuerentscheidRepository.findByZeitVonBetween(
                INTERVALL.minusHours(1), INTERVALL.plusHours(1)))
                .extracting(Steuerentscheid::getOrgId).containsExactly(orgId);
    }

    // ==================== Hilfsmittel ====================

    /** Die 17 Wertspalten eines Entscheids — je Spalte ein eigener Wert, siehe Tests. */
    private record Werte(BigDecimal preis, BigDecimal preisTiefRest, BigDecimal produktion,
                         BigDecimal verbrauch, BigDecimal bezug, BigDecimal ruecklieferung,
                         BigDecimal soc, BigDecimal speicherLadung, BigDecimal speicherEntladung,
                         BigDecimal ueberschuss, Steuerregel regel, Steuerzustand batterieladung,
                         Steuerzustand einspeisung, BigDecimal schwellwert, BigDecimal speicherwert,
                         BigDecimal socMinimum, BigDecimal socHysterese,
                         BigDecimal mindestAbstand) {

        /** Erste Fassung — alle Spalten belegt, jede mit einem unverwechselbaren Wert. */
        static Werte erste() {
            return new Werte(
                    new BigDecimal("0.11000"),   // preis
                    new BigDecimal("0.02000"),   // preisTiefRest
                    new BigDecimal("3.100"),     // produktion
                    new BigDecimal("1.200"),     // verbrauch
                    new BigDecimal("0.300"),     // bezug
                    new BigDecimal("1.400"),     // ruecklieferung
                    new BigDecimal("42.5"),      // soc
                    new BigDecimal("0.500"),     // speicherLadung
                    new BigDecimal("0.600"),     // speicherEntladung
                    new BigDecimal("1.900"),     // ueberschuss
                    Steuerregel.EINSPEISEN_LOHNT,
                    Steuerzustand.GESPERRT,
                    Steuerzustand.FREI,
                    new BigDecimal("0.07000"),   // schwellwert
                    new BigDecimal("0.15000"),   // speicherwert
                    new BigDecimal("20.0"),      // socMinimum
                    new BigDecimal("5.0"),       // socHysterese
                    new BigDecimal("0.02000"));  // mindestAbstand
        }

        /** Zweite Fassung — jeder Wert anders als in {@link #erste()}, auch die drei Enums. */
        static Werte zweite() {
            return new Werte(
                    new BigDecimal("-0.03000"),  // preis: darf negativ sein
                    new BigDecimal("-0.04000"),
                    new BigDecimal("8.700"),
                    new BigDecimal("2.800"),
                    new BigDecimal("0.900"),
                    new BigDecimal("5.100"),
                    new BigDecimal("18.3"),
                    new BigDecimal("2.200"),
                    new BigDecimal("3.300"),
                    new BigDecimal("5.900"),
                    Steuerregel.SOC_TIEF,
                    Steuerzustand.FREI,
                    Steuerzustand.GESPERRT,
                    new BigDecimal("0.09000"),
                    new BigDecimal("0.21000"),
                    new BigDecimal("25.0"),
                    new BigDecimal("7.5"),
                    new BigDecimal("0.03000"));
        }

        /** Nur die nicht-nullbaren Spalten — Anlage ohne Speicher, kein Preis verfuegbar. */
        static Werte nurPflichtfelder() {
            return new Werte(null, null,
                    new BigDecimal("0.000"), new BigDecimal("0.000"),
                    null, null, null, null, null,
                    new BigDecimal("0.000"),
                    Steuerregel.KEIN_UEBERSCHUSS, Steuerzustand.FREI, Steuerzustand.FREI,
                    new BigDecimal("0.07000"), new BigDecimal("0.15000"),
                    null, null, null);
        }
    }

    private void upsert(Long orgId, LocalDateTime zeitVon, Werte w) {
        steuerentscheidRepository.upsert(orgId, zeitVon, w.preis(), w.preisTiefRest(),
                w.produktion(), w.verbrauch(), w.bezug(), w.ruecklieferung(), w.soc(),
                w.speicherLadung(), w.speicherEntladung(), w.ueberschuss(),
                w.regel().name(), w.batterieladung().name(), w.einspeisung().name(),
                w.schwellwert(), w.speicherwert(), w.socMinimum(), w.socHysterese(),
                w.mindestAbstand());
    }

    /** Vergleicht Spalte fuer Spalte — bewusst ohne Schleife, damit die Meldung die Spalte nennt. */
    private void pruefe(Steuerentscheid s, Werte w) {
        assertThat(s.getPreis()).isEqualByComparingTo(w.preis());
        assertThat(s.getPreisTiefRest()).isEqualByComparingTo(w.preisTiefRest());
        assertThat(s.getProduktion()).isEqualByComparingTo(w.produktion());
        assertThat(s.getVerbrauch()).isEqualByComparingTo(w.verbrauch());
        assertThat(s.getBezug()).isEqualByComparingTo(w.bezug());
        assertThat(s.getRuecklieferung()).isEqualByComparingTo(w.ruecklieferung());
        assertThat(s.getSoc()).isEqualByComparingTo(w.soc());
        assertThat(s.getSpeicherLadung()).isEqualByComparingTo(w.speicherLadung());
        assertThat(s.getSpeicherEntladung()).isEqualByComparingTo(w.speicherEntladung());
        assertThat(s.getUeberschuss()).isEqualByComparingTo(w.ueberschuss());
        assertThat(s.getRegel()).isEqualTo(w.regel());
        assertThat(s.getBatterieladung()).isEqualTo(w.batterieladung());
        assertThat(s.getEinspeisung()).isEqualTo(w.einspeisung());
        assertThat(s.getSchwellwert()).isEqualByComparingTo(w.schwellwert());
        assertThat(s.getSpeicherwert()).isEqualByComparingTo(w.speicherwert());
        assertThat(s.getSocMinimum()).isEqualByComparingTo(w.socMinimum());
        assertThat(s.getSocHysterese()).isEqualByComparingTo(w.socHysterese());
        assertThat(s.getMindestAbstand()).isEqualByComparingTo(w.mindestAbstand());
    }

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
