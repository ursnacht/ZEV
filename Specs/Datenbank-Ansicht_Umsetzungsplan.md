# Datenbank-Ansicht – Umsetzungsplan

## Zusammenfassung

Die Seite `/einstellungen` erhält einen nur für `zev_admin` sichtbaren Bereich **Datenbank-Ansicht**:
Auswahl einer Tabelle des Schemas `zev`, optionale SQL-WHERE-Klausel als Filter und **generische**
(spaltenunabhängige) Anzeige der Zeilen. Der Zugriff ist **strikt read-only** und über eine neue
feingranulare Permission **`datenbank:read`** geschützt. Umsetzung ohne neue Tabelle/Entity —
generisch via `JdbcTemplate` + Katalog-Metadaten.

Grundlage: [`Specs/Datenbank-Ansicht.md`](./Datenbank-Ansicht.md); Permission-Modell:
[`Specs/Composite-Roles.md`](./Composite-Roles.md) / [`Specs/Berechtigungen.md`](./Berechtigungen.md).

> **Abweichung vom Standard-Template:** Kein Entity/Repository/keine Flyway-Tabellen-Migration
> (reiner Lesezugriff auf bestehende Tabellen). Der Service nutzt **`JdbcTemplate`** statt eines
> Spring-Data-Repositories. Kein neues Routing/keine Navigation (Bereich liegt in `/einstellungen`).

## Betroffene Komponenten

**Backend (neu):**
- `dto/DatenbankAbfrageRequestDTO.java` – `{ tabelle, where, page, size }` (DTO-Suffix wg. ArchUnit-Namenskonvention)
- `dto/DatenbankAbfrageResponseDTO.java` – `{ spalten[], zeilen[[]], seite, groesse, hatMehr }`
- `service/DatenbankService.java` – `JdbcTemplate`: Tabellenliste, Spalten (ohne `bytea`), read-only-Abfrage, Audit-Log
- `service/WhereClauseValidator.java` (oder `util/`) – Guard-Prüfung der WHERE-Eingabe
- `controller/DatenbankController.java` – `/api/datenbank`, `@PreAuthorize("hasAuthority('datenbank:read')")`

**Frontend (neu):**
- `models/datenbank.model.ts` – Request/Response-Interfaces
- `services/datenbank.service.ts` – API-Calls (`getTabellen`, `abfrage`)
- `components/datenbank-ansicht/datenbank-ansicht.component.*` – Dropdown, WHERE-Feld, generische Ergebnis-Tabelle, Pagination, Messages

**Geändert:**
- `components/einstellungen/einstellungen.component.{ts,html}` – Bereich einbinden, `canViewDatenbank = inject(Keycloak).hasRealmRole('datenbank:read')`
- `keycloak/realms/zev-realm.json` – Rolle `datenbank:read` + Composite in `zev_admin`
- `Specs/Berechtigungen.md` – neue Permission dokumentieren
- Flyway `V71__Add_DatenbankAnsicht_Translations.sql` – i18n-Keys (DE/EN)

## Umsetzungsreihenfolge (Phasen)

