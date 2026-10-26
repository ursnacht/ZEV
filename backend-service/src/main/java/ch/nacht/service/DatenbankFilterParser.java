package ch.nacht.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Zerlegt die Filter-Eingabe der Datenbank-Ansicht und baut daraus SQL neu auf
 * (Specs/Datenbank-Ansicht.md, NFR-2).
 *
 * <p>Allowlist statt Denylist: Durchgelassen wird nur, was die Grammatik unten kennt. Spalten
 * müssen in der Spaltenliste der gewählten Tabelle stehen, Werte sind Zahl- oder Text-Literale.
 * Funktionsaufrufe, Casts, Sub-SELECTs und Bezeichner anderer Schemas gibt es in der Grammatik
 * nicht. Das ausgegebene SQL entsteht ausschliesslich aus den erkannten Bausteinen, nie aus dem
 * Eingabetext.
 *
 * <pre>
 * filter     := [bedingung] [ORDER BY sortierung {, sortierung}]
 * bedingung  := und {OR und}
 * und        := nicht {AND nicht}
 * nicht      := NOT nicht | ( bedingung ) | praedikat
 * praedikat  := operand ( vergleich operand
 *                       | [NOT] (LIKE | ILIKE) operand
 *                       | IS [NOT] NULL
 *                       | [NOT] IN ( literal {, literal} )
 *                       | [NOT] BETWEEN operand AND operand )
 * operand    := spalte | literal
 * literal    := [-] zahl | 'text' | TRUE | FALSE      (Wörter und Zahlen nur ASCII)
 * vergleich  := = | &lt;&gt; | != | &lt; | &gt; | &lt;= | &gt;=
 * sortierung := spalte [ASC | DESC]
 * </pre>
 *
 * <p>Hintergrund (Anhang A.1 der Spec): Die frühere Denylist liess Funktionsaufrufe wie
 * {@code query_to_xml(...)} oder {@code pg_read_file(...)} durch und damit Lesezugriffe über
 * die Tabellen-Whitelist hinaus.
 */
@Component
public class DatenbankFilterParser {

    static final int MAX_LENGTH = 500;

    private static final Set<String> VERGLEICHE = Set.of("=", "<>", "!=", "<", ">", "<=", ">=");

    /** Ergebnis: Bedingung (ohne {@code WHERE}) und Sortierung (mit {@code ORDER BY}), je leer möglich. */
    public record GeparsterFilter(String bedingung, String sortierung) {
    }

    private enum Art { WORT, ZAHL, TEXT, SYMBOL }

    private record Token(Art art, String wert) {
        boolean istWort(String wort) {
            return art == Art.WORT && wert.equalsIgnoreCase(wort);
        }

        boolean istSymbol(String symbol) {
            return art == Art.SYMBOL && wert.equals(symbol);
        }
    }

    /**
     * Zerlegt den Filter und baut Bedingung und Sortierung aus den erkannten Bausteinen neu auf.
     *
     * @param filter Eingabe des Anwenders; leer oder {@code null} bedeutet „kein Filter"
     * @param spalten zulässige Spalten der gewählten Tabelle (aus dem Katalog)
     * @throws IllegalArgumentException mit Übersetzungs-Key bei unzulässiger Eingabe
     */
    public GeparsterFilter parse(String filter, List<String> spalten) {
        if (filter == null || filter.isBlank()) {
            return new GeparsterFilter("", "");
        }
        if (filter.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("DATENBANK_WHERE_ZU_LANG");
        }
        return new Parser(zerlege(filter), spalten).filter();
    }

