# Systemmeldungen

## 1. Ziel & Kontext - Warum wird das Feature benötigt?
* **Was soll erreicht werden:** Eine Seite **Systemmeldungen**, auf der Benutzer sehen, wenn im Betrieb etwas schiefgelaufen ist. Zusätzlich zum bestehenden **Log-Eintrag** wird pro Fehler auch ein **persistenter Eintrag** angezeigt (Level, Kategorie, erstmals/zuletzt aufgetreten, Meldung, Zähler, Erledigt-Status). Einträge lassen sich filtern (nach Erledigt-Status, Kategorie und Level), über alle Spalten sortieren, als erledigt markieren und löschen. Wiederkehrende Fehler werden dedupliziert (Zähler), bei erfolgreichem Folgelauf automatisch erledigt (Auto-Resolve) und nach einer Frist bereinigt (Retention). **Start: Fehler aus dem Bilanzmodell**; weitere Fehlerquellen folgen später.
* **Warum machen wir das:** Fehler aus **Hintergrund-Läufen** (z.B. MQTT-Aggregation/Solarverteilung) sind heute nur im Server-Log sichtbar und für Benutzer praktisch unauffindbar. Eine sichtbare, verwaltbare Fehlerliste macht Betriebsprobleme (z.B. fehlende Bilanzdaten) transparent und nachverfolgbar.
* **Aktueller Stand:** Bilanzmodell-Fehler werden nur **geloggt**: der manuelle Solarverteil-Lauf (`/solar-calculation`) bricht mit `IllegalStateException` → HTTP 400 (Key `BILANZMODELL_KEINE_BILANZDATEN` + Intervall) ab; der MQTT-Auto-Lauf (`ZaehlerAggregationService`) loggt **ERROR** mit Intervall-Angabe. Es gibt **keine** persistente, in der UI sichtbare Fehlerliste.

## 2. Funktionale Anforderungen (FR) - Was soll das System tun?

