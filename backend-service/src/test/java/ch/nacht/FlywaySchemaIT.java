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
