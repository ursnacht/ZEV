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

    private final JdbcTemplate jdbcTemplate;
    private final WhereClauseValidator whereClauseValidator;

    public DatenbankService(JdbcTemplate jdbcTemplate, WhereClauseValidator whereClauseValidator) {
        this.jdbcTemplate = jdbcTemplate;
        this.whereClauseValidator = whereClauseValidator;
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

        String cols = String.join(", ", spalten.stream().map(c -> "\"" + c + "\"").toList());
        String whereSql = (where == null || where.isBlank()) ? "" : " WHERE " + where;
        // Tabellenname/Spalten/Sortierspalte stammen aus dem Katalog -> injektionssicher.
        String sql = "SELECT " + cols + " FROM " + SCHEMA + ".\"" + tabelle + "\"" + whereSql
                + orderBySql + " LIMIT ? OFFSET ?";

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
