# Datenbank-Ansicht

## 1. Ziel & Kontext - Warum wird das Feature benötigt?
* **Was soll erreicht werden:** Die bestehende GUI-Seite `/einstellungen` erhält eine zusätzliche **Datenbank-Ansicht**: Ein `zev_admin` wählt eine Tabelle des Anwendungsschemas `zev` aus, gibt optional eine **SQL-WHERE-Klausel** als Filter ein und erhält die Zeilen der Tabelle **generisch** (spaltenunabhängig) angezeigt.
* **Warum machen wir das:** Schnelle, mandantenübergreifende Inspektion der Daten direkt im Betrieb (Support/Debugging), ohne DB-Client/SSH-Zugang. Ersetzt punktuell den Griff zur Konsole (`psql`) bzw. zum `zev-db`-MCP.
* **Aktueller Stand:** `/einstellungen` (`EinstellungenComponent`, `EinstellungenController` unter `einstellungen:write`) bietet mandantenspezifische Einstellungen. Es gibt **keine** generische Tabellen-Ansicht. Direkter DB-Zugriff nur via `docker exec psql` oder `zev-db`-MCP.

> **Sicherheits-Hinweis (Design):** Freie Tabellenauswahl + freie WHERE-Eingabe sind per Definition ein Injection-naher Zugriffspfad. Das Feature ist deshalb **ausschliesslich lesend**, **nur für `zev_admin`**, auf das Schema **`zev` beschränkt** und durch mehrere Guards abgesichert (NFR-2). Es ist ein Admin-/Inspektionswerkzeug, kein Endbenutzer-Feature.

## 2. Funktionale Anforderungen (FR) - Was soll das System tun?

### FR-1: Ablauf / Flow
1. `zev_admin` öffnet `/einstellungen`; ein zusätzlicher Bereich **„Datenbank-Ansicht"** ist sichtbar (nur mit Permission `datenbank:read`; siehe NFR-2).
2. Das System lädt die Liste der **auswählbaren Tabellen** (alle Basistabellen des Schemas `zev`, dynamisch aus dem Katalog) in ein Auswahlfeld (Dropdown).
3. Der Admin wählt eine Tabelle und gibt **optional** eine WHERE-Klausel in ein Textfeld ein (ohne das Schlüsselwort `WHERE`, z.B. `org_id = 42 AND zeit > '2026-01-01'`).
4. Der Admin klickt **„Anzeigen"** — alternativ genügt die **Enter-Taste**, während der Fokus im Filter-Eingabefeld liegt (löst dieselbe Aktion aus, sofern eine Tabelle gewählt ist und keine Abfrage läuft).
5. Das System validiert Tabelle + WHERE (NFR-2), führt eine **read-only**-Abfrage aus und liefert die Zeilen **paginiert** zurück.
6. Das System zeigt die Ergebnisse **generisch** als Tabelle an: Spaltenüberschriften aus den DB-Metadaten, darunter die Zeilen; plus Zeilenanzahl und Pagination.
7. Der Admin kann per **Klick auf eine Spaltenüberschrift** nach dieser Spalte **sortieren** (erneuter Klick kehrt die Richtung um). Die Sortierung erfolgt **serverseitig über den gesamten Datensatz** (nicht nur die aktuelle Seite) und setzt die Anzeige auf Seite 1 zurück.
8. Bei ungültiger Eingabe / Fehler zeigt das System eine verständliche Fehlermeldung; es werden **keine** Daten geändert.

### FR-1a: Standard-Filter auf die eigene Organisation (Nachtrag)
* Bei der **Auswahl einer Tabelle** prüft das System, ob diese eine `org_id`-Spalte besitzt. Ist das der Fall, wird das **Filter-Feld** als **Default** mit der `org_id` des **eingeloggten Benutzers** vorbefüllt (`org_id = <orgId>`).
* Besitzt die Tabelle **keine** `org_id`-Spalte (z.B. `translation`), bleibt das Filter-Feld **leer**.
* Der Default ist nur ein **Vorschlag**: Der Admin kann die WHERE-Klausel jederzeit ändern oder löschen, um weiterhin **mandantenübergreifend** (Rohansicht) abzufragen.
* Ein **erneuter** Tabellenwechsel setzt den Standard-Filter neu (überschreibt eine vorherige, ggf. angepasste Eingabe) und verwirft das bisherige Ergebnis.
* Die interne `org_id` (BIGINT) ist **nicht** im JWT enthalten, sondern wird serverseitig aus dem Organisations-Kontext des Requests ermittelt; das Frontend ruft sie über einen Endpunkt ab (FR-3). Der Tabellenname wird dabei gegen die Whitelist geprüft (injektionssicher).

