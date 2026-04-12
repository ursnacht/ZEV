# Übersetzungsverwaltung

## 1. Ziel & Kontext - Warum wird das Feature benötigt?
* **Was soll erreicht werden:** Administratoren können Übersetzungsschlüssel für die gesamte Anwendung verwalten (erstellen, bearbeiten, löschen). Die Übersetzungen stehen in Deutsch und Englisch zur Verfügung und werden von allen UI-Komponenten via `TranslationService` / `TranslatePipe` verwendet.
* **Warum machen wir das:** Alle UI-Texte sind in der Datenbank gespeichert und können ohne Deployment geändert werden. Dies ermöglicht zentrale Textpflege und einfache Mehrsprachigkeit.
* **Aktueller Stand:** Das Feature ist vollständig implementiert. Dieses Dokument beschreibt den Ist-Stand und dient als Referenz-Vorlage für weitere Anforderungsdokumente.

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
1. Admin klickt im Kebab-Menü einer Zeile auf «Löschen» (rot hervorgehoben).
2. System zeigt eine Bestätigungsabfrage mit Key-Name.
3. Bei Bestätigung: System sendet `DELETE /api/translations/{key}`.
4. Bei Erfolg: Eintrag wird aus der Tabelle entfernt; Übersetzungscache wird neu geladen.

### FR-6: Persistierung
* Tabelle: `app.translation`
* Spalten:

| Spalte    | Typ          | Pflicht | Constraints      |
|-----------|--------------|---------|------------------|
| `key`     | VARCHAR(255) | ja      | PRIMARY KEY      |
| `deutsch` | TEXT         | nein    | –                |
| `englisch`| TEXT         | nein    | –                |

* Flyway-Migration: `V2__Create_Translation_Table.sql`
* Neue System-Übersetzungen werden via Flyway-Migration mit `ON CONFLICT (key) DO NOTHING` eingefügt.
* **Kein `org_id`:** Übersetzungen sind mandantenübergreifend gültig (globale Systemtexte).

### FR-7: Layout
* Seitentitel mit Feather Icon `globe`: «Übersetzungen» / «Translations»
* Formular oberhalb der Tabelle mit `.app-form-container`
* Tabellenbreite anpassbar via `ColumnResizeDirective` (Drag-to-resize)
* Aktionen pro Zeile über `KebabMenuComponent`

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)

### Anzeige & Suche
* [ ] Die Seite `/translations` ist nur für Benutzer mit der Rolle `admin` zugänglich; `user` erhält 403.
* [ ] Die Tabelle zeigt alle gespeicherten Übersetzungen alphabetisch nach Key sortiert.
* [ ] Klick auf Spaltenheader sortiert die Tabelle auf- und absteigend; ein zweiter Klick kehrt die Richtung um.
* [ ] Die Suchfunktion filtert die Tabelle live nach Key, Deutsch und Englisch (case-insensitive).
* [ ] Die Anzeige `X / Y` zeigt die Anzahl der gefilterten und die Gesamtanzahl der Übersetzungen.
* [ ] Bei leerer Suche wird die vollständige Liste angezeigt.
* [ ] Bei leerem Übersetzungs-Datenbestand wird keine Tabelle angezeigt (Tabelle wird ausgeblendet).

### Erstellen
* [ ] Das Formular kann nur gespeichert werden, wenn das Feld «Key» nicht leer ist (Button deaktiviert).
* [ ] Nach erfolgreichem Erstellen erscheint der neue Eintrag sofort in der Tabelle.
* [ ] Nach dem Erstellen ist das Formular zurückgesetzt (alle Felder leer).
* [ ] Ein Key, der bereits existiert, führt zu einer Fehlermeldung (HTTP 409 / DB-Constraint-Fehler).

### Bearbeiten
* [ ] Klick auf «Bearbeiten» füllt das Formular mit den Werten der Zeile vor.
* [ ] Das Key-Feld ist im Bearbeitungsmodus nicht editierbar (`readonly`).
* [ ] Nach erfolgreichem Speichern wird der aktualisierte Text in der Tabelle angezeigt.
* [ ] Klick auf «Abbrechen» setzt das Formular zurück, ohne Änderungen zu speichern.

