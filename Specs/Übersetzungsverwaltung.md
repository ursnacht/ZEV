# Übersetzungsverwaltung

## 1. Ziel & Kontext - Warum wird das Feature benötigt?
* **Was soll erreicht werden:** Administratoren können Übersetzungsschlüssel für die gesamte Anwendung verwalten (erstellen, bearbeiten, löschen). Die Übersetzungen stehen in Deutsch und Englisch zur Verfügung und werden von allen UI-Komponenten via `TranslationService` / `TranslatePipe` verwendet.
* **Warum machen wir das:** Alle UI-Texte sind in der Datenbank gespeichert und können ohne Deployment geändert werden. Dies ermöglicht zentrale Textpflege und einfache Mehrsprachigkeit. Texte, die nicht mehr verwendet werden, lassen sich löschen (FR-5).
* **Ausgangslage:** n/a – die Spec dient zugleich als Referenz-Vorlage für weitere Anforderungsdokumente (Hintergrund: Anhang A.1).

## 2. Funktionale Anforderungen (FR) - Was soll das System tun?

### FR-1: Übersetzungen anzeigen
1. Admin navigiert zu `/translations`.
2. System lädt alle Übersetzungen via `GET /api/translations/list` (alphabetisch nach Key sortiert).
3. System zeigt die Übersetzungen in einer sortierbaren Tabelle mit den Spalten: **Key**, **Deutsch**, **Englisch**, **Aktionen**.
4. Benutzer kann auf Spaltenheader klicken, um auf- oder absteigend zu sortieren.
5. Aktuelle Sortierrichtung wird durch ▲/▼ angezeigt.

### FR-2: Suche / Filterung
1. Über einer Tabelle befindet sich ein Suchfeld.
2. Eingabe filtert die Tabelle live nach Treffer in Key, Deutsch oder Englisch (case-insensitive).
3. Anzahl der gefilterten / Gesamtanzahl wird neben dem Suchfeld angezeigt (z.B. `12 / 50`).
4. Suchfeld kann via X-Button geleert werden.

### FR-3: Übersetzung erstellen
1. Über der Tabelle befindet sich ein Formular mit den Feldern **Key**, **Deutsch**, **Englisch**.
2. Admin füllt das Formular aus und klickt auf «Hinzufügen».
3. System sendet `POST /api/translations` mit den eingegebenen Daten.
4. Bei Erfolg: Neuer Eintrag erscheint sofort in der Tabelle; Formular wird zurückgesetzt.

### FR-4: Übersetzung bearbeiten
1. Admin klickt im Kebab-Menü einer Zeile auf «Bearbeiten».
2. Formular wird mit den bestehenden Werten vorbelegt; Key-Feld wird als `readonly` angezeigt.
3. Admin ändert Deutsch und/oder Englisch und klickt auf «Speichern».
4. System sendet `PUT /api/translations/{key}`.
5. Bei Erfolg: Aktualisierter Eintrag wird in der Tabelle aktualisiert; Formular wird zurückgesetzt.
6. Admin kann die Bearbeitung via «Abbrechen» jederzeit abbrechen.

### FR-5: Übersetzung löschen
> Zweck: Texte, die nicht mehr verwendet werden, lassen sich entfernen (Hintergrund: Anhang A.2).

1. Admin sucht den zu löschenden Text (FR-2) und klickt im Kebab-Menü der Zeile auf «Löschen» (rot hervorgehoben).
2. System zeigt eine Bestätigungsabfrage mit Key-Name; der Text der Abfrage ist übersetzbar.
3. Bei Bestätigung: System sendet `DELETE /api/translations/{key}`.
4. Bei Erfolg: Eintrag wird aus der Tabelle entfernt; Übersetzungscache wird neu geladen.
5. Am Datenmodell ändert das Löschen nichts (FR-6).

### FR-6: Persistierung
* Tabelle: `zev.translation`
* Spalten:

| Spalte    | Typ          | Pflicht | Constraints      |
|-----------|--------------|---------|------------------|
| `key`     | VARCHAR(255) | ja      | PRIMARY KEY      |
| `deutsch` | TEXT         | nein    | –                |
| `englisch`| TEXT         | nein    | –                |