    private static List<Token> zerlege(String eingabe) {
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        while (i < eingabe.length()) {
            char c = eingabe.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (istBuchstabe(c)) {
                // Nur ASCII: Character.isLetter/isDigit liessen Unicode-Ziffern (PostgreSQL liest
                // sie als Bezeichner) und Zeichen wie das Kelvin-K (gilt per equalsIgnoreCase als K)
                // durch - beides gelangte als Eingabetext ins SQL
                int start = i;
                while (i < eingabe.length() && (istBuchstabe(eingabe.charAt(i)) || istZiffer(eingabe.charAt(i)))) {
                    i++;
                }
                tokens.add(new Token(Art.WORT, eingabe.substring(start, i)));
            } else if (istZiffer(c)) {
                int start = i;
                while (i < eingabe.length() && istZiffer(eingabe.charAt(i))) {
                    i++;
                }
                if (i + 1 < eingabe.length() && eingabe.charAt(i) == '.' && istZiffer(eingabe.charAt(i + 1))) {
                    i++;
                    while (i < eingabe.length() && istZiffer(eingabe.charAt(i))) {
                        i++;
                    }
                }
                tokens.add(new Token(Art.ZAHL, eingabe.substring(start, i)));
            } else if (c == '\'') {
                // Text-Literal; '' steht für ein einzelnes Apostroph
                StringBuilder text = new StringBuilder();
                i++;
                while (true) {
                    if (i >= eingabe.length()) {
                        throw ungueltig();
                    }
                    if (eingabe.charAt(i) == '\'') {
                        if (i + 1 < eingabe.length() && eingabe.charAt(i + 1) == '\'') {
                            text.append('\'');
                            i += 2;
                            continue;
                        }
                        i++;
                        break;
                    }
                    if (eingabe.charAt(i) == '\0') {
                        // PostgreSQL kennt kein NUL in Texten - lieber hier als mit DB-Fehler abweisen
                        throw ungueltig();
                    }
                    text.append(eingabe.charAt(i++));
                }
                tokens.add(new Token(Art.TEXT, text.toString()));
            } else {
                String zwei = i + 1 < eingabe.length() ? eingabe.substring(i, i + 2) : "";
                if (VERGLEICHE.contains(zwei)) {
                    tokens.add(new Token(Art.SYMBOL, zwei));
                    i += 2;
                } else if ("=<>(),-".indexOf(c) >= 0) {
                    tokens.add(new Token(Art.SYMBOL, String.valueOf(c)));
                    i++;
                } else {
                    throw ungueltig();
                }
            }
        }
        return tokens;
    }

    private static boolean istBuchstabe(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }

    private static boolean istZiffer(char c) {
        return c >= '0' && c <= '9';
    }

    private static IllegalArgumentException ungueltig() {
        return new IllegalArgumentException("DATENBANK_WHERE_UNGUELTIG");
    }

    /** Rekursiver Abstieg über die Grammatik; schreibt das SQL beim Erkennen mit. */
    private static final class Parser {

        private final List<Token> tokens;
        private final List<String> spalten;
        private int pos;

        Parser(List<Token> tokens, List<String> spalten) {
            this.tokens = tokens;
            this.spalten = spalten;
        }

        GeparsterFilter filter() {
            String bedingung = "";
            if (!amEnde() && !aktuell().istWort("ORDER")) {
                bedingung = bedingung();
            }
            String sortierung = "";
            if (!amEnde()) {
                erwarteWort("ORDER");
                erwarteWort("BY");
                List<String> teile = new ArrayList<>();
                do {
                    String spalte = spalte();
                    String richtung = "";
                    if (nimmWort("ASC")) {
                        richtung = " ASC";
                    } else if (nimmWort("DESC")) {
                        richtung = " DESC";
                    }
                    teile.add(spalte + richtung);
                } while (nimmSymbol(","));
                sortierung = "ORDER BY " + String.join(", ", teile);
            }
            if (!amEnde()) {
                throw ungueltig();
            }
            return new GeparsterFilter(bedingung, sortierung);
        }

        private String bedingung() {
            StringBuilder sql = new StringBuilder(und());
            while (nimmWort("OR")) {
                sql.append(" OR ").append(und());
            }
            return sql.toString();
        }

        private String und() {
            StringBuilder sql = new StringBuilder(nicht());
            while (nimmWort("AND")) {
                sql.append(" AND ").append(nicht());
            }
            return sql.toString();
        }

