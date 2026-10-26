package ch.nacht.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit-Tests für {@link DatenbankFilterParser} — den SQL-Injection-Schutz der Datenbank-Ansicht
 * (Specs/Datenbank-Ansicht.md, NFR-2).
 *
 * <p>Der Parser lässt nur durch, was seine Grammatik kennt, und baut das SQL aus den erkannten
 * Bausteinen neu auf. Die Tests pinnen beides: was erlaubt bleibt (sonst ist die Ansicht
 * unbrauchbar) und dass die Umgehungen der früheren Denylist abgewiesen werden.
 */
public class DatenbankFilterParserTest {

    private static final List<String> SPALTEN =
            List.of("id", "org_id", "name", "zeit", "betrag", "zahldatum", "created_at", "selection_id");

    private DatenbankFilterParser parser;

    @BeforeEach
    void setUp() {
        parser = new DatenbankFilterParser();
    }

    private String bedingung(String filter) {
        return parser.parse(filter, SPALTEN).bedingung();
    }

    private void assertUngueltig(String filter, String key) {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> parser.parse(filter, SPALTEN));
        // Die Meldung ist ein Übersetzungs-Key, kein Klartext
        assertEquals(key, exception.getMessage());
    }

    // ==================== Leer ====================

    @ParameterizedTest
    @ValueSource(strings = { "", "   ", "\t", "\n" })
    void parse_Leer_KeinFilter(String filter) {
        DatenbankFilterParser.GeparsterFilter result = parser.parse(filter, SPALTEN);

        assertEquals("", result.bedingung());
        assertEquals("", result.sortierung());
    }

    @Test
    void parse_Null_KeinFilter() {
        assertEquals(new DatenbankFilterParser.GeparsterFilter("", ""), parser.parse(null, SPALTEN));
    }

    // ==================== Zulässige Filter ====================

    static Stream<Arguments> gaengigeFilter() {
        return Stream.of(
                Arguments.of("org_id = 42", "\"org_id\" = 42"),
                Arguments.of("betrag > 100 AND name = 'Bern'", "\"betrag\" > 100 AND \"name\" = E'Bern'"),
                Arguments.of("zeit > '2026-01-01'", "\"zeit\" > E'2026-01-01'"),
                Arguments.of("zahldatum IS NULL", "\"zahldatum\" IS NULL"),
                Arguments.of("zahldatum is not null", "\"zahldatum\" IS NOT NULL"),
                Arguments.of("name LIKE 'Muster%'", "\"name\" LIKE E'Muster%'"),
                Arguments.of("name not ilike '%bern%'", "\"name\" NOT ILIKE E'%bern%'"),
                Arguments.of("id BETWEEN 1 AND 10", "\"id\" BETWEEN 1 AND 10"),
                Arguments.of("id NOT IN (1, 2, -3)", "\"id\" NOT IN (1, 2, -3)"),
                Arguments.of("betrag >= 0.5 OR betrag <= -0.5", "\"betrag\" >= 0.5 OR \"betrag\" <= -0.5"),
                Arguments.of("NOT (betrag = 0)", "NOT (\"betrag\" = 0)"),
                Arguments.of("betrag <> 0 AND betrag != 1", "\"betrag\" <> 0 AND \"betrag\" != 1"),
                Arguments.of("(id = 1 OR id = 2) AND org_id = 4", "(\"id\" = 1 OR \"id\" = 2) AND \"org_id\" = 4"),
                Arguments.of("ORG_ID = 42", "\"org_id\" = 42"),
                Arguments.of("id = selection_id", "\"id\" = \"selection_id\"")
        );
    }

    @ParameterizedTest
    @MethodSource("gaengigeFilter")
    void parse_GaengigeFilter_WerdenNeuAufgebaut(String filter, String erwartet) {
        assertEquals(erwartet, bedingung(filter));
    }

    @Test
    void parse_ApostrophImText_WirdNeuMaskiert() {
        assertEquals("\"name\" = E'O''Neill'", bedingung("name = 'O''Neill'"));
    }

    @Test
    void parse_BackslashImText_WirdVerdoppelt() {
        // In E'...' ist der Backslash immer ein Escape. Unverdoppelt wuerde 'a\' das
        // schliessende Apostroph maskieren und den Text verlassen.
        assertEquals("\"name\" = E'a\\\\'", bedingung("name = 'a\\'"));
        assertEquals("\"name\" LIKE E'C:\\\\daten\\\\%'", bedingung("name LIKE 'C:\\daten\\%'"));
    }

    @Test
    void parse_SchluesselwortImText_IstZulaessig() {
        // Anders als die frühere Denylist: Text-Literale sind Daten, kein SQL
        assertEquals("\"name\" LIKE E'%select%; drop'", bedingung("name LIKE '%select%; drop'"));
    }

    @Test
    void parse_Wahrheitswerte_SindLiterale() {
        assertEquals("\"betrag\" = TRUE OR \"betrag\" = FALSE", bedingung("betrag = true OR betrag = false"));
    }

    // ==================== ORDER BY ====================

    @Test
    void parse_StandardFilter_TrenntBedingungUndSortierung() {
        DatenbankFilterParser.GeparsterFilter result = parser.parse("org_id = 42 ORDER BY id DESC", SPALTEN);

        assertEquals("\"org_id\" = 42", result.bedingung());
        assertEquals("ORDER BY \"id\" DESC", result.sortierung());
    }

    @ParameterizedTest
    @ValueSource(strings = { "ORDER BY id DESC", "order by id desc", "Order  By id desc", "ORDER\tBY id DESC" })
    void parse_NurSortierung_OhneBedingung(String filter) {
        DatenbankFilterParser.GeparsterFilter result = parser.parse(filter, SPALTEN);

        assertEquals("", result.bedingung());
        assertEquals("ORDER BY \"id\" DESC", result.sortierung());
    }

    @Test
    void parse_MehrereSortierspalten() {
        assertEquals("ORDER BY \"zeit\" DESC, \"id\"",
                parser.parse("ORDER BY zeit DESC, id", SPALTEN).sortierung());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ORDER BY unbekannt",
            "ORDER BY id DESC; DROP TABLE zev.tarif",
            "ORDER BY 1",
            "ORDER BY length(name)",
            "ORDER BY id DESC id = 1"
    })
    void parse_UnzulaessigeSortierung_WirdAbgewiesen(String filter) {
        assertThrows(IllegalArgumentException.class, () -> parser.parse(filter, SPALTEN));
    }

    // ==================== Umgehungen (Security-Check 26.10.2026) ====================

    @ParameterizedTest
    @ValueSource(strings = {
            "query_to_xml('sel'||'ect * from keycloak.credential',true,false,'')::text like 'a%'",
            "length(pg_read_file('/etc/passwd')) > 0",
            "pg_sleep(10) IS NULL",
            "set_config('role','postgres',true) = 'x'",
            "lower(name) = 'bern'"
    })
    void parse_Funktionsaufruf_WirdAbgewiesen(String filter) {
        // Funktionsnamen sind keine Spalten der Tabelle - die Grammatik kennt keine Aufrufe.
        // Welcher Key kommt, haengt davon ab, ob schon die Zerlegung (::, ||) oder erst der
        // Spaltenabgleich scheitert; entscheidend ist die Abweisung.
        assertThrows(IllegalArgumentException.class, () -> parser.parse(filter, SPALTEN));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "zeit::date = '2026-01-01'",
            "id = 1; DROP TABLE zev.tarif",
            "id = 1 --",
            "id = 1 /* x */",
            "name = 'a' || 'b'",
            "id IN (SELECT id FROM zev.mieter)",
            // Sub-SELECT ohne Punkt: scheitert an der Grammatik, nicht schon an der Zerlegung
            "id IN (SELECT id FROM mieter)",
            "id = (SELECT 1)",
            "id = (1)",
            "id = 1 OR",
            "id =",
            "id",
            "(id = 1",
            "id = 1)",
            "name = 'offen",
            "id = 1 2",
            "\"name\" = 'x'",
            "id = $1",
            "id = 1e5",
            "name LIKE 'a' ESCAPE '!'",
            // Nur ASCII (Vorlagen-Check 26.10.2026): Unicode-Ziffern las PostgreSQL als Bezeichner,
            // das Kelvin-K (U+212A) galt per equalsIgnoreCase als K und landete so im SQL
            "id = ١",
            "id = １",
            "name LIKE 'x'",
            "näme = 1",
            // NUL gibt es in PostgreSQL-Texten nicht
            "name = 'a\u0000b'"
    })
    void parse_SyntaxAusserhalbDerGrammatik_WirdAbgewiesen(String filter) {
        assertUngueltig(filter, "DATENBANK_WHERE_UNGUELTIG");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "keycloak.credential IS NULL",
            "unbekannt = 1",
            "zev.mieter = 1",
            "id IN (SELECT 1)"
    })
    void parse_FremdeBezeichner_WerdenAbgewiesen(String filter) {
        // Schema-qualifizierte Namen und Spalten anderer Tabellen sind nicht in der Spaltenliste
        assertThrows(IllegalArgumentException.class, () -> parser.parse(filter, SPALTEN));
    }

    @Test
    void parse_SpalteNurInAndererTabelle_WirdAbgewiesen() {
        // Die Spaltenliste kommt pro Tabelle aus dem Katalog
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> parser.parse("mieter_id = 1", List.of("id", "name")));

        assertEquals("DATENBANK_WHERE_SPALTE_UNBEKANNT", exception.getMessage());
    }

    // ==================== Länge ====================

    @Test
    void parse_ExaktMaximallaenge_IstZulaessig() {
        String filter = "id = " + "1".repeat(DatenbankFilterParser.MAX_LENGTH - 5);

        assertEquals(DatenbankFilterParser.MAX_LENGTH, filter.length());
        assertEquals("\"id\" = " + "1".repeat(DatenbankFilterParser.MAX_LENGTH - 5), bedingung(filter));
    }

    @Test
    void parse_UeberMaximallaenge_WirdAbgewiesen() {
        assertUngueltig("id = " + "1".repeat(DatenbankFilterParser.MAX_LENGTH - 4), "DATENBANK_WHERE_ZU_LANG");
    }
}
