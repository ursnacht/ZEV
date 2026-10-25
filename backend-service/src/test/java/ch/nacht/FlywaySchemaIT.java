package ch.nacht;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integrationstest gegen das Schema <b>aus den Flyway-Migrationen</b>, nicht aus dem Mapping.
 *
 * <p><b>Warum eigenes Schema.</b> Die uebrigen ITs laufen mit {@code ddl-auto=create-drop}: Hibernate
 * baut das Schema aus den Entities, und Regeln, die nur in den Migrationen stehen, fehlen dort —
 * etwa {@code ON DELETE CASCADE} auf {@code tarifposition} und {@code geraetezustand}
 * ({@code Geraetezustand} fuehrt {@code einheit_id} sogar ohne Beziehung, also ganz ohne FK). Ein
 * Test des Loeschverhaltens dort bewiese nichts ueber die produktive Datenbank.
 *
 * <p><b>Aufbau.</b> Im geteilten Testcontainer wird eine eigene, leere Datenbank angelegt und mit
 * allen Migrationen befuellt — so, wie Flyway sie beim Start einer neuen Installation einspielt.
 * Die Datenbank der uebrigen ITs bleibt unberuehrt. Kein Spring-Kontext: reines JDBC. Liegt im Paket
 * {@code ch.nacht}, weil es den Container aus {@link AbstractIntegrationTest} direkt braucht.
 *
 * <p>Specs/Einheitenverwaltung.md, Abschnitt 5 (Loeschen referenzierter Daten).
 * Specs/Gerätezustand.md, Abschnitt 3 (CHECK, Unique-Schluessel und Index von {@code geraetezustand}).
 * Specs/Messpunkt-Empfang.md, Datenmodell (CHECKs und Unique-Constraint von {@code messpunkt_empfang}).
 */
class FlywaySchemaIT extends AbstractIntegrationTest {

    private static final String DATENBANK = "zev_flyway_schema_it";

    private static String url;

    private static MigrateResult migration;

    private Connection verbindung;

    private long orgId;

    @BeforeAll
    static void migriereLeereDatenbank() throws SQLException {
        try (Connection admin = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement stmt = admin.createStatement()) {
            stmt.execute("DROP DATABASE IF EXISTS " + DATENBANK);
            stmt.execute("CREATE DATABASE " + DATENBANK);
        }
        url = String.format("jdbc:postgresql://%s:%d/%s",
                postgres.getHost(), postgres.getMappedPort(5432), DATENBANK);
        migration = Flyway.configure()
                .dataSource(url, postgres.getUsername(), postgres.getPassword())
                .schemas("zev")
                .createSchemas(true)
                .load()
                .migrate();
    }

    @BeforeEach
    void setUp() throws SQLException {
        verbindung = DriverManager.getConnection(url, postgres.getUsername(), postgres.getPassword());
        orgId = einfuegen("INSERT INTO zev.organisation (keycloak_org_id, name) VALUES (?, 'Test') RETURNING id",
                UUID.randomUUID());
    }

    @AfterEach
    void tearDown() throws SQLException {
        verbindung.close();
    }

    @Test
    void alleMigrationenLaufenAufEinerLeerenDatenbank() {
        assertThat(migration.success).isTrue();
        assertThat(migration.migrationsExecuted).isPositive();
    }