        private String nicht() {
            if (nimmWort("NOT")) {
                return "NOT " + nicht();
            }
            if (nimmSymbol("(")) {
                String innen = bedingung();
                erwarteSymbol(")");
                return "(" + innen + ")";
            }
            return praedikat();
        }

        private String praedikat() {
            String links = operand();
            if (amEnde()) {
                throw ungueltig();
            }
            Token t = aktuell();
            if (t.art() == Art.SYMBOL && VERGLEICHE.contains(t.wert())) {
                pos++;
                return links + " " + t.wert() + " " + operand();
            }
            if (nimmWort("IS")) {
                String not = nimmWort("NOT") ? "NOT " : "";
                erwarteWort("NULL");
                return links + " IS " + not + "NULL";
            }
            String not = nimmWort("NOT") ? "NOT " : "";
            if (nimmWort("LIKE")) {
                return links + " " + not + "LIKE " + operand();
            }
            if (nimmWort("ILIKE")) {
                return links + " " + not + "ILIKE " + operand();
            }
            if (nimmWort("IN")) {
                erwarteSymbol("(");
                List<String> werte = new ArrayList<>();
                do {
                    werte.add(literal());
                } while (nimmSymbol(","));
                erwarteSymbol(")");
                return links + " " + not + "IN (" + String.join(", ", werte) + ")";
            }
            if (nimmWort("BETWEEN")) {
                String von = operand();
                erwarteWort("AND");
                return links + " " + not + "BETWEEN " + von + " AND " + operand();
            }
            throw ungueltig();
        }

        private String operand() {
            if (!amEnde() && aktuell().art() == Art.WORT
                    && !aktuell().istWort("TRUE") && !aktuell().istWort("FALSE")) {
                return spalte();
            }
            return literal();
        }

        private String literal() {
            if (amEnde()) {
                throw ungueltig();
            }
            Token t = naechstes();
            if (t.istSymbol("-") && !amEnde() && aktuell().art() == Art.ZAHL) {
                return "-" + naechstes().wert();
            }
            return switch (t.art()) {
                case ZAHL -> t.wert();
                // Als E'...' neu maskiert statt durchgereicht: In E-Strings gilt der Backslash
                // immer als Escape, unabhaengig von standard_conforming_strings. Darum werden
                // Backslash und Apostroph verdoppelt; ein normales '...' brach bei
                // standard_conforming_strings = off mit '\' aus.
                case TEXT -> "E'" + t.wert().replace("\\", "\\\\").replace("'", "''") + "'";
                case WORT -> {
                    if (t.istWort("TRUE")) {
                        yield "TRUE";
                    }
                    if (t.istWort("FALSE")) {
                        yield "FALSE";
                    }
                    throw ungueltig();
                }
                default -> throw ungueltig();
            };
        }

        private String spalte() {
            if (amEnde() || aktuell().art() != Art.WORT) {
                throw ungueltig();
            }
            // PostgreSQL faltet unquotierte Bezeichner auf Kleinbuchstaben, der Katalog ebenso
            String name = naechstes().wert().toLowerCase(Locale.ROOT);
            if (!spalten.contains(name)) {
                throw new IllegalArgumentException("DATENBANK_WHERE_SPALTE_UNBEKANNT");
            }
            return "\"" + name + "\"";
        }

        private boolean amEnde() {
            return pos >= tokens.size();
        }

        private Token aktuell() {
            return tokens.get(pos);
        }

        private Token naechstes() {
            return tokens.get(pos++);
        }

        private boolean nimmWort(String wort) {
            if (!amEnde() && aktuell().istWort(wort)) {
                pos++;
                return true;
            }
            return false;
        }

        private boolean nimmSymbol(String symbol) {
            if (!amEnde() && aktuell().istSymbol(symbol)) {
                pos++;
                return true;
            }
            return false;
        }

        private void erwarteWort(String wort) {
            if (!nimmWort(wort)) {
                throw ungueltig();
            }
        }

        private void erwarteSymbol(String symbol) {
            if (!nimmSymbol(symbol)) {
                throw ungueltig();
            }
        }
    }
}
