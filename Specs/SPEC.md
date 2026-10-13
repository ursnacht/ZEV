# Titel des Features / der Änderung

> **Hinweise zum Ausfüllen**
> * Die **8 Hauptabschnitte** sind fix — nicht entfernen oder umbenennen (`/0_anforderungen-check` prüft auf Vollständigkeit).
> * Jeden Slot ausfüllen. Trifft etwas nicht zu: **`n/a` + kurze Begründung** (z.B. „Kein `org_id`: globale Systemdaten").
> * Ziel ist deterministische Codegenerierung — je konkreter Spalten, Endpunkte und Validierungen, desto eindeutiger der generierte Code.
> * `<Platzhalter>` in spitzen Klammern und Beispielzeilen in Tabellen ersetzen.

## 1. Ziel & Kontext - Warum wird das Feature benötigt?
* **Was soll erreicht werden:** (Kurze Zusammenfassung)
* **Warum machen wir das:** (Business Value / Hintergrund)
* **Aktueller Stand:** Wie funktioniert es jetzt? (Falls relevant)

## 2. Funktionale Anforderungen (FR) - Was soll das System tun?

> Erst die Abläufe (`FR-1 … FR-N`), danach Datenmodell, API, Validierungen, i18n und Layout.

### FR-1: <Ablauf / Flow>
1. User klickt auf …
2. System validiert …
3. System speichert …

### FR-2: <weiterer Flow>
1. …

### Datenmodell & Persistierung
* **Tabelle:** `zev.<tabelle>`
* **Spalten:**

| Spalte    | Typ          | Pflicht | Constraints                  |
|-----------|--------------|---------|------------------------------|
| `id`      | BIGINT       | ja      | PRIMARY KEY (generated)      |
| `org_id`  | BIGINT       | ja      | siehe Multi-Tenancy          |
| `<feld>`  | VARCHAR(255) | ja/nein | UNIQUE / FK → `zev.<tab>(id)`|

* **Primary Key:** `<spalte>`
* **Foreign Keys:** `<spalte>` → `zev.<tabelle>(<spalte>)`, `ON DELETE <CASCADE / RESTRICT / SET NULL>`
* **Unique-Constraints:** `<spalte(n)>` (für Upsert / Duplikatsprüfung) — oder `n/a`
* **Multi-Tenancy (`org_id`):** **ja** / **nein** — **Begründung:** <z.B. „mandantenspezifische Daten" oder „Kein org_id: globale Systemdaten">
* **Flyway-Migration:** `V<XX>__<Beschreibung>.sql`

### API-Endpunkte

> Autorisiert wird über **Permissions**, nicht Rollen: `@PreAuthorize("hasAuthority('<permission>')")`.
> Welche Fachrolle (`zev_user`, `org_admin`, `zev_admin`) welche Permission hat, steht in
> `Specs/Berechtigungen.md`. Neue Permission → dort ergänzen und als Abhängigkeit vermerken.

| Methode | Pfad                  | Permission           | Request-Body | Response       | Status-Codes      |
|---------|-----------------------|----------------------|--------------|----------------|-------------------|
| GET     | `/api/<entität>`      | `<entität>:read`     | –            | `List<…DTO>`   | 200 / 403         |
| GET     | `/api/<entität>/{id}` | `<entität>:read`     | –            | `…DTO`         | 200 / 403 / 404   |
| POST    | `/api/<entität>`      | `<entität>:manage`   | `…DTO`       | `…DTO`         | 201 / 400 / 403   |
| PUT     | `/api/<entität>/{id}` | `<entität>:manage`   | `…DTO`       | `…DTO`         | 200 / 400 / 403 / 404 |
| DELETE  | `/api/<entität>/{id}` | `<entität>:manage`   | –            | –              | 204 / 403 / 404   |

### Validierungen

| Feld     | Regel        | FE / BE  | Fehlermeldung (Translation-Key)        |
|----------|--------------|----------|----------------------------------------|
| `<feld>` | nicht leer   | FE + BE  | `<FEATURE>_<FELD>_ERFORDERLICH`        |
| `<feld>` | eindeutig    | BE       | `<FEATURE>_<FELD>_EXISTIERT`           |

### Übersetzungen (i18n)

> Alle UI-Texte via `TranslationService` / `TranslatePipe` — keine hartcodierten Texte. Neue Keys hier auflisten → Flyway-Migration mit `ON CONFLICT (key) DO NOTHING`.
> Keys in GROSSBUCHSTABEN mit Unterstrich (wie `TARIF_PREIS_PRO_JAHR`); bestehende Keys wiederverwenden (z.B. `SPEICHERN`, `ABBRECHEN`). Deutsche Texte mit Umlauten, `ss` statt `ß`.

| Key                        | Deutsch        | Englisch      |
|----------------------------|----------------|---------------|
| `<FEATURE>_TITEL`          | …              | …             |
| `<FEATURE>_GESPEICHERT`    | … gespeichert  | … saved       |

### Layout & Navigation
* **Route:** `/<route>` → `<Komponente>` (`data: { permissions: ['<permission>'] }`)
* **Menüeintrag:** Label `<…>` in `frontend-service/src/app/components/navigation/navigation.component.html` (Feather-Icon: `<icon>`)
* **Seitentitel:** `<…>` mit Feather-Icon `<icon>`
* **Design-System-Klassen:** nur bestehende Klassen verwenden (siehe `generell.md`, Abschnitt Design System), z.B. `.zev-container`, `.zev-table`, `.zev-form-container`

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)

