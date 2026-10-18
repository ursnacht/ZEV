# Einheitenverwaltung

## 1. Ziel & Kontext - Warum wird das Feature benötigt?
* **Was soll erreicht werden:** Die Messstellen eines Mandanten – Wohnungen und andere Verbraucher, Produzenten, Bilanzmesspunkte, Ladestationen und den Batteriespeicher – als **Einheiten** pflegen: anlegen, bearbeiten, löschen und als sortierbare Liste anzeigen. Jede Einheit hat einen Namen, einen Typ, optional einen Messpunkt und (bei Verbrauchern) das Kennzeichen, ob sie als Wohnung in der Nebenkostenabrechnung zählt.
* **Warum machen wir das:** *(Vermutung, aus dem Code nicht ableitbar)* Die Einheit ist das Stammdatum, an dem Messwerte, Mieterzuordnungen, Tarifpositionen, Rechnungen und die Solarverteilung hängen. Ohne gepflegte Einheiten lassen sich weder Messwerte importieren noch Rechnungen erzeugen.
* **Ausgangslage:** n/a – die Spec beschreibt eine bestehende Stammdatenverwaltung. Die typspezifischen Regeln stammen aus späteren Features (Bilanzmesspunkt, Ladestationen, Batteriespeicher, Nebenkostenabrechnung) und sind dort beschrieben (Abschnitt 7).

## 2. Funktionale Anforderungen (FR) - Was soll das System tun?

### FR-1: Einheiten anzeigen
1. User öffnet `/einheiten` (Menü „Admin“ › „Einheiten“).
2. System lädt die Einheiten des eigenen Mandanten über `GET /api/einheit`, serverseitig nach Name aufsteigend sortiert (`EinheitRepository.findAllByOrderByNameAsc`).
3. System zeigt eine Tabelle mit den Spalten **ID**, **Name**, **Typ** (übersetzt über `EinheitTypPipe`), **Messpunkt** (leer → `-`) und je Zeile ein Kebab-Menü.
4. Gibt es keine Einheiten, zeigt das System statt der Tabelle den Text `KEINE_EINHEITEN_VORHANDEN`.
5. Schlägt das Laden fehl, zeigt das System eine Fehlermeldung (`.zev-message--error`, bleibt bis zum Wegklicken).

### FR-2: Liste sortieren
1. Die Spalten ID, Name, Typ und Messpunkt sind sortierbar (`.zev-table__header--sortable`). Vorbelegt ist Name aufsteigend.
2. Klick auf die aktive Spalte kehrt die Richtung um; Klick auf eine andere Spalte sortiert nach ihr aufsteigend.
3. Sortiert wird clientseitig: Texte ohne Gross-/Kleinschreibung, die ID numerisch, der Typ nach dem **Enum-Wert** (`CONSUMER`, `LADESTATION`, …), nicht nach dem angezeigten Text. Leere Werte (`null`/`undefined`) stehen in beiden Richtungen am Ende.
4. Die aktive Spalte trägt einen Pfeil `▲`/`▼` (`.zev-table__sort-indicator`).
5. Die Spaltenbreiten sind veränderbar (`appColumnResize`, siehe `Specs/SpaltenbreiteVeränderbar.md`).

### FR-3: Einheit anlegen
1. User klickt „Neue Einheit erstellen“ (`NEUE_EINHEIT_ERSTELLEN`); die Liste wird durch das Formular ersetzt.
2. Das Formular ist vorbelegt mit leerem Namen, Typ **Konsument** (`CONSUMER`) und gesetztem Kennzeichen „Zählt als Wohnung“.
3. User erfasst Name, Typ (Auswahl aller sechs Typen: Produzent, Konsument, Bezug VNB, Rücklieferung VNB, Ladestation, Speicher) und optional den Messpunkt.
4. Der Hinweis unter dem Messpunkt lautet je nach Typ `MESSPUNKT_HINT` bzw. bei Ladestation `MESSPUNKT_HINT_LADESTATION` (RFID, siehe `Specs/Ladestationen.md`, FR-3).
5. Nur beim Typ **Konsument** erscheint die Checkbox „Zählt als Wohnung in der Nebenkostenabrechnung“ (`EINHEIT_NEBENKOSTEN_RELEVANT`) mit Hinweistext; bei allen anderen Typen ist sie ausgeblendet. Bedeutung des Kennzeichens: `Specs/Nebenkosten/Abrechnung.md`, FR-2.
6. Der Button „Erstellen“ ist gesperrt, solange der Name leer oder nur Leerzeichen ist; darunter steht dann `NAME_IST_ERFORDERLICH`.
7. User klickt „Erstellen“ → `POST /api/einheit`.
8. Das Backend validiert (Abschnitt Validierungen), prüft die Einmaligkeit von `BEZUG`/`RUECKLIEFERUNG`/`SPEICHER`, normalisiert dann einen leeren bzw. nur aus Leerzeichen bestehenden Messpunkt auf `null`, prüft die RFID-Eindeutigkeit bei `LADESTATION` (FR-6), verwirft eine mitgeschickte `id`, setzt `org_id` aus dem Mandantenkontext und speichert – es entsteht immer eine neue Einheit (Hintergrund: Anhang A.4).
9. Bei Erfolg: Formular schliesst, Erfolgsmeldung `EINHEIT_ERSTELLT` (verschwindet nach 5 s), Liste wird neu geladen.
10. Bei Fehler bleibt das Formular offen; die Meldung ist der übersetzte Fehler-Key aus `error.error` des Backends, sonst der Text „Fehler beim Erstellen: “ + technische Meldung.

### FR-4: Einheit bearbeiten
1. User wählt im Kebab-Menü einer Zeile „Bearbeiten“; das Formular öffnet sich mit einer Kopie der Zeile (Titel `EINHEIT_BEARBEITEN`, Button `AKTUALISIEREN`).
2. Fehlt das Kennzeichen „Zählt als Wohnung“ in den geladenen Daten (`null`/`undefined`), ist es im Formular gesetzt.
3. User klickt „Aktualisieren“ → `PUT /api/einheit/{id}`.
4. Das Backend lädt die Einheit unter dem Mandantenfilter (`findFirstById`); fehlt sie → `404`. Sonst gelten dieselbe Normalisierung und dieselben Regeln wie beim Anlegen (die eigene Einheit ausgenommen). Die ID kommt aus dem Pfad, `org_id` bleibt die der bestehenden Einheit.
5. Bei Erfolg: Formular schliesst, Erfolgsmeldung `EINHEIT_AKTUALISIERT`, Liste wird neu geladen.
6. Bei Fehler bleibt das Formular offen; Meldung wie FR-3.10, Fallback „Fehler beim Aktualisieren: “ + technische Meldung.
7. „Abbrechen“ schliesst das Formular ohne Speichern und zeigt wieder die Liste.

