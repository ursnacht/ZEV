package ch.nacht.service;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Validiert die optionale SQL-WHERE-Eingabe der Datenbank-Ansicht (defense-in-depth).
 * Erlaubt ist nur ein reiner Filter-Ausdruck; abgelehnt werden Mehrfach-Statements,
 * Kommentare, DML/DDL-Schlüsselwörter und Sub-SELECTs. Zusätzlich greifen im Service
 * read-only-Transaktion, statement_timeout und LIMIT.
 */
@Component
public class WhereClauseValidator {

    private static final int MAX_LENGTH = 500;

    // Verbotene Schlüsselwörter (Wortgrenzen, case-insensitive): DML/DDL + Sub-SELECT.
    private static final Pattern FORBIDDEN_KEYWORDS = Pattern.compile(
            "\\b(insert|update|delete|drop|alter|truncate|create|grant|revoke|merge|copy|call|do|select)\\b",
            Pattern.CASE_INSENSITIVE);

    /**
     * Prüft die WHERE-Klausel. Leere/nicht gesetzte Eingabe ist zulässig (keine Filterung).
     *
     * @throws IllegalArgumentException (mit Übersetzungs-Key als Meldung) bei unzulässiger Eingabe
     */
    public void validate(String where) {
        if (where == null || where.isBlank()) {
            return;
        }
        if (where.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("DATENBANK_WHERE_ZU_LANG");
        }
        if (where.contains(";") || where.contains("--") || where.contains("/*") || where.contains("*/")) {
            throw new IllegalArgumentException("DATENBANK_WHERE_UNGUELTIG");
        }
        if (FORBIDDEN_KEYWORDS.matcher(where).find()) {
            throw new IllegalArgumentException("DATENBANK_WHERE_UNGUELTIG");
        }
    }
}