* Flyway-Migration: `V10__Create_Translation_Table.sql`
* Neue System-Übersetzungen werden via Flyway-Migration mit `ON CONFLICT (key) DO NOTHING` eingefügt.
* **Kein `org_id`:** Übersetzungen sind mandantenübergreifend gültig (globale Systemtexte).

### FR-7: Layout
* Seitentitel mit Feather Icon `globe`: «Übersetzungen» / «Translations»
* Formular oberhalb der Tabelle mit `.zev-form-container`
* Tabellenbreite anpassbar via `ColumnResizeDirective` (Drag-to-resize)
* Aktionen pro Zeile über `KebabMenuComponent`

### FR-8: Export / Import
> Ergänzt am 10.07.2026 (Hintergrund: Anhang A.3).

* **Format:** **CSV** (Komma als Trenner), UTF-8 mit BOM, Zeilenenden CRLF. **Kopfzeile** `key,deutsch,englisch`, danach eine Zeile je Übersetzung. Felder mit Komma, Anführungszeichen oder Zeilenumbruch werden nach RFC 4180 in `"…"` gesetzt, eingebettete Anführungszeichen verdoppelt (`""`) — dadurch ist der Round-Trip Export→Import verlustfrei (Übersetzungstexte enthalten Kommas, Apostrophe, Gedankenstriche, ggf. Zeilenumbrüche). Serialisierung/Parsing erfolgen clientseitig (`utils/csv-utils.ts`); die Wire-Übertragung zum Backend bleibt JSON (`Translation[]`).
* **Export:**
  1. Über der Tabelle (Toolbar-Zeile) befindet sich ein Button **«Exportieren»** (Icon `download`).
  2. Klick lädt **alle** aktuell geladenen Übersetzungen als Datei `uebersetzungen.csv` herunter (clientseitig via Blob; kein Backend-Call nötig).
  3. Der Button ist deaktiviert, wenn keine Übersetzungen vorhanden sind.
* **Import:**
  1. Button **«Importieren»** (Icon `upload`) öffnet einen Datei-Dialog (nur `.csv`/`text/csv`).
  2. **Rechts** neben dem Import-Button steht die Option **«Bestehende Keys überschreiben»** mit **zwei Radio-Buttons nebeneinander**: **Ja** / **Nein** (Default: **Ja**). Die Auswahl bestimmt, wie mit bereits vorhandenen Keys verfahren wird.
  3. Die gewählte Datei wird im Frontend gelesen und als CSV geparst; eine vorhandene Kopfzeile (`key,deutsch,englisch`) wird übersprungen. Ergibt das Parsen keine Datenzeile, erscheint eine Fehlermeldung (kein Backend-Call).
  4. Andernfalls sendet das Frontend `POST /api/translations/import?ueberschreiben=<true|false>` mit der Liste.
  5. Der Server legt **neue** Keys immer an; nicht in der Datei enthaltene Keys bleiben **unverändert** (kein Löschen). Für **bereits bestehende** Keys gilt:
     * **Überschreiben = Ja** (`ueberschreiben=true`): bestehende Keys werden **überschrieben** (Upsert).
     * **Überschreiben = Nein** (`ueberschreiben=false`): bestehende Keys werden **übersprungen** (nur neue Keys werden angelegt).
     Der Import ist **atomar** (bei einem ungültigen Eintrag wird nichts gespeichert).
  6. Bei Erfolg: Erfolgsmeldung mit Anzahl **tatsächlich gespeicherter** Einträge (bei «Nein» ohne die übersprungenen); Admin-Liste **und** Übersetzungscache (`TranslationService`) werden neu geladen.
  7. Nach dem Import wird das Datei-Input zurückgesetzt, sodass dieselbe Datei erneut gewählt werden kann.
