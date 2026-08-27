package ch.nacht.repository;

import ch.nacht.AbstractIntegrationTest;
import ch.nacht.entity.Preiszeitreihe;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integrationstests für {@link PreiszeitreiheRepository} mit Testcontainers.
 *
 * <p>Beide Abfragen dieses Repositories sind von Hand geschrieben und nur gegen eine echte
 * Postgres-Instanz prüfbar:
 * <ul>
 *   <li>{@code upsert} ist natives SQL mit {@code ON CONFLICT (zeit_von)}. Davon hängt ab, dass ein
 *       zweiter Abruf desselben Tages <b>keine</b> Duplikate erzeugt und eine Preiskorrektur der
 *       Quelle den alten Wert ersetzt. Für Hibernate ist der Ausdruck undurchsichtig.</li>
 *   <li>{@code findByZeitraum} hat eine <b>ausschliessende</b> Obergrenze. Diese Kante bestimmt, ob
 *       der Wert an der Tagesgrenze in zwei Abfragen fällt oder in keine.</li>
 * </ul>
 *
 * <p>Dazu die CHECK-Constraints aus V129: Sie sind die letzte Verteidigungslinie hinter der
 * Prüfung im Service und sollen halten, auch wenn jemand später an der Abfrage vorbeischreibt.
 *
 * <p><b>Kein Mandantenfilter:</b> Die Entity trägt bewusst kein {@code org_id} (Marktdaten,
 * Specs/Preiszeitreihe.md FR-2) — hier gibt es also nichts zu trennen und nichts einzuschalten.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class PreiszeitreiheRepositoryIT extends AbstractIntegrationTest {

    private static final LocalDateTime T10_00 = LocalDateTime.of(2026, 8, 26, 10, 0);
    private static final LocalDateTime T10_15 = LocalDateTime.of(2026, 8, 26, 10, 15);
    private static final LocalDateTime T10_30 = LocalDateTime.of(2026, 8, 26, 10, 30);
    private static final LocalDateTime PUBLIKATION = LocalDateTime.of(2026, 8, 26, 8, 0);

    @Autowired
    private PreiszeitreiheRepository preiszeitreiheRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        angleichAnFlywaySchema();
        preiszeitreiheRepository.deleteAll();
    }

    // Bewusst KEIN @AfterEach: Nach einer Constraint-Verletzung ist die Transaktion in Postgres
    // abgebrochen ("current transaction is aborted"), und ein Aufraeumen darin scheitert selbst.
    // @DataJpaTest rollt jeden Test ohnehin zurueck; das deleteAll in setUp genuegt.

    /**
     * Ergänzt am Mapping-Schema den Spalten-Default, den {@code upsert} braucht und der nur in
     * {@code V129__Create_Preiszeitreihe.sql} steht.
     *
     * <p>Die Integrationstests laufen mit {@code ddl-auto=create-drop}: Das Schema entsteht aus dem
     * Mapping, Hibernate vergibt die ID selbst und legt deshalb keinen Default an. Das native INSERT
     * der Upsert-Abfrage liefert aber keine ID mit und liefe ohne diesen Angleich in eine
     * NOT-NULL-Verletzung. Dasselbe Vorgehen wie in {@code DebitorRepositoryIT}.
     *
     * <p>DDL ist in Postgres transaktional: Die Änderung verschwindet mit dem Rollback des Tests.
     */
    private void angleichAnFlywaySchema() {
        entityManager.createNativeQuery("ALTER TABLE zev.preiszeitreihe "
                        + "ALTER COLUMN id SET DEFAULT nextval('zev.preiszeitreihe_seq')")
                .executeUpdate();
    }

    private Preiszeitreihe speichere(LocalDateTime von, LocalDateTime bis, String preis) {
        return preiszeitreiheRepository.saveAndFlush(
                new Preiszeitreihe(von, bis, new BigDecimal(preis), PUBLIKATION));
    }

    // ==================== upsert ====================

    @Test
    void upsert_NeuerWert_LegtAn() {
        preiszeitreiheRepository.upsert(T10_00, T10_15, new BigDecimal("0.13800"), PUBLIKATION);
        entityManager.clear();

        List<Preiszeitreihe> alle = preiszeitreiheRepository.findAll();
        assertThat(alle).hasSize(1);
        assertThat(alle.get(0).getZeitVon()).isEqualTo(T10_00);
        assertThat(alle.get(0).getZeitBis()).isEqualTo(T10_15);
        assertThat(alle.get(0).getPreis()).isEqualByComparingTo("0.13800");
        assertThat(alle.get(0).getPublikation()).isEqualTo(PUBLIKATION);
        assertThat(alle.get(0).getAktualisiertAm()).isNotNull();
    }

    /**
     * Der Kern des Upsert: Ein zweiter Abruf desselben Fensters erzeugt <b>keine</b> zweite Zeile.
     * Ohne diese Zusicherung wüchse die Reihe mit jedem Lauf um ein Vielfaches.
     */
    @Test
    void upsert_ZweimalDerselbeBeginn_LegtNurEineZeileAn() {
        preiszeitreiheRepository.upsert(T10_00, T10_15, new BigDecimal("0.13800"), PUBLIKATION);
        preiszeitreiheRepository.upsert(T10_00, T10_15, new BigDecimal("0.13800"), PUBLIKATION);
        entityManager.clear();

        assertThat(preiszeitreiheRepository.findAll()).hasSize(1);
    }

    @Test
    void upsert_Preiskorrektur_UeberschreibtPreisUndPublikation() {
        preiszeitreiheRepository.upsert(T10_00, T10_15, new BigDecimal("0.13800"), PUBLIKATION);
        LocalDateTime neuePublikation = PUBLIKATION.plusHours(6);

        preiszeitreiheRepository.upsert(T10_00, T10_15, new BigDecimal("0.09900"), neuePublikation);
        entityManager.clear();

        List<Preiszeitreihe> alle = preiszeitreiheRepository.findAll();
        assertThat(alle).hasSize(1);
        assertThat(alle.get(0).getPreis()).isEqualByComparingTo("0.09900");
        assertThat(alle.get(0).getPublikation()).isEqualTo(neuePublikation);
    }

    @Test
    void upsert_GeaendertesIntervallende_WirdUebernommen() {
        preiszeitreiheRepository.upsert(T10_00, T10_15, new BigDecimal("0.13800"), PUBLIKATION);

        preiszeitreiheRepository.upsert(T10_00, T10_30, new BigDecimal("0.13800"), PUBLIKATION);
        entityManager.clear();

        assertThat(preiszeitreiheRepository.findAll().get(0).getZeitBis()).isEqualTo(T10_30);
    }

    @Test
    void upsert_OhnePublikation_SpeichertNull() {
        preiszeitreiheRepository.upsert(T10_00, T10_15, new BigDecimal("0.13800"), null);
        entityManager.clear();

        assertThat(preiszeitreiheRepository.findAll().get(0).getPublikation()).isNull();
    }

    @Test
    void upsert_VerschiedeneBeginne_LegenMehrereZeilenAn() {
        preiszeitreiheRepository.upsert(T10_00, T10_15, new BigDecimal("0.13800"), PUBLIKATION);
        preiszeitreiheRepository.upsert(T10_15, T10_30, new BigDecimal("0.14200"), PUBLIKATION);
        entityManager.clear();

        assertThat(preiszeitreiheRepository.findAll()).hasSize(2);
    }

    // ==================== findByZeitraum ====================

    @Test
    void findByZeitraum_LiefertWerteInnerhalbDerSpanne() {
        speichere(T10_00, T10_15, "0.13800");
        speichere(T10_15, T10_30, "0.14200");

        List<Preiszeitreihe> treffer = preiszeitreiheRepository.findByZeitraum(T10_00, T10_30);

        assertThat(treffer).hasSize(2);
    }

    @Test
    void findByZeitraum_UntereGrenzeIstEinschliesslich() {
        speichere(T10_00, T10_15, "0.13800");

        assertThat(preiszeitreiheRepository.findByZeitraum(T10_00, T10_30)).hasSize(1);
    }

    /**
     * Die Obergrenze ist <b>ausschliessend</b>. Der Service übergibt deshalb den Beginn des
     * Folgetags; wäre sie einschliessend, erschiene der Wert an der Tagesgrenze in zwei Abfragen.
     */
    @Test
    void findByZeitraum_ObereGrenzeIstAusschliesslich() {
        speichere(T10_00, T10_15, "0.13800");
        speichere(T10_15, T10_30, "0.14200");

        List<Preiszeitreihe> treffer = preiszeitreiheRepository.findByZeitraum(T10_00, T10_15);

        assertThat(treffer).hasSize(1);
        assertThat(treffer.get(0).getZeitVon()).isEqualTo(T10_00);
    }

    @Test
    void findByZeitraum_WertDavor_WirdNichtGeliefert() {
        speichere(T10_00, T10_15, "0.13800");

        assertThat(preiszeitreiheRepository.findByZeitraum(T10_15, T10_30)).isEmpty();
    }

    @Test
    void findByZeitraum_SortiertAufsteigend() {
        speichere(T10_15, T10_30, "0.14200");
        speichere(T10_00, T10_15, "0.13800");

        List<Preiszeitreihe> treffer =
                preiszeitreiheRepository.findByZeitraum(T10_00, T10_30.plusMinutes(15));

        assertThat(treffer).extracting(Preiszeitreihe::getZeitVon)
                .containsExactly(T10_00, T10_15);
    }

    @Test
    void findByZeitraum_LeereSpanne_LiefertLeereListe() {
        speichere(T10_00, T10_15, "0.13800");

        assertThat(preiszeitreiheRepository.findByZeitraum(
                T10_00.plusDays(1), T10_30.plusDays(1))).isEmpty();
    }

    // ==================== Constraints aus V129 ====================

    @Test
    void speichern_ZweimalDerselbeBeginn_VerletztUniqueConstraint() {
        speichere(T10_00, T10_15, "0.13800");

        assertThatThrownBy(() -> speichere(T10_00, T10_30, "0.14200"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void speichern_EndeVorBeginn_VerletztCheckConstraint() {
        assertThatThrownBy(() -> speichere(T10_15, T10_00, "0.13800"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
      * <b>Negative Preise sind zulässig</b> und müssen gespeichert werden.
      *
      * <p>Bei Überangebot kostet das Einspeisen Geld statt Ertrag zu bringen. V129 hatte hier
      * fälschlich {@code CHECK (preis >= 0)}; V132 hat den Constraint entfernt. Dieser Test hält
      * fest, dass er nicht zurückkommt — sonst fiele der Abruf genau in den interessantesten
      * Stunden aus.
      */
     @Test
     void upsert_NegativerPreis_WirdGespeichert() {
         preiszeitreiheRepository.upsert(T10_00, T10_15, new BigDecimal("-0.02500"), PUBLIKATION);
         entityManager.flush();
         entityManager.clear();

         assertThat(preiszeitreiheRepository.findAll().get(0).getPreis())
                 .isEqualByComparingTo("-0.02500");
     }

    @Test
    void upsert_EndeVorBeginn_VerletztCheckConstraint() {
        assertThatThrownBy(() -> {
            preiszeitreiheRepository.upsert(T10_15, T10_00, new BigDecimal("0.13800"), PUBLIKATION);
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
     void speichern_NegativerPreis_WirdGespeichert() {
         // Auch über JPA: keine Bean-Validierung auf das Vorzeichen (siehe upsert-Test oben).
         Preiszeitreihe gespeichert = speichere(T10_00, T10_15, "-0.02500");

         assertThat(gespeichert.getPreis()).isEqualByComparingTo("-0.02500");
     }

    @Test
    void speichern_PreisNull_IstErlaubt() {
        // 0.00000 ist ein gueltiger Preis - Einspeisung kann unentgeltlich sein.
        Preiszeitreihe gespeichert = speichere(T10_00, T10_15, "0.00000");

        assertThat(gespeichert.getId()).isNotNull();
        assertThat(gespeichert.getPreis()).isEqualByComparingTo("0.00000");
    }
}