    /**
     * Ohne Mieter darf eine Einheit geloescht werden; ihre Tarifpositionen und Geraetezustaende
     * haengen nur an ihr und verschwinden mit ihr (FK {@code ON DELETE CASCADE}).
     */
    @Test
    void einheitLoeschen_LoeschtTarifpositionenUndGeraetezustaendeMit() throws SQLException {
        long einheit = einheit("Ladestation", "LADESTATION");
        long andere = einheit("Andere Ladestation", "LADESTATION");
        long tarif = einfuegen("INSERT INTO zev.tarif (org_id, bezeichnung, tariftyp, preis, gueltig_von, gueltig_bis) "
                + "VALUES (?, 'Ladestrom', 'LADESTROM', 0.30, DATE '2026-01-01', DATE '2026-12-31') RETURNING id", orgId);
        tarifposition(einheit, tarif);
        tarifposition(andere, tarif);
        geraetezustand(einheit);
        geraetezustand(andere);

        aendern("DELETE FROM zev.einheit WHERE id = ?", einheit);

        assertThat(zaehle("zev.tarifposition", einheit)).isZero();
        assertThat(zaehle("zev.geraetezustand", einheit)).isZero();
        assertThat(zaehle("zev.tarifposition", andere)).isEqualTo(1);
        assertThat(zaehle("zev.geraetezustand", andere)).isEqualTo(1);
    }

    /**
     * Gegenstueck: Eine zugeordnete Einheit blockiert die Datenbank ({@code ON DELETE RESTRICT}).
     * {@code EinheitService.deleteEinheit} prueft das vorher, um eine verwertbare Meldung zu geben;
     * der FK ist die letzte Sicherung.
     */
    @Test
    void einheitLoeschen_MitZugeordnetemMieter_WirdVonDerDatenbankAbgewiesen() throws SQLException {
        long einheit = einheit("Wohnung", "CONSUMER");
        long mieter = einfuegen("INSERT INTO zev.mieter (org_id, name, strasse, plz, ort, mietbeginn) "
                + "VALUES (?, 'Muster', 'Teststrasse 1', '3000', 'Bern', DATE '2026-01-01') RETURNING id", orgId);
        aendern("INSERT INTO zev.mieter_einheit (id, org_id, mieter_id, einheit_id) "
                + "VALUES (nextval('zev.mieter_einheit_seq'), " + orgId + ", " + mieter + ", ?)", einheit);

        assertThatThrownBy(() -> aendern("DELETE FROM zev.einheit WHERE id = ?", einheit))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("fk_mieter_einheit_einheit");
    }

    /**
     * Der CHECK {@code ck_geraetezustand_prozent} weist einen Ladezustand ueber 100 % ab — auch an
     * der Anwendung vorbei (Specs/Gerätezustand.md, FR-2). Die Registry im Code prueft das zwar
     * schon; der CHECK ist die Sicherung fuer SQL-Importe und einen kuenftigen zweiten Schreibpfad.
     */
    @Test
    void geraetezustand_DirektesInsertSocUeber100_WirdVonDerDatenbankAbgewiesen() throws SQLException {
        long speicher = einheit("Batterie", "SPEICHER");

        assertThatThrownBy(() -> aendern("INSERT INTO zev.geraetezustand (org_id, einheit_id, zeit, groesse, wert) "
                + "VALUES (" + orgId + ", ?, TIMESTAMP '2026-10-18 12:00', 'SOC', 101)", speicher))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("ck_geraetezustand_prozent");
        assertThatThrownBy(() -> aendern("INSERT INTO zev.geraetezustand (org_id, einheit_id, zeit, groesse, wert) "
                + "VALUES (" + orgId + ", ?, TIMESTAMP '2026-10-18 12:00', 'SOC', -1)", speicher))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("ck_geraetezustand_prozent");
        assertThat(zaehle("zev.geraetezustand", speicher)).isZero();
    }

    /**
     * Gegenprobe zum CHECK: Die Grenzen 0 und 100 sind erlaubt, und eine Grad-Celsius-Groesse
     * faellt nicht unter die Prozent-Regel.
     */
    @Test
    void geraetezustand_SocAufGrenzenUndAndereGroesse_WerdenAngenommen() throws SQLException {
        long speicher = einheit("Batterie", "SPEICHER");

        aendern("INSERT INTO zev.geraetezustand (org_id, einheit_id, zeit, groesse, wert) "
                + "VALUES (" + orgId + ", ?, TIMESTAMP '2026-10-18 12:00', 'SOC', 0)", speicher);
        aendern("INSERT INTO zev.geraetezustand (org_id, einheit_id, zeit, groesse, wert) "
                + "VALUES (" + orgId + ", ?, TIMESTAMP '2026-10-18 12:01', 'SOC', 100)", speicher);
        aendern("INSERT INTO zev.geraetezustand (org_id, einheit_id, zeit, groesse, wert) "
                + "VALUES (" + orgId + ", ?, TIMESTAMP '2026-10-18 12:00', 'TEMPERATUR', 120)", speicher);

        assertThat(zaehle("zev.geraetezustand", speicher)).isEqualTo(3);
    }

