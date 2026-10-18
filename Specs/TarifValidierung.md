# Validierung der Tarife

## 1. Ziel & Kontext - Warum wird das Feature benötigt?
* **Was soll erreicht werden:** Es soll geprüft werden, dass die Tarife die Zeitachse lückenlos abdecken.
* **Warum machen wir das:** Schon beim Erfassen der Tarife möchte ich sicher sein, dass es keine Lücken in der Tarifabdeckung gibt.
* **Ausgangslage:** Lücken fielen erst beim Generieren der Rechnungen auf (Hintergrund: Anhang A.1).

## 2. Funktionale Anforderungen (FR) - Was soll das System tun?
### FR-1: Ablauf / Flow
1. In der Tarifverwaltung (`/tarife`) kann der Benutzer auf den Button „Quartale validieren" klicken.
2. Das System prüft für alle Quartale, für die es mind. einen Tarif gibt, ob das ganze Quartal mit diesem Tariftyp abgedeckt wird.
3. Ein zweiter Button „Jahre validieren" macht die Prüfung für das ganze Jahr.
4. Geprüft werden nur Jahre, die mind. einen Tarif haben.
5. Auf Lücken geprüft werden die Tariftypen `ZEV` und `VNB`. Die übrigen sind optional (Grundgebühr) oder werden an der Position gewählt (`LADESTROM`, `ZUSATZ`); welche Perioden geprüft werden, bestimmen aber die Tarife aller Typen (`Specs/Tarifverwaltung.md`, FR-2 und FR-7; Hintergrund: Anhang A.4).
6. Bei Validierungsfehlern erscheint eine Fehlermeldung, die die Lücke oder die Lücken auflistet.
7. Die Lücken-Meldungen sind mehrsprachig: Das Backend liefert die Lücken **strukturiert** pro Periode (`{ periode, luecken: [{ tarifTyp, datum, weitere }] }`); das Frontend setzt die angezeigten Texte aus Translation-Keys zusammen (`TARIF_LUECKE_ZEV`/`TARIF_LUECKE_VNB`, `TARIF_LUECKE_WEITERE`). Periodenlabel (z.B. `Q1/2024`, `2024`), Tarif-Typ-Code und Datum (`dd.MM.yyyy`) sind sprachneutral. Dieselben Keys verwendet die Rechnungserzeugung (Hintergrund: Anhang A.2).

### FR-2: Persistierung
* Es findet keine Persistierung statt.

### FR-3: Layout
* Die beiden Buttons stehen rechts vom Button zum Erfassen eines neuen Tarifes.
* Farbe `--color-secondary` aus dem Design System.
* Schweizer Datumsformat `dd.MM.yyyy`.

