package ch.nacht.service;

import ch.nacht.dto.DatenbankAbfrageRequestDTO;
import ch.nacht.dto.DatenbankAbfrageResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generische, read-only Datenbank-Ansicht (nur zev_admin, Permission datenbank:read).
 *
 * <p>Bewusste Abweichung vom Repository-Pattern: greift direkt via {@link JdbcTemplate}
 * auf das Schema {@code zev} zu, um beliebige Tabellen spaltenunabhängig anzuzeigen.
 * Der Hibernate-{@code orgFilter} wird NICHT angewendet (mandantenübergreifende Rohansicht).
 *
 * <p>Sicherheit: Tabellenname nur aus dynamischer Whitelist des Schemas {@code zev};
 * WHERE über {@link WhereClauseValidator} geprüft; Ausführung in read-only-Transaktion mit
 * {@code statement_timeout}; harte Zeilenobergrenze (Pagination); bytea-Spalten ausgeschlossen.
 */
@Service
public class DatenbankService {

    private static final Logger log = LoggerFactory.getLogger(DatenbankService.class);

    private static final String SCHEMA = "zev";
    private static final int DEFAULT_SIZE = 50;
    private static final int MAX_SIZE = 500;
    private static final int STATEMENT_TIMEOUT_MS = 5000;

    private static final String ORG_ID_SPALTE = "org_id";
    private static final String ID_SPALTE = "id";

    /**
     * Trennt eine {@code ORDER BY}-Klausel vom Bedingungsteil des Filters. Der Standard-Filter
     * bringt eine mit (siehe {@link #getStandardFilter(String)}), und der Anwender darf eine
     * eintippen — beides muss <b>hinter</b> das {@code WHERE}, sonst entsteht Unsinn wie
     * {@code WHERE ORDER BY id DESC}.
     */
    private static final Pattern ORDER_BY = Pattern.compile("\\bORDER\\s+BY\\b", Pattern.CASE_INSENSITIVE);

    private final JdbcTemplate jdbcTemplate;
    private final WhereClauseValidator whereClauseValidator;
    private final OrganizationContextService organizationContextService;

    public DatenbankService(JdbcTemplate jdbcTemplate, WhereClauseValidator whereClauseValidator,
                            OrganizationContextService organizationContextService) {
        this.jdbcTemplate = jdbcTemplate;
        this.whereClauseValidator = whereClauseValidator;
        this.organizationContextService = organizationContextService;
    }