### FR-5: Einheit löschen
1. User wählt im Kebab-Menü „Löschen“; das System fragt per Browser-Dialog `CONFIRM_DELETE_EINHEIT`.
2. Bricht der User ab, passiert nichts.
3. Bestätigt er → `DELETE /api/einheit/{id}`.
4. Existiert die Einheit (im eigenen Mandanten) nicht → `404`.
5. Sind der Einheit Mieter zugeordnet, weist das Backend das Löschen ab (`400`, Meldung mit der Anzahl zugeordneter Mieter) – Regel aus `Specs/Ladestationen.md`, FR-2.
6. Sonst wird die Einheit gelöscht (`204`); ihre Tarifpositionen und Gerätezustände verschwinden per `ON DELETE CASCADE` mit ihr.
7. Bei Erfolg: Erfolgsmeldung `EINHEIT_GELOESCHT`, Liste wird neu geladen.
8. Bei Fehler: Meldung ist `error.error.error`, sonst `error.error` (Text-Rumpf), sonst `FEHLER_LOESCHEN_EINHEIT`; die Liste wird nicht neu geladen.

### FR-6: Typspezifische Regeln beim Speichern
Die Regeln gehören den Features, die die Typen eingeführt haben; hier nur der Verweis:
* `BEZUG` und `RUECKLIEFERUNG`: je Mandant höchstens eine Einheit pro Typ → `400` mit `EINHEIT_BILANZ_TYP_EXISTIERT` (`Specs/Bilanzmesspunkt.md`, FR-1.3).
* `SPEICHER`: je Mandant höchstens eine Einheit → `400` mit `EINHEIT_SPEICHER_EXISTIERT` (`Specs/Batteriespeicher.md`, FR-1.3).
* `LADESTATION`: Messpunkt (RFID) je Mandant eindeutig unter den Ladestationen → `400` mit `EINHEIT_MESSPUNKT_EXISTIERT` (`Specs/Ladestationen.md`, FR-2).
* `PRODUCER` und `CONSUMER`: keine Mengen- oder Messpunktbeschränkung.

### FR-7: Sprung zu den Tarifpositionen
1. User wählt im Kebab-Menü einer Zeile „Tarifpositionen“.
2. System navigiert auf `/tarifpositionen?einheitId=<id>` (Seite und Vorauswahl: `Specs/Ladestationen.md`, FR-3). Der Eintrag erscheint bei jeder Einheit, unabhängig vom Typ.

### Datenmodell & Persistierung
* **Tabelle:** `zev.einheit` (Ist-Schema per `information_schema`/`pg_constraint`; Entity `backend-service/src/main/java/ch/nacht/entity/Einheit.java`)
* **Spalten:**

| Spalte                 | Typ         | Pflicht | Constraints / Default                                                        |
|------------------------|-------------|---------|------------------------------------------------------------------------------|
| `id`                   | BIGINT      | ja      | PRIMARY KEY, Sequenz `zev.einheit_seq` (`allocationSize = 1`)                |
| `org_id`               | BIGINT      | ja      | FK → `zev.organisation(id)`, Index `idx_einheit_org_id`; serverseitig gesetzt |
| `name`                 | VARCHAR(30) | ja      | –                                                                            |
| `typ`                  | VARCHAR(20) | ja      | Enum `EinheitTyp` als String; **kein** CHECK-Constraint                      |
| `messpunkt`            | VARCHAR(50) | nein    | partieller Unique-Index (siehe unten)                                       |
| `nebenkosten_relevant` | BOOLEAN     | ja      | DEFAULT `true`                                                               |

* **Typen (`EinheitTyp`):** `PRODUCER`, `CONSUMER`, `BEZUG`, `RUECKLIEFERUNG`, `LADESTATION`, `SPEICHER` (`entity/EinheitTyp.java`; Frontend `models/einheit.model.ts`).
* **Primary Key:** `id`
* **Foreign Keys:** `org_id` → `zev.organisation(id)` (ohne `ON DELETE`-Klausel).
* **Fremdschlüssel auf `zev.einheit(id)`** (bestimmen das Löschverhalten):

| Tabelle            | Constraint                         | ON DELETE            |
|--------------------|------------------------------------|----------------------|
| `mieter_einheit`   | `fk_mieter_einheit_einheit`        | RESTRICT             |
| `messwerte`        | `fk_messwerte_einheit`             | – (NO ACTION)        |
| `zaehler_rohdaten` | `zaehler_rohdaten_einheit_id_fkey` | – (NO ACTION)        |
| `tarifposition`    | `tarifposition_einheit_id_fkey`    | CASCADE              |
| `geraetezustand`   | `geraetezustand_einheit_id_fkey`   | CASCADE              |

* **Unique-Constraints:** `uk_einheit_messpunkt_ladestation` – UNIQUE `(org_id, messpunkt) WHERE typ = 'LADESTATION' AND messpunkt IS NOT NULL`. Kein weiterer Unique-Constraint (Name nicht eindeutig; die Einmaligkeit von `BEZUG`/`RUECKLIEFERUNG`/`SPEICHER` prüft nur der Service).
* **Multi-Tenancy (`org_id`):** **ja** – **Begründung:** mandantenspezifische Stammdaten. `@Filter(name = "orgFilter", condition = "org_id = :orgId")` an der Entity; jede Service-Methode ruft `hibernateFilterService.enableOrgFilter()`; Lesen und Ändern einer einzelnen Einheit über `findFirstById` statt `findById` (`EinheitRepository.java`, Javadoc zu `findFirstById`), Löschen über die gefilterte Abfrage `existsById`.
* **Flyway-Migration:** u.a. `V2__create_table_einheit.sql`, `V3__add_typ_to_einheit.sql`, `V6__convert_einheit_typ_to_varchar.sql`, `V19__Add_Einheit_Mieter_Messpunkt.sql`, `V26__Add_Org_Id_To_Tables.sql`, `V37__Remove_Mietername_From_Einheit.sql`, `V46__Migrate_Org_Id_To_Organisation_FK.sql`, `V104__Add_Ladestation_Messpunkt_Unique.sql`, `V123__Add_Einheit_Nebenkosten_Relevant.sql`.

### API-Endpunkte

> Klassenebene `@PreAuthorize("hasAuthority('einheit:write')")`; nur `GET /api/einheit` überschreibt mit `einheit:read` (`controller/EinheitController.java`). Request- und Response-Typ ist die Entity `Einheit` (kein DTO).