### FR-1b: Filter-Historie im Browser (Nachtrag)
* Nach jeder **erfolgreich ausgeführten** Abfrage wird die verwendete (nicht-leere) WHERE-Klausel in eine **lokale Filter-Historie** aufgenommen. Abgewiesene/fehlerhafte Filter (`400`) werden **nicht** gespeichert – die Historie enthält damit nur gültige Filter.
* Die Historie wird **pro Tabelle** geführt und ausschliesslich **lokal im Browser** gehalten (`localStorage`, Key `zev-datenbank-filter-historie`, JSON `{ "<tabelle>": ["<filter>", …] }`). Es werden **maximal 20 Einträge je Tabelle** gespeichert, **neuester zuerst**; beim Überschreiten fällt der älteste Eintrag weg.
* **Keine Duplikate:** Wird ein bereits vorhandener Filter erneut verwendet, wandert er an die erste Stelle (kein zweiter Eintrag).
* Die Historie wird am Filter-Feld als **Auswahlliste (Dropdown)** angeboten (natives `<datalist>` am bestehenden Eingabefeld): Klick bzw. Tippen zeigt die Vorschläge, die Auswahl übernimmt den Filter in das Feld. Der Browser filtert die angezeigten Vorschläge nach dem aktuellen Feldinhalt; über den Löschen-Button (×) wird die **vollständige** Liste sichtbar.
* Beim **Tabellenwechsel** wird die Historie der neu gewählten Tabelle geladen (die des vorherigen bleibt gespeichert).
* Die Historie ist **gerätelokal** (Browser-Profil): keine Server-Persistenz, keine Übertragung an das Backend, keine Synchronisation zwischen Geräten/Benutzern.

### FR-2: Generische Abfrage (kein tabellenspezifischer Code)
* Die Anzeige ist **datengetrieben**: Spalten stammen aus `ResultSetMetaData` bzw. dem Ergebnis-Mapping (`JdbcTemplate`), **nicht** aus fest codierten DTOs/Entities je Tabelle.
* Erzeugte Abfrage (konzeptionell): `SELECT * FROM zev.<tabelle> [WHERE <where>] [ORDER BY <sortSpalte> <ASC|DESC>] LIMIT <size> OFFSET <offset>`.
* **Sortierung (optional, serverseitig):** `sortSpalte` muss **exakt** einer Spalte der dynamischen Katalog-Spaltenliste entsprechen (gleiche Whitelist-Prüfung wie der Tabellenname → injektionssicher); `sortRichtung` ist strikt `ASC`/`DESC`. Ohne `sortSpalte` wird **kein `ORDER BY`** gesetzt.
  > *Hinweis:* Ohne aktive Sortierung ist die Zeilenreihenfolge über Pagination-Seiten hinweg von PostgreSQL **nicht garantiert stabil**; eine gewählte Sortierung stabilisiert sie.
* **Binärspalten (`bytea`) werden ausgeblendet** (nicht in Spalten/Zeilen zurückgegeben); übrige Typen (inkl. `jsonb`) werden als Text dargestellt (ggf. gekürzt).
* Der Tabellenname wird **nicht** interpoliert übernommen, sondern gegen die dynamisch ermittelte **Whitelist** der `zev`-Tabellen geprüft (exakte Übereinstimmung) — dadurch ist der Tabellenname injektionssicher.
* **Rohansicht über alle Mandanten:** Der Hibernate-`orgFilter` wird **nicht** angewendet (native Abfrage via `JdbcTemplate`); Zeilen aller Mandanten werden angezeigt, `org_id` erscheint als normale Spalte. (Bewusste Ausnahme von der sonstigen Mandanten-Isolation, gerechtfertigt durch die globale Rolle `zev_admin` — siehe NFR-2.)