| Status | Phase | Beschreibung |
|--------|-------|--------------|
| [x] | 1. Keycloak-Permission | `zev-realm.json`: Realm-Rolle `datenbank:read` angelegt + in `zev_admin`-Composite aufgenommen. **Anwendung/Reimport durch den Benutzer** (Umgebungs-Konvention). |
| [x] | 2. Backend-DTOs | `DatenbankAbfrageRequest` (`tabelle` `@NotBlank`; `page`/`size` als `Integer`, im Service geklemmt), `DatenbankAbfrageResponse` (`spalten`, `zeilen`, `seite`, `groesse`, `hatMehr`). |
| [x] | 3. WHERE-Validator | `WhereClauseValidator` (`@Component`): lehnt `;`, Kommentare (`--`, `/*`, `*/`), DML/DDL-Keywords (`INSERT/UPDATE/DELETE/DROP/ALTER/TRUNCATE/CREATE/GRANT/REVOKE/MERGE/COPY/CALL/DO`) und Sub-`SELECT` ab (Wortgrenzen-Regex, case-insensitive); Längenlimit 500. Wirft `IllegalArgumentException` mit Übersetzungs-Key. |
| [x] | 4. Backend-Service | `DatenbankService` (`JdbcTemplate`): `getTabellen()` (`information_schema.tables`, `zev`/`BASE TABLE`); `getSpalten()` (`information_schema.columns`, **`bytea` ausgeschlossen**); `abfrage()`: Whitelist-Prüfung, WHERE-Validierung, `@Transactional(readOnly=true)` + `SET LOCAL statement_timeout=5000`, `SELECT <spalten> FROM zev.<tabelle> [WHERE ...] LIMIT ? OFFSET ?` (kein ORDER BY), `hatMehr` via size+1, Werte als String, DB-Fehler ohne Interna, **Audit-Log**. |
| [x] | 5. Backend-Controller | `DatenbankController` (`/api/datenbank`, `@PreAuthorize("hasAuthority('datenbank:read')")`): `GET /tabellen`, `POST /abfrage`; `IllegalArgumentException` → `400`. |
| [x] | 6. Frontend Model + Service | `datenbank.model.ts`, `datenbank.service.ts` (`getTabellen()`, `abfrage(req)`), `subscribe({next,error})`. |
| [x] | 7. Frontend-Komponente | `DatenbankAnsichtComponent` (standalone, `WithMessage`): Tabellen-Dropdown (`/tabellen`), WHERE-Textfeld (Platzhalter), „Anzeigen"-Button (disabled ohne Tabelle), **generische** Ergebnis-Tabelle (Spalten/Zeilen aus Response), Pagination (`hatMehr`), Message-Bereich; `@if`/`@for`, `TranslatePipe`. |
| [x] | 8. Integration in Einstellungen | `<app-datenbank-ansicht>` in `einstellungen.component.html` unter `@if (canViewDatenbank)`; `canViewDatenbank = inject(Keycloak).hasRealmRole('datenbank:read')`. |
| [x] | 9. Übersetzungen | `V71__Add_DatenbankAnsicht_Translations.sql` (DE/EN, `ON CONFLICT DO NOTHING`): Titel, Labels, Pagination, Fehlermeldungen/Validierungs-Keys. |
| [x] | 10. Berechtigungen-Doku | `Berechtigungen.md`: `datenbank:read` in Fachrolle→Permission, Matrix (Fussnote ⁵) + Endpunkt-Referenz (`/api/datenbank`), nur `zev_admin`. |
| [x] | 11. Nachtrag (Vibe): Sortierung + Enter | **Serverseitige Sortierung**: DTO um `sortSpalte`/`sortRichtung` erweitert; `DatenbankService` prüft `sortSpalte` gegen die Katalog-Spaltenliste (Whitelist, injektionssicher), `sortRichtung` strikt `ASC`/`DESC`, hängt `ORDER BY "<spalte>" ASC|DESC` an. Frontend: klickbare `<th>` mit **Design-System-Klassen** `zev-table__header--sortable` + `zev-table__sort-indicator` (▲/▼, wie `tarif-list` – kein eigenes CSS), `onSort()` (Richtung togglen, Seite→0). **Enter im Filterfeld** (`(keyup.enter)`) löst „Anzeigen" aus. |

> **Tests** (`/3_backend-tests`, `/4_frontend-unit-tests`, `/5_e2e-tests`) werden separat erstellt und sind **nicht** Teil dieser Umsetzung. Schwerpunkte: WHERE-Validator (Guards), Whitelist-/`403`-Fälle, `bytea`-Ausschluss, Pagination, generische Anzeige, Sichtbarkeit nur für `zev_admin`.

## Validierungen