    /**
     * Der Upsert des Ingest stuetzt sich auf {@code uk_geraetezustand (einheit_id, zeit, groesse)}:
     * Ein zweiter Datensatz zu demselben Schluessel wird abgewiesen. Im Hibernate-Testschema der
     * uebrigen ITs fehlt der Constraint.
     */
    @Test
    void geraetezustand_DoppelterSchluessel_WirdVonDerDatenbankAbgewiesen() throws SQLException {
        long speicher = einheit("Batterie", "SPEICHER");
        geraetezustand(speicher);

        assertThatThrownBy(() -> geraetezustand(speicher))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("uk_geraetezustand");
    }

    /**
     * „Letzter Wert vor Zeitpunkt" (FR-5.1) ist ein Index-Scan ohne Sortierung — kein Seq-Scan
     * durch die Zeitreihe. Heutiger Betrieb: nur {@code SOC}.
     *
     * <p><b>Welcher Index</b>, ist mit nur einer Groesse offen: Der Unique-Index
     * {@code uk_geraetezustand (einheit_id, zeit, groesse)} bedient die Abfrage dann genauso gut
     * (rueckwaerts), und PostgreSQL waehlt ihn auch. Erst mit einer zweiten Groesse traegt
     * {@code idx_geraetezustand_letzter}, siehe naechster Test.
     *
     * <p>Die Abfrage entspricht der, die Spring Data aus
     * {@code findFirstByEinheitIdAndGroesseAndZeitLessThanOrderByZeitDesc} erzeugt.
     * {@code enable_seqscan = off} nimmt dem Planer die Ausrede einer kleinen Testtabelle; geprueft
     * wird so, dass ein Index fuer diese Abfrage <b>taugt</b> und gewaehlt wird.
     */
    @Test
    void geraetezustand_LetzterWertVorZeitpunkt_NurSoc_IndexScanOhneSort() throws SQLException {
        long speicher = einheit("Batterie", "SPEICHER");
        zeitreihe(speicher, "SOC");

        String plan = planLetzterWertVor(speicher);

        assertThat(plan)
                .contains("Index Scan")
                .doesNotContain("Seq Scan")
                .doesNotContain("Sort");
    }

    /**
     * Mit einer zweiten Groesse an derselben Einheit nutzt die Abfrage
     * {@code idx_geraetezustand_letzter (einheit_id, groesse, zeit DESC)}: Der Unique-Index fuehrt
     * {@code groesse} erst an dritter Stelle und muesste die fremden Zeilen ueberspringen.
     */
    @Test
    void geraetezustand_LetzterWertVorZeitpunkt_MehrereGroessen_NutztIdxGeraetezustandLetzter() throws SQLException {
        long speicher = einheit("Batterie", "SPEICHER");
        zeitreihe(speicher, "SOC");
        zeitreihe(speicher, "TEMPERATUR");
        zeitreihe(speicher, "SOH");

        String plan = planLetzterWertVor(speicher);

        assertThat(plan)
                .contains("idx_geraetezustand_letzter")
                .doesNotContain("Seq Scan")
                .doesNotContain("Sort");
    }

    // ==================== Messpunkt-Empfang (Specs/Messpunkt-Empfang.md, Datenmodell, V186) ====================