### FR-3: Backend-Endpunkte
Neuer Controller unter `/api/datenbank`, klassenweit `@PreAuthorize("hasAuthority('datenbank:read')")`:
| Methode | Pfad | Zweck |
|---------|------|-------|
| `GET` | `/api/datenbank/tabellen` | Liste der auswählbaren Tabellennamen (Schema `zev`) |
| `GET` | `/api/datenbank/standard-filter?tabelle=<name>` | Standard-Filter für eine Tabelle → `{ where: "org_id = <orgId>" }` (bzw. `{ where: "" }`, falls keine `org_id`-Spalte). Tabellenname gegen Whitelist geprüft. |
| `POST` | `/api/datenbank/abfrage` | Body `{ tabelle, where, page, size, sortSpalte?, sortRichtung? }` → `{ spalten[], zeilen[[]], seite, groesse, hatMehr }` |

* `POST` statt `GET`, damit die WHERE-Klausel nicht in der URL/den Logs landet.
* Rückgabe: Spaltennamen als String-Liste, Zeilen als Liste von Wert-Listen (oder Liste von Maps); Werte als String-repräsentiert/JSON-serialisierbar.

### FR-4: Layout / Anzeige
* Der Bereich wird in `/einstellungen` als eigener Abschnitt/Panel eingefügt (Design-System-`panel`/`card`), sichtbar nur bei `datenbank:read`.
* Elemente: **Tabellen-Dropdown**, **WHERE-Textfeld** (Platzhalter mit Beispiel; **Enter** löst „Anzeigen" aus; **Löschen-Button ×** am rechten Feldrand leert das Feld, sichtbar nur bei nicht-leerem Inhalt), **„Anzeigen"-Button**, **Ergebnis-Tabelle** (Design-System-`table`) mit **klickbaren, sortierbaren Spaltenüberschriften** inkl. Richtungs-Indikator (▲/▼), **Pagination**, **Message-Bereich** (Design-System-`message`) für Fehler/Hinweise.
* Der Löschen-Button nutzt das wiederverwendbare Design-System-Muster **`zev-input-wrapper` + `zev-input-clear`** (kein komponenteneigenes CSS).
* Die **Filter-Historie** (FR-1b) hängt als natives `<datalist>` am WHERE-Textfeld (`list`-Attribut) – **kein** zusätzliches Bedienelement, **kein** neues CSS. `autocomplete="off"` verhindert, dass sich die Browser-Formular-Autofill-Liste mit den Vorschlägen überlagert.
* **Paginierung doppelt – oben und unten (Nachtrag):** Die Blätter-Bedienelemente (Vorherige / Seitenanzeige / Nächste) erscheinen **zusätzlich oben** in der Aktionszeile, **direkt neben** dem „Anzeigen"-Button. So kann bei langen Ergebnistabellen geblättert werden, **ohne ans Seitenende scrollen** zu müssen. Die untere Paginierung bleibt bestehen.
  * Beide Stellen zeigen **denselben Zustand** (Seitennummer, Aktiv-/Inaktiv-Zustand der Buttons) und lösen dieselben Aktionen aus; im Markup sind sie **einmal** definiert und werden zweimal eingebunden (keine Duplikation).
  * Die obere Paginierung erscheint **nur**, wenn ein Ergebnis mit mindestens einer Zeile vorliegt und keine Abfrage läuft.
  * **Beide** Paginierungen sind **linksbündig**. Darstellung über das Design-System: `.zev-pagination.zev-pagination--start` (unten) bzw. `.zev-pagination.zev-pagination--inline` (oben, ohne eigenen Abstand nach oben) – **kein** komponenteneigenes CSS.
  * Die Blätter-Buttons haben oben die **normale** Button-Höhe (wie „Anzeigen"), unten die kompakte.
* **Kein Flackern beim Blättern (Nachtrag):** Während einer laufenden Abfrage bleibt die bisherige Ergebnistabelle **stehen** und wird lediglich **gedimmt/gesperrt** (`.zev-busy`), statt ausgeblendet zu werden. Andernfalls kollabiert die Seitenhöhe bei jedem Seitenwechsel auf eine einzeilige „Laden…"-Meldung und wächst danach wieder – ein deutlich sichtbarer Layout-Sprung, bei dem zudem der gerade geklickte Blätter-Button unter dem Cursor verschwindet.
  * Der eigenständige „Laden…"-Hinweis erscheint nur bei der **ersten** Abfrage (solange noch kein Ergebnis vorliegt).
  * Die Blätter-Buttons sind während des Ladens **deaktiviert** (keine Doppelklicks/überholenden Abfragen).
* Alle Texte via `TranslationService`/`TranslatePipe` (keine Hardcodings).

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)
* [ ] Der Bereich „Datenbank-Ansicht" ist auf `/einstellungen` **nur** für Benutzer mit `datenbank:read` (→ `zev_admin`) sichtbar; `org_admin`/`zev_user` sehen ihn **nicht**.
* [ ] `GET /api/datenbank/tabellen` liefert genau die Basistabellen des Schemas `zev` (keine `keycloak`-/System-Tabellen).
* [ ] Auswahl einer Tabelle ohne WHERE zeigt deren Zeilen (paginiert, mit harter Obergrenze) generisch an — Spaltenüberschriften aus den DB-Metadaten.
* [ ] Eine gültige WHERE-Klausel (z.B. `org_id = 42`) filtert das Ergebnis korrekt.
* [ ] Die Ansicht zeigt Zeilen **aller Mandanten** (orgFilter nicht angewendet); `org_id` ist als Spalte sichtbar.
* [ ] Eine WHERE-Eingabe mit `;`, SQL-Kommentar (`--`, `/* */`), Mehrfach-Statement oder DML/DDL-Schlüsselwort (`INSERT/UPDATE/DELETE/DROP/ALTER/TRUNCATE/CREATE/GRANT/...`) wird **abgewiesen** (Fehlermeldung, keine Ausführung).
* [ ] Eine WHERE-Eingabe mit Sub-`SELECT` wird **abgewiesen**.
* [ ] Ein nicht in der Whitelist enthaltener/ungültiger Tabellenname wird **abgewiesen** (kein SQL ausgeführt).
* [ ] Jeder Schreibversuch (z.B. `UPDATE`-artige Eingabe) hat **keinerlei** Datenänderung zur Folge (read-only-Transaktion).
* [ ] Zugriff auf die Endpunkte ohne `datenbank:read` → `403`.
* [ ] Ungültige WHERE-Syntax → verständliche Fehlermeldung (`400`), keine Stacktraces/DB-Interna nach aussen.
* [ ] Leeres Ergebnis → Hinweis „keine Daten" statt leerer/fehlerhafter Tabelle.
* [ ] Ausgeführte Abfragen werden serverseitig protokolliert (wer, Tabelle, WHERE, Zeitpunkt) — Audit.
* [ ] Klick auf eine Spaltenüberschrift sortiert **serverseitig** nach dieser Spalte (über alle Seiten); erneuter Klick kehrt die Richtung um; ein Indikator (▲/▼) zeigt die aktive Sortierung.
* [ ] Enter im Filter-Eingabefeld löst „Anzeigen" aus (wenn eine Tabelle gewählt ist und keine Abfrage läuft).
* [ ] Eine ungültige/nicht existierende `sortSpalte` wird **abgewiesen** (`400`, kein SQL); `sortRichtung` nur `ASC`/`DESC`.
* [ ] Wird eine Tabelle **mit** `org_id`-Spalte gewählt, ist das Filter-Feld mit `org_id = <orgId des eingeloggten Benutzers>` vorbefüllt.
* [ ] Wird eine Tabelle **ohne** `org_id`-Spalte gewählt (z.B. `translation`), bleibt das Filter-Feld leer.
* [ ] Der vorbefüllte Filter kann geändert/gelöscht werden; ohne `org_id`-Filter werden weiterhin Zeilen aller Mandanten angezeigt.
* [ ] `GET /api/datenbank/standard-filter` mit einem nicht in der Whitelist enthaltenen Tabellennamen → `400`, kein SQL; Zugriff ohne `datenbank:read` → `403`.
* [ ] Bei nicht-leerem Filter-Feld ist ein Löschen-Button (×) sichtbar; Klick leert das Feld; bei leerem Feld ist der Button ausgeblendet.
* [ ] Liegt ein Ergebnis mit mindestens einer Zeile vor, sind die Blätter-Bedienelemente **zweimal** sichtbar: oben direkt neben „Anzeigen" und unterhalb der Tabelle; beide **linksbündig**.
* [ ] Blättern über die **obere** Paginierung wirkt identisch zur unteren (gleiche Seite, gleicher Inhalt); die Seitenanzeige stimmt an beiden Stellen überein.
* [ ] Auf Seite 1 ist „Vorherige" an **beiden** Stellen deaktiviert; ohne weitere Seiten (`hatMehr = false`) ist „Nächste" an beiden Stellen deaktiviert.
* [ ] Solange kein Ergebnis vorliegt oder das Ergebnis leer ist, wird die **obere** Paginierung **nicht** angezeigt.
* [ ] Die Blätter-Buttons haben oben dieselbe Höhe wie der „Anzeigen"-Button.
* [ ] **Beim Blättern springt das Layout nicht:** Die bisherige Tabelle bleibt sichtbar (gedimmt) und wird erst beim Eintreffen der neuen Seite ersetzt; die Paginierung bleibt an beiden Stellen sichtbar.
* [ ] Während des Ladens sind „Vorherige"/„Nächste" deaktiviert; der eigenständige „Laden…"-Hinweis erscheint nur, solange **noch kein** Ergebnis vorliegt.
* [ ] Nach einer erfolgreichen Abfrage mit nicht-leerem Filter steht dieser Filter in der Auswahlliste am Filter-Feld zur Verfügung.
* [ ] Die Auswahl eines Eintrags aus der Liste übernimmt den Filter unverändert in das Eingabefeld.
* [ ] Die Historie ist **pro Tabelle** getrennt: nach Wechsel auf eine andere Tabelle erscheinen nur deren Filter; ein Rückwechsel zeigt die vorherige Liste wieder.
* [ ] Es werden höchstens **20** Filter je Tabelle gespeichert; der 21. Filter verdrängt den ältesten.
* [ ] Ein erneut verwendeter Filter erscheint **einmal** und steht danach an erster Stelle.
* [ ] Ein abgewiesener Filter (`400`, z.B. `;` oder DML-Keyword) landet **nicht** in der Historie.
* [ ] Die Historie überlebt einen Seiten-Reload (Persistenz im `localStorage`) und wird **nicht** an den Server gesendet.
* [ ] Ist `localStorage` nicht verfügbar oder der gespeicherte Inhalt defekt, funktioniert die Datenbank-Ansicht unverändert (nur ohne Vorschläge) – keine Fehlermeldung, kein Absturz.

## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Performance
* Jede Abfrage ist **hart limitiert** (Pagination; **Default `size=50`, Max `size=500`**) via `LIMIT`/`OFFSET`.
* Ein DB-seitiges **Statement-Timeout von 5 s** verhindert Langläufer (`SET LOCAL statement_timeout`).
* `GET /tabellen` ist leichtgewichtig (Katalog-Abfrage).

### NFR-2: Sicherheit
* **Nur `zev_admin`:** Neue feingranulare Permission **`datenbank:read`**, ausschliesslich der Composite-Rolle `zev_admin` zugeordnet (konsistent mit `Specs/Composite-Roles.md`/`Berechtigungen.md`). Backend: `@PreAuthorize("hasAuthority('datenbank:read')")`; Frontend: Route `/einstellungen` bleibt `einstellungen:write`, der DB-Bereich wird zusätzlich per `datenbank:read` ein-/ausgeblendet (`AuthService`/Permission-Prüfung).
* **Read-only erzwungen:** Abfrage in read-only-Transaktion (`@Transactional(readOnly = true)` + `Connection.setReadOnly(true)`/`SET TRANSACTION READ ONLY`). **Keine** dedizierte DB-Rolle (Entscheidung) — die read-only-Transaktion genügt.
* **Audit-Log:** Ausführungen werden ins **Application-Log** geschrieben (Benutzer, Tabelle, WHERE, Zeitpunkt) — **keine** Audit-Tabelle (Entscheidung).
* **Tabellen-Whitelist:** Tabellenname muss exakt einer dynamisch aus dem Katalog gelesenen `zev`-Tabelle entsprechen; `keycloak`-, `pg_catalog`-, `information_schema`-Objekte sind ausgeschlossen.
* **WHERE-Guards:** Ablehnung bei `;`, Kommentaren (`--`, `/*`, `*/`), mehreren Statements, DML/DDL-Schlüsselwörtern und Sub-`SELECT`. Genau **ein** `SELECT` auf **eine** Whitelist-Tabelle.
* **Fehler-Hygiene:** Fehlermeldungen ohne DB-interne Details/Stacktraces (kein Informationsleck).
* **Bewusste Mandanten-Ausnahme:** Die mandantenübergreifende Rohansicht ist eine dokumentierte Ausnahme der Isolation, ausschliesslich für `zev_admin`.
* **Filter-Historie (FR-1b):** Gespeichert werden **ausschliesslich die WHERE-Klauseln**, **keine** Abfrage-Ergebnisse/Daten. Die Ablage erfolgt unverschlüsselt im `localStorage` des Admin-Browsers (gerätelokal, kein Serverbezug); die Werte fliessen bei der Ausführung durch dieselben WHERE-Guards wie eine manuelle Eingabe (aus dem Storage übernommene Filter erhalten **keinen** Vertrauensvorschuss).

### NFR-3: Kompatibilität
* Rein additiv: neue Endpunkte + UI-Bereich; keine Änderung an bestehenden Einstellungen-Funktionen, keine DB-Schema-Änderung (nur Lesezugriff).
* Die Filter-Historie (FR-1b) ist rein frontendseitig: **keine** Backend-Änderung, **keine** Flyway-Migration, **keine** neuen Übersetzungs-Keys (die Auswahlliste enthält nur die gespeicherten Filtertexte).
* Neue Permission `datenbank:read` muss im Keycloak-Realm (`zev_admin`-Composite) ergänzt werden.

## 5. Edge Cases & Fehlerbehandlung
| Szenario | Verhalten |
|----------|-----------|
| Keine Tabelle gewählt | „Anzeigen" deaktiviert bzw. Hinweis; keine Abfrage |
| Leere WHERE-Klausel | Gesamte Tabelle (mit LIMIT) anzeigen |
| Ungültige WHERE-Syntax | `400`, verständliche Meldung, keine DB-Interna |
| Verbotene Tokens (`;`, `--`, DML/DDL, Sub-SELECT) | `400` „unzulässige Eingabe", keine Ausführung |
| Unbekannter/ungültiger Tabellenname | `400`/`404`, kein SQL |
| Leeres Ergebnis | Hinweis „keine Daten" |
| Sehr grosses Ergebnis | Pagination + harte Obergrenze; Hinweis, dass gekürzt |
| Langläufer-Query | Statement-Timeout → `400`/`408` mit Hinweis |
| `bytea`/Binärspalten | Spalte **ausgeblendet** (nicht zurückgegeben) |
| Breite Werte (z.B. `jsonb`, langer Text) | als Text darstellen, ggf. kürzen mit Hinweis |
| `NULL`-Werte | als leer/„NULL" kenntlich darstellen |
| Netzwerkfehler (Frontend) | Fehlermeldung, kein Absturz |
| `localStorage` nicht verfügbar (privater Modus, Quota, blockiert) | Historie stillschweigend leer/ohne Persistenz; Abfragen funktionieren normal |
| Defekter/manipulierter Historie-Eintrag im Storage | Beim Lesen bereinigt (nur nicht-leere Strings, max. 20 je Tabelle); ungültiger JSON → leere Historie |
| Aus der Historie gewählter Filter ist inzwischen ungültig (Spalte entfernt) | Normale Backend-Fehlermeldung (`400`); der Eintrag bleibt in der Historie und kann überschrieben/gelöscht werden |
| Tabelle ohne bisherige Filter | Keine Vorschläge (leere Auswahlliste), Feld normal nutzbar |
| Zugriff ohne `datenbank:read` | `403`; UI-Bereich gar nicht sichtbar |

## 6. Abhängigkeiten & betroffene Funktionalität
* **Voraussetzungen:** Bestehende `/einstellungen`-Seite und Permission-Modell (`Composite-Roles.md`, `Berechtigungen.md`); PostgreSQL-Katalog (`information_schema`/`pg_catalog`).
* **Betroffener Code:**
  - Backend (neu): `DatenbankController`, `DatenbankService` (`JdbcTemplate`), DTOs (`DatenbankAbfrageRequest`, `DatenbankAbfrageResponse`), WHERE-Validator.
  - Frontend (neu/erweitert): Abschnitt in `EinstellungenComponent` (oder Sub-Component `DatenbankAnsichtComponent`), `DatenbankService`, ggf. generische Ergebnis-Tabelle, `DatenbankFilterHistorieService` (localStorage, FR-1b).
  - Security: `SecurityConfig`/JWT-Mapping unverändert (Permission kommt aus Keycloak-Rolle); Keycloak-Realm um `datenbank:read` (→ `zev_admin`) ergänzen.
  - i18n: neue Übersetzungs-Keys (Flyway-Migration `V[XX]__Add_DatenbankAnsicht_Translations.sql`).
  - `Berechtigungen.md`: neue Permission dokumentieren.
* **Datenmigration:** Keine (nur Lesezugriff). Neue Übersetzungs-Keys via Flyway.

## 7. Abgrenzung / Out of Scope
* **Kein Schreibzugriff** (kein INSERT/UPDATE/DELETE/DDL) über die Oberfläche.
* **Keine** `keycloak`-/System-Schemas.
* **Keine** JOINs, keine freie Spaltenauswahl, kein freies `SELECT`-Statement (nur `SELECT *` + WHERE auf einer Whitelist-Tabelle).
* **Keine serverseitig** gespeicherten Abfragen/Favoriten: Die Filter-Historie (FR-1b) ist rein browserlokal, nicht benannt/kuratierbar (keine Favoriten), nicht geräteübergreifend synchronisiert und nicht exportierbar. Ein manuelles Löschen einzelner Einträge über die Oberfläche ist **nicht** vorgesehen (Verdrängung nach 20 Einträgen bzw. Löschen der Browser-Daten).
* **Kein** allgemeiner SQL-Editor.
* **Kein** CSV-/Daten-Export (Entscheidung).
* **Keine** dedizierte read-only DB-Rolle; **keine** Audit-Tabelle (nur Application-Log).
* Mandantenspezifische Filterung ist bewusst **nicht** aktiv (Rohansicht).

## 8. Offene Fragen
Alle geklärt (in FR/NFR/Out-of-Scope eingearbeitet):
* [x] **CSV-/Daten-Export:** → **nein** (Out of Scope).
* [x] **Sortierung/`ORDER BY`:** → **serverseitig per Spalten-Klick** (Nachtrag): `sortSpalte` gegen Katalog-Whitelist geprüft, `sortRichtung` `ASC`/`DESC`, über den gesamten Datensatz (FR-1/FR-2/FR-4).
* [x] **Dedizierte read-only DB-Rolle:** → **nein**; read-only-Transaktion genügt (NFR-2).
* [x] **Audit-Persistenz:** → **Application-Log** genügt (keine Audit-Tabelle, NFR-2).
* [x] **Statement-Timeout / Pagegrösse:** → **5 s**, Default **50** / Max **500** (NFR-1).
* [x] **Permission-Name:** → **`datenbank:read`** (nur `zev_admin`, NFR-2).
* [x] **`bytea`/Binärspalten:** → **ausblenden** (FR-2, Edge Cases).
* [x] **Geltungsbereich der Filter-Historie:** → **pro Tabelle** getrennt (FR-1b), damit die Vorschläge zu den Spalten der gewählten Tabelle passen.
* [x] **Darstellung der Auswahlliste:** → natives **`<datalist>`** am bestehenden Filter-Feld (FR-4) – kein zusätzliches Bedienelement, kein neues CSS.
* [x] **Zeitpunkt der Aufnahme in die Historie:** → erst nach **erfolgreicher** Abfrage (ungültige Filter werden nicht gemerkt).