* **Rückmeldung:** Erfolg (auto-dismiss nach 5 s) bzw. Fehler (bleibt bis manuell geschlossen) über den Design-System-`message`-Bereich (`zev-message`).
* **Backend-Endpunkt:** `POST /api/translations/import` (Body: `Translation[]`, Query `ueberschreiben` – Default `true`) → `{ "importiert": <n> }`; Autorisierung `translations:manage`. Validierung: jeder Eintrag braucht einen nicht-leeren Key (max. 200 Zeichen), sonst `400` mit Übersetzungs-Key.
* **Design System:** Die Radio-Auswahl nutzt die wiederverwendbaren Klassen **`zev-radio-group`** + **`zev-radio-label`** (im Design-System-`form`-Bereich; kein komponenteneigenes CSS).
* **CSV-Hilfsfunktionen:** `utils/csv-utils.ts` (`toCsv`, `parseCsv`) — RFC-4180-orientiert, mit Unit-Tests (`csv-utils.spec.ts`).

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)

### Anzeige & Suche
* [x] Die Seite `/translations` ist nur mit der Permission `translations:manage` (Fachrolle `zev_admin`) zugänglich; ohne sie (z.B. `zev_user`, `org_admin`) weist der `AuthGuard` ab. (E2E: composite-roles.spec.ts › Composite-Roles: permission-basierter Zugriff › org_admin wird vom Übersetzungs-Editor weggeleitet (fehlt translations:manage))
* [ ] Die Tabelle zeigt alle gespeicherten Übersetzungen alphabetisch nach Key sortiert.
* [ ] Klick auf Spaltenheader sortiert die Tabelle auf- und absteigend; ein zweiter Klick kehrt die Richtung um.
* [ ] Die Suchfunktion filtert die Tabelle live nach Key, Deutsch und Englisch (case-insensitive).
* [ ] Die Anzeige `X / Y` zeigt die Anzahl der gefilterten und die Gesamtanzahl der Übersetzungen.
* [ ] Bei leerer Suche wird die vollständige Liste angezeigt.
* [ ] Bei leerem Übersetzungs-Datenbestand wird keine Tabelle angezeigt (Tabelle wird ausgeblendet).

### Erstellen
* [ ] Das Formular kann nur gespeichert werden, wenn das Feld «Key» nicht leer ist (Button deaktiviert).
* [x] Nach erfolgreichem Erstellen erscheint der neue Eintrag sofort in der Tabelle. (E2E: translation-editor.spec.ts › Translation Editor - Delete Functionality via Kebab Menu › should delete translation when confirmed via kebab menu; E2E: translation-editor.spec.ts › Translation Editor - Save Functionality via Kebab Menu › should save translation changes via kebab menu)
* [ ] Nach dem Erstellen ist das Formular zurückgesetzt (alle Felder leer).
* [ ] Ein Key, der bereits existiert, führt zu einer Fehlermeldung (HTTP 409 / DB-Constraint-Fehler).

### Bearbeiten
* [ ] Klick auf «Bearbeiten» füllt das Formular mit den Werten der Zeile vor.
* [ ] Das Key-Feld ist im Bearbeitungsmodus nicht editierbar (`readonly`).
* [x] Nach erfolgreichem Speichern wird der aktualisierte Text in der Tabelle angezeigt. (E2E: translation-editor.spec.ts › Translation Editor - Save Functionality via Kebab Menu › should save translation changes via kebab menu)
* [ ] Klick auf «Abbrechen» setzt das Formular zurück, ohne Änderungen zu speichern.

### Löschen
* [ ] Klick auf «Löschen» im Kebab-Menü öffnet eine Bestätigungsabfrage mit dem Key-Namen.
* [x] Nach Bestätigung wird der Eintrag aus der Tabelle entfernt. (E2E: translation-editor.spec.ts › Translation Editor - Delete Functionality via Kebab Menu › should delete translation when confirmed via kebab menu)
* [x] Nach dem Löschen wird der Übersetzungscache (TranslationService) neu geladen. (Test: translation.service.spec.ts › TranslationService › deleteTranslation › should make DELETE request and then reload translations)
* [x] Bei Abbruch der Bestätigung bleibt der Eintrag unverändert bestehen. (E2E: translation-editor.spec.ts › Translation Editor - Delete Functionality via Kebab Menu › should not delete translation when cancelled via kebab menu)

