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
- `services/datenbank-filter-historie.service.ts` – lokale Filter-Historie je Tabelle (`localStorage`, max. 20)
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
| [x] | 12. Nachtrag (Vibe): Standard-Filter auf eigene Organisation | Bei Tabellenwahl wird das Filter-Feld mit `org_id = <orgId des Benutzers>` vorbefüllt, sofern die Tabelle eine `org_id`-Spalte hat. **Backend:** `DatenbankService.getStandardFilter(tabelle)` (`OrganizationContextService` injiziert wie in `FeatureFlagService`; Whitelist-Prüfung, `getSpalten().contains("org_id")`, `getCurrentOrgId()`); neuer Endpunkt `GET /api/datenbank/standard-filter?tabelle=` → `{ where }` (JSON-Objekt statt String, damit HttpClient korrekt parst). **Frontend:** `DatenbankService.getStandardFilter()`, `DatenbankAnsichtComponent.onTabelleChange()` (via `(ngModelChange)` am Dropdown) setzt `whereClause`, verwirft Ergebnis. Keine neue Migration/Übersetzung nötig (bestehendes Feld). |
| [x] | 13. Nachtrag (Vibe): Löschen-Button im Filter-Feld | Löschen-Button (×) im WHERE-Feld. **Design System:** neues wiederverwendbares Muster `zev-input-wrapper` + `zev-input-clear` in `design-system/src/components/form/form.css` (aus dem lokalen `search-clear`-Muster des `translation-editor` in die DS gehoben). **Frontend:** WHERE-`<input>` in `zev-input-wrapper` gewrappt, `<button class="zev-input-clear">×</button>` (`@if (whereClause)`), `onFilterLeeren()` leert das Feld. **i18n:** `V75__Add_DatenbankFilterLeeren_Translation.sql` (`DATENBANK_FILTER_LEEREN`, DE/EN, `ON CONFLICT DO NOTHING`). Design-System neu gebaut (`dist/index.css`). |
| [x] | 14. Nachtrag (Vibe): Paginierung zusätzlich oben | Blätter-Bedienelemente auch **oben** in der Aktionszeile neben „Anzeigen", damit bei langen Tabellen nicht zum Blättern gescrollt werden muss. **Frontend:** Bedienelemente einmal als `<ng-template #paginierung>` definiert und via `*ngTemplateOutlet` **zweimal** eingebunden (oben + unten) – keine Markup-Duplikation; obere Instanz nur bei vorhandenem, nicht-leerem Ergebnis (`@if`). **Design System:** neue Modifier in `table.css` – `.zev-pagination--inline` (ohne `margin-top`, für die Nutzung in einer Aktionszeile) und `.zev-pagination--start` (linksbündig statt zentriert); beide Paginierungen der Datenbank-Ansicht sind linksbündig. Die zentrierte Standard-Variante (`systemmeldungen`) bleibt unverändert. Die Button-Grösse wird über den Template-Kontext gesteuert (`compact: false` oben → gleiche Höhe wie „Anzeigen"; `compact: true` unten). **Kein Flackern:** Die Tabelle bleibt während einer laufenden Abfrage **stehen** (`@if (result)` statt `@if (result && !loading)`) und wird nur gedimmt/gesperrt – dafür neue DS-Utility `.zev-busy` in `spinner.css`; der „Laden…"-Hinweis erscheint nur noch bei der ersten Abfrage (`@if (loading && !result)`), Blätter-Buttons sind währenddessen deaktiviert. Zuvor wurde die Tabelle bei **jedem** Seitenwechsel aus dem DOM entfernt → Seitenhöhe kollabierte auf eine Zeile und wuchs wieder (starker Layout-Sprung, Button verschwand unter dem Cursor). **Aufräumen:** komponentenlokales `.zev-datenbank-pagination` entfernt – es duplizierte `.zev-pagination` aus dem Design System (das `systemmeldungen` bereits nutzt); Component-CSS enthält nur noch den Tabellen-Scroll-Container. Keine Migration/Übersetzung nötig (bestehende Keys). |

| [x] | 15. Nachtrag (Vibe): Filter-Historie im Browser | Die zuletzt verwendeten WHERE-Filter werden **lokal im Browser** gespeichert und am Filter-Feld als Auswahlliste angeboten (Spec FR-1b). **Neu:** `services/datenbank-filter-historie.service.ts` (`@Injectable({providedIn:'root'})`) – `getHistorie(tabelle)` / `addFilter(tabelle, filter)`, `localStorage`-Key `zev-datenbank-filter-historie`, JSON `{ "<tabelle>": ["<filter>", …] }`, **pro Tabelle max. 20** Einträge (`MAX_EINTRAEGE`), neuester zuerst, Duplikat wandert nach vorne; Lesen defensiv bereinigt (nur nicht-leere Strings, Kappung auf 20) und `try/catch` wie in `ThemeService` (privater Modus/Quota → Historie einfach leer). **Frontend-Komponente:** `filterHistorie`-Feld; `onTabelleChange()` lädt die Historie der neuen Tabelle; im **Erfolgs**-Zweig von `abfrage()` wird der tatsächlich abgefragte Filter aufgenommen (Tabelle/Filter vor dem Request festgehalten, da das Feld bis zur Antwort geändert werden kann; die sichtbare Liste wird nur aktualisiert, wenn die Tabelle noch dieselbe ist) – fehlgeschlagene Abfragen (`400`) landen **nicht** in der Historie. **Template:** natives `<datalist id="dbWhereHistorie">` mit `@for` über `filterHistorie`, am `<input>` via `list="dbWhereHistorie"` + `autocomplete="off"` – **kein** zusätzliches Bedienelement, **kein** neues CSS (Design System unverändert). Kein Backend-Anteil, **keine** Migration/Übersetzung (die Liste zeigt nur die gespeicherten Filtertexte). |

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
- **Filter-Historie:** nur nicht-leere, **erfolgreich** ausgeführte Filter werden gespeichert (getrimmt, dedupliziert, max. 20 je Tabelle); aus der Historie übernommene Filter durchlaufen beim Ausführen dieselbe Backend-Validierung wie eine manuelle Eingabe.
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
- **Filter-Historie / `<datalist>`:** Browser filtern die angezeigten Vorschläge nach dem aktuellen Feldinhalt. Da das Feld beim Tabellenwechsel mit dem Standard-Filter (`org_id = …`) vorbefüllt wird, ist die **vollständige** Liste erst nach dem Leeren des Feldes (Löschen-Button ×) sichtbar – bewusst in Kauf genommen, um ohne zusätzliches Bedienelement/CSS auszukommen.
- **Kein Löschen einzelner Historie-Einträge** über die Oberfläche (Verdrängung nach 20 Einträgen bzw. Browser-Daten löschen) – siehe Spec §7.