> Konkret und testbar formulieren (prüfbares Verhalten, nicht „soll funktionieren"). Nach Themen gruppieren. Permissions/403 und HTTP-Status-Codes explizit abdecken. Edge Cases aus Abschnitt 5 als AK abbilden.

### <Themengruppe, z.B. Anzeige>
* [ ] Muss X können
* [ ] Darf Y nicht zulassen

### <Themengruppe, z.B. Erstellen / Bearbeiten / Löschen>
* [ ] Muss Fehlermeldung Z anzeigen, wenn …

### Sicherheit & API
* [ ] Route `/<route>` und Endpunkte sind nur mit Permission `<permission>` zugänglich; ohne sie gibt das Backend 403 (z.B. Rolle `zev_user` bei `…:manage`).
* [ ] `<Endpoint>` gibt bei `<Fall>` HTTP `<code>` zurück.

## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Performance
* z.B. „muss unter 200 ms antworten"

### NFR-2: Sicherheit
* **Backend:** `@PreAuthorize("hasAuthority('<permission>')")` auf Klassenebene — Permission pro Endpoint gemäss Tabelle in Abschnitt 2 (API-Endpunkte).
* **Frontend:** Route mit `AuthGuard` und `data.permissions` geschützt.
* **Fachrollen:** welche Rollen die Permission erhalten (`zev_user` / `org_admin` / `zev_admin`), siehe `Specs/Berechtigungen.md`.

### NFR-3: Kompatibilität
* z.B. Daten rückwärtskompatibel / Migration `ON CONFLICT DO NOTHING` / Löschverhalten bei referenzierten Daten

## 5. Edge Cases & Fehlerbehandlung

> Mindestens abdecken (siehe `generell.md`, Abschnitt 5):

* [ ] Leere Eingaben / leere Liste → Hinweis „Keine Daten vorhanden"
* [ ] Netzwerkfehler → klare Fehlermeldung (`.zev-message--error`)
* [ ] Ungültige / nicht existierende ID → 404 (nicht 500)
* [ ] Doppelte / ungültige Werte → Validierungsfehler
* [ ] Löschen referenzierter Daten → <Verhalten: Kaskade / blockieren>
* [ ] Gleichzeitige Bearbeitung → <Verhalten>

## 6. Abhängigkeiten & betroffene Funktionalität
* **Voraussetzungen:** Welche Features / Tabellen / Permissions müssen bereits existieren?
* **Betroffener Code:** Welcher bestehende Code muss angepasst werden?
* **Datenmigration:** Müssen bestehende Daten migriert oder transformiert werden?

## 7. Abgrenzung / Out of Scope
* Was wird bewusst **nicht** umgesetzt?

## 8. Offene Fragen

> Format für `/1_umsetzungsplan`: beantwortete Fragen mit ` --> <Antwort>` markieren — diese werden als Annahme in den Plan übernommen. Unbeantwortete Fragen, die Scope/Architektur betreffen, blockieren die Umsetzung.

* <Frage 1>
* <Frage 2> --> <Antwort, falls bereits geklärt>