### Export / Import
* [ ] Klick auf «Exportieren» lädt eine Datei `uebersetzungen.csv` (Komma-getrennt, Kopfzeile `key,deutsch,englisch`) mit allen Übersetzungen herunter.
* [ ] Der Export-Button ist deaktiviert, wenn keine Übersetzungen vorhanden sind.
* [ ] Klick auf «Importieren» öffnet einen Datei-Dialog (nur `.csv`).
* [x] Der Import einer zuvor exportierten CSV-Datei stellt exakt dieselben Übersetzungen wieder her (verlustfreier Round-Trip, auch bei Werten mit Kommas, Anführungszeichen und Zeilenumbrüchen). (Test: csv-utils.spec.ts › csv-utils › round-trip › should preserve tricky translation values; Test: csv-utils.spec.ts › csv-utils › parseCsv › should strip a leading BOM)
* [ ] Rechts neben dem Import-Button stehen zwei Radio-Buttons «Ja»/«Nein» für «Bestehende Keys überschreiben» (Default: Ja).
* [ ] Bei **«Ja»** überschreibt der Import bestehende Keys mit den Werten aus der Datei; neue Keys werden angelegt.
* [ ] Bei **«Nein»** bleiben bestehende Keys unverändert; nur neue Keys werden angelegt; die Erfolgsmeldung zählt nur die tatsächlich angelegten Einträge.
* [ ] In beiden Fällen bleiben nicht in der Datei enthaltene Keys unverändert (kein Löschen).
* [ ] Nach erfolgreichem Import erscheint eine Erfolgsmeldung mit der Anzahl importierter Einträge; Tabelle und Übersetzungscache werden aktualisiert.
* [ ] Eine Datei ohne verwertbare Datenzeile (leer / nur Kopfzeile) führt zu einer Fehlermeldung, ohne einen Backend-Call auszulösen.
* [ ] Ein Eintrag ohne Key (bzw. Key > 200 Zeichen) führt zu `400`; es wird **nichts** gespeichert (atomar).
* [ ] `POST /api/translations/import` ist nur für `translations:manage` zugänglich (sonst `403`).
* [ ] Dieselbe Datei kann nach einem Import direkt erneut gewählt werden (Datei-Input wird zurückgesetzt).

### Backend API
* [ ] `GET /api/translations` ist mit `translations:read` (alle Fachrollen) zugänglich und gibt `{de: {...}, en: {...}}` zurück.
* [ ] `GET /api/translations/list` ist nur mit `translations:manage` zugänglich und gibt eine Liste von Translation-Objekten zurück.
* [ ] `POST`, `PUT`, `DELETE` auf `/api/translations` sind nur mit `translations:manage` zugänglich (sonst `403`).
* [ ] `PUT /api/translations/{key}` gibt HTTP 400 zurück, wenn der Key im Pfad nicht mit dem Key im Body übereinstimmt.
* [ ] `DELETE /api/translations/{key}` gibt HTTP 404 zurück, wenn der Key nicht existiert.

## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Performance
* `GET /api/translations` (für TranslatePipe) muss unter 200 ms antworten, da er beim App-Start aufgerufen wird.
* Die Suchfunktion filtert clientseitig; kein zusätzlicher API-Call beim Tippen.

### NFR-2: Sicherheit
* Nur mit der Permission `translations:manage` lassen sich Übersetzungen auflisten, erstellen, bearbeiten, löschen und importieren; sie hat nur die Fachrolle `zev_admin` (`Specs/Berechtigungen.md`).
* Backend: `@PreAuthorize("hasAuthority('translations:manage')")` je Methode in `TranslationController` (`GET /list`, `POST`, `PUT`, `DELETE`, `POST /import`); `GET /api/translations` (für die `TranslatePipe`) mit `@PreAuthorize("hasAuthority('translations:read')")`, die alle Fachrollen haben.
* Frontend: Route `/translations` ist mit `AuthGuard` und `data.permissions: ['translations:manage']` geschützt.

### NFR-3: Kompatibilität
* Neue Übersetzungen werden immer via Flyway-Migration mit `ON CONFLICT (key) DO NOTHING` eingefügt, um bestehende Anpassungen nicht zu überschreiben.
* Das Löschen eines Keys, der von der Anwendung aktiv verwendet wird, führt zu einer Fallback-Anzeige des Keys im UI (kein Fehler, kein Absturz).

## 5. Edge Cases & Fehlerbehandlung

