package ch.nacht.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit-Tests für {@link WhereClauseValidator} — den SQL-Injection-Schutz der Datenbank-Ansicht.
 *
 * <p>Der Validator ist die einzige Stelle, an der die frei eingegebene WHERE-Klausel geprüft wird,
 * bevor sie im {@code DatenbankService} per String-Konkatenation in die Abfrage wandert. Sein
 * Vertrag — verbotene Zeichenfolgen, verbotene Schlüsselwörter, Längenlimit — war bisher nirgends
 * festgehalten; eine Änderung am regulären Ausdruck wäre unbemerkt geblieben.
 *
 * <p>Die Tests pinnen bewusst auch, was <b>erlaubt</b> bleiben muss: Ein zu gieriges Muster würde
 * gängige Spaltennamen mitsperren und die Ansicht unbrauchbar machen.
 */
public class WhereClauseValidatorTest {

    private WhereClauseValidator validator;

    @BeforeEach
    void setUp() {
        validator = new WhereClauseValidator();
    }

    // ==================== Zulässige Eingaben ====================

    @Test
    void validate_Null_PassesThrough() {
        // Keine Filterung ist zulässig
        assertDoesNotThrow(() -> validator.validate(null));
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "   ", "\t", "\n" })
    void validate_Blank_PassesThrough(String where) {
        assertDoesNotThrow(() -> validator.validate(where));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "betrag > 100",
            "ort = 'Bern'",
            "betrag > 100 AND ort = 'Bern'",
            "zahldatum IS NULL",
            "name LIKE 'Muster%'",
            "jahr BETWEEN 2024 AND 2026",
            "quartal IN (1, 2)",
            "menge >= 0.5 OR menge <= -0.5",
            "NOT (betrag = 0)"
    })
    void validate_GaengigeFilter_PassThrough(String where) {
        assertDoesNotThrow(() -> validator.validate(where));
    }

    /**
     * Die Schlüsselwortprüfung arbeitet mit Wortgrenzen. Spaltennamen, die ein verbotenes Wort
     * als Präfix enthalten, müssen zulässig bleiben — sonst wäre die Ansicht für halbwegs
     * übliche Schemata unbenutzbar.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "created_at IS NOT NULL",
            "updated_by = 'system'",
            "deleted_flag = false",
            "selection_id = 3",
            "dokument IS NOT NULL",
            "callback_url IS NULL"
    })
    void validate_SpaltennamenMitSchluesselwortPraefix_PassThrough(String where) {
        assertDoesNotThrow(() -> validator.validate(where));
    }

    @Test
    void validate_ExaktMaximallaenge_PassesThrough() {
        String where = "a".repeat(500);
        assertDoesNotThrow(() -> validator.validate(where));
    }

    // ==================== Länge ====================

    @Test
    void validate_UeberMaximallaenge_ThrowsException() {
        String where = "a".repeat(501);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(where)
        );

        // Die Meldung ist ein Übersetzungs-Key, kein Klartext
        assertThat(exception.getMessage(), is("DATENBANK_WHERE_ZU_LANG"));
    }

    // ==================== Verbotene Zeichenfolgen ====================

    @ParameterizedTest
    @ValueSource(strings = {
            "betrag > 100; DROP TABLE zev.tarif",   // Mehrfach-Statement
            "betrag > 100 --",                       // Zeilenkommentar
            "betrag > 100 /* Kommentar",             // Blockkommentar Anfang
            "betrag > 100 */",                       // Blockkommentar Ende
            ";",
            "--",
            "/*",
            "*/"
    })
    void validate_VerbotenesZeichen_ThrowsException(String where) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(where)
        );

        assertThat(exception.getMessage(), is("DATENBANK_WHERE_UNGUELTIG"));
    }

    // ==================== Verbotene Schlüsselwörter ====================

    @ParameterizedTest
    @ValueSource(strings = {
            "insert", "update", "delete", "drop", "alter", "truncate",
            "create", "grant", "revoke", "merge", "copy", "call", "do", "select"
    })
    void validate_VerbotenesSchluesselwort_ThrowsException(String schluesselwort) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate("betrag > 0 AND " + schluesselwort + " x")
        );

        assertThat(exception.getMessage(), is("DATENBANK_WHERE_UNGUELTIG"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "SELECT", "SeLeCt", "DROP", "DrOp", "UPDATE", "TRUNCATE" })
    void validate_SchluesselwortUnabhaengigVonGrossschreibung_ThrowsException(String schluesselwort) {
        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate("id = 1 AND " + schluesselwort + " y")
        );
    }

    @Test
    void validate_SubSelect_ThrowsException() {
        // Der eigentliche Angriffspfad: ein Sub-SELECT auf eine fremde Tabelle
        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate("id IN (SELECT id FROM zev.mieter)")
        );
    }

    @Test
    void validate_SchluesselwortInZeichenkette_ThrowsException() {
        // Bewusst streng: Auch in Anführungszeichen wird abgewiesen. Der Validator ist
        // defense-in-depth und kein SQL-Parser - lieber eine zulässige Eingabe zu viel
        // abweisen als eine Umgehung durchlassen.
        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate("bemerkung LIKE '%select%'")
        );
    }
}