| Methode | Pfad                   | Permission      | Request-Body                          | Response                  | Status-Codes                |
|---------|------------------------|-----------------|---------------------------------------|---------------------------|-----------------------------|
| GET     | `/api/einheit`         | `einheit:read`  | –                                     | `List<Einheit>` (Name ↑)  | 200 / 401 / 403             |
| GET     | `/api/einheit/{id}`    | `einheit:write` | –                                     | `Einheit`                 | 200 / 401 / 403 / 404       |
| POST    | `/api/einheit`         | `einheit:write` | `Einheit` (`@Valid`)                  | `Einheit`                 | 201 / 400 / 401 / 403       |
| PUT     | `/api/einheit/{id}`    | `einheit:write` | `Einheit` (`@Valid`)                  | `Einheit`                 | 200 / 400 / 401 / 403 / 404 |
| DELETE  | `/api/einheit/{id}`    | `einheit:write` | –                                     | –                         | 204 / 400 / 401 / 403 / 404 |
| POST    | `/api/einheit/match`   | `einheit:write` | `EinheitMatchRequestDTO` (`filename`) | `EinheitMatchResponseDTO` | 200 / 400 / 401 / 403       |

* **Fehlerrümpfe** (`exception/GlobalExceptionHandler.java`): Bean-Validation → `400` mit `{ "<feld>": "<Meldung>" }`; fachliche Regeln (`IllegalStateException`) → `400` mit `{ "error": "<Key oder Text>" }`; Datenbank-Constraint (`DataIntegrityViolationException`) → `409` mit Klartext-Rumpf.
* **`id` und `orgId` im Request-Body** übernimmt der Server nicht (`@JsonProperty(access = READ_ONLY)` in `entity/Einheit.java`); `PUT` nimmt die ID aus dem Pfad. In der Antwort stehen beide Felder weiterhin (Anhang A.4).
* **`POST /match`** gehört fachlich zum Messwerte-Upload (KI-Zuordnung über den Dateinamen, `Specs/Messwerte-mit-KI.md`) und wird hier nur aufgeführt, weil er im `EinheitController` liegt und `einheit:write` verlangt.

### Validierungen

| Feld        | Regel                                                         | FE / BE | Fehlermeldung (Translation-Key / Text)                       |
|-------------|---------------------------------------------------------------|---------|--------------------------------------------------------------|
| `name`      | nicht leer / nicht nur Leerzeichen                            | FE + BE | FE: `NAME_IST_ERFORDERLICH`; BE: `400` „Name is required“ oder „Name must be between 2 and 30 characters“ (bei leerem Namen greifen `@NotBlank` und `@Size` zugleich; der Handler behält nur eine Meldung je Feld) |
| `name`      | max. 30 Zeichen                                               | FE (`maxlength="30"`) + BE | BE: `400` „Name must be between 2 and 30 characters“ |
| `name`      | min. 2 Zeichen                                                | BE      | `400` „Name must be between 2 and 30 characters“             |
| `typ`       | Pflicht                                                       | FE (`required`, vorbelegt) + BE (`@NotNull`) | BE: `400` „Typ is required“            |
| `messpunkt` | max. 50 Zeichen                                               | FE (`maxlength="50"`) + BE (`@Size`) | BE: `400` „Messpunkt must not exceed 50 characters“ |
| `messpunkt` | leer / nur Leerzeichen → `null`                               | BE      | – (Normalisierung, `EinheitService.pruefeLadestationMesspunkt`) |
| `typ`       | `BEZUG`/`RUECKLIEFERUNG` je Mandant einmal                     | BE      | `EINHEIT_BILANZ_TYP_EXISTIERT` (FR-6)                        |
| `typ`       | `SPEICHER` je Mandant einmal                                  | BE      | `EINHEIT_SPEICHER_EXISTIERT` (FR-6)                          |
| `messpunkt` | bei `LADESTATION` je Mandant eindeutig                         | BE (+ DB-Index) | `EINHEIT_MESSPUNKT_EXISTIERT` (FR-6)                 |
| –           | Löschen nur ohne zugeordnete Mieter                            | BE      | Text „Einheit kann nicht gelöscht werden: <n> Mieter zugeordnet“ |

Quellen: `entity/Einheit.java` (Bean-Validation), `service/EinheitService.java`, `components/einheit-form/einheit-form.component.html`.

### Übersetzungen (i18n)

Texte aus `zev.translation` (gelesen per `zev-db`):

| Key                                 | Deutsch                                                    | Englisch                                       |
|-------------------------------------|------------------------------------------------------------|------------------------------------------------|
| `EINHEITEN_VERWALTUNG`              | Einheiten                                                  | Units                                          |
| `NEUE_EINHEIT_ERSTELLEN`            | Neue Einheit erstellen                                     | Create new unit                                |
| `EINHEIT_BEARBEITEN`                | Einheit bearbeiten                                         | Edit unit                                      |
| `KEINE_EINHEITEN_VORHANDEN`         | Keine Einheiten vorhanden                                  | No units available                             |
| `ID` / `NAME` / `TYP` / `MESSPUNKT` | ID / Name / Typ / Messpunkt                                | ID / Name / Type / Metering Point              |
| `EINHEIT_NAME`                      | Einheit Name                                               | Unit name                                      |
| `NAME_IST_ERFORDERLICH`             | Name ist erforderlich                                      | Name is required                               |
| `MESSPUNKT_PLACEHOLDER`             | z.B. CH1012501234500000000011000006457                     | e.g. CH1012501234500000000011000006457         |
| `MESSPUNKT_HINT`                    | Messpunkt-ID für Rechnung (optional)                       | Metering point ID for invoice (optional)       |
| `MESSPUNKT_HINT_LADESTATION`        | RFID, mit der der Ladevorgang gestartet wird. Je Mandant eindeutig; beim Mieterwechsel eine neue Einheit mit neuer RFID erfassen. | RFID used to start the charging session. … |
| `EINHEIT_NEBENKOSTEN_RELEVANT`      | Zählt als Wohnung in der Nebenkostenabrechnung             | Counts as an apartment in the ancillary cost billing |
| `EINHEIT_NEBENKOSTEN_RELEVANT_HINT` | Bestimmt den Nenner der Umlage. Abwählen bei Verbrauchern, die keine Wohnung sind - etwa Allgemeinstrom oder Eigenverbrauch der PV-Anlage. | Determines the denominator of the allocation. … |
| `PRODUZENT` / `KONSUMENT`           | Produzent / Konsument                                      | Producer / Consumer                            |
| `TYP_BEZUG` / `TYP_RUECKLIEFERUNG`  | Bezug VNB / Rücklieferung VNB                              | Grid supply / Feed-in                          |
| `TYP_LADESTATION` / `TYP_SPEICHER`  | Ladestation / Speicher                                     | Charging station / Storage                     |
| `BEARBEITEN` / `TARIFPOSITIONEN` / `LOESCHEN` | Bearbeiten / Tarifpositionen / Löschen           | Edit / Tariff positions / Delete               |
| `ERSTELLEN` / `AKTUALISIEREN` / `ABBRECHEN` | Erstellen / Aktualisieren / Abbrechen              | Create / Update / Cancel                       |
| `CONFIRM_DELETE_EINHEIT`            | Möchten Sie diese Einheit wirklich löschen?                | Do you really want to delete this unit?        |
| `EINHEIT_ERSTELLT`                  | Einheit erfolgreich erstellt                               | Unit successfully created                      |
| `EINHEIT_AKTUALISIERT`              | Einheit erfolgreich aktualisiert                           | Unit successfully updated                      |
| `EINHEIT_GELOESCHT`                 | Einheit erfolgreich gelöscht                               | Unit successfully deleted                      |
| `EINHEIT_BILANZ_TYP_EXISTIERT`      | Es existiert bereits eine Einheit dieses Bilanz-Typs.      | A unit of this balance type already exists.    |
| `EINHEIT_SPEICHER_EXISTIERT`        | Es ist bereits eine Einheit vom Typ Speicher erfasst. Pro Mandant ist nur eine zulässig. | A storage unit already exists. Only one is allowed per tenant. |
| `EINHEIT_MESSPUNKT_EXISTIERT`       | Diese RFID ist bereits einer anderen Ladestation zugeordnet | This RFID already belongs to another charging station |

