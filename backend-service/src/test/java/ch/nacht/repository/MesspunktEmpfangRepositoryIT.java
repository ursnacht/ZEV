package ch.nacht.repository;

import ch.nacht.AbstractIntegrationTest;
import ch.nacht.entity.Einheit;
import ch.nacht.entity.EinheitTyp;
import ch.nacht.entity.MesspunktEmpfang;
import ch.nacht.entity.MesspunktEmpfangErgebnis;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integrationstests für die <b>nativen</b> Abfragen von {@link MesspunktEmpfangRepository} —
 * Upsert und Aufräumen — und für {@code findFirstById} unter dem Mandantenfilter
 * (Specs/Messpunkt-Empfang.md, FR-1.1, FR-1.4, FR-1.9, FR-3).
 *
 * <p>Natives SQL mit {@code ON CONFLICT}: Ob eine zweite Meldung dieselbe Zeile überschreibt oder
 * eine zweite anlegt, und ob der Zähler steigt, sagt nur ein Lauf gegen echtes PostgreSQL.
 *
 * <p><b>Schema aus dem Mapping:</b> Der Unique-Constraint steht deshalb auch an der Entity — ohne
 * ihn scheiterte {@code ON CONFLICT (org_id, messpunkt)} hier. Die CHECKs der Migration V186 prüft
 * {@code FlywaySchemaIT}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class MesspunktEmpfangRepositoryIT extends AbstractIntegrationTest {

    private static final long ORG_ID = 4711L;
    private static final long ANDERE_ORG_ID = 4712L;

    @Autowired
    private MesspunktEmpfangRepository messpunktEmpfangRepository;

    @Autowired
    private EinheitRepository einheitRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        messpunktEmpfangRepository.deleteAll();
    }

    private void upsert(Instant zeitpunkt, MesspunktEmpfangErgebnis ergebnis, String grund, String payload) {
        messpunktEmpfangRepository.upsert(ORG_ID, "MP-001", "PI_GATEWAY", zeitpunkt,
                new BigDecimal("1.5"), BigDecimal.ZERO, ergebnis.name(), grund, payload);
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void upsert_ZweiteMeldung_UeberschreibtPayloadUndZaehltHoch() {
        Instant erste = Instant.parse("2026-10-25T08:00:00Z");
        Instant zweite = Instant.parse("2026-10-25T08:01:00Z");

        upsert(erste, MesspunktEmpfangErgebnis.GESPEICHERT, null, "{\"erste\":1}");
        upsert(zweite, MesspunktEmpfangErgebnis.UNGUELTIG, "Payload nicht lesbar", "kein-json");

        List<MesspunktEmpfang> zeilen = messpunktEmpfangRepository.findAll();
        assertThat(zeilen).hasSize(1);
        MesspunktEmpfang zeile = zeilen.get(0);
        assertThat(zeile.getLetzteMeldung()).isEqualTo("kein-json");
        assertThat(zeile.getAnzahlMeldungen()).isEqualTo(2);
        assertThat(zeile.getErsterEmpfang()).isEqualTo(erste);
        assertThat(zeile.getLetzterEmpfang()).isEqualTo(zweite);
        assertThat(zeile.getErgebnis()).isEqualTo(MesspunktEmpfangErgebnis.UNGUELTIG);
        assertThat(zeile.getGrund()).isEqualTo("Payload nicht lesbar");
    }

    @Test
    void upsert_ErsteMeldung_LegtZeileMitPayloadAn() {
        Instant zeitpunkt = Instant.parse("2026-10-25T08:00:00Z");

        upsert(zeitpunkt, MesspunktEmpfangErgebnis.GESPEICHERT, null, "{\"a\":1}");

        MesspunktEmpfang zeile = messpunktEmpfangRepository.findAll().get(0);
        assertThat(zeile.getId()).isNotNull();
        assertThat(zeile.getAnzahlMeldungen()).isEqualTo(1);
        assertThat(zeile.getErsterEmpfang()).isEqualTo(zeile.getLetzterEmpfang());
        assertThat(zeile.getLetzteMeldung()).isEqualTo("{\"a\":1}");
    }

    /**
     * Die Zeile zeigt immer genau die letzte Meldung: Eine unlesbare Meldung nach einer mit Ständen
     * setzt beide Stände auf {@code NULL} (FR-1.4).
     */
    @Test
    void upsert_UnlesbareMeldungNachStaenden_StaendeNull() {
        Instant erste = Instant.parse("2026-10-25T08:00:00Z");
        upsert(erste, MesspunktEmpfangErgebnis.GESPEICHERT, null, "{}");

        messpunktEmpfangRepository.upsert(ORG_ID, "MP-001", "PI_GATEWAY", erste.plusSeconds(60),
                null, null, "UNGUELTIG", "Payload nicht lesbar", "kein-json");
        entityManager.flush();
        entityManager.clear();

        MesspunktEmpfang zeile = messpunktEmpfangRepository.findAll().get(0);
        assertThat(zeile.getZaehlerstandBezug()).isNull();
        assertThat(zeile.getZaehlerstandEinspeisung()).isNull();
    }

    /** Die Liste läuft unter dem {@code orgFilter} und zeigt nur Zeilen des eigenen Mandanten (FR-2.2). */
    @Test
    void findAllByOrderByLetzterEmpfangDesc_UnterOrgFilter_NurEigenerMandant() {
        upsert(Instant.parse("2026-10-25T08:00:00Z"), MesspunktEmpfangErgebnis.GESPEICHERT, null, "{}");
        messpunktEmpfangRepository.upsert(ANDERE_ORG_ID, "MP-FREMD", "GPLUG", Instant.parse("2026-10-25T08:00:00Z"),
                null, null, "UNBEKANNTER_MESSPUNKT", null, "{}");
        entityManager.flush();
        entityManager.clear();

        aktiviereOrgFilter(entityManager, ORG_ID);
        List<MesspunktEmpfang> zeilen = messpunktEmpfangRepository.findAllByOrderByLetzterEmpfangDesc();

        assertThat(zeilen).extracting(MesspunktEmpfang::getMesspunkt).containsExactly("MP-001");
    }

    /**
     * Nach dem Löschen legt die nächste Meldung die Zeile neu an: Zähler wieder 1, neuer erster
     * Empfang (FR-3.1).
     */
    @Test
    void upsert_NachLoeschen_LegtZeileNeuAn() {
        Instant erste = Instant.parse("2026-10-25T08:00:00Z");
        Instant nachLoeschen = Instant.parse("2026-10-25T09:00:00Z");
        upsert(erste, MesspunktEmpfangErgebnis.GESPEICHERT, null, "{}");
        upsert(erste.plusSeconds(60), MesspunktEmpfangErgebnis.GESPEICHERT, null, "{}");
        messpunktEmpfangRepository.deleteAll(messpunktEmpfangRepository.findAll());
        entityManager.flush();

        upsert(nachLoeschen, MesspunktEmpfangErgebnis.GESPEICHERT, null, "{}");

        MesspunktEmpfang zeile = messpunktEmpfangRepository.findAll().get(0);
        assertThat(zeile.getAnzahlMeldungen()).isEqualTo(1);
        assertThat(zeile.getErsterEmpfang()).isEqualTo(nachLoeschen);
    }

    /**
     * {@code findFirstById} läuft unter dem {@code orgFilter}: Eine Zeile eines fremden Mandanten ist
     * nicht zu finden — Grundlage für 404 bei {@code DELETE} und {@code GET …/letzte-meldung}.
     */
    @Test
    void findFirstById_FremdeZeileUnterOrgFilter_Leer() {
        upsert(Instant.parse("2026-10-25T08:00:00Z"), MesspunktEmpfangErgebnis.GESPEICHERT, null, "{}");
        Long id = messpunktEmpfangRepository.findAll().get(0).getId();

        aktiviereOrgFilter(entityManager, ANDERE_ORG_ID);
        assertThat(messpunktEmpfangRepository.findFirstById(id)).isEmpty();

        aktiviereOrgFilter(entityManager, ORG_ID);
        assertThat(messpunktEmpfangRepository.findFirstById(id)).isPresent();
    }

    // ==================== loescheVerwaisteVor (FR-3.2) ====================

    private void empfang(long orgId, String messpunkt, Instant letzterEmpfang) {
        messpunktEmpfangRepository.upsert(orgId, messpunkt, "PI_GATEWAY", letzterEmpfang,
                BigDecimal.ONE, BigDecimal.ZERO, "UNBEKANNTER_MESSPUNKT", null, "{}");
    }

    private void einheit(long orgId, String messpunkt, EinheitTyp typ) {
        Einheit einheit = new Einheit("Einheit " + messpunkt, typ);
        einheit.setOrgId(orgId);
        einheit.setMesspunkt(messpunkt);
        einheitRepository.save(einheit);
    }

    private List<String> verbleibend() {
        entityManager.clear();
        return messpunktEmpfangRepository.findAll().stream()
                .map(m -> m.getOrgId() + "/" + m.getMesspunkt()).sorted().toList();
    }

    @Test
    void loescheVerwaisteVor_LoeschtNurZeilenOhneEinheitStriktVorDerGrenze() {
        Instant grenze = Instant.parse("2026-09-25T09:00:00Z");
        empfang(ORG_ID, "alt-ohne-einheit", grenze.minusSeconds(1));
        empfang(ORG_ID, "genau-grenze", grenze);
        empfang(ORG_ID, "jung-ohne-einheit", grenze.plusSeconds(1));
        empfang(ORG_ID, "alt-mit-einheit", grenze.minus(10, ChronoUnit.DAYS));
        einheit(ORG_ID, "alt-mit-einheit", EinheitTyp.CONSUMER);
        // Eine Ladestation traegt eine RFID, keine Zaehlerkennung - sie zaehlt nicht als Einheit
        empfang(ORG_ID, "alt-nur-ladestation", grenze.minusSeconds(1));
        einheit(ORG_ID, "alt-nur-ladestation", EinheitTyp.LADESTATION);
        // Ueber alle Mandanten; eine Einheit zaehlt nur im eigenen Mandanten
        empfang(ANDERE_ORG_ID, "alt-ohne-einheit", grenze.minusSeconds(1));
        empfang(ANDERE_ORG_ID, "alt-mit-einheit", grenze.minusSeconds(1));
        entityManager.flush();

        int geloescht = messpunktEmpfangRepository.loescheVerwaisteVor(grenze);

        assertThat(geloescht).isEqualTo(4);
        assertThat(verbleibend()).containsExactly(
                ORG_ID + "/alt-mit-einheit",
                ORG_ID + "/genau-grenze",
                ORG_ID + "/jung-ohne-einheit");
    }
}