    /** Eine Empfangszeile direkt per SQL - an der Anwendung vorbei, damit die CHECKs greifen müssen. */
    private void empfang(String messpunkt, String quelle, long anzahl, String ergebnis, String grund)
            throws SQLException {
        try (PreparedStatement ps = verbindung.prepareStatement(
                "INSERT INTO zev.messpunkt_empfang (org_id, messpunkt, quelle, erster_empfang, letzter_empfang, "
                        + "anzahl_meldungen, ergebnis, grund) VALUES (?, ?, ?, now(), now(), ?, ?, ?)")) {
            ps.setLong(1, orgId);
            ps.setString(2, messpunkt);
            ps.setString(3, quelle);
            ps.setLong(4, anzahl);
            ps.setString(5, ergebnis);
            ps.setString(6, grund);
            ps.executeUpdate();
        }
    }

    private long zaehleEmpfang(String messpunkt) throws SQLException {
        try (PreparedStatement ps = verbindung.prepareStatement(
                "SELECT COUNT(*) FROM zev.messpunkt_empfang WHERE org_id = ? AND messpunkt = ?")) {
            ps.setLong(1, orgId);
            ps.setString(2, messpunkt);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    /** Gegenprobe zu den CHECKs: gültige Zeilen beider Quellen und aller Ergebnisse gehen durch. */
    @Test
    void messpunktEmpfang_GueltigeZeilen_WerdenAngenommen() throws SQLException {
        empfang("MP-1", "PI_GATEWAY", 1, "GESPEICHERT", null);
        empfang("MP-2", "GPLUG", 2, "UNBEKANNTER_MESSPUNKT", null);
        empfang("MP-3", "GPLUG", 1, "UNGUELTIG", "Payload nicht lesbar");

        assertThat(zaehleEmpfang("MP-1") + zaehleEmpfang("MP-2") + zaehleEmpfang("MP-3")).isEqualTo(3);
    }

    @Test
    void messpunktEmpfang_UnbekannteQuelle_WirdAbgewiesen() {
        assertThatThrownBy(() -> empfang("MP-1", "TASMOTA", 1, "GESPEICHERT", null))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("ck_messpunkt_empfang_quelle");
    }

    @Test
    void messpunktEmpfang_UnbekanntesErgebnis_WirdAbgewiesen() {
        assertThatThrownBy(() -> empfang("MP-1", "PI_GATEWAY", 1, "VERWORFEN", null))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("ck_messpunkt_empfang_ergebnis");
    }

    @Test
    void messpunktEmpfang_AnzahlNull_WirdAbgewiesen() {
        assertThatThrownBy(() -> empfang("MP-1", "PI_GATEWAY", 0, "GESPEICHERT", null))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("ck_messpunkt_empfang_anzahl");
    }

    /** Ein Grund genau dann, wenn die Meldung verworfen wurde - in beide Richtungen. */
    @Test
    void messpunktEmpfang_GrundNichtGenauBeiUngueltig_WirdAbgewiesen() {
        assertThatThrownBy(() -> empfang("MP-1", "PI_GATEWAY", 1, "UNGUELTIG", null))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("ck_messpunkt_empfang_grund");
        assertThatThrownBy(() -> empfang("MP-2", "PI_GATEWAY", 1, "GESPEICHERT", "Pflichtfeld fehlt"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("ck_messpunkt_empfang_grund");
    }

    /** Der Upsert stützt sich auf {@code (org_id, messpunkt)}; ein zweiter Datensatz wird abgewiesen. */
    @Test
    void messpunktEmpfang_DoppelterMesspunkt_WirdAbgewiesen() throws SQLException {
        empfang("MP-1", "PI_GATEWAY", 1, "GESPEICHERT", null);

        assertThatThrownBy(() -> empfang("MP-1", "GPLUG", 1, "GESPEICHERT", null))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("uq_messpunkt_empfang_org_messpunkt");
    }

    /**
     * Kein FK auf {@code zev.einheit} (FR-1.7): Ändern des Messpunkts und Löschen der Einheit lassen
     * die Empfangszeile stehen.
     */
    @Test
    void messpunktEmpfang_EinheitGeaendertUndGeloescht_ZeileBleibt() throws SQLException {
        long einheit = einheit("Wohnung", "CONSUMER");
        aendern("UPDATE zev.einheit SET messpunkt = 'MP-1' WHERE id = ?", einheit);
        empfang("MP-1", "PI_GATEWAY", 1, "GESPEICHERT", null);

        aendern("UPDATE zev.einheit SET messpunkt = 'MP-ANDERS' WHERE id = ?", einheit);
        assertThat(zaehleEmpfang("MP-1")).isEqualTo(1);

        aendern("DELETE FROM zev.einheit WHERE id = ?", einheit);
        assertThat(zaehleEmpfang("MP-1")).isEqualTo(1);
    }

    // ==================== Hilfsmethoden ====================

    private long einheit(String name, String typ) throws SQLException {
        return einfuegen("INSERT INTO zev.einheit (org_id, name, typ) VALUES (" + orgId + ", ?, '" + typ + "') RETURNING id",
                name);
    }

    private void tarifposition(long einheit, long tarif) throws SQLException {
        aendern("INSERT INTO zev.tarifposition (org_id, einheit_id, tarif_id, jahr, quartal, menge) "
                + "VALUES (" + orgId + ", ?, " + tarif + ", 2026, 1, 10)", einheit);
    }

    private void geraetezustand(long einheit) throws SQLException {
        aendern("INSERT INTO zev.geraetezustand (org_id, einheit_id, zeit, groesse, wert) "
                + "VALUES (" + orgId + ", ?, TIMESTAMP '2026-10-18 12:00', 'SOC', 50)", einheit);
    }

    /** 2000 Werte im Minutentakt ab dem 01.10.2026 fuer eine Groesse (Wert 0-99, also CHECK-konform). */
    private void zeitreihe(long einheit, String groesse) throws SQLException {
        try (PreparedStatement ps = verbindung.prepareStatement(
                "INSERT INTO zev.geraetezustand (org_id, einheit_id, zeit, groesse, wert) "
                        + "SELECT " + orgId + ", ?, TIMESTAMP '2026-10-01 00:00' + n * INTERVAL '1 minute', '"
                        + groesse + "', n % 100 FROM generate_series(0, 1999) AS n")) {
            ps.setLong(1, einheit);
            ps.executeUpdate();
        }
    }

    /** {@code EXPLAIN} der Abfrage „letzter SOC vor 01.10.2026 12:00", mit {@code enable_seqscan = off}. */
    private String planLetzterWertVor(long einheit) throws SQLException {
        StringBuilder plan = new StringBuilder();
        try (Statement stmt = verbindung.createStatement()) {
            stmt.execute("ANALYZE zev.geraetezustand");
            stmt.execute("SET enable_seqscan = off");
            try (ResultSet rs = stmt.executeQuery("EXPLAIN SELECT * FROM zev.geraetezustand "
                    + "WHERE einheit_id = " + einheit + " AND groesse = 'SOC' "
                    + "AND zeit < TIMESTAMP '2026-10-01 12:00' ORDER BY zeit DESC LIMIT 1")) {
                while (rs.next()) {
                    plan.append(rs.getString(1)).append(System.lineSeparator());
                }
            }
            stmt.execute("RESET enable_seqscan");
        }
        return plan.toString();
    }

    private long zaehle(String tabelle, long einheit) throws SQLException {
        try (PreparedStatement ps = verbindung.prepareStatement(
                "SELECT COUNT(*) FROM " + tabelle + " WHERE einheit_id = ?")) {
            ps.setLong(1, einheit);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private long einfuegen(String sql, Object parameter) throws SQLException {
        try (PreparedStatement ps = verbindung.prepareStatement(sql)) {
            ps.setObject(1, parameter);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private void aendern(String sql, long parameter) throws SQLException {
        try (PreparedStatement ps = verbindung.prepareStatement(sql)) {
            ps.setLong(1, parameter);
            ps.executeUpdate();
        }
    }
}