* **Leere Tabelle:** Wenn keine Übersetzungen vorhanden sind, wird die Tabelle ausgeblendet (`@if (filteredTranslations.length > 0)`).
* **Kein Suchtreffer:** Suche ergibt keine Treffer → Tabelle leer, Zähler zeigt `0 / N`.
* **Netzwerkfehler beim Laden:** Fehler wird in der Konsole geloggt (`console.error`); Tabelle bleibt leer; `loading`-Flag wird zurückgesetzt.
* **Netzwerkfehler beim Speichern/Löschen:** Fehler wird in der Konsole geloggt; Tabelle bleibt unverändert.
* **Doppelter Key beim Erstellen:** DB-Constraint (PRIMARY KEY) verhindert doppelte Keys; Backend gibt Fehler zurück.
* **Key-Mismatch bei PUT:** Wenn Key im URL-Pfad ≠ Key im Request-Body, gibt Backend HTTP 400 zurück.
* **Löschen eines nicht existierenden Keys:** Backend gibt HTTP 404 zurück; kein Absturz.
* **Löschen eines aktiv verwendeten Übersetzungskeys:** Frontend zeigt den Key als Fallback an (kein gesonderter Schutz nötig).

## 6. Abhängigkeiten & betroffene Funktionalität

* **Voraussetzungen:**
  * Flyway-Migration `V10__Create_Translation_Table.sql` muss ausgeführt sein.
  * Keycloak muss die Permissions `translations:read` und `translations:manage` über die Fachrollen vergeben (`Specs/Berechtigungen.md`).
* **Betroffener Code:**
  * `TranslationService` (Frontend) — verwendet `GET /api/translations` beim App-Start; wird nach jedem Delete neu geladen.
  * `TranslatePipe` — alle Komponenten, die Übersetzungen anzeigen, sind indirekt abhängig.
  * Alle Flyway-Migrationen, die Übersetzungen via `INSERT ... ON CONFLICT DO NOTHING` einfügen (ab `V11__Add_Algorithm_Translations.sql`; je Feature eine `V…__Add_…_Translations.sql`).
* **Datenmigration:** Bestehende Übersetzungen in `zev.translation` bleiben unverändert.

## 7. Abgrenzung / Out of Scope

* Mehr als zwei Sprachen (Deutsch, Englisch) werden nicht unterstützt.
* Import/Export erfolgt ausschliesslich als **CSV** (Komma-getrennt, kein JSON/XLSX) — siehe FR-8.
* Der Import **löscht keine** vorhandenen Keys (nur Anlegen bzw. optionales Überschreiben, siehe FR-8); ein Voll-Sync mit Löschen ist nicht vorgesehen.
* Keine Versionierung oder History von Änderungen.
* Keine Übersetzungen auf Mandantenebene (kein `org_id`); alle Mandanten teilen denselben Übersetzungsstamm.
* Kein Inline-Editing direkt in der Tabelle; Bearbeitung erfolgt ausschliesslich über das Formular.
* Kein Bulk-Delete oder Bulk-Edit.

## 8. Offene Fragen

* Sollen Fehlermeldungen im Frontend (z.B. bei Netzwerkfehlern) per `.zev-message--error` dem Benutzer angezeigt werden, anstatt nur in der Konsole geloggt zu werden? (Aktuell: nur `console.error`)
* Soll nach erfolgreichem Erstellen/Bearbeiten eine Erfolgsmeldung (`.zev-message--success`) angezeigt werden? (Aktuell: kein visuelles Feedback)
* Soll die Löschbestätigung als natives `confirm()`-Dialog bleiben oder durch eine modalähnliche Komponente aus dem Design System ersetzt werden?

## Anhang A – Entscheidungen und Verlauf

### A.1 Rolle der Spec (Abschnitt 1)
Abschnitt 1 enthielt den Satz: „**Aktueller Stand:** Das Feature ist vollständig implementiert. Dieses Dokument beschreibt den Ist-Stand und dient als Referenz-Vorlage für weitere Anforderungsdokumente.“ Bei der Konsolidierung am 19.10.2026 durch „Ausgangslage“ nach `Specs/SPEC.md` ersetzt; den Umsetzungsstand zeigen die Checkboxen. Die Spec ist weiterhin Referenz-Vorlage (`.claude/commands/10_vibe.md`).

