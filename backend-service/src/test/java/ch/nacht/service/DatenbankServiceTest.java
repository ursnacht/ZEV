package ch.nacht.service;

import ch.nacht.dto.DatenbankAbfrageRequestDTO;
import ch.nacht.dto.DatenbankAbfrageResponseDTO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit-Tests für {@link DatenbankService} — die generische, mandantenübergreifende Rohansicht
 * auf das Schema {@code zev}.
 *
 * <p>Der Service weicht bewusst vom Repository-Pattern ab und baut sein SQL per
 * String-Konkatenation zusammen. Genau das macht ihn zur heikelsten Stelle des Backends:
 * Tabellen-, Spalten- und Sortiername landen unmaskiert in der Abfrage. Sicher ist das nur,
 * solange alle drei aus dem Katalog stammen und die WHERE-Klausel den Validator passiert.
 *
 * <p>Die Tests pinnen deshalb nicht bloss Rückgabewerte, sondern das erzeugte SQL selbst:
 * Whitelist-Abgleich, Anführungszeichen um Bezeichner, gebundene Parameter für LIMIT/OFFSET,
 * die Klemmung der Seitengrösse und dass Datenbankfehler nicht nach aussen durchschlagen.
 * Der WHERE-Ausdruck selbst ist in {@link WhereClauseValidatorTest} abgedeckt; hier wird nur
 * geprüft, dass der Validator überhaupt gefragt wird.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class DatenbankServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private WhereClauseValidator whereClauseValidator;

    @Mock
    private OrganizationContextService organizationContextService;

    @InjectMocks
    private DatenbankService datenbankService;

    private static final List<String> TABELLEN = List.of("debitor", "einheit", "mieter", "tarif");
    private static final List<String> SPALTEN = List.of("id", "name", "org_id");

    @BeforeEach
    void setUp() {
        when(jdbcTemplate.queryForList(contains("information_schema.tables"), eq(String.class), eq("zev")))
                .thenReturn(TABELLEN);
        when(jdbcTemplate.queryForList(contains("information_schema.columns"), eq(String.class),
                eq("zev"), eq("einheit"))).thenReturn(SPALTEN);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), anyInt(), anyInt()))
                .thenReturn(new ArrayList<>());
    }

    private DatenbankAbfrageRequestDTO request(String tabelle) {
        DatenbankAbfrageRequestDTO request = new DatenbankAbfrageRequestDTO();
        request.setTabelle(tabelle);
        return request;
    }

    /** Das tatsächlich abgesetzte SELECT - die eigentliche Prüfgrösse dieser Tests. */
    private String erzeugtesSql() {
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class), anyInt(), anyInt());
        return sql.getValue();
    }

    /** Liefert n Zeilen, damit die Pagination geprüft werden kann. */
    private void gibZeilenZurueck(int anzahl) {
        List<List<Object>> zeilen = new ArrayList<>();
        for (int i = 0; i < anzahl; i++) {
            zeilen.add(new ArrayList<>(List.of("wert" + i)));
        }
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), anyInt(), anyInt())).thenReturn(zeilen);
    }

    // ==================== getTabellen ====================

    @Test
    void getTabellen_ReturnsBaseTablesOfSchemaZev() {
        List<String> result = datenbankService.getTabellen();

        assertEquals(TABELLEN, result);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForList(sql.capture(), eq(String.class), eq("zev"));
        // Nur Basistabellen (keine Views) und alphabetisch - beides bestimmt die Auswahlliste
        assertTrue(sql.getValue().contains("table_type = 'BASE TABLE'"));
        assertTrue(sql.getValue().contains("ORDER BY table_name"));
    }

    // ==================== getStandardFilter ====================

    @Test
    void getStandardFilter_TabelleMitOrgId_ReturnsOrgFilter() {
        when(organizationContextService.getCurrentOrgId()).thenReturn(42L);

        assertEquals("org_id = 42", datenbankService.getStandardFilter("einheit"));
    }

    @Test
    void getStandardFilter_TabelleOhneOrgId_ReturnsEmpty() {
        when(organizationContextService.getCurrentOrgId()).thenReturn(42L);
        when(jdbcTemplate.queryForList(contains("information_schema.columns"), eq(String.class),
                eq("zev"), eq("tarif"))).thenReturn(List.of("id", "bezeichnung"));

        assertEquals("", datenbankService.getStandardFilter("tarif"));
    }

    @Test
    void getStandardFilter_OhneOrgKontext_ReturnsEmpty() {
        // Ohne Mandanten-Kontext darf kein Filter vorgeschlagen werden - "org_id = null"
        // waere eine syntaktisch gueltige, fachlich sinnlose Klausel
        when(organizationContextService.getCurrentOrgId()).thenReturn(null);

        assertEquals("", datenbankService.getStandardFilter("einheit"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "pg_shadow", "flyway_schema_history; DROP TABLE zev.tarif", "EINHEIT" })
    void getStandardFilter_UnbekannteTabelle_ThrowsException(String tabelle) {
        // Der Abgleich ist exakt: auch die abweichende Gross-/Kleinschreibung faellt durch
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> datenbankService.getStandardFilter(tabelle));

        assertEquals("DATENBANK_TABELLE_UNGUELTIG", exception.getMessage());
    }

    @Test
    void getStandardFilter_Null_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> datenbankService.getStandardFilter(null));
    }

    // ==================== abfrage: Tabellen-Whitelist ====================

    @Test
    void abfrage_UnbekannteTabelle_ThrowsExceptionOhneAbfrage() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> datenbankService.abfrage(request("pg_authid")));

        assertEquals("DATENBANK_TABELLE_UNGUELTIG", exception.getMessage());
        // Entscheidend: Es darf gar keine Abfrage abgesetzt werden
        verify(jdbcTemplate, never()).query(anyString(), any(RowMapper.class), anyInt(), anyInt());
    }

    @Test
    void abfrage_Null_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> datenbankService.abfrage(request(null)));
    }

    @Test
    void abfrage_TabelleOhneSpalten_ThrowsException() {
        when(jdbcTemplate.queryForList(contains("information_schema.columns"), eq(String.class),
                eq("zev"), eq("mieter"))).thenReturn(List.of());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> datenbankService.abfrage(request("mieter")));

        assertEquals("DATENBANK_TABELLE_UNGUELTIG", exception.getMessage());
    }

    // ==================== abfrage: WHERE ====================

    @Test
    void abfrage_UebergibtWhereAnDenValidator() {
        DatenbankAbfrageRequestDTO request = request("einheit");
        request.setWhere("org_id = 1");

        datenbankService.abfrage(request);

        verify(whereClauseValidator).validate("org_id = 1");
        assertTrue(erzeugtesSql().contains(" WHERE org_id = 1"));
    }

    @Test
    void abfrage_ValidatorLehntAb_AbfrageWirdNichtAbgesetzt() {
        DatenbankAbfrageRequestDTO request = request("einheit");
        request.setWhere("id = 1; DROP TABLE zev.tarif");
        doThrowUngueltig();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> datenbankService.abfrage(request));

        assertEquals("DATENBANK_WHERE_UNGUELTIG", exception.getMessage());
        verify(jdbcTemplate, never()).query(anyString(), any(RowMapper.class), anyInt(), anyInt());
    }

    private void doThrowUngueltig() {
        org.mockito.Mockito.doThrow(new IllegalArgumentException("DATENBANK_WHERE_UNGUELTIG"))
                .when(whereClauseValidator).validate(anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "   " })
    void abfrage_LeeresWhere_ErzeugtKeineWhereKlausel(String where) {
        DatenbankAbfrageRequestDTO request = request("einheit");
        request.setWhere(where);

        datenbankService.abfrage(request);

        assertFalse(erzeugtesSql().contains("WHERE"));
    }

    @Test
    void abfrage_OhneWhere_ErzeugtKeineWhereKlausel() {
        datenbankService.abfrage(request("einheit"));

        assertFalse(erzeugtesSql().contains("WHERE"));
    }

    // ==================== abfrage: SQL-Aufbau ====================

    @Test
    void abfrage_MaskiertBezeichnerUndBindetPagination() {
        datenbankService.abfrage(request("einheit"));

        String sql = erzeugtesSql();
        // Spalten und Tabelle in Anfuehrungszeichen, Schema fest vorangestellt
        assertEquals("SELECT \"id\", \"name\", \"org_id\" FROM zev.\"einheit\" LIMIT ? OFFSET ?", sql);
        // LIMIT/OFFSET als gebundene Parameter, nie als Text im SQL
        verify(jdbcTemplate).query(anyString(), any(RowMapper.class), eq(51), eq(0));
    }

    @Test
    void abfrage_SetztStatementTimeout() {
        // Ohne Timeout koennte eine Abfrage ueber eine grosse Tabelle die Verbindung blockieren
        datenbankService.abfrage(request("einheit"));

        verify(jdbcTemplate).execute("SET LOCAL statement_timeout = 5000");
    }

    @Test
    void abfrage_SchliesstByteaSpaltenAus() {
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);

        datenbankService.abfrage(request("einheit"));

        verify(jdbcTemplate).queryForList(sql.capture(), eq(String.class), eq("zev"), eq("einheit"));
        assertTrue(sql.getValue().contains("data_type <> 'bytea'"));
        assertTrue(sql.getValue().contains("ORDER BY ordinal_position"));
    }

    // ==================== abfrage: Sortierung ====================

    @ParameterizedTest
    @CsvSource({
            "ASC,  'ORDER BY \"name\" ASC'",
            "asc,  'ORDER BY \"name\" ASC'",
            "DESC, 'ORDER BY \"name\" DESC'",
            "desc, 'ORDER BY \"name\" DESC'"
    })
    void abfrage_SortierungMitBekannterSpalte(String richtung, String erwartet) {
        DatenbankAbfrageRequestDTO request = request("einheit");
        request.setSortSpalte("name");
        request.setSortRichtung(richtung);

        datenbankService.abfrage(request);

        assertTrue(erzeugtesSql().contains(erwartet), erzeugtesSql());
    }

    @Test
    void abfrage_SortierungOhneRichtung_DefaultAufscending() {
        DatenbankAbfrageRequestDTO request = request("einheit");
        request.setSortSpalte("name");

        datenbankService.abfrage(request);

        assertTrue(erzeugtesSql().contains("ORDER BY \"name\" ASC"));
    }

    @Test
    void abfrage_UnsinnigeRichtung_FaelltAufAufsteigendZurueck() {
        // Alles ausser DESC ist ASC - die Richtung darf nie durchgereicht werden
        DatenbankAbfrageRequestDTO request = request("einheit");
        request.setSortSpalte("name");
        request.setSortRichtung("DESC; DROP TABLE zev.tarif");

        datenbankService.abfrage(request);

        assertTrue(erzeugtesSql().endsWith("ORDER BY \"name\" ASC LIMIT ? OFFSET ?"), erzeugtesSql());
    }

    @ParameterizedTest
    @ValueSource(strings = { "unbekannt", "id; DROP TABLE zev.tarif", "ID", "(SELECT 1)" })
    void abfrage_SortierspalteNichtImKatalog_ThrowsException(String sortSpalte) {
        // Die Sortierspalte landet unmaskiert im SQL - sie MUSS aus dem Katalog stammen
        DatenbankAbfrageRequestDTO request = request("einheit");
        request.setSortSpalte(sortSpalte);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> datenbankService.abfrage(request));

        assertEquals("DATENBANK_SORT_UNGUELTIG", exception.getMessage());
        verify(jdbcTemplate, never()).query(anyString(), any(RowMapper.class), anyInt(), anyInt());
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "   " })
    void abfrage_LeereSortierspalte_ErzeugtKeineSortierung(String sortSpalte) {
        DatenbankAbfrageRequestDTO request = request("einheit");
        request.setSortSpalte(sortSpalte);

        datenbankService.abfrage(request);

        assertFalse(erzeugtesSql().contains("ORDER BY"));
    }

    // ==================== abfrage: Pagination ====================

    @Test
    void abfrage_OhneAngaben_VerwendetStandardgroesse() {
        DatenbankAbfrageResponseDTO response = datenbankService.abfrage(request("einheit"));

        assertEquals(50, response.getGroesse());
        assertEquals(0, response.getSeite());
        // size + 1 wird gelesen, um "hatMehr" ohne zweite Abfrage zu bestimmen
        verify(jdbcTemplate).query(anyString(), any(RowMapper.class), eq(51), eq(0));
    }

    @ParameterizedTest
    @CsvSource({
            "1,    1",
            "500,  500",
            "501,  500",
            "99999, 500",
            "0,    1",
            "-5,   1"
    })
    void abfrage_KlemmtSeitengroesse(int angefragt, int erwartet) {
        // Die Obergrenze schuetzt vor dem versehentlichen Ziehen einer ganzen Tabelle
        DatenbankAbfrageRequestDTO request = request("einheit");
        request.setSize(angefragt);

        DatenbankAbfrageResponseDTO response = datenbankService.abfrage(request);

        assertEquals(erwartet, response.getGroesse());
        verify(jdbcTemplate).query(anyString(), any(RowMapper.class), eq(erwartet + 1), eq(0));
    }

    @ParameterizedTest
    @CsvSource({
            "0,  10,  0",
            "2,  10,  20",
            "3,  500, 1500",
            // Eine negative Seite wird auf 0 geklemmt - ein negativer OFFSET waere ein SQL-Fehler
            "-1, 10,  0"
    })
    void abfrage_BerechnetOffsetAusSeiteUndGroesse(int seite, int groesse, int erwarteterOffset) {
        DatenbankAbfrageRequestDTO request = request("einheit");
        request.setPage(seite);
        request.setSize(groesse);

        datenbankService.abfrage(request);

        verify(jdbcTemplate).query(anyString(), any(RowMapper.class), eq(groesse + 1), eq(erwarteterOffset));
    }

    @Test
    void abfrage_MehrZeilenAlsSeitengroesse_SetztHatMehrUndSchneidetAb() {
        DatenbankAbfrageRequestDTO request = request("einheit");
        request.setSize(3);
        gibZeilenZurueck(4);

        DatenbankAbfrageResponseDTO response = datenbankService.abfrage(request);

        assertTrue(response.isHatMehr());
        // Die zusaetzlich gelesene Zeile darf nicht ausgeliefert werden
        assertEquals(3, response.getZeilen().size());
    }

    @Test
    void abfrage_GenauSeitengroesse_SetztHatMehrNicht() {
        DatenbankAbfrageRequestDTO request = request("einheit");
        request.setSize(3);
        gibZeilenZurueck(3);

        DatenbankAbfrageResponseDTO response = datenbankService.abfrage(request);

        assertFalse(response.isHatMehr());
        assertEquals(3, response.getZeilen().size());
    }

    @Test
    void abfrage_LeeresErgebnis_LiefertSpaltenUndKeineZeilen() {
        DatenbankAbfrageResponseDTO response = datenbankService.abfrage(request("einheit"));

        assertEquals(SPALTEN, response.getSpalten());
        assertTrue(response.getZeilen().isEmpty());
        assertFalse(response.isHatMehr());
    }

    // ==================== abfrage: Fehlerbehandlung ====================

    @Test
    void abfrage_DatenbankFehler_WirdNichtNachAussenGegeben() {
        // Die Originalmeldung enthaelt Schema- und Spaltennamen und darf den Aufrufer
        // nicht erreichen - er sieht nur den Uebersetzungs-Key
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), anyInt(), anyInt()))
                .thenThrow(new QueryTimeoutException("ERROR: canceling statement due to statement timeout"));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> datenbankService.abfrage(request("einheit")));

        assertEquals("DATENBANK_ABFRAGE_FEHLER", exception.getMessage());
    }

    // ==================== abfrage: Zeilen-Abbildung ====================

    @Test
    void abfrage_BildetWerteAlsZeichenketteAb() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getObject(1)).thenReturn(7L);
        when(resultSet.getObject(2)).thenReturn("Wohnung 1");
        when(resultSet.getObject(3)).thenReturn(null);

        List<Object> zeile = mappeZeile(resultSet);

        // Alles wird als Text geliefert - die Ansicht kennt die Spaltentypen nicht
        assertEquals("7", zeile.get(0));
        assertEquals("Wohnung 1", zeile.get(1));
        // NULL bleibt NULL und wird nicht zu "null"
        assertNull(zeile.get(2));
    }

    /** Fängt den RowMapper der Abfrage ab und wendet ihn auf ein ResultSet an. */
    @SuppressWarnings("unchecked")
    private List<Object> mappeZeile(ResultSet resultSet) throws Exception {
        datenbankService.abfrage(request("einheit"));

        ArgumentCaptor<RowMapper<List<Object>>> mapper = ArgumentCaptor.forClass(RowMapper.class);
        verify(jdbcTemplate).query(anyString(), mapper.capture(), anyInt(), anyInt());
        return mapper.getValue().mapRow(resultSet, 0);
    }
}