### Backend (maßgeblich)
- **`tabelle`:** nicht leer; muss exakt einer `zev`-Basistabelle entsprechen (dynamische Whitelist) — sonst `400`, **kein** SQL.
- **`where`:** optional; falls gesetzt Guard-Prüfung (siehe Phase 3); Längenlimit. Verstoss → `400`, keine Ausführung.
- **`size`:** 1..500 (Default 50); **`page`:** ≥ 0.
- **`sortSpalte`:** optional; falls gesetzt muss sie exakt einer Katalog-Spalte entsprechen (Whitelist) — sonst `400`, **kein** SQL. **`sortRichtung`:** nur `ASC`/`DESC` (Default `ASC`).
- **Ausführung:** read-only-Transaktion + `statement_timeout=5s`; `bytea`-Spalten ausgeschlossen; `ORDER BY` nur bei gültiger `sortSpalte`.
- **Autorisierung:** `datenbank:read` (nur `zev_admin`) — sonst `403`.
- **Fehler-Hygiene:** Fehlermeldung ohne Stacktrace/DB-Interna.

### Frontend
- „Anzeigen" deaktiviert, solange keine Tabelle gewählt; **Enter** im Filterfeld löst „Anzeigen" aus (nur wenn Tabelle gewählt und keine Abfrage läuft).
- `size`/`page` über Pagination-Steuerung; WHERE optional.
- **Sortierung:** Klick auf Spaltenkopf → `onSort()` (bei gleicher Spalte Richtung umkehren, sonst `ASC`), Seite auf 0; „Anzeigen" setzt die Sortierung zurück.
- Backend-Fehler (`400`/`403`/Timeout) als Message anzeigen; leeres Ergebnis → „keine Daten".
- Bereich nur bei `hasRealmRole('datenbank:read')` gerendert.

## Offene Punkte / Annahmen

- **Alle Spec-Fragen sind beantwortet** (Datenbank-Ansicht.md §8): serverseitige Sortierung per Spalten-Klick (Nachtrag), `bytea` ausblenden, keine dedizierte DB-Rolle, Audit = Application-Log, Timeout 5 s / Paging 50/500, Permission `datenbank:read`, kein CSV-Export.
- **Migrationsnummer `V71`:** V69/V70 sind im MQTT-Integration-Plan reserviert; bei der Umsetzung die **nächste freie Nummer verifizieren** (zev-db MCP / `flyway:info`).
- **Keycloak:** Realm-Änderung (`datenbank:read` + Composite) wird **vom Benutzer** angewandt (kein Reimport durch Claude). `hasRealmRole('datenbank:read')` funktioniert, da Composite-Rollen im Token expandiert werden (wie `featureflags:manage`).
- **`JdbcTemplate`** ist über `spring-boot-starter-data-jpa` (JDBC-Autoconfig) verfügbar; kein zusätzliches Starter-Dependency nötig.
- **Pagination ohne aktive Sortierung:** Reihenfolge über Seiten hinweg nicht garantiert stabil; eine per Spalten-Klick gewählte Sortierung (`ORDER BY`) stabilisiert sie.
- **Restrisiko WHERE-Guard:** Keyword-Blacklist per Wortgrenzen-Regex; Rest-Absicherung durch read-only-Transaktion + `statement_timeout` + `LIMIT` (defense-in-depth). Sub-`SELECT` explizit verboten.
- **ArchUnit:** `ArchitectureTest` ist grün. Der direkte `JdbcTemplate`-Zugriff im Service verletzt **keine** Schichtenregel (keine Ausnahme nötig). Einzige Anpassung: DTOs müssen auf `DTO` enden (`dtosShouldEndWithDTO`) → `DatenbankAbfrageRequestDTO`/`DatenbankAbfrageResponseDTO`.
- **Whitelist-Ermittlung:** pro Anfrage aus dem Katalog (leichtgewichtig); optionales Caching später.