### A.2 Zusammengeführt: `Specs/DeleteTranslations.md` (FR-5, 05.12.2025)
Das Löschen war zuerst als eigene Spec „Übersetzungstexte löschen“ beschrieben (Commits `cb0750f` „Sprecs / DeleteTranslation“ und `b8faba5` „DeleteTranslation inkl. Tests“) und wurde am 19.10.2026 in FR-5 übernommen; die Datei ist gelöscht. Ihr Inhalt:
* **Ziel:** „Übersetzungstexte sollen gelöscht werden können, so dass ich die Texte löschen kann, die nicht mehr verwendet werden.“ User Story: „Als Admin möchte ich die Übersetzungstexte löschen, damit ich die Texte löschen kann, die nicht mehr verwendet werden.“
* **Ablauf:** 1. Admin klickt auf „Übersetzungstexte“; 2. Admin sucht den zu löschenden Text; 3. Admin klickt auf „Löschen“; 4. Es erscheint eine Bestätigungsmeldung; 5. Admin bestätigt, der Eintrag wird gelöscht; 6. Text im Bestätigungsdialog übersetzbar machen.
* **API:** `DELETE /api/translations/{id}` – der Code und FR-5 verwenden `{key}`; die Übersetzung hat keine separate ID, der Key ist der Primärschlüssel (FR-6).
* **Datenmodell:** „Am Datenmodell muss nichts geändert werden.“
* **NFR:** keine.
* **Testing:** „Erstelle einen E2E und einen Integrationstest für den neuen Use case.“

### A.3 Export / Import ergänzt (FR-8, 10.07.2026)
FR-8 kam als Nachtrag „Export / Import (Nachtrag)“ dazu (Commit `f2c2efe` „Übersetzungsverwaltung: CSV-Import/-Export mit Überschreiben-Option“); Abgrenzung (Abschnitt 7) und Akzeptanzkriterien wurden damals mit ergänzt.

### A.4 An den Code angeglichen (FR-6, FR-7, NFR-2, Abschnitte 3, 6, 8; 19.10.2026)
Bei der Konsolidierung fielen Aussagen auf, die der Code überholt hatte; auf Wunsch des Users korrigiert. Vorher galt:
* **Berechtigung:** Rollen statt Permissions – „Die Seite `/translations` ist nur für Benutzer mit der Rolle `admin` zugänglich; `user` erhält 403.“; „`GET /api/translations` ist für Rollen `user` und `admin` zugänglich …“; „`GET /api/translations/list` ist nur für Rolle `admin` zugänglich …“; „`POST`, `PUT`, `DELETE` … sind nur für Rolle `admin` zugänglich.“ (AK); NFR-2: „Nur Benutzer mit der Rolle `admin` …“, „`@PreAuthorize("hasRole('admin')")` auf Klassenebene; `GET /api/translations` … via `@PreAuthorize("hasAnyRole('user', 'admin')")`“, „Route `/translations` ist mit `AuthGuard` und der Rolle `admin` geschützt.“; Abschnitt 6: „Keycloak muss mit den Rollen `user` und `admin` konfiguriert sein.“ Seit der Umstellung auf Permissions (`Specs/Berechtigungen.md`, `Specs/Composite-Roles.md`) gelten `translations:read`/`translations:manage`.
* **Tabelle und Migrationen:** „`app.translation`“ (FR-6, Abschnitt 6) – das Schema heisst `zev`; „`V2__Create_Translation_Table.sql`“ – die Tabelle entsteht in `V10__Create_Translation_Table.sql` (`V2` legt `einheit` an); „aktuell: `V4__Add_Initial_Translations.sql`“ – eine solche Migration gibt es nicht (`V4` ordnet Spalten von `einheit` um), die ersten Übersetzungen kommen mit `V11`.
* **Design-System-Klassen:** „`.app-form-container`“ (FR-7), „`.app-message--error`“ / „`.app-message--success`“ (Abschnitt 8) – das Präfix ist `zev-`.
Die Akzeptanzkriterien zur Berechtigung behalten ihren Status `[ ]`; sie sind nur umformuliert.