### API-Endpunkte
* `GET /api/tarife/validate?modus=quartale|jahre` (Permission wie die übrige Tarifverwaltung, NFR-2).

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)
* [x] Die Buttons „Quartale validieren" und „Jahre validieren" sind vorhanden. (E2E: tarif-verwaltung.spec.ts › Tarif Management - Validation Buttons › should display validation buttons)
* [x] Die Validierung deckt immer ganze Quartale ab, für die mind. ein Tarif existiert. (Test: TarifServiceTest#validateQuartale_GapInQuarter_ReturnsInvalidWithErrors; TarifServiceTest#validateQuartale_MissingTarifInQuarter_ReturnsInvalidWithErrors; TarifServiceTest#validateQuartale_NoTarife_ReturnsValidWithMessage; TarifServiceTest#validateQuartale_NurGrundgebuehrImQuartal_PrueftZevUndVnb)
* [x] Es wird eine Fehlermeldung angezeigt, wenn eine Validierung fehlschlägt. (Test: tarif-list.component.spec.ts › onValidateQuartale › should show error message with errors when invalid; tarif-list.component.spec.ts › onValidateJahre › should show multiple errors when invalid)
* [x] Die angezeigten Lücken-Meldungen sind mehrsprachig (Translation-Keys statt hartcodierter deutscher Texte); dieselben Keys werden auch beim Generieren von Rechnungen verwendet. (Test: tarif-list.component.spec.ts › onValidateQuartale › should show error message with errors when invalid; rechnungen.component.spec.ts › onGenerate › should build a translated message from structured tariff gaps)

## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Performance
* Die Validierung erfolgt innerhalb einer Sekunde.

### NFR-2: Sicherheit
* Die Validierung benötigt dieselbe Permission wie die Erfassung eines neuen Tarifes (`tarife:manage`, `Specs/Tarifverwaltung.md`, NFR-2).

### NFR-3: Kompatibilität
* Die Validierung (`validateQuartale`, `validateJahre`) nutzt dieselbe Lückenprüfung des TarifService wie die Rechnungserzeugung (`validateTarifAbdeckung`).

## 5. Edge Cases & Fehlerbehandlung
* Die angezeigte Fehlermeldung verschwindet nicht automatisch, kann aber durch Klicken entfernt werden.

## 6. Abhängigkeiten & betroffene Funktionalität
* **Voraussetzungen:** `Specs/Tarifverwaltung.md` (Tarife, Lückenprüfung je Typ: FR-2, FR-7).
* **Betroffener Code:** `TarifController` (Endpunkt), `TarifService` (`validateQuartale`, `validateJahre`, Lückenprüfung), Frontend `tarif.service.ts`, `tarif-list` (Buttons, Meldung).
* **Datenmigration:** keine; Übersetzungen per Flyway: `V33__Add_TarifValidierung_Translations.sql` (Buttons) und `V66__Add_Tarif_Luecke_Translations.sql` (Lücken-Meldungen, FR-1.7).

## 7. Abgrenzung / Out of Scope
* Keine Validierung von Zeitbereichen (Quartale, Jahre), die keinen einzigen Tarif haben.

## 8. Offene Fragen
* Habe ich etwas vergessen?

## Anhang A – Entscheidungen und Verlauf

### A.1 Ausgangslage (FR-1, 13.01.2026)
Vor diesem Feature wurde erst beim Generieren der Rechnungen geprüft, ob es Lücken gibt. Die Buttons waren als „neue Buttons" spezifiziert; der Endpunkt war zuerst ein `POST` und wurde am 18.08.2026 auf `GET` umgestellt (`Specs/TarifValidierung_Umsetzungsplan.md`). NFR-3 lautete: „Verwende die im TarifService bereits vorhandene Methode validateTarifAbdeckung." Abschnittsnummern: Die Spec hatte 7 Abschnitte (6 = Abgrenzung, 7 = Offene Fragen); bei der Konsolidierung am 18.10.2026 kam Abschnitt 6 „Abhängigkeiten" nach `Specs/SPEC.md` dazu, Abgrenzung und Offene Fragen wurden zu 7 und 8.

### A.2 Mehrsprachige Lücken-Meldungen (FR-1.7, 17.06.2026)
Mit „Rechnungen: Tarif-Validierungsfehler statt 500 + i18n + dismissible" liefert das Backend die Lücken strukturiert statt als fertigen deutschen Text; dieselben Translation-Keys gelten für die Rechnungserzeugung.

### A.3 Glättung (18.10.2026)
Bei der Konsolidierung wurden Imperative ins Präsens gesetzt, ohne den Inhalt zu ändern. Vorher: „Platziere einen zweiten Button …", „Prüfe nur Jahre …", „Zeige bei Validierungsfehlern eine Fehlermeldung an …" (FR-1); „Platziere die neuen Buttons rechts …", „Verwende die Farbe --color-secondary …", „Verwende das Schweizer Datumsformat …" (FR-3); „Die Validierung muss innerhalb einer Sekunde erfolgen" (NFR-1); „Die angezeigt Fehlermeldung soll nicht automatisch verschwinden …" (Edge Cases); „Validierung keine Zeitbereiche …" (Abgrenzung).

### A.4 Geprüfte Tariftypen (FR-1.5, 18.10.2026)
FR-1.5 lautete: „Die Prüfung erfolgt immer für alle Tariftypen." Das stimmte am 13.01.2026, als es nur `ZEV` und `VNB` gab. Mit `GRUNDGEBUEHR`, `LADESTROM` und `ZUSATZ` kamen Typen dazu, die keine lückenlose Abdeckung brauchen; `TarifService.findTarifLuecken` prüft seither nur `ZEV` und `VNB`. Bei der Konsolidierung am 18.10.2026 an den Code und an `Specs/Tarifverwaltung.md` (FR-2: Lückenprüfung je Typ; FR-7: Grundgebühr optional) angeglichen.

### A.5 Kriterium zur Position der Buttons ersetzt (FR-3, 18.10.2026)
Das Kriterium lautete: „Die Button stehen rechts vom grünen Button zum Erfassen neuer Tarife." Die Position ist eine Designentscheidung und steht in FR-3; ein Test dafür schützte vor keinem realen Fehler. Beim Akzeptanzkriterien-Check vom 18.10.2026 ersetzt durch „Die Buttons sind vorhanden".