* `FEHLER_LOESCHEN_EINHEIT` wird im Code als Fallback verwendet, steht aber **nicht** in `zev.translation` (Abschnitt 8).

### Layout & Navigation
* **Route:** `/einheiten` → `EinheitListComponent` (`data: { permissions: ['einheit:read'] }`, `frontend-service/src/app/app.routes.ts`)
* **Menüeintrag:** Untermenü „Admin“, erster Eintrag, Label `EINHEITEN_VERWALTUNG`, Feather-Icon `grid` (`navigation.component.ts`, `adminEintraege`; Permissions aus der Route gelesen) – siehe `Specs/Hamburgermenü.md`, FR-2.
* **Seitentitel:** `EINHEITEN_VERWALTUNG` mit Feather-Icon `home`.
* **Aufbau:** Liste und Formular auf derselben Seite, nie gleichzeitig sichtbar (`showForm`). Formular als eigene Komponente `EinheitFormComponent` (`@Input einheit`, `@Output save`/`cancel`).
* **Kebab-Menü je Zeile:** Bearbeiten (`edit-2`), Tarifpositionen (`zap`), Löschen (`trash-2`, rot markiert).
* **Design-System-Klassen:** `.zev-container`, `.zev-message` (`--success`/`--error`/`--clickable`, `__dismiss`), `.zev-button--primary`/`--secondary`, `.zev-table`, `.zev-table__header--sortable`, `.zev-table__sort-indicator`, `.zev-form-group`, `.zev-input`, `.zev-select`, `.zev-form-error`, `.zev-form-hint`, `.zev-checkbox-item`, `.zev-checkbox`, `.zev-form-actions--equal`. Der Formularrahmen nutzt die lokale Klasse `.form-container` (`einheit-form.component.css`).

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)