### FR-1: Ablauf / Flow
1. Neue Seite aus dem Menü erreichbar: **Systemmeldungen** (Route `/systemmeldungen`).
2. Die Seite zeigt eine Tabelle der Systemmeldungen mit den Spalten:
   * **Level** – Schweregrad: `INFO` / `WARN` / `ERROR` (Bilanzmodell-Fehler = `ERROR`); als übersetztes Status-Badge dargestellt
   * **Kategorie** – Fehlerquelle/-gruppe (zu Beginn nur „Bilanzmodell"); übersetzt
   * **Erstmals aufgetreten** – Zeitpunkt des ersten Auftretens (bei Dedup unverändert)
   * **Zuletzt aufgetreten** – Zeitpunkt des letzten Auftretens (bei Dedup aktualisiert)
   * **Meldung** – Fehlertext, im Frontend **konkateniert** aus `translate(meldung_key)` + (falls vorhanden) `parameter` (z.B. betroffenes Intervall Tag/Zeit). Die Übersetzungs-Infrastruktur (`TranslatePipe`/`TranslationService.translate(key)`) kennt **keine** Platzhalter-Interpolation – daher Konkatenation statt Parameter-Einsetzung.
   * **Zähler** – Anzahl Vorkommen derselben (offenen) Meldung (Start = 1)
   * **Erledigt** – Checkbox zur Markierung, dass der Fehler bearbeitet / erledigt / nicht mehr relevant ist
3. **Filter** (kombinierbar):
   * über den **Erledigt-Status**: **Alle / Offene / Erledigte** (Default: **Offene**).
   * über die **Kategorie**: **Alle Kategorien** oder eine bestimmte Kategorie (zu Beginn nur „Bilanzmodell"; die Auswahl ergibt sich aus den vorhandenen Kategorien); Default: **Alle Kategorien**.
   * über das **Level**: **Alle Level** oder ein bestimmtes Level (INFO/WARN/ERROR); Default: **Alle Level**.
4. **Sortierung** über **alle Spalten** (auf-/absteigend). Da die Liste **serverseitig paginiert** wird (FR-1.11), erfolgt auch die **Sortierung serverseitig** (`sortSpalte` + `sortRichtung`, analog Datenbank-Ansicht) auf den **gespeicherten Werten**: Zeitstempel und `Zähler` natürlich, **`Level` nach Schweregrad** (ERROR > WARN > INFO), `Kategorie`/`Meldung` nach dem gespeicherten Key. *(Eine Sortierung nach dem übersetzten Anzeigetext ist mit serverseitiger Paginierung nicht möglich.)*
5. **Erledigt umschalten:** Über die Checkbox in der Zeile kann eine Meldung als erledigt / wieder offen markiert werden. Erfordert `systemmeldungen:manage`.
   * Beim **Wieder-Öffnen** werden `erledigt_am` und `erledigt_automatisch` zurückgesetzt.
   * Das Wieder-Öffnen wird **abgelehnt**, wenn bereits ein **offener** Eintrag mit demselben `meldung_key` im Mandanten existiert (Dedup-Invariante / UNIQUE-Teil-Index, s. FR-2) – mit verständlicher Fehlermeldung.
6. **Löschen:** Einzelne Einträge können gelöscht werden (Kebab-Menü, `systemmeldungen:manage`).
7. **Nur-Lese-Zugriff:** Benutzer mit `systemmeldungen:read` (aber ohne `:manage`) sehen die Liste, Filter und Sortierung; Checkbox und Löschen sind deaktiviert/ausgeblendet.
8. **Automatische Erzeugung (Backend):** Tritt ein unterstützter Fehler auf, wird **zusätzlich zum Log-Eintrag** eine Systemmeldung erzeugt (mit passendem **Level**; Bilanzmodell-Fehler = `ERROR`). Zu Beginn abgedeckt: **Bilanzmodell** – `BILANZMODELL_KEINE_BILANZDATEN` aus (a) dem manuellen Solarverteil-Lauf und (b) dem MQTT-Auto-Lauf.
   * **Deduplizierung:** Existiert bereits eine **offene** Meldung mit **gleichem Übersetzungs-Key (`meldung_key`)** im selben Mandanten – **unabhängig von den Parametern** –, wird **kein neuer** Eintrag erzeugt; stattdessen werden **`Zähler` um 1 erhöht**, **`zuletzt_aufgetreten` aktualisiert** und die **`parameter` auf das aktuelle Vorkommen** gesetzt. `erstmals_aufgetreten` bleibt unverändert.
   * Ist die passende Meldung bereits **erledigt** und tritt der Fehler erneut auf, wird ein **neuer offener** Eintrag angelegt (der erledigte bleibt unverändert).
   * Die Erzeugung erfolgt in einer **eigenen Transaktion** (`REQUIRES_NEW`): Der Bilanzmodell-Verteillauf rollt bei Fehler per `@Transactional` zurück – die Systemmeldung darf davon **nicht** mitgerollt werden.
   * **Org-explizit:** Im MQTT-Auto-Lauf ist die `org_id` bekannt und wird **explizit mitgegeben**. Dedup-Lookup und Erzeugung nutzen die **org-explizite** Variante der `SystemmeldungService`-Methoden (Parameter `orgId`, `HibernateFilterService.enableOrgFilter(orgId)`) und **nie** `getCurrentOrgId()`/den request-scoped Filter – sonst `NoOrganizationException` (analog `Bilanzmodell.md` FR-1.4). Im manuellen Lauf stammt die `orgId` aus dem Kontext.
9. **Auto-Resolve (Selbstheilung):** Läuft der auslösende Vorgang für denselben Mandanten später **erfolgreich** durch, werden die zugehörigen **offenen** Einträge automatisch auf **erledigt** gesetzt (`erledigt = true`, `erledigt_am = jetzt`, `erledigt_automatisch = true`). Konkret: nach einem erfolgreichen Solarverteil-Lauf (manuell **oder** MQTT-Auto-Lauf) im Bilanzmodus werden offene Einträge des Mandanten mit `meldung_key = BILANZMODELL_KEINE_BILANZDATEN` aufgelöst (**org-explizit**, `orgId` wird im Auto-Lauf mitgegeben). So verrottet die Liste nicht; ein wiederkehrender Fehler erzeugt gemäss FR-1.8 wieder einen neuen offenen Eintrag. Das Auto-Resolve läuft im Erfolgsfall (kein Rollback) und darf den auslösenden Vorgang nicht beeinträchtigen (Fehler beim Auflösen werden geloggt, nicht propagiert). **Hinweis (akzeptiert):** Wechselt ein Mandant von `BILANZ` zu `PRODUCER_MESSUNG`, gibt es keinen erfolgreichen Bilanzlauf mehr – etwaige alte offene `BILANZMODELL_...`-Einträge bleiben dann bis zur manuellen Erledigung bzw. Retention bestehen (bewusst so).
10. **Aufbewahrung / Retention:** Ein **geplanter Cleanup-Job** löscht **erledigte** Einträge, deren `erledigt_am` älter als eine konfigurierbare Frist ist (Default **90 Tage**, `application.yml`). **Offene** Einträge werden **nie** automatisch gelöscht. Der Job ist mandantenübergreifend, respektiert aber die Daten je `org_id`. **Wichtig:** Der Job ist **nicht** an das Spring-Profil `mqtt` gebunden (Systemmeldungen entstehen auch im manuellen Lauf ohne MQTT), sondern läuft in allen Umgebungen.
11. **Paginierung:** Die Liste wird **serverseitig** seitenweise geladen – **analog zur Datenbank-Ansicht** in den Einstellungen (`DatenbankController`/`datenbank-ansicht`): 0-basierte `page`, feste Seitengrösse (Default **50**), das Backend liefert ein **`hatMehr`**-Flag (kein Gesamt-Count). Navigation über **Vorherige/Nächste Seite** (Vorherige deaktiviert auf Seite 0, Nächste deaktiviert bei `hatMehr = false`). Filter (FR-1.3) und Sortierung (FR-1.4) werden serverseitig angewandt; ein **Wechsel von Filter oder Sortierung setzt auf Seite 0 zurück**.

### FR-2: Persistierung
* Neue Tabelle `zev.systemmeldung` (Flyway-Migration), mandantenfähig (`org_id`, Hibernate-`@Filter` `orgFilter` analog zu den übrigen Entities):

| Spalte                 | Typ            | Pflicht | Beschreibung |
|------------------------|----------------|---------|--------------|
| `id`                   | BIGINT PK      | ja      | Sequenz/Identity |
| `org_id`               | BIGINT         | ja      | Mandant (analog `messwerte`/`zaehler_rohdaten`); **serverseitig** gesetzt, nie vom Client |
| `level`                | VARCHAR(10)    | ja      | Schweregrad: `INFO` / `WARN` / `ERROR` |
| `kategorie`            | VARCHAR(50)    | ja      | Fehlerquelle/-gruppe als Übersetzungs-Key (z.B. `SYSTEMMELDUNG_KATEGORIE_BILANZMODELL`) |
| `meldung_key`          | VARCHAR(100)   | ja      | Übersetzungs-Key des Fehlers (z.B. `BILANZMODELL_KEINE_BILANZDATEN`) |
| `parameter`            | VARCHAR(500)   | nein    | Dynamische Teile der Meldung (z.B. Intervall Tag/Zeit) des letzten Vorkommens, fürs Rendering |
| `erstmals_aufgetreten` | TIMESTAMP      | ja      | Zeitpunkt des ersten Auftretens (bei Dedup unverändert) |
| `zuletzt_aufgetreten`  | TIMESTAMP      | ja      | Zeitpunkt des letzten Auftretens (bei Dedup aktualisiert) |
| `erledigt`             | BOOLEAN        | ja      | Default `FALSE` |
| `erledigt_am`          | TIMESTAMP      | nein    | Zeitpunkt der Erledigung (manuell oder Auto-Resolve); Basis für die Retention |
| `erledigt_automatisch` | BOOLEAN        | ja      | Default `FALSE`; `true` = per Auto-Resolve erledigt (Unterscheidung von manuell) |
| `zaehler`              | INTEGER        | ja      | Anzahl Vorkommen, Default `1` |

* **Dedup-Identität:** (`org_id`, `meldung_key`, `erledigt = false`) – **nur** über den Key, unabhängig von `parameter`. Ein **UNIQUE-Teil-Index** `(org_id, meldung_key) WHERE erledigt = false` erzwingt die Invariante „**max. ein offener Eintrag pro Key & Mandant**" und entschärft die Race bei gleichzeitigem manuellem + MQTT-Lauf (der Verlierer fällt auf „Zähler++/Zeitstempel-Update" zurück).
* Speichern des Meldungstexts als **Übersetzungs-Key + Parameter** (nicht als fertig gerenderter Text), damit die Anzeige mehrsprachig bleibt (i18n).

### FR-3: Layout
* **Vorlage für Layout:** Tarifverwaltung / Mieterverwaltung (Design-System-Tabelle, sortierbare Header, Kebab-Menü).
* **Menüposition:** im Hauptmenü **nach „Statistik"**.
* **Icon:** Feather-Icon **`file-text`** (Navigation + Seitentitel).
* **Filter** oberhalb der Tabelle (Erledigt-Status, Kategorie, Level) als Segmented-Control/Dropdown.
* **Paginierung** unterhalb der Tabelle (Vorherige/Nächste + Seitenanzeige) – analog `datenbank-ansicht`.
* **Erledigt** als `.zev-checkbox` in der Zeile; erledigte Zeilen dürfen optisch dezent abgesetzt werden (z.B. `.zev-text--muted`).
* **Level** als Status-Badge (`.zev-status`, z.B. Farbstufen für ERROR/WARN/INFO).
* Leere Liste: Hinweis „Keine Einträge vorhanden" (`.zev-empty-state`).

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)
* [ ] Die Seite **Systemmeldungen** ist aus dem Menü (**nach „Statistik", Icon `file-text`**) erreichbar und listet die Einträge mit Level, Kategorie, Erstmals aufgetreten, Zuletzt aufgetreten, Meldung, Zähler und Erledigt-Checkbox.
* [ ] Der Erledigt-Filter **Alle/Offene/Erledigte** wirkt korrekt; Default ist **Offene**.
* [ ] Der **Kategorie-Filter** (Alle Kategorien / bestimmte Kategorie) wirkt korrekt und ist mit dem Erledigt-Filter kombinierbar; Default ist **Alle Kategorien**.
* [ ] Der **Level-Filter** (Alle Level / INFO / WARN / ERROR) wirkt korrekt und ist mit den übrigen Filtern kombinierbar; Default ist **Alle Level**.
* [ ] Alle Spalten (Level, Kategorie, Erstmals aufgetreten, Zuletzt aufgetreten, Meldung, Zähler, Erledigt) sind **serverseitig** auf-/absteigend sortierbar; `Level` sortiert nach Schweregrad (ERROR > WARN > INFO).
* [ ] Die Liste ist **serverseitig paginiert** (Default 50/Seite) mit Vorherige/Nächste-Navigation über `hatMehr`; ein Filter- oder Sortierwechsel setzt auf Seite 0 zurück.
* [ ] Der Bilanzmodell-Fehler wird mit Level `ERROR` erfasst.
* [ ] Bei einem Bilanzmodell-Fehler (`BILANZMODELL_KEINE_BILANZDATEN`) entsteht **zusätzlich zum Log-Eintrag** eine Systemmeldung mit Kategorie „Bilanzmodell" – sowohl beim **manuellen** Solarverteil-Lauf als auch beim **MQTT-Auto-Lauf**.
* [ ] Tritt derselbe Fehler (gleicher `meldung_key`, unabhängig von den Parametern) erneut auf, während ein Eintrag **offen** ist, wird **kein** neuer Eintrag erzeugt; `Zähler` erhöht sich um 1, `zuletzt_aufgetreten` und `parameter` werden aktualisiert, `erstmals_aufgetreten` bleibt unverändert.
* [ ] Ist der passende Eintrag bereits **erledigt**, erzeugt ein erneutes Auftreten einen **neuen offenen** Eintrag.
* [ ] **Auto-Resolve:** Nach einem **erfolgreichen** Solarverteil-Lauf (manuell oder MQTT-Auto-Lauf) im Bilanzmodus werden offene Einträge des Mandanten mit `meldung_key = BILANZMODELL_KEINE_BILANZDATEN` automatisch auf erledigt gesetzt (`erledigt_am` gesetzt, `erledigt_automatisch = true`); ein Fehler beim Auflösen beeinträchtigt den Verteillauf nicht.
* [ ] **Retention:** Der Cleanup-Job löscht **erledigte** Einträge mit `erledigt_am` älter als die konfigurierte Frist (Default 90 Tage); **offene** Einträge werden nie automatisch gelöscht.
* [ ] Die Systemmeldung wird auch dann persistiert, wenn der auslösende Verteillauf zurückrollt (separate Transaktion) – der Verteillauf selbst wird durch das Schreiben der Meldung nicht beeinflusst.
* [ ] Mit `systemmeldungen:manage` kann der Erledigt-Status umgeschaltet und ein Eintrag gelöscht werden; nach jeder Mutation ist die Liste aktualisiert.
* [ ] Beim Wieder-Öffnen werden `erledigt_am`/`erledigt_automatisch` zurückgesetzt; existiert bereits ein offener Eintrag desselben `meldung_key`, wird das Wieder-Öffnen mit verständlicher Meldung abgelehnt.
* [ ] Mit nur `systemmeldungen:read` ist die Liste sichtbar, Umschalten/Löschen jedoch nicht möglich (UI deaktiviert **und** Backend lehnt ab).
* [ ] Löschen einer nicht (mehr) existierenden ID liefert **404** (kein 500).
* [ ] Alle UI-Texte kommen aus dem `TranslationService` (DE/EN); die Meldung wird durch **Konkatenation** von `translate(meldung_key)` + `parameter` gerendert.
* [ ] Multi-Tenancy: Ein Benutzer sieht nur Systemmeldungen des eigenen Mandanten; `org_id` wird serverseitig gesetzt.
* [ ] Leere Liste zeigt einen Hinweis statt einer leeren Tabelle.

## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Performance
* Liste **serverseitig paginiert** (Default-Seitengrösse 50, `hatMehr`-Flag) und **mandantengefiltert**; Sortierung serverseitig, Standard nach `zuletzt_aufgetreten` absteigend. Der Dedup-Lookup nutzt den **UNIQUE**-Teil-Index auf (`org_id`, `meldung_key`) `WHERE erledigt = false` (identisch zu FR-2).

### NFR-2: Sicherheit
* **Lesen (Liste, Filter, Sortierung):** Permission `systemmeldungen:read` – für **alle** Fachrollen (`zev_user`, `org_admin`, `zev_admin`).
* **Verwalten (erledigt umschalten, löschen):** Permission `systemmeldungen:manage` – nur **`zev_admin` / `org_admin`**.
* Backend: `@PreAuthorize("hasAuthority('systemmeldungen:read')")` bzw. `...('systemmeldungen:manage')`. Frontend: `AuthGuard` mit `data.permissions`.
* Neue Permissions in Keycloak (Composite Roles) und in `Specs/Berechtigungen.md` ergänzen.
* `org_id` stammt im Request aus dem Kontext, im **Hintergrund-Lauf explizit** mitgegeben (org-explizite Service-Methoden + `enableOrgFilter(orgId)`), **nie** vom Client.

### NFR-3: Kompatibilität
* Rein additiv: neue Tabelle + Migration, neue Permissions, neue Route/Seite. Keine Änderung an bestehenden Abläufen; der Bilanzmodell-Verteillauf verhält sich unverändert (die Meldungserzeugung ist ein zusätzlicher, entkoppelter Schritt).

## 5. Edge Cases & Fehlerbehandlung
| Szenario | Verhalten |
|----------|-----------|
| Keine Einträge | Hinweis „Keine Einträge vorhanden" (leere Liste ist kein Fehler) |
| Netzwerkfehler beim Laden/Mutieren | Fehlermeldung `.zev-message--error`; bestehende Fehlerbehandlung greift |
| Löschen einer nicht (mehr) existierenden ID | 404, keine 500 |
| Gleicher Fehler mehrfach in kurzer Folge (15-Min-Job) | Dedup nach `meldung_key`: `Zähler`++, `zuletzt_aufgetreten`/`parameter`-Update statt Flut neuer Zeilen |
| Fehler beim Schreiben der Systemmeldung | Wird geloggt, beeinflusst den auslösenden Lauf **nicht** (separate Transaktion, Fehler geschluckt) |
| Erledigt markieren durch Nur-Leser | Backend lehnt ab (`systemmeldungen:manage` erforderlich) |
| Erledigter Eintrag, Fehler tritt erneut auf | Neuer **offener** Eintrag; erledigter bleibt bestehen |
| Auslösender Lauf wird wieder erfolgreich | **Auto-Resolve:** offene Einträge desselben Keys/Mandanten → erledigt (`erledigt_automatisch = true`) |
| Fehler beim Auto-Resolve | Geloggt, **nicht** propagiert – der erfolgreiche Verteillauf bleibt unberührt |
| Wachstum der Tabelle | Retention-Job löscht erledigte Einträge älter als die Frist (Default 90 Tage); offene bleiben |

## 6. Abhängigkeiten & betroffene Funktionalität
* **Voraussetzungen:** Bilanzmodell (`Specs/Bilanzmodell.md`) als erste Fehlerquelle; Design System (Tabelle, Kebab-Menü, Checkbox, Filter, Empty-State); `TranslationService`; Multi-Tenancy (`OrganizationContextService`, `HibernateFilterService`).
* **Neuer Code (Backend):** `entity/Systemmeldung.java` (+ Enum `MeldungLevel`), `repository/SystemmeldungRepository.java` (paginierte/sortierte/gefilterte Abfrage via `Pageable`/`Slice`), `service/SystemmeldungService.java` (Dedup + `REQUIRES_NEW`-Erzeugung, Auto-Resolve, Retention-Löschung), `controller/SystemmeldungController.java` (`/api/systemmeldungen` – Liste **paginiert/sortiert/gefiltert serverseitig, analog `DatenbankController`** mit `page`/`size`/`sortSpalte`/`sortRichtung` + `hatMehr`), ein **Scheduled Cleanup-Job** (Retention, `@Scheduled`; **ohne** `@Profile("mqtt")`, damit er auch ohne MQTT läuft), Flyway-Migration (Tabelle + Übersetzungs-Keys).
* **Betroffener Code (Backend):** `service/MesswerteService.java` bzw. der aufrufende Pfad (manueller Lauf) und `service/ZaehlerAggregationService.java` (Auto-Lauf) – im **Fehler-/Catch-Pfad** eine Systemmeldung erzeugen (`REQUIRES_NEW`, mit expliziter `org_id` im Hintergrund-Lauf) und im **Erfolgsfall** die offenen Einträge auto-resolven (FR-1.9).
* **Konfiguration (`application.yml`):** Retention-Frist der Systemmeldungen (Default 90 Tage) + Cron des Cleanup-Jobs.
* **Neuer Code (Frontend):** `models/systemmeldung.model.ts`, `services/systemmeldung.service.ts`, `components/systemmeldungen/` (List-Component mit Filter/Sortierung/**Paginierung (Vorherige/Nächste)**/Kebab, analog `datenbank-ansicht`), Route in `app.routes.ts`, Menü-Eintrag in `app.component.html`.
* **Berechtigungen:** neue Permissions `systemmeldungen:read` / `systemmeldungen:manage` in Keycloak-Realm + `Specs/Berechtigungen.md`.
* **i18n:** neue Übersetzungs-Keys (Menü/Seitentitel, Spalten, Filter, Aktionen, Meldungs-Keys) via Flyway (`ON CONFLICT (key) DO NOTHING`, DE/EN). Der Key `BILANZMODELL_KEINE_BILANZDATEN` existiert bereits.
* **Datenmigration:** keine (nur neue Tabelle + Übersetzungs-Keys; nächste freie Flyway-Version zum Umsetzungszeitpunkt prüfen).

## 7. Abgrenzung / Out of Scope
* **Nur Bilanzmodell-Fehler** zu Beginn; weitere Fehlerquellen (andere Hintergrund-/Ingest-Fehler) folgen später.
* **Keine** aktive Benachrichtigung (E-Mail/Push/Badge-Zähler in der Navigation) – reine Seite.
* **Keine** mandantenübergreifende Gesamtansicht (jede Sicht ist mandantengefiltert).
* **Kein** Bearbeiten des Meldungstexts durch Benutzer (nur erledigt/löschen).

## 8. Offene Fragen

Geklärt (im Dokument eingearbeitet):
* [x] **Dedup-Identität:** **nur nach `meldung_key`** (unabhängig von den Parametern) – aggressiv zusammenfassen; `parameter` spiegelt das letzte Vorkommen.
* [x] **Erledigt + Wiederauftreten:** **neuer offener Eintrag** (der erledigte bleibt unverändert).
* [x] **Meldungstext:** **Übersetzungs-Key + Parameter** (i18n-konform, Frontend rendert).
* [x] **Zeitstempel:** **zwei** Zeitstempel – `erstmals_aufgetreten` und `zuletzt_aufgetreten`.
* [x] **Kategorie:** neues Attribut **`kategorie`** (zu Beginn „Bilanzmodell"), als übersetzbarer Key.
* [x] **Menüposition & Icon:** im Menü **nach „Statistik"**, Icon **`file-text`**.
* [x] **Kategorie-Filter:** ja – zusätzlich zum Erledigt-Filter kann nach Kategorie gefiltert werden (kombinierbar), Default „Alle Kategorien".
* [x] **Level:** neues Attribut `level` (INFO/WARN/ERROR), als Spalte + Filter; Bilanzmodell-Fehler = `ERROR`.
* [x] **Auto-Resolve:** offene Einträge werden bei erfolgreichem Folgelauf automatisch erledigt (Selbstheilung, `erledigt_automatisch`).
* [x] **Retention:** geplanter Cleanup-Job löscht erledigte Einträge älter als die konfigurierte Frist (Default 90 Tage); offene bleiben.
* [x] **Meldungs-Rendering:** **Konkatenation** `translate(meldung_key)` + `parameter` (keine Platzhalter-Interpolation in der Übersetzungs-Infrastruktur).
* [x] **Paginierung:** **serverseitig**, analog Datenbank-Ansicht (`page`/`size`=50/`hatMehr`, Vorherige/Nächste).
* [x] **Sortierung:** dadurch **serverseitig** auf gespeicherten Werten (Level nach Schweregrad, Meldung/Kategorie nach Key) – ersetzt die frühere Idee „nach übersetztem Anzeigetext" (mit serverseitiger Paginierung nicht umsetzbar).

Noch offen: –