### Löschen
* [ ] Klick auf «Löschen» im Kebab-Menü öffnet eine Bestätigungsabfrage mit dem Key-Namen.
* [ ] Nach Bestätigung wird der Eintrag aus der Tabelle entfernt.
* [ ] Nach dem Löschen wird der Übersetzungscache (TranslationService) neu geladen.
* [ ] Bei Abbruch der Bestätigung bleibt der Eintrag unverändert bestehen.

### Backend API
* [ ] `GET /api/translations` ist für Rollen `user` und `admin` zugänglich und gibt `{de: {...}, en: {...}}` zurück.
* [ ] `GET /api/translations/list` ist nur für Rolle `admin` zugänglich und gibt eine Liste von Translation-Objekten zurück.
* [ ] `POST`, `PUT`, `DELETE` auf `/api/translations` sind nur für Rolle `admin` zugänglich.
* [ ] `PUT /api/translations/{key}` gibt HTTP 400 zurück, wenn der Key im Pfad nicht mit dem Key im Body übereinstimmt.
* [ ] `DELETE /api/translations/{key}` gibt HTTP 404 zurück, wenn der Key nicht existiert.

## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Performance
* `GET /api/translations` (für TranslatePipe) muss unter 200 ms antworten, da er beim App-Start aufgerufen wird.
* Die Suchfunktion filtert clientseitig; kein zusätzlicher API-Call beim Tippen.

### NFR-2: Sicherheit
* Nur Benutzer mit der Rolle `admin` können Übersetzungen erstellen, bearbeiten und löschen.
* Backend: `@PreAuthorize("hasRole('admin')")` auf Klassenebene; `GET /api/translations` explizit für `user` und `admin` freigegeben via `@PreAuthorize("hasAnyRole('user', 'admin')")`.
* Frontend: Route `/translations` ist mit `AuthGuard` und der Rolle `admin` geschützt.

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
  * Flyway-Migration `V2__Create_Translation_Table.sql` muss ausgeführt sein.
  * Keycloak muss mit den Rollen `user` und `admin` konfiguriert sein.
* **Betroffener Code:**
  * `TranslationService` (Frontend) — verwendet `GET /api/translations` beim App-Start; wird nach jedem Delete neu geladen.
  * `TranslatePipe` — alle Komponenten, die Übersetzungen anzeigen, sind indirekt abhängig.
  * Alle Flyway-Migrationen, die Übersetzungen via `INSERT ... ON CONFLICT DO NOTHING` einfügen (aktuell: `V4__Add_Initial_Translations.sql`).
* **Datenmigration:** Bestehende Übersetzungen in `app.translation` bleiben unverändert.

## 7. Abgrenzung / Out of Scope

* Mehr als zwei Sprachen (Deutsch, Englisch) werden nicht unterstützt.
* Kein Import/Export von Übersetzungen (z.B. CSV).
* Keine Versionierung oder History von Änderungen.
* Keine Übersetzungen auf Mandantenebene (kein `org_id`); alle Mandanten teilen denselben Übersetzungsstamm.
* Kein Inline-Editing direkt in der Tabelle; Bearbeitung erfolgt ausschliesslich über das Formular.
* Kein Bulk-Delete oder Bulk-Edit.

## 8. Offene Fragen

* Sollen Fehlermeldungen im Frontend (z.B. bei Netzwerkfehlern) per `.app-message--error` dem Benutzer angezeigt werden, anstatt nur in der Konsole geloggt zu werden? (Aktuell: nur `console.error`)
* Soll nach erfolgreichem Erstellen/Bearbeiten eine Erfolgsmeldung (`.app-message--success`) angezeigt werden? (Aktuell: kein visuelles Feedback)
* Soll die Löschbestätigung als natives `confirm()`-Dialog bleiben oder durch eine modalähnliche Komponente aus dem Design System ersetzt werden?
