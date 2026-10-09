package ch.nacht.repository;

import ch.nacht.AbstractIntegrationTest;
import ch.nacht.entity.Einheit;
import ch.nacht.entity.EinheitTyp;
import ch.nacht.entity.Messwerte;
import ch.nacht.entity.Quelle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Integrationstest des einmaligen Umstellungsskripts {@code scripts/messwerte-zeit-intervallbeginn.sql}
 * (Specs/Messwerte-Zeitkonvention.md, FR-5).
 *
 * <p><b>Laeuft ausschliesslich im Testcontainer</b> ({@link AbstractIntegrationTest}) — nie gegen
 * die lokale Entwicklungsdatenbank oder Hene. Das Skript wird <b>unveraendert und als Ganzes</b>
 * per JDBC ausgefuehrt, wie {@code psql} es tut: ein einziger {@code DO}-Block. {@code ScriptUtils}
 * truege an jedem {@code ;} auseinander und zerlegte den Block.
 *
 * <p><b>Nicht transaktional</b> ({@link Propagation#NOT_SUPPORTED}): Bricht das Skript mit
 * {@code RAISE EXCEPTION} ab, waere eine umschliessende Testtransaktion verdorben, und „Datenbank
 * unveraendert" liesse sich nicht mehr pruefen. Jede Anweisung laeuft deshalb in Autocommit; die
 * Testdaten werden selbst angelegt und in {@link #tearDown()} geloescht.
 *
 * <p><b>Der Container ist fuer alle ITs geteilt.</b> Deshalb setzt {@link #tearDown()} auch den
 * Spaltenkommentar auf {@code zev.messwerte.zeit} zurueck — er ist die Wiederholungssperre des
 * Skripts, und ein stehengebliebener Kommentar liesse jeden spaeteren Lauf „Bereits umgestellt"
 * melden. Geprueft wird nur auf den eigenen Einheiten; fremde Zeilen im Schema stoeren nicht.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MesswerteZeitUmstellungSkriptIT extends AbstractIntegrationTest {

    /** Arbeitsverzeichnis von Failsafe ist das Modulverzeichnis {@code backend-service}. */
    private static final Path SKRIPT = Path.of("..", "scripts", "messwerte-zeit-intervallbeginn.sql");

    private static final Long ORG_ID = 990_001L;

    private static final String KOMMENTAR_NACH_UMSTELLUNG =
            "Beginn des 15-Minuten-Intervalls, Ortszeit Europe/Zurich ohne Zone";

    /** Ein Tag, an dem nur Testdaten dieser Klasse liegen. */
    private static final LocalDateTime T = LocalDateTime.of(2031, 3, 14, 10, 0);

    @Autowired
    private DataSource dataSource;

    @Autowired
    private EinheitRepository einheitRepository;

    @Autowired
    private MesswerteRepository messwerteRepository;

    private Einheit einheitA;
    private Einheit einheitB;

    @BeforeEach
    void setUp() throws SQLException {
        // Ein stehengebliebener Kommentar (abgebrochener frueherer Lauf) liesse jeden Fall hier
        // "Bereits umgestellt" melden.
        setzeKommentarZurueck();

        einheitA = neueEinheit("Skript-IT Wohnung A", EinheitTyp.CONSUMER);
        einheitB = neueEinheit("Skript-IT Solaranlage B", EinheitTyp.PRODUCER);
    }

    @AfterEach
    void tearDown() throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM zev.messwerte WHERE einheit_id IN (?, ?)")) {
            ps.setLong(1, einheitA.getId());
            ps.setLong(2, einheitB.getId());
            ps.executeUpdate();
        }
        einheitRepository.deleteAllById(List.of(einheitA.getId(), einheitB.getId()));
        setzeKommentarZurueck();
    }

    // ==================== Umstellung ====================

    /**
     * Alle MQTT-Zeilen wandern um 15 Minuten zurueck — mit unveraenderten Werten
     * ({@code total}, {@code zev}, {@code zev_calculated}). Anzahl und Summe je Einheit bleiben
     * gleich.
     *
     * <p>Aufeinanderfolgende MQTT-Zeilen (10:15 → 10:00, waehrend 10:15 schon belegt war) sind
     * <b>keine</b> Kollision: Sie wandern gemeinsam.
     */
    @Test
    void skript_MqttZeilen_WerdenUmEineViertelstundeZurueckverschoben() throws Exception {
        speichere(einheitA, T.plusMinutes(15), 1.25, 0.5, 0.75, Quelle.MQTT);
        speichere(einheitA, T.plusMinutes(30), 2.5, 1.0, 1.5, Quelle.MQTT);
        speichere(einheitA, T.plusMinutes(45), -3.75, -3.75, null, Quelle.MQTT);
        long summeVorher = summeTotal(einheitA);
        long mqttGesamt = zaehleMqttGesamt();

        List<String> meldungen = fuehreSkriptAus();

        List<Zeile> nachher = zeilen(einheitA);
        assertThat(nachher).containsExactly(
                new Zeile(T, 1.25, 0.5, 0.75, "MQTT"),
                new Zeile(T.plusMinutes(15), 2.5, 1.0, 1.5, "MQTT"),
                new Zeile(T.plusMinutes(30), -3.75, -3.75, null, "MQTT"));
        assertThat(summeTotal(einheitA)).isEqualTo(summeVorher);
        assertThat(spaltenkommentar()).isEqualTo(KOMMENTAR_NACH_UMSTELLUNG);
        // Die Kontrolle aus NFR-3: n in der NOTICE = Anzahl MQTT-Zeilen
        assertThat(meldungen).anyMatch(m ->
                m.contains(mqttGesamt + " MQTT-Messwerte auf den Intervallbeginn umgestellt"));
    }

    /**
     * CSV-Zeilen bleiben, wo sie sind. Eine CSV-Zeile einer <b>anderen</b> Einheit genau
     * 15 Minuten vor einer MQTT-Zeile ist keine Kollision — verglichen wird je Einheit.
     */
    @Test
    void skript_CsvZeilen_BleibenUnveraendert() throws Exception {
        speichere(einheitA, T.plusMinutes(15), 1.0, 0.0, null, Quelle.MQTT);
        speichere(einheitB, T, -4.0, -4.0, null, Quelle.CSV);
        speichere(einheitB, T.plusMinutes(15), -5.0, -5.0, null, Quelle.CSV);
        // CSV derselben Einheit, aber nicht auf dem Ziel der MQTT-Zeile
        speichere(einheitA, T.minusDays(1), 7.0, 0.0, 7.0, Quelle.CSV);

        fuehreSkriptAus();

        assertThat(zeilen(einheitB)).containsExactly(
                new Zeile(T, -4.0, -4.0, null, "CSV"),
                new Zeile(T.plusMinutes(15), -5.0, -5.0, null, "CSV"));
        assertThat(zeilen(einheitA)).containsExactly(
                new Zeile(T.minusDays(1), 7.0, 0.0, 7.0, "CSV"),
                new Zeile(T, 1.0, 0.0, null, "MQTT"));
    }

    // ==================== Abbrueche ====================

    /**
     * Faellt eine MQTT-Zeile auf eine CSV-Zeile derselben Einheit, bricht das Skript ab — mit
     * Anzahl und Beispiel. Weder Daten noch Spaltenkommentar sind veraendert: Der DO-Block ist
     * atomar.
     */
    @Test
    void skript_KollisionMitCsvZeile_BrichtAbUndAendertNichts() throws Exception {
        speichere(einheitA, T, 9.0, 0.0, 9.0, Quelle.CSV);          // Ziel der MQTT-Zeile
        speichere(einheitA, T.plusMinutes(15), 1.0, 0.0, null, Quelle.MQTT);
        speichere(einheitA, T.plusMinutes(30), 2.0, 0.0, null, Quelle.MQTT); // kollisionsfrei
        List<Zeile> vorher = zeilen(einheitA);

        SQLException fehler = catchThrowableOfType(SQLException.class, this::fuehreSkriptAus);

        assertThat((Throwable) fehler).isNotNull();
        assertThat(fehler.getMessage())
                .contains("1 MQTT-Messwerte fielen auf bestehende Nicht-MQTT-Werte")
                .contains("Einheit " + einheitA.getId() + ", 2031-03-14 10:00");
        assertThat(zeilen(einheitA)).containsExactlyElementsOf(vorher);
        assertThat(spaltenkommentar()).isNull();
    }

    /**
     * Ein zweiter Lauf bricht mit „Bereits umgestellt" ab und verschiebt <b>nichts</b> ein
     * zweites Mal — sonst lagen die Stempel eine Viertelstunde vor dem Beginn.
     */
    @Test
    void skript_ZweiterLauf_BrichtAbUndVerschiebtNichtErneut() throws Exception {
        speichere(einheitA, T.plusMinutes(15), 1.0, 0.0, null, Quelle.MQTT);
        fuehreSkriptAus();
        List<Zeile> nachErstemLauf = zeilen(einheitA);
        assertThat(nachErstemLauf).extracting(Zeile::zeit).containsExactly(T);

        SQLException fehler = catchThrowableOfType(SQLException.class, this::fuehreSkriptAus);

        assertThat((Throwable) fehler).isNotNull();
        assertThat(fehler.getMessage()).contains("Bereits umgestellt");
        assertThat(zeilen(einheitA)).containsExactlyElementsOf(nachErstemLauf);
        assertThat(spaltenkommentar()).isEqualTo(KOMMENTAR_NACH_UMSTELLUNG);
    }

    /**
     * Ohne MQTT-Zeilen der eigenen Einheiten laeuft das Skript fehlerfrei durch und setzt den
     * Kommentar — eine Installation, die nur CSV kennt, ist danach ebenfalls als umgestellt
     * markiert.
     */
    @Test
    void skript_OhneMqttZeilen_LaeuftDurchUndSetztKommentar() throws Exception {
        speichere(einheitA, T, 3.0, 0.0, 3.0, Quelle.CSV);
        speichere(einheitA, T.plusMinutes(15), 4.0, 0.0, 4.0, Quelle.CSV);
        List<Zeile> vorher = zeilen(einheitA);

        fuehreSkriptAus();

        assertThat(zeilen(einheitA)).containsExactlyElementsOf(vorher);
        assertThat(spaltenkommentar()).isEqualTo(KOMMENTAR_NACH_UMSTELLUNG);
    }

    // ==================== Hilfsmittel ====================

    /** Eine Zeile aus {@code zev.messwerte}, wie sie die Pruefungen vergleichen. */
    private record Zeile(LocalDateTime zeit, Double total, Double zev, Double zevCalculated,
                         String quelle) {
    }

    /**
     * Fuehrt die Skriptdatei <b>unveraendert und als eine Anweisung</b> aus — in Autocommit, wie
     * {@code psql} ohne {@code BEGIN}. Liefert die {@code RAISE NOTICE}-Meldungen.
     *
     * <p>Die Verbindung im Testcontainer laeuft mit {@code client_min_messages = warning}; ohne
     * Anheben kaeme die NOTICE gar nicht beim Client an. Danach wird die Session-Einstellung
     * zurueckgesetzt — die Verbindung geht in den Pool zurueck.
     */
    private List<String> fuehreSkriptAus() throws IOException, SQLException {
        String sql = Files.readString(SKRIPT, StandardCharsets.UTF_8);
        List<String> meldungen = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement()) {
            c.setAutoCommit(true);
            st.execute("SET client_min_messages = notice");
            st.clearWarnings();
            try {
                st.execute(sql);
                for (SQLWarning w = st.getWarnings(); w != null; w = w.getNextWarning()) {
                    meldungen.add(w.getMessage());
                }
            } finally {
                st.execute("RESET client_min_messages");
            }
        }
        return meldungen;
    }

    private Einheit neueEinheit(String name, EinheitTyp typ) {
        Einheit einheit = new Einheit(name, typ);
        einheit.setOrgId(ORG_ID);
        return einheitRepository.save(einheit);
    }

    private void speichere(Einheit einheit, LocalDateTime zeit, double total, double zev,
                           Double zevCalculated, Quelle quelle) {
        Messwerte m = new Messwerte();
        m.setOrgId(ORG_ID);
        m.setEinheit(einheit);
        m.setZeit(zeit);
        m.setTotal(total);
        m.setZev(zev);
        m.setZevCalculated(zevCalculated);
        m.setQuelle(quelle);
        messwerteRepository.save(m);
    }

    private List<Zeile> zeilen(Einheit einheit) throws SQLException {
        List<Zeile> zeilen = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT zeit, total, zev, zev_calculated, quelle FROM zev.messwerte "
                             + "WHERE einheit_id = ? ORDER BY zeit")) {
            ps.setLong(1, einheit.getId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    zeilen.add(new Zeile(rs.getObject("zeit", LocalDateTime.class),
                            rs.getObject("total", Double.class),
                            rs.getObject("zev", Double.class),
                            rs.getObject("zev_calculated", Double.class),
                            rs.getString("quelle")));
                }
            }
        }
        return zeilen;
    }

    /** Summe {@code total} in Tausendsteln — ganzzahlig, damit der Vergleich exakt ist. */
    private long summeTotal(Einheit einheit) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COALESCE(round(sum(total) * 1000), 0) FROM zev.messwerte WHERE einheit_id = ?")) {
            ps.setLong(1, einheit.getId());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private long zaehleMqttGesamt() throws SQLException {
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT count(*) FROM zev.messwerte WHERE quelle = 'MQTT'")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private String spaltenkommentar() throws SQLException {
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT col_description('zev.messwerte'::regclass, "
                             + "(SELECT attnum FROM pg_attribute WHERE attrelid = 'zev.messwerte'::regclass "
                             + "AND attname = 'zeit'))")) {
            rs.next();
            return rs.getString(1);
        }
    }

    private void setzeKommentarZurueck() throws SQLException {
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement()) {
            st.execute("COMMENT ON COLUMN zev.messwerte.zeit IS NULL");
        }
    }
}