    /**
     * Liefert die auswählbaren Basistabellen des Schemas {@code zev} (alphabetisch).
     */
    @Transactional(readOnly = true)
    public List<String> getTabellen() {
        return jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = ? AND table_type = 'BASE TABLE' "
                        + "ORDER BY table_name",
                String.class, SCHEMA);
    }

    /**
     * Liefert den Standard-Filter für eine Tabelle. Er besteht aus bis zu zwei Teilen:
     * <ul>
     *   <li>Hat die Tabelle eine {@code org_id}-Spalte, wird die Organisation des eingeloggten
     *       Benutzers vorgeschlagen ({@code org_id = <orgId>}).</li>
     *   <li>Hat die Tabelle eine {@code id}-Spalte, wird {@code ORDER BY id DESC} angehängt.</li>
     * </ul>
     * Trifft beides nicht zu, bleibt der Filter leer.
     *
     * <p>Die Standardsortierung ist kein Schönheitsmerkmal: Ohne {@code ORDER BY} ist die
     * Zeilenreihenfolge in Postgres <b>undefiniert</b>. In der Praxis liefert der Seq-Scan die
     * ältesten Zeilen zuerst — bei {@code zaehler_rohdaten} sah man auf Seite 1 monatealte
     * Daten und hielt eine seither ergänzte Spalte für kaputt. Zusätzlich können beim Blättern
     * über {@code OFFSET} Zeilen doppelt erscheinen oder ausfallen.
     *
     * <p>Der Tabellenname wird gegen die Katalog-Whitelist geprüft (injektionssicher);
     * die {@code org_id} des Benutzers stammt aus dem Request-Kontext (keine Benutzereingabe).
     */
    @Transactional(readOnly = true)
    public String getStandardFilter(String tabelle) {
        if (tabelle == null || !getTabellen().contains(tabelle)) {
            throw new IllegalArgumentException("DATENBANK_TABELLE_UNGUELTIG");
        }
        List<String> spalten = getSpalten(tabelle);
        StringBuilder filter = new StringBuilder();

        Long orgId = organizationContextService.getCurrentOrgId();
        if (orgId != null && spalten.contains(ORG_ID_SPALTE)) {
            filter.append(ORG_ID_SPALTE).append(" = ").append(orgId);
        }
        if (spalten.contains(ID_SPALTE)) {
            if (!filter.isEmpty()) {
                filter.append(" ");
            }
            filter.append("ORDER BY ").append(ID_SPALTE).append(" DESC");
        }
        return filter.toString();
    }

    /**
     * Führt eine read-only Abfrage auf der gewählten Tabelle aus und liefert die Zeilen paginiert.
     */
    @Transactional(readOnly = true)
    public DatenbankAbfrageResponseDTO abfrage(DatenbankAbfrageRequestDTO request) {
        String tabelle = request.getTabelle();

        // 1) Tabellen-Whitelist (injektionssicher: exakter Abgleich gegen Katalog)
        if (tabelle == null || !getTabellen().contains(tabelle)) {
            throw new IllegalArgumentException("DATENBANK_TABELLE_UNGUELTIG");
        }

        // 2) WHERE validieren (Guards)
        String where = request.getWhere();
        whereClauseValidator.validate(where);

        // 3) Pagination klemmen
        int size = request.getSize() == null ? DEFAULT_SIZE : Math.min(Math.max(request.getSize(), 1), MAX_SIZE);
        int page = request.getPage() == null ? 0 : Math.max(request.getPage(), 0);
        int offset = page * size;

        // 4) Spalten aus dem Katalog (bytea ausgeschlossen)
        List<String> spalten = getSpalten(tabelle);
        if (spalten.isEmpty()) {
            throw new IllegalArgumentException("DATENBANK_TABELLE_UNGUELTIG");
        }

        // 5) Read-only absichern + Langläufer verhindern
        jdbcTemplate.execute("SET LOCAL statement_timeout = " + STATEMENT_TIMEOUT_MS);

        // 6) Optionale Sortierung: Spalte MUSS aus der Katalog-Whitelist stammen (injektionssicher),
        //    Richtung strikt ASC/DESC.
        String orderBySql = "";
        String sortSpalte = request.getSortSpalte();
        if (sortSpalte != null && !sortSpalte.isBlank()) {
            if (!spalten.contains(sortSpalte)) {
                throw new IllegalArgumentException("DATENBANK_SORT_UNGUELTIG");
            }
            boolean desc = "DESC".equalsIgnoreCase(request.getSortRichtung());
            orderBySql = " ORDER BY \"" + sortSpalte + "\" " + (desc ? "DESC" : "ASC");
        }

        // 7) Filter in Bedingung und (optionale) ORDER BY-Klausel zerlegen. Der Filter ist ein
        //    einziges Eingabefeld; die Sortierung daraus muss hinter das WHERE, sonst entsteht
        //    " WHERE ORDER BY id DESC". Eine per Klick gewaehlte Sortierspalte hat Vorrang.
        String bedingung = where;
        String filterOrderBySql = "";
        if (where != null && !where.isBlank()) {
            Matcher orderBy = ORDER_BY.matcher(where);
            if (orderBy.find()) {
                bedingung = where.substring(0, orderBy.start()).trim();
                filterOrderBySql = " " + where.substring(orderBy.start()).trim();
            }
        }

        String cols = String.join(", ", spalten.stream().map(c -> "\"" + c + "\"").toList());
        String whereSql = (bedingung == null || bedingung.isBlank()) ? "" : " WHERE " + bedingung;
        // Tabellenname/Spalten/Sortierspalte stammen aus dem Katalog -> injektionssicher.
        // Die ORDER BY-Klausel aus dem Filter ist dagegen freier Text und nur ueber den
        // WhereClauseValidator abgesichert - wie der Bedingungsteil, in dem sie bisher stand.
        String sql = "SELECT " + cols + " FROM " + SCHEMA + ".\"" + tabelle + "\"" + whereSql
                + (orderBySql.isEmpty() ? filterOrderBySql : orderBySql) + " LIMIT ? OFFSET ?";

        // size+1 lesen, um hatMehr ohne separaten COUNT zu bestimmen
        List<List<Object>> zeilen;
        try {
            zeilen = jdbcTemplate.query(sql, (rs, rowNum) -> {
                List<Object> row = new ArrayList<>(spalten.size());
                for (int i = 1; i <= spalten.size(); i++) {
                    Object value = rs.getObject(i);
                    row.add(value == null ? null : value.toString());
                }
                return row;
            }, size + 1, offset);
        } catch (DataAccessException e) {
            // Keine DB-Interna nach aussen geben (nur intern loggen)
            log.warn("Datenbank-Ansicht: Abfrage fehlgeschlagen (Tabelle={}): {}", tabelle, e.getMessage());
            throw new IllegalArgumentException("DATENBANK_ABFRAGE_FEHLER");
        }

        boolean hatMehr = zeilen.size() > size;
        if (hatMehr) {
            zeilen = zeilen.subList(0, size);
        }

        // Audit-Log (Application-Log): wer hat welche Abfrage ausgeführt
        log.info("Datenbank-Ansicht: user={}, tabelle={}, where='{}', sort='{}', page={}, size={} -> {} Zeilen",
                aktuellerBenutzer(), tabelle, where == null ? "" : where, orderBySql.trim(), page, size,
                zeilen.size());

        return new DatenbankAbfrageResponseDTO(spalten, zeilen, page, size, hatMehr);
    }

    /**
     * Spaltennamen der Tabelle in Katalog-Reihenfolge; bytea-/Binärspalten werden ausgeschlossen.
     */
    private List<String> getSpalten(String tabelle) {
        return jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns "
                        + "WHERE table_schema = ? AND table_name = ? AND data_type <> 'bytea' "
                        + "ORDER BY ordinal_position",
                String.class, SCHEMA, tabelle);
    }

    private String aktuellerBenutzer() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "unbekannt";
    }
}