### Anzeige
* [x] `/einheiten` zeigt Titel, Button „Neue Einheit erstellen“ und eine Tabelle. (E2E: einheiten-verwaltung.spec.ts › should display the einheiten management page with table and create button)
* [ ] Die Tabelle hat die Spalten ID, Name, Typ, Messpunkt und je Zeile ein Kebab-Menü.
* [x] Beim Öffnen werden die Einheiten geladen und als Zeilen angezeigt. (Test: einheit-list.component.spec.ts › initialization › should load einheiten on init; Test: einheit-list.component.spec.ts › loadEinheiten › should update einheiten array on success; E2E: einheiten-verwaltung.spec.ts › should display einheiten data in table rows)
* [x] `GET /api/einheit` liefert nur die Einheiten des eigenen Mandanten, nach Name aufsteigend. (Test: EinheitRepositoryIT#shouldNotSeeEinheitOfOtherOrgWhenOrgFilterEnabled; Test: EinheitRepositoryIT#shouldFindAllByOrderByNameAsc; Test: EinheitServiceTest#getAllEinheiten_ReturnsSortedList)
* [x] Die Typ-Spalte zeigt für alle sechs Typen den eigenen übersetzten Text (kein Typ fällt auf „Produzent“ zurück). (Test: einheit-typ.pipe.spec.ts › transform › should give every known type its own label; Test: einheit-typ.pipe.spec.ts › transform › should not label a LADESTATION as PRODUZENT)
* [ ] Ein leerer Messpunkt erscheint in der Tabelle als `-`.
* [ ] Ohne Einheiten erscheint `KEINE_EINHEITEN_VORHANDEN` statt der Tabelle.
* [x] Schlägt das Laden fehl, erscheint eine Fehlermeldung vom Typ `error`. (Test: einheit-list.component.spec.ts › loadEinheiten › should show error message on failure)

### Sortierung
* [x] Vorbelegt ist Name aufsteigend. (Test: einheit-list.component.spec.ts › initialization › should have default sort by name ascending)
* [x] Klick auf die aktive Spalte kehrt die Richtung um, Klick auf eine andere Spalte sortiert aufsteigend nach ihr. (Test: einheit-list.component.spec.ts › onSort › should toggle sort direction when clicking same column; Test: einheit-list.component.spec.ts › onSort › should set new column and reset direction to asc)
* [x] Die aktive Sortierspalte zeigt einen Pfeil. (E2E: einheiten-verwaltung.spec.ts › should sort einheiten by clicking on column headers)
* [x] Sortieren nach Name, Typ und Messpunkt ordnet die Zeilen entsprechend. (Test: einheit-list.component.spec.ts › onSort › should sort einheiten by name ascending; Test: einheit-list.component.spec.ts › onSort › should sort einheiten by typ descending; Test: einheit-list.component.spec.ts › onSort › should sort by messpunkt so RFIDs are groupable)
* [x] Leere Werte stehen beim aufsteigenden Sortieren am Ende. (Test: einheit-list.component.spec.ts › onSort › should handle null values in sort)
* [ ] Leere Werte stehen auch beim absteigenden Sortieren am Ende.

### Erstellen
* [x] „Neue Einheit erstellen“ blendet die Liste aus und zeigt ein leeres Formular mit Name, Typ, Messpunkt, Erstellen und Abbrechen. (Test: einheit-list.component.spec.ts › onCreateNew › should set selectedEinheit to null and show form; E2E: einheiten-verwaltung.spec.ts › should show einheit form when clicking create button)
* [x] Das leere Formular ist mit Typ Konsument vorbelegt. (Test: einheit-form.component.spec.ts › initialization › should initialize with default form data when no einheit input)
* [x] Das Typ-Dropdown bietet jeden der sechs Typen genau einmal an. (Test: einheit-form.component.spec.ts › initialization › should offer every einheit type exactly once)
* [x] Bei leerem oder nur aus Leerzeichen bestehendem Namen wird nichts gesendet. (Test: einheit-form.component.spec.ts › events › should not emit save event when name is empty; Test: einheit-form.component.spec.ts › events › should not emit save event when name is only whitespace)
* [ ] Bei leerem oder nur aus Leerzeichen bestehendem Namen ist „Erstellen“ gesperrt und `NAME_IST_ERFORDERLICH` steht unter dem Feld.
* [x] Eine Einheit ohne Messpunkt lässt sich speichern. (Test: einheit-form.component.spec.ts › optional fields › should allow submission without messpunkt)
* [ ] Die Checkbox „Zählt als Wohnung“ erscheint nur beim Typ Konsument und ist beim Anlegen gesetzt.
* [ ] Der Messpunkt-Hinweis wechselt beim Typ Ladestation auf `MESSPUNKT_HINT_LADESTATION`.
* [x] Nach erfolgreichem Anlegen schliesst das Formular, `EINHEIT_ERSTELLT` erscheint und die Liste wird neu geladen. (Test: einheit-list.component.spec.ts › onFormSubmit - create › should hide form after successful create; Test: einheit-list.component.spec.ts › onFormSubmit - create › should show success message after create; Test: einheit-list.component.spec.ts › onFormSubmit - create › should reload einheiten after create)
* [x] Neue Einheiten der Typen Konsument und Produzent erscheinen mit übersetztem Typ in der Liste. (E2E: einheiten-verwaltung.spec.ts › should create a new CONSUMER einheit successfully; E2E: einheiten-verwaltung.spec.ts › should create a new PRODUCER einheit successfully)
* [x] Das Backend setzt `org_id` aus dem Mandantenkontext. (Test: EinheitServiceTest#createEinheit_SetsOrgIdFromContext)
* [x] Ein leerer oder nur aus Leerzeichen bestehender Messpunkt wird als `null` gespeichert. (Test: EinheitServiceTest#createEinheit_LadestationMitLeeremMesspunkt_NormalisiertAufNull; Test: EinheitServiceTest#updateEinheit_LadestationMitLeeremMesspunkt_NormalisiertAufNull)
* [x] „Abbrechen“ schliesst das Formular ohne Speichern. (Test: einheit-list.component.spec.ts › onFormCancel › should hide form and clear selected einheit; E2E: einheiten-verwaltung.spec.ts › should cancel form and return to list)

### Bearbeiten
* [x] „Bearbeiten“ öffnet das Formular mit einer Kopie der Zeile, vorbelegt mit deren Werten. (Test: einheit-list.component.spec.ts › onEdit › should create a copy of the einheit; Test: einheit-form.component.spec.ts › initialization › should populate form with einheit data when input is provided)
* [ ] Fehlt `nebenkostenRelevant` in den geladenen Daten, ist die Checkbox im Formular gesetzt.
* [x] Geänderte Werte werden gespeichert und in der Liste angezeigt; danach erscheint `EINHEIT_AKTUALISIERT`. (Test: einheit-list.component.spec.ts › onFormSubmit - update › should show success message after update; E2E: einheiten-verwaltung.spec.ts › should edit an existing einheit)
* [x] Das Backend übernimmt die ID aus dem Pfad und behält die `org_id` der bestehenden Einheit. (Test: EinheitServiceTest#updateEinheit_SetsIdFromPath; Test: EinheitServiceTest#updateEinheit_PreservesOrgId)

### Löschen
* [x] „Löschen“ fragt per Dialog nach; bei Abbruch bleibt die Einheit erhalten. (Test: einheit-list.component.spec.ts › onDelete › should not delete when user cancels; E2E: einheiten-verwaltung.spec.ts › should show confirmation dialog when deleting einheit)
* [x] Nach Bestätigung wird die Einheit gelöscht, `EINHEIT_GELOESCHT` erscheint und die Liste wird neu geladen. (Test: einheit-list.component.spec.ts › onDelete › should reload einheiten after successful delete; Test: einheit-list.component.spec.ts › onDelete › should show success message after delete; E2E: einheiten-verwaltung.spec.ts › should delete einheit when confirmed)
* [x] Ist der Einheit ein Mieter zugeordnet, wird das Löschen abgewiesen; die Meldung des Servers mit der Anzahl erscheint, die Liste wird nicht neu geladen. (Test: EinheitServiceTest#deleteEinheit_MitZugeordnetenMietern_ThrowsException; Test: einheit-list.component.spec.ts › onDelete › should show the server message when the unit is still assigned to tenants; Test: einheit-list.component.spec.ts › onDelete › should not reload the einheiten when the delete is rejected)

### Typspezifische Regeln (Anzeige der Fehler)
* [x] Lehnt das Backend das Speichern mit einem Fehler-Key in `error.error` ab, zeigt die Liste den übersetzten Text als Fehlermeldung. (Test: einheit-list.component.spec.ts › onFormSubmit - create › should translate the server key when the RFID is already used; Test: einheit-list.component.spec.ts › onFormSubmit - update › should translate the server key when the RFID is already used)
* [ ] Nach einem abgewiesenen Speichern bleibt das Formular offen.
* [x] Fehler-Keys der Service-Regeln kommen als `400` mit `{ "error": "<Key>" }`. (Test: EinheitControllerTest#createEinheit_ZweiterBilanzTyp_Returns400MitFehlerKey; Test: EinheitControllerTest#createEinheit_LadestationMitVergebenerRfid_Returns400MitFehlerKey)
* [x] `PRODUCER` unterliegt keiner Einmaligkeitsprüfung. (Test: EinheitServiceTest#createEinheit_Producer_PruefteKeineEinmaligkeit)

### Tarifpositionen-Sprung
* [x] Der Kebab-Eintrag „Tarifpositionen“ navigiert auf `/tarifpositionen?einheitId=<id>`. (Test: einheit-list.component.spec.ts › onTarifpositionen › should navigate to the tarifpositionen page with the einheitId)
* [x] Das Kebab-Menü enthält Bearbeiten, Tarifpositionen und Löschen in dieser Reihenfolge. (Test: einheit-list.component.spec.ts › initialization › should have menu items for edit, tarifpositionen and delete)

### Sicherheit & API
* [x] `POST /api/einheit` mit `einheit:write` erreicht den Controller (`201`). (Test: ControllerAuthorizationTest#createEinheit_withEinheitWrite_reachesController)
* [x] `POST /api/einheit` nur mit `einheit:read` (Rolle `zev_user`) → `403`, der Service wird nicht aufgerufen. (Test: ControllerAuthorizationTest#createEinheit_withReadPermissionOnly_forbidden)
* [x] `PUT /api/einheit/{id}` ohne `einheit:write` → `403`. (Test: ControllerAuthorizationTest#updateEinheit_withoutPermission_forbidden)
* [x] Nicht authentifiziert → `401`. (Test: ControllerAuthorizationTest#createEinheit_unauthenticated_unauthorized)
* [x] `GET /api/einheit` ist mit `einheit:read` erreichbar (`200`), `DELETE` und `GET /{id}` ohne `einheit:write` → `403`. (Test: ControllerAuthorizationTest#getAllEinheiten_withEinheitRead_ok; Test: ControllerAuthorizationTest#getAllEinheiten_withoutEinheitRead_forbidden; Test: ControllerAuthorizationTest#deleteEinheit_withReadPermissionOnly_forbidden; Test: ControllerAuthorizationTest#getEinheitById_withReadPermissionOnly_forbidden)
* [x] `POST` → `201`. (Test: EinheitControllerTest#createEinheit_ValidInput_ReturnsCreated)
* [x] `PUT` auf nicht existierende ID → `404`. (Test: EinheitControllerTest#updateEinheit_NotFound_Returns404; Test: EinheitServiceTest#updateEinheit_NotFound_ReturnsEmpty)
* [x] `DELETE` → `204`, auf nicht existierende ID → `404`. (Test: EinheitControllerTest#deleteEinheit_Exists_ReturnsNoContent; Test: EinheitControllerTest#deleteEinheit_NotFound_Returns404)
* [x] `GET /api/einheit/{id}` auf nicht existierende oder fremde ID → `404`. (Test: EinheitControllerTest#getEinheitById_NotFound_Returns404; Test: EinheitServiceTest#getEinheitById_NotFound_ReturnsEmpty; Test: EinheitRepositoryIT#shouldNotSeeEinheitOfOtherOrgWhenOrgFilterEnabled)
* [x] `DELETE /api/einheit/{id}` auf die ID eines fremden Mandanten → `404`. (Test: EinheitControllerTest#deleteEinheit_NotFound_Returns404; Test: EinheitServiceTest#deleteEinheit_NotExists_ReturnsFalse; Test: EinheitRepositoryIT#existsByIdSiehtNurDenEigenenMandanten)
* [x] Leerer Name, fehlender Typ oder Messpunkt über 50 Zeichen → `400`. (Test: EinheitControllerTest#createEinheit_InvalidName_ReturnsBadRequest; Test: EinheitControllerTest#createEinheit_NullTyp_ReturnsBadRequest; Test: EinheitControllerTest#createEinheit_MesspunktZuLang_ReturnsBadRequest)
* [ ] Name mit einem Zeichen oder über 30 Zeichen → `400`. (Test: EinheitControllerTest#createEinheit_NameMitEinemZeichen_ReturnsBadRequest; Test: EinheitControllerTest#createEinheit_NameMit31Zeichen_ReturnsBadRequest; Test: EinheitControllerTest#createEinheit_NameMit2Und30Zeichen_ReturnsCreated)
* [x] Die Einmaligkeitsprüfungen sehen nur den eigenen Mandanten. (Test: EinheitRepositoryIT#shouldNotLetBilanzTypOfOtherOrgBlockSingletonCheck; Test: EinheitRepositoryIT#shouldNotLetRfidOfOtherOrgBlockUniquenessCheck)
* [x] `id` und `orgId` im Body von `POST /api/einheit` werden nicht übernommen. (Test: EinheitControllerTest#createEinheit_IdUndOrgIdImBody_WerdenIgnoriert)
* [x] Eine beim Anlegen mitgeschickte ID erzeugt eine neue Einheit; die Einheit mit dieser ID – auch die eines fremden Mandanten – bleibt unverändert. (Test: EinheitServiceTest#createEinheit_MitId_LegtNeueEinheitAn; Test: FremdeIdBeimSpeichernIT#createEinheit_MitFremderId_LegtNeueEinheitAnUndLaesstFremdeUnveraendert)

## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Performance
* n/a – keine Vorgabe im Code. Die Liste wird ohne Paginierung vollständig geladen und clientseitig sortiert.

### NFR-2: Sicherheit
* **Backend:** `@PreAuthorize("hasAuthority('einheit:write')")` auf Klassenebene von `EinheitController`; `GET /api/einheit` mit `@PreAuthorize("hasAuthority('einheit:read')")` auf Methodenebene.
* **Frontend:** Route `/einheiten` mit `AuthGuard` und `data.permissions: ['einheit:read']`. Buttons und Kebab-Einträge zum Ändern sind nicht zusätzlich nach Permission ausgeblendet.
* **Fachrollen:** `einheit:read` – `zev_user`, `org_admin`, `zev_admin`; `einheit:write` – `org_admin`, `zev_admin` (`Specs/Berechtigungen.md`).
* **Mandantentrennung:** `orgFilter` in jeder Service-Methode; Einzelzugriff beim Lesen und Ändern über `findFirstById` (`EinheitRepository.java`), beim Löschen über `existsById` (gefilterte Abfrage) vor `deleteById`; `org_id` setzt immer der Server (Anlegen: Mandantenkontext, Ändern: bestehende Einheit); `id` und `orgId` aus dem Request-Body werden nicht übernommen, und `createEinheit` verwirft eine `id` zusätzlich selbst (Anhang A.4).

### NFR-3: Kompatibilität
* Bestandsdaten ohne `nebenkosten_relevant` bekamen mit `V123` den Default `true`, Nicht-Konsumenten und Konsumenten ohne Mieter wurden einmalig auf `false` gesetzt (`V123__Add_Einheit_Nebenkosten_Relevant.sql`). Das Formular behandelt ein fehlendes Kennzeichen als `true`.
* Neue Typen brauchen keine DDL (`typ` ist `VARCHAR(20)` ohne CHECK-Constraint), aber einen eigenen Fall in `EinheitTypPipe` (sonst Anzeige „Produzent“) und einen Eintrag in `einheitTypOptions`.

## 5. Edge Cases & Fehlerbehandlung

* [ ] Leere Liste → Hinweis `KEINE_EINHEITEN_VORHANDEN` statt Tabelle (`einheit-list.component.html`).
* [x] Netzwerkfehler beim Laden → Fehlermeldung „Fehler beim Laden der Einheiten: …“ (`.zev-message--error`, bleibt bis zum Wegklicken). (Test: einheit-list.component.spec.ts › loadEinheiten › should show error message on failure)
* [x] Nicht existierende oder fremde ID bei `GET /{id}`, `PUT`, `DELETE` → `404` (Service liefert leer bzw. `false`). (Test: EinheitControllerTest#getEinheitById_NotFound_Returns404; Test: EinheitControllerTest#updateEinheit_NotFound_Returns404; Test: EinheitControllerTest#deleteEinheit_NotFound_Returns404; Test: EinheitServiceTest#updateEinheit_NotFound_ReturnsEmpty; Test: EinheitRepositoryIT#shouldNotSeeEinheitOfOtherOrgWhenOrgFilterEnabled; Test: EinheitRepositoryIT#existsByIdSiehtNurDenEigenenMandanten)
* [ ] Ungültige Werte → `400` aus der Bean-Validation, Rumpf `{ "<feld>": "<Meldung>" }`; die Liste zeigt dann den Fallback „Fehler beim Erstellen/Aktualisieren: “ + technische Meldung. (Test: EinheitControllerTest#createEinheit_InvalidName_ReturnsBadRequest; Test: EinheitControllerTest#createEinheit_InvalidName_LiefertFeldRumpf; Test: einheit-list.component.spec.ts › onFormSubmit - create › should show error message on create failure; Test: einheit-list.component.spec.ts › onFormSubmit - update › should show error message on update failure)
* [x] Zweite Bilanz-/Speicher-Einheit oder doppelte RFID → `400` mit Fehler-Key, übersetzt angezeigt (FR-6). (Test: EinheitServiceTest#createEinheit_ZweiterBezug_ThrowsBilanzTypKey; Test: EinheitServiceTest#createEinheit_ZweiteRuecklieferung_ThrowsBilanzTypKey; Test: EinheitServiceTest#updateEinheit_TypwechselZuBezugNebenAnderemBezug_ThrowsBilanzTypKey; Test: EinheitServiceTest#createEinheit_ZweiterSpeicher_ThrowsMitEigenemKey; Test: EinheitServiceTest#createEinheit_LadestationMitVergebenerRfid_ThrowsException; Test: EinheitControllerTest#createEinheit_ZweiterBilanzTyp_Returns400MitFehlerKey; Test: einheit-list.component.spec.ts › onFormSubmit - create › should translate the server key when the RFID is already used)
* [x] Löschen mit zugeordneten Mietern → abgewiesen (`400`, Meldung mit Anzahl). (Test: EinheitServiceTest#deleteEinheit_MitZugeordnetenMietern_ThrowsException; Test: einheit-list.component.spec.ts › onDelete › should show the server message when the unit is still assigned to tenants)
* [ ] Löschen ohne Mieter → Tarifpositionen und Gerätezustände werden mitgelöscht (`ON DELETE CASCADE`). (Test: FlywaySchemaIT#einheitLoeschen_LoeschtTarifpositionenUndGeraetezustaendeMit)
* [ ] Wechsel des Typs von Konsument auf einen anderen Typ blendet die Checkbox aus; der gesetzte Wert wird trotzdem mitgesendet (nur bei `CONSUMER` ausgewertet).
* [ ] Gleichzeitige Bearbeitung → keine Versionsprüfung; der letzte Speichervorgang überschreibt.
* [ ] Löschen einer Einheit mit Messwerten oder Zählerrohdaten → siehe Abschnitt 8.

## 6. Abhängigkeiten & betroffene Funktionalität
* **Voraussetzungen:** Tabelle `zev.organisation` (FK `org_id`); Permissions `einheit:read`/`einheit:write` in Keycloak (`Specs/Berechtigungen.md`); Mandantenkontext (`OrganizationContextService`, `HibernateFilterService`, `Specs/Mandantenfähigkeit.md`); Übersetzungen in `zev.translation`.
* **Betroffener Code (nutzt Einheiten):**
  * Mieterzuordnung `zev.mieter_einheit` – `Specs/Mieterverwaltung.md`, `Specs/Ladestationen.md` (FR-2, FR-3)
  * Tarifpositionen am Einheiten-Anker – `Specs/Ladestationen.md`
  * Messwerte-Upload mit KI-Zuordnung (`POST /api/einheit/match`) – `Specs/Messwerte-mit-KI.md`, `Specs/Bilanz-Messwerte-Upload.md`
  * MQTT-Ingest über `(org_id, messpunkt)` (`EinheitRepository.findAllByOrgIdAndMesspunkt`) – `Specs/MQTT-Integration.md`
  * Bilanz-Typen und Statistik-Vergleiche – `Specs/Bilanzmesspunkt.md`; Speicher – `Specs/Batteriespeicher.md`
  * Nenner der Nebenkostenumlage (`countByTypAndNebenkostenRelevantTrue`) – `Specs/Nebenkosten/Abrechnung.md`, FR-2 und FR-9
  * Einheiten-Auswahllisten (`EinheitSelectorComponent`, Rechnungen, Debitorkontrolle, Messwerte-Grafik)
  * Menü – `Specs/Hamburgermenü.md`, FR-2
* **Datenmigration:** n/a – keine.

## 7. Abgrenzung / Out of Scope
* **KI-Matching** (`POST /api/einheit/match`, `EinheitMatchingService`): nur verwiesen, `Specs/Messwerte-mit-KI.md`.
* **Mieter-Zuordnung** (Mehrfachzuordnung, Pflicht „mindestens eine Einheit“, Regel des Löschschutzes): nur verwiesen, `Specs/Mieterverwaltung.md` und `Specs/Ladestationen.md`, FR-2.
* **Ladestationen** (Typ `LADESTATION`, RFID im Messpunkt, RFID-Eindeutigkeit, Tarifpositionen-Seite): nur verwiesen, `Specs/Ladestationen.md`.
* **Speicher** (Typ `SPEICHER`, Einmaligkeit, Messung): nur verwiesen, `Specs/Batteriespeicher.md`.
* **Bilanzmesspunkte** (`BEZUG`/`RUECKLIEFERUNG`, Einmaligkeit, gemeinsamer Messpunkt): nur verwiesen, `Specs/Bilanzmesspunkt.md`.
* **Bedeutung des Kennzeichens `nebenkosten_relevant`** für die Umlage: `Specs/Nebenkosten/Abrechnung.md`.
* **`EinheitSelectorComponent`** (Auswahlliste anderer Seiten) gehört nicht zur Verwaltungsseite.
* Kein Import/Export von Einheiten, keine Paginierung, keine Suche/Filter in der Liste.

## 8. Offene Fragen

* **Schreib-Buttons ohne `einheit:write`:** Ist: Die Route verlangt nur `einheit:read`; ein `zev_user` sieht „Neue Einheit erstellen“ und im Kebab „Bearbeiten“/„Löschen“, das Backend antwortet beim Speichern/Löschen mit `403`. Vermutlich gemeint: Schreibaktionen nur mit `einheit:write` anzeigen (z.B. `*appPermission`) – oder die Seite bewusst als Leseansicht mit Fehlermeldung beim Schreiben?
* **Mindestlänge Name:** Ist: Das Backend verlangt 2–30 Zeichen (`@Size(min = 2)`), das Formular prüft nur „nicht leer“; ein Name mit einem Zeichen endet in `400` und der Meldung „Fehler beim Erstellen: Http failure response …“. Vermutlich gemeint: Formular prüft ebenfalls min. 2 Zeichen mit übersetzter Meldung – oder Mindestlänge im Backend entfernen?
* **Validierungsfehler nicht übersetzt:** Ist: Bean-Validation liefert englische Texte („Name is required“) im Feld-Rumpf `{ "name": "…" }`; die Liste wertet nur `error.error.error` aus und zeigt sonst „Fehler beim Erstellen: “ + `error.message`. Vermutlich gemeint: übersetzte Feldmeldung.
* **Hartcodierte Texte (Verstoss gegen `Specs/generell.md`):** „Fehler beim Laden der Einheiten: “, „Fehler beim Erstellen: “, „Fehler beim Aktualisieren: “ (`einheit-list.component.ts`) und die Löschschutz-Meldung „Einheit kann nicht gelöscht werden: <n> Mieter zugeordnet“ (`EinheitService.deleteEinheit`) sind nicht übersetzt. Vermutlich gemeint: Translation-Keys (beim Löschschutz mit Platzhalter für die Anzahl).
* **`FEHLER_LOESCHEN_EINHEIT` fehlt:** Ist: Der Fallback beim Löschfehler zeigt den Key ungeprüft an (ohne `translate`), und der Key steht nicht in `zev.translation` – der Benutzer sieht „FEHLER_LOESCHEN_EINHEIT“ (z.B. bei `404`). Vermutlich gemeint: übersetzter Fallback mit Key in einer Migration.
* **Löschen einer Einheit mit Messwerten:** Ist: `messwerte` und `zaehler_rohdaten` verweisen ohne `ON DELETE`-Klausel auf die Einheit; der Service prüft nur zugeordnete Mieter. Nach Code-Lesart scheitert das Löschen erst an der Datenbank und endet über den `GlobalExceptionHandler` als `409` mit dem generischen Klartext („…verletzt eine Regel der Datenbank…“); kein Test belegt das. Gewollt: Löschen abweisen mit eigener Meldung („Einheit hat Messwerte“) – oder Messwerte mitlöschen?
* **`GET /api/einheit/{id}` verlangt `einheit:write`:** Ist: Der Einzelabruf erbt die Klassen-Permission, obwohl er nur liest (dokumentiert in `Specs/Berechtigungen.md`); im Frontend wird `getEinheitById` nirgends aufgerufen. Gewollt so – oder `einheit:read`, bzw. Endpunkt entfernen?
* **Ladestation ohne RFID:** Ist: Eine Ladestation ohne Messpunkt wird gespeichert (Server prüft die Eindeutigkeit nur bei gesetztem Messpunkt). Der Kommentar im Test `einheit-form.component.spec.ts › should allow a LADESTATION without RFID (server rejects it, not the form)` behauptet, der Server weise das ab. Was gilt?
* **Sortierung nach Typ:** Ist: Sortiert wird nach dem Enum-Wert (`BEZUG` < `CONSUMER` < `LADESTATION` < `PRODUCER` …), nicht nach dem angezeigten übersetzten Text. Gewollt?
* **Sortierzustand nach Neuladen:** Ist: Nach Speichern/Löschen lädt die Liste neu in der Server-Reihenfolge (Name ↑), Spalte und Pfeil der zuletzt gewählten Sortierung bleiben aber stehen. Vermutlich gemeint: gewählte Sortierung nach dem Laden erneut anwenden.
* **Kebab „Tarifpositionen“ bei jedem Typ:** Ist: Der Eintrag erscheint auch bei Konsumenten, Produzenten usw.; die Seite Tarifpositionen bietet laut `Specs/Ladestationen.md`, FR-3 nur Ladestationen an. Gewollt – oder nur bei `LADESTATION` anzeigen?
* **Icons:** Ist: Menüeintrag mit Icon `grid`, Seitentitel mit Icon `home`. Gewollt unterschiedlich?
* **Formularrahmen:** Ist: lokale Klasse `.form-container` mit eigenen Farben und ein lokal neu definiertes `.zev-form-hint` statt der Design-System-Klasse `.zev-form-container`. Gewollt?

## Anhang A – Entscheidungen und Verlauf

### A.1 Entstehung (18.10.2026)
Aus dem Code rekonstruiert (Stand Commit `a1cf33a`). Verhalten, das andere Specs beschreiben, ist nur verwiesen (Abschnitt 7).

### A.2 Einzelzugriff unter dem Mandantenfilter
`findById` wurde durch `findFirstById` ersetzt, weil Hibernate `@Filter` beim Laden über den Primärschlüssel nicht anwendet (Commit `70f2eca` „fix(security): Mandantenfilter greift auch beim Zugriff ueber die ID“; Javadoc in `EinheitRepository.findFirstById`).

### A.3 Löschschutz bei zugeordneten Mietern
Der Service prüft die Zuordnungen vor dem Löschen, statt sich auf `ON DELETE RESTRICT` zu verlassen: Der FK hätte das Löschen ebenfalls abgewiesen, aber ohne verwertbare Meldung (Kommentar in `EinheitService.deleteEinheit`, `Specs/Ladestationen.md`, FR-2; Commits `9af0831`, `5c225b1`).

### A.4 Mitgeschickte `id` beim Anlegen (FR-3, NFR-2, 18.10.2026)
Beim Reverse Engineering als Offene Frage festgehalten:

> **`id` im Rumpf beim Anlegen:** Ist: `POST` nimmt die Entity entgegen und übernimmt eine mitgesendete `id`; `save` mit gesetzter ID wird zu `merge` – nach Code-Lesart würde so eine bestehende Einheit (auch eines fremden Mandanten, da `merge` nicht gefiltert lädt) überschrieben und dem eigenen Mandanten zugeschlagen. Das Frontend sendet beim Anlegen keine ID. Vermutlich gemeint: `id` beim Anlegen ignorieren (`setId(null)`) bzw. ein Request-DTO nach Vorlage `DebitorDTO` statt der Entity. Bitte mit `/8_security-check` prüfen.

Der Security-Check vom 18.10.2026 hat das bestätigt (OWASP A01): `save` mit gesetzter ID wird zu `merge`, und `merge` lädt den Datensatz über den Primärschlüssel, wo der Mandantenfilter nicht greift (vgl. A.2). Ein Benutzer mit `einheit:write` konnte so die Einheit eines fremden Mandanten überschreiben und dem eigenen zuschlagen. `FremdeIdBeimSpeichernIT` war gegen den alten Code rot (`createEinheit` gab die fremde ID zurück).

Entscheid: `createEinheit` setzt die ID auf `null`, und `id`/`orgId` sind für Jackson nur lesbar. Ein Request-DTO wäre die gründlichere Lösung, hätte aber Controller, Frontend-Modell und Tests umgebaut; offen für später. Dieselbe Lücke bestand in `TarifService.saveTarif` und `MieterService.saveMieter` (`Specs/Tarifverwaltung.md`, Anhang A.13; `Specs/Mieterverwaltung.md`, Anhang A.1).
