# Nebenkosten-Abrechnung — Umsetzungsplan

## Zusammenfassung

Umgesetzt wird [`Abrechnung.md`](./Abrechnung.md): Nebenkostenabrechnungen je Zeitraum mit vier
Positionsarten (Umlage, Verbrauch, Anteil, Zuschlag), Positionen und Akonto je Mieter, Saldo als
Nachzahlung oder Guthaben. Fünf neue Tabellen, ein Berechnungsservice und eine Bearbeitungsmaske
mit Sofortberechnung.

Der Plan setzt **Backend und Berechnung zuerst** um und die Oberfläche danach. Die Rechenregeln —
zeitanteilige Umlage, Zuschlagskaskade, Rundung — sind der Teil, bei dem Fehler am teuersten
sind; sie sollen isoliert stehen und prüfbar sein, bevor eine Maske darauf gesetzt wird.

## Betroffene Komponenten

### Datenbank (Flyway)
| Datei | Inhalt |
|---|---|
| `V117__Add_Mengeneinheit_M3.sql` | `ck_tarif_mengeneinheit` um `M3` erweitern |
| `V118__Create_Nebenkosten_Tables.sql` | fünf Tabellen, Sequenzen, CHECK- und Unique-Constraints |
| `V119__Add_Mieter_Akonto.sql` | `zev.mieter.akonto_pro_monat` |
| `V120__Add_Nebenkosten_Abrechnung_Translations.sql` | Übersetzungen DE + EN |
| `V121__Add_Mengeneinheit_CHF.sql` | `CHF` in allen drei Einheiten-Constraints + Übersetzung |
| `V122__Add_Nebenkosten_Zurueck_Translation.sql` | Schlüssel `NK_ZURUECK_UEBERSICHT` |
| `V123__Add_Einheit_Nebenkosten_Relevant.sql` | `zev.einheit.nebenkosten_relevant` + Startbelegung + Übersetzungen |
| `V124__Add_Nebenkosten_Eingaben_Fehler_Translation.sql` | Schlüssel `NK_FEHLER_EINGABEN` |
| `V125__Add_Nk_Positionsart_Anteil.sql` | `ANTEIL` in `ck_nk_position_art` und `ck_nk_position_felder` + Übersetzungen |

### Backend
| Datei | Änderung |
|---|---|
| `entity/Mengeneinheit.java` | neuer Wert `M3` |
| `entity/NkPositionsart.java` | neu — `UMLAGE`, `VERBRAUCH`, `ZUSCHLAG`, `ANTEIL` |
| `entity/NkAbrechnung.java`, `NkPosition.java`, `NkVerbrauch.java`, `NkZusatz.java`, `NkAkonto.java` | neu |
| `repository/Nk*Repository.java` | neu (fünf) |
| `repository/EinheitRepository.java` | **neu:** `countByTypAndNebenkostenRelevantTrue` für die Vorbelegung |
| `repository/MieterRepository.java` | **neu:** Mieter, deren Mietverhältnis einen Zeitraum berührt |
| `service/NkBerechnungService.java` | neu — reine Rechenlogik, ohne Persistenz |
| `service/NkAbrechnungService.java` | neu — CRUD, Flag-Prüfung, Schreibschutz |
| `controller/NkAbrechnungController.java` | neu |
| `dto/NkAbrechnungDetailDTO.java` u.a. | neu — zusammengesetzte Antwort (FR-6) |
| `service/MieterService.java` | Löschschutz um NK-Abrechnungen erweitern |
| `test/.../ArchitectureTest.java` | Regel: jede NK-Service-Methode prüft den Flag |

### Frontend
| Datei | Änderung |
|---|---|
| `models/nebenkosten.model.ts` | neu — Abrechnung, Positionen, Arten, DTOs |
| `models/tarif.model.ts` | `Mengeneinheit` um `M3`; `mengeneinheitKey()` **und** `preisEinheitKey()` |
| `services/nebenkosten.service.ts` | neu |
| `utils/nebenkosten-berechnung.ts` | neu — Sofortberechnung in der Maske |
| `components/nebenkosten-abrechnung/` | ersetzt das Gerüst — Liste |
| `components/nebenkosten-abrechnung-form/` | neu — Bearbeitungsmaske |
| `package.json` | `@angular/cdk@^21` für Drag & Drop (Entscheid 1) |
| `design-system/…/button/button.css` | **neu:** `.zev-button--icon` für Aktionen in einer Tabellenzeile |
| `components/design-system-showcase/` | Showcase um die neue Variante ergänzt |

## Umsetzungsreihenfolge (Phasen)

| Status | Phase | Beschreibung |
|--------|-------|--------------|
| [x] | 1. Mengeneinheit `M3` | Enum-Wert + Migration V117 für `ck_tarif_mengeneinheit`. Frontend-Enum und **beide** Key-Funktionen in `tarif.model.ts` mitziehen — sonst wird `M3` als „kWh" beschriftet. |
| [x] | 2. Migration Tabellen | V118: `nk_abrechnung`, `nk_position`, `nk_verbrauch`, `nk_zusatz`, `nk_akonto` samt Sequenzen, CHECK- und Unique-Constraints, Spaltenkommentaren. Mieter-FKs auf **`ON DELETE RESTRICT`**. |
| [x] | 3. Migration Akonto-Stammdatum | V119: `zev.mieter.akonto_pro_monat NUMERIC(10,2)` nullable. |
| [x] | 4. Entities und Repositories | Fünf Entities nach dem Muster von `Tarifposition.java` (`@Filter(orgFilter)`, Sequenz-Generator), Repositories dazu. `Mieter` um das neue Feld erweitern. |
| [x] | 5. Hilfsabfragen | `EinheitRepository.countByTyp`; `MieterRepository`-Abfrage für Mieter im Zeitraum. |
| [x] | 6. **Berechnungsservice** | `NkBerechnungService`: Tage je Mieter, Umlage zeitanteilig, unverteilter Leerstandsanteil, Verbrauch, Zusatz, Zuschlagskaskade über beide Tabellen inkl. Gleichstandsregel, Akonto und Saldo. **`BigDecimal`, `setScale(2, HALF_UP)` je Zeile.** Ohne Persistenz, ohne Spring-Kontext — reine Funktionen auf Eingabedaten. |
| [x] | 7. Service | `NkAbrechnungService`: CRUD, `enableOrgFilter()` **und** `pruefeFeatureFlag()` am Anfang jeder Methode, Schreibschutz bei `abgerechnet`, Validierung `Σ Tage(i) <= Nenner`. |
| [x] | 8. DTOs und Controller | Zusammengesetzte Antwort für `GET /{id}` (alles in einem Aufruf, NFR-1); Endpunkte nach FR-6 inkl. `PATCH .../abgerechnet`. `@PreAuthorize` auf Klassenebene. |
| [x] | 9. Löschschutz Mieter | `MieterService.deleteMieter` um die NK-Prüfung erweitern, damit statt des Datenbankfehlers eine verständliche Meldung erscheint. |
| [x] | 10. ArchUnit-Regel | Test, der sicherstellt, dass jede öffentliche Methode des NK-Service den Feature-Flag prüft. |
| [x] | 11. Frontend-Modell und -Service | `nebenkosten.model.ts`, `nebenkosten.service.ts` nach dem Muster von `tarif.service.ts`. |
| [x] | 12. Liste | Gerüstseite ersetzen: `zev-table`, Sortierung nach `datum_von` absteigend, Inline-Checkbox „abgerechnet" mit Rückfrage nur beim Deaktivieren, Kebab-Menü, Schaltfläche **unterhalb** der Tabelle. |
| [x] | 13a. `@angular/cdk` aufnehmen | `npm install @angular/cdk@^21` — Entscheid 1. Vor Phase 13, weil die Positionstabelle darauf aufbaut. |
| [x] | 13. Maske: Kopf und allgemeine Positionen | Angaben zur Abrechnung inkl. **Anzahl Wohnungen** (vorbelegt, leer wenn keine `CONSUMER`-Einheiten), Positionstabelle mit art-abhängigen Feldern und **Drag & Drop**. |
| [x] | 14. Maske: Mieterblöcke | Je Mieter `zev-panel` mit Umlage-, Verbrauchs-, Zusatz- und Zuschlagszeilen, Kostentotal, Akonto und Saldo; Hinweis bei Mietern ohne Einheit. |
| [x] | 15. Sofortberechnung | `utils/nebenkosten-berechnung.ts` — dieselben Regeln clientseitig als **Vorschau**; Anzeige von Leerstandsanteil und Rundungsdifferenz getrennt. **Nach dem Speichern werden die Werte des Servers geladen und angezeigt** (Entscheid 2), nicht die selbst gerechneten. |
| [x] | 16. Übersetzungen | V120 mit allen Schlüsseln aus FR-7, je deutsch und englisch. |
| [x] | 17. Sperre | Alle Eingabefelder bei `abgerechnet` sperren, Hinweis anzeigen; nur das Flag bleibt bedienbar. |
| [x] | 18. Hinweise wegklickbar | Alle `zev-message--info` der Maske mit `--statisch` und `--dismissible`. **Nachtrag:** Ohne `--statisch` lagen sie als Overlay über dem Seitenanfang. Erklärhinweis dauerhaft (`localStorage`), Zustandshinweise nur für die geöffnete Maske. |
| [x] | 19. Mieterblöcke aufklappbar | `zev-collapsible` statt `zev-panel`, beim Öffnen der Maske alle geschlossen. Die Kopfzeile trägt Name, Miettage und **Saldo** — sonst wäre die geschlossene Ansicht eine reine Namensliste. |
| [x] | 20. Fix: `null` statt `undefined` | Die Herkunftsprüfung einer Zeile verglich auf `undefined`; Jackson schickt aber `null`. Folge: alle Mengenfelder gesperrt, bis eine Eingabe die Vorschau neu aufbaute. Ebenso korrigiert: Zuordnung Zeile → Kontrollzahlen über die Datenbank-ID statt über die Reihenfolge. |
| [x] | 21. Mengenfelder über `ngModel` | `[value]`/`(change)` ersetzt: rechnet jetzt beim Tippen (FR-7) und liest die Menge aus der Position statt aus der bei jeder Neuberechnung ersetzten Zeile. Dazu 13 Unit-Tests der Maske mit **serverförmigen** Daten (`null` statt `undefined`). |
| [x] | 22. Fix: Neuladen verschachtelter Routen | `SpaRedirectController` (frontend-service) leitete nur einstufige Pfade auf `index.html`. Neuladen von `/nebenkosten/abrechnung` endete auf der Whitelabel-Seite. Jetzt bis drei Ebenen, jedes Segment punktfrei — Dateien unter `/assets/` bleiben Dateien. Betrifft **alle** künftigen verschachtelten Routen. |
| [x] | 23. Mengeneinheit `CHF` | Enum-Wert `CHF` („Fr.") für Umlagen, deren verteilte Grösse selbst ein Betrag ist. V121 ersetzt **alle drei** CHECK-Constraints (`ck_tarif_mengeneinheit`, `ck_nk_position_einheit`, `ck_nk_zusatz_einheit`) und bringt die Übersetzung. Im Tarifformular bewusst **nicht** wählbar. |
| [x] | 24. Schaltflächen | „Neue Abrechnung erstellen" **vor** die Tabelle (wie Tarife, Einheiten, Mieter). In der Maske zusätzlich „Zurück zur Übersicht" — nach dem Speichern bleibt die Maske offen, und „Abbrechen" ist dann das falsche Wort. Übersetzung in V122. |
| [x] | 25. Vorschlag Anzahl Wohnungen | Neues Kennzeichen `zev.einheit.nebenkosten_relevant` (V123) statt Ableitung aus der Mieterzuordnung: Allgemeinstrom und PV-Eigenverbrauch sind Verbraucher, aber keine Wohnungen — und in der Praxis dem Eigentümer als Mieter zugeordnet, kämen also durch. Entity, `countByTypAndNebenkostenRelevantTrue`, Checkbox in der Einheiten-Maske (nur bei `CONSUMER`), Startbelegung in der Migration. Fünf IT-Tests. |
| [x] | 26. Fix: Zähler wie Nenner | `ladeMieter` zählte weiterhin **alle** `CONSUMER`-Einheiten eines Mieters, der Nenner aber nur gekennzeichnete. Folge: Σ Tage > Nenner, jedes Speichern abgewiesen — und ohne diese Prüfung hätte der Eigentümer still einen Wohnungsanteil je Umlage erhalten. Test, der die Symmetrie festnagelt. |
| [x] | 27. Fehlermeldungen | Feldfehler erst nach dem ersten Speicherversuch (`speichernVersucht`), Meldung mit Schliesskreuz, Speichern-Schaltfläche nicht mehr per `[disabled]` gesperrt. Vier Frontend-Tests. Übersetzung in V124. |
| [x] | 28. Zweites Speichern oben | Neben „Position hinzufügen" bei den allgemeinen Positionen, damit für das Speichern nicht ans Ende der Maske gescrollt werden muss. Dieselbe Aktion, kein neuer Übersetzungsschlüssel. |
| [x] | 29. Positionsart `ANTEIL` | Vierte Art: Totalbetrag an der Position, **Prozentsatz je Mieter** (Heizkosten mit fremdem Verteilschlüssel). Der Prozentsatz liegt in `nk_verbrauch.menge` — ein Wert je Position und Mieter, die Bedeutung folgt aus der Art. Summe der Anteile als Kontrollzahl, bei ≠ 100% hervorgehoben, aber **nicht** erzwungen. V125 ersetzt beide CHECK-Constraints. Sieben neue Tests. |
| [x] | 30. Ausrichtung der Positionsfelder | Einheitlicher Feldaufbau: Titelzeile plus Eingabe, wo nötig eine **leere** Titelzeile (Pseudo-Element, kein leeres Markup); Zellen oben ausgerichtet; Hinweise **ausserhalb** der Feldzeile. **Nachtrag:** Der erste Anlauf richtete an der Unterkante aus — das brach, sobald unter einem Feld ein Hinweis stand (Anteil, Zuschlag), weil sich die übrigen Felder dann an ihm ausrichteten. Bewusst nicht im Design System, dort träfe es jede Tabelle der Anwendung. |

### Validierung nach den Phasen
* Backend: `cd backend-service && mvn compile -q`, nach Phase 10 `mvn test`
* Frontend: `cd frontend-service && npx ng build --configuration=development`
* Nach Phase 6: Der Berechnungsservice ist ohne Datenbank testbar — die Beispiele aus FR-2
  (Leerstand 9 Wohnungen/900.00 CHF → 24.66 CHF unverteilt) und FR-4 (`A = 4.50`) als
  Referenzwerte verwenden.

## Validierungen

### Backend
| Regel | Ort | Verhalten bei Verstoss |
|---|---|---|
| `datum_von <= datum_bis` | Service + CHECK | `IllegalArgumentException` → `400` |
| `anzahl_wohnungen > 0` | Service + CHECK | `400` |
| `Σ Tage(i) <= Nenner` | Service | `400` mit beiden Werten in der Meldung |
| Art-abhängige Pflichtfelder | Service + CHECK | `400` |
| `prozentsatz` 0–100 | Service | `400` |
| `menge >= 0`, `betrag_pro_einheit >= 0` | Service + CHECK | `400` |
| `reihenfolge` eindeutig je Abrechnung | Unique-Constraint | `400` |
| Schreibzugriff auf `abgerechnet = true` | Service | `400` mit Hinweis |
| Feature-Flag aktiv | Service, jede Methode | `403` |
| Permission `nebenkosten:manage` | `@PreAuthorize` | `403` |
| Mandant | `@Filter(orgFilter)` | fremde Abrechnung → `404` |
| Mieter mit NK-Bezug löschen | `MieterService` | `400` mit Hinweis |

### Frontend
| Regel | Verhalten |
|---|---|
| Pflichtfelder Bezeichnung, Datum, Anzahl Wohnungen | Feldfehler, kein Request |
| `datum_von <= datum_bis` | Feldfehler |
| Anzahl Wohnungen ganzzahlig > 0 | Feldfehler |
| Menge und Beträge nicht negativ | Feldfehler |
| Prozentsatz 0–100 | Feldfehler |
| `abgerechnet` gesetzt | alle Felder gesperrt |

## Offene Punkte / Annahmen

### Aus der Spec übernommen
Abschnitt 8 der Spec ist leer — alle 14 Entscheide sind dort tabelliert und in diesen Plan
eingeflossen (Umlage zeitanteilig, Leerstand zu Lasten des Eigentümers, Kaskade, Rundung auf
1 Rappen je Zeile, `BigDecimal`, `RESTRICT`, expliziter Flag-Aufruf, Drag & Drop).

### Entscheid 1: `@angular/cdk` für Drag & Drop
Das Projekt hatte **kein** Drag-&-Drop-Paket. Verwendet wird `@angular/cdk`
(`cdkDropList` / `cdkDrag`): vom Angular-Team, versionsgleich mit Angular 21 (`^21`), mit
Tastaturbedienung und Barrierefreiheit — anders als handgebautes HTML5-Drag-&-Drop.

Die Abhängigkeit wird in **Phase 13a** aufgenommen (`npm install @angular/cdk@^21`), bevor die
Positionstabelle entsteht. Beim nächsten Angular-Upgrade zieht sie mit.

### Entscheid 2: Das Backend ist massgebend
Die Rechenregeln existieren zwangsläufig zweimal — im Backend (`GET` liefert fertige Beträge,
FR-6) und clientseitig für die Sofortberechnung ohne Speichern (FR-7). Umlage, Kaskade und
Rundung stehen damit in Java **und** in TypeScript und können auseinanderlaufen.

Verbindlich gilt deshalb:
* **Das Backend ist die Wahrheit.** Die clientseitige Rechnung ist ausschliesslich Vorschau.
* **Nach dem Speichern lädt die Maske die Antwort des Servers und zeigt dessen Werte an** —
  nicht die selbst gerechneten. Weicht die Vorschau ab, wird das im selben Moment sichtbar,
  statt monatelang unbemerkt zu bleiben.
* Die Referenzbeispiele aus FR-2 (Leerstand → 24.66 CHF unverteilt) und FR-4 (`A = 4.50`) sind
  **auf beiden Seiten** als Testfall zu verwenden.

### Annahme 3: Nur Wohnungen zählen bei `Tage(i)`

Die Spec schreibt „Σ über die **Einheiten** des Mieters" (FR-2), der Nenner ist aber die
**Anzahl Wohnungen**. Gezählt werden deshalb ausschliesslich `CONSUMER`-Einheiten: Eine
Ladestation zählte sonst als zweite Wohnung und verdoppelte den Anteil ihres Mieters — bei
vollständiger Belegung liefe die Prüfung `Σ Tage(i) <= Nenner` zusätzlich ins Leere.

### Annahme 4: Zusätzlicher Endpunkt `GET /vorlage`

FR-6 zählt sechs Endpunkte auf; keiner davon liefert die vorgeschlagene Anzahl Wohnungen für eine
**neue** Abrechnung — `GET /{id}` setzt eine gespeicherte voraus. Ergänzt wurde deshalb
`GET /api/nebenkosten/abrechnungen/vorlage`, das dieselbe Struktur wie `GET /{id}` mit leeren
Listen zurückgibt. Ohne ihn müsste der Benutzer den Nenner der Umlage beim Anlegen raten.

### Annahme 5: Mengen hängen an ihrer Position

Im Rumpf von `PUT` stehen die je Mieter erfassten Mengen **in** der Position
(`NkPositionDTO.verbraeuche`) statt in einer eigenen Liste mit `positionId`. Sonst liessen sich zu
einer gerade erst hinzugefügten Position keine Mengen erfassen — der Client hätte für sie noch
keine ID. Positionen, Zusatz- und Akontozeilen werden beim Speichern **ersetzt**, nicht
abgeglichen; die Maske schickt immer den vollständigen Stand.

### Annahme 1: Migrationsnummern
V117–V120, ausgehend von V116 als höchster vorhandener. Bei parallelen Änderungen vor der
Umsetzung neu prüfen.

### Annahme 2: Kein PDF, keine Debitorenbuchung
Beides ist in der Spec ausdrücklich out of scope (Abschnitt 7) und in keiner Phase enthalten.

### „Zurück zur Übersicht" oben wiederholt, Mengeneinheit in eigener Rasterspalte

Zwei Feinschliffe an der Erfassungsmaske (FR-7, 27.08.2026).

**1. Zweiter Weg zurück oben.** „Zurück zur Übersicht" steht jetzt auch in der Button-Zeile der
allgemeinen Positionen — aus demselben Grund wie das zweite Speichern: Bei dreissig Mieterblöcken
liegt das Ende der Maske mehrere Bildschirmseiten entfernt.

- Die Zeile steht neu **ausserhalb** von `@if (!gesperrt)`. Innerhalb wäre der Weg zurück genau
  dann verschwunden, wenn man die Maske nur liest — und dort ist er der einzige Grund, überhaupt
  eine Schaltfläche zu suchen. „Position hinzufügen" und das obere Speichern bleiben an die
  Sperre gebunden.
- Der Kommentar im bestehenden Test („oben verschwindet sie mit der ganzen Zeile") war damit
  falsch und ist korrigiert; die Zusicherungen des Tests gelten unverändert.

**2. Mengeneinheit untereinander.** `.nk-positionen__werte` ist von Flex auf ein **Raster mit drei
festen Spalten** umgestellt: Betrag, Menge, Mengeneinheit. Die Mengeneinheit trägt
`.nk-positionen__feld--einheit` mit `grid-column: 3` und steht damit bei jeder Positionsart an
derselben Stelle.

- Vorher rückten die Felder auf: Eine Verbrauchsposition kennt nur Betrag pro Einheit und
  Mengeneinheit, deren Auswahlfeld landete deshalb unter der *Gesamtmenge* der Umlage darüber.
- Die Spalten sind **fest** (`repeat(3, 10rem)`) und nicht `minmax(0, …)`: Eine leere Spalte fiele
  sonst auf 0 zusammen — genau das Zusammenfallen war das Problem. Der Preis: Eine Anteil- oder
  Zuschlagszeile beansprucht die volle Rasterbreite, obwohl sie ein Feld zeigt. Das kostet nichts,
  weil die Spaltenbreite der Tabelle sich ohnehin nach der breitesten Zeile richtet.
- Unter 600px stehen die Felder untereinander (`grid-template-columns: minmax(0, 1fr)`,
  `grid-column: auto`); dort ist eine Ausrichtung über Zeilen hinweg nicht sichtbar, und drei feste
  Spalten schöben die Tabelle aus dem Bild.
- **Kein Design-System-Eingriff:** Die Klassen sind komponentenspezifisch, wie schon bei der
  Feldausrichtung entschieden — `.zev-table td { vertical-align: top }` und ein Werte-Raster träfen
  jede Tabelle der Anwendung.
- **Tests:** Sieben Unit-Tests — zwei Wege zurück, oberer löst dieselbe Aktion aus, oberer bleibt
  bei abgeschlossener Abrechnung, „Position hinzufügen" fällt weg, und die Spaltenzuordnung der
  Mengeneinheit je Positionsart. Die Ausrichtung selbst ist eine Frage des Stylesheets und im jsdom
  nicht messbar; geprüft wird die Klasse, die sie herstellt.

### „Abbrechen" verwirft, statt die Maske zu verlassen

„Abbrechen" und „Zurück zur Übersicht" riefen beide `this.closed.emit()` — zwei Schaltflächen mit
identischem Verhalten, von denen eine ein Versprechen gab, das sie nicht hielt: Verworfen wurde
nichts, die Maske wurde bloss verlassen (FR-7, 27.08.2026).

- **`onAbbrechen()`** lädt jetzt über `ladeDetail(id, 'NK_AENDERUNGEN_VERWORFEN')` neu und bleibt in
  der Maske. `speichernVersucht` wird zurückgesetzt: Feldfehler eines gescheiterten
  Speicherversuchs gehören zu Eingaben, die es nicht mehr gibt.
- **Neue Abrechnung:** Ohne `abrechnungId` gibt es keinen Stand, auf den man zurückfallen könnte —
  dort schliesst „Abbrechen" die Maske wie bisher (Entscheid des Users). Derselbe Knopf hat damit
  je nach Zustand zwei Bedeutungen; die Alternative, eine leergeräumte Maske, überrascht beim
  Anlegen mehr als sie hilft.
- **`ladeDetail(id, erfolgsmeldung?)`**: Die Meldung erscheint **im Erfolgszweig**, nicht beim
  Aufrufer. Vor dem Ergebnis gezeigt, hätte ihr Fünf-Sekunden-Timer eine danach eintreffende
  Fehlermeldung mitgenommen.
- **Latenter Fehler mitkorrigiert:** `showMessage` löschte `this.message` nach fünf Sekunden
  **bedingungslos**. Eine Fehlermeldung, die nach einer Erfolgsmeldung eintraf, verschwand damit
  von selbst — entgegen der Konvention, dass Fehler bis zum Wegklicken stehen. Der Timer räumt
  jetzt nur ab, wenn noch seine eigene Meldung steht. Betrifft jede Meldung dieser Maske, nicht nur
  das Abbrechen.
- **Migration V127**: Schlüssel `NK_AENDERUNGEN_VERWORFEN` (DE/EN), `ON CONFLICT (key) DO NOTHING`.
  V126 war laut `flyway_schema_history` bereits ausgeführt, also eine neue Migration.
- **Tests:** Sieben neue Unit-Tests (Neuladen statt Verlassen, Änderungen verworfen, Meldung,
  Feldfehler weg, neue Abrechnung schliesst, Fehler beim Laden statt Erfolgsmeldung, Fehler
  überlebt den Timer). Der bestehende Test `should emit closed on cancel` hielt das alte Verhalten
  fest und ist zur Abgrenzung umgeschrieben: Von den beiden Schaltflächen verlässt allein „Zurück
  zur Übersicht" die Maske.
- **E2E unberührt:** Die Suite klickt „Abbrechen" nicht; sie greift den Weg zurück über
  `.zev-form-actions .zev-button--secondary').last()`.

### „Zurück zur Übersicht" speichert; Schaltflächen in beiden Bereichen gleich

Zwei Nachträge (FR-7, 27.08.2026). Damit sind die drei Schaltflächen klar getrennt: **Speichern**
sichert und bleibt, **Abbrechen** verwirft und bleibt, **Zurück zur Übersicht** sichert und geht.

**1. Speichern auf dem Weg hinaus.** `onSpeichern()` ist zu `speichereUnd(danach?)`
verallgemeinert; `onZurueckZurUebersicht()` ruft es mit `() => this.closed.emit()`.

- Der Rückruf läuft **nur nach erfolgreichem Speichern**. Bei ungültigen Eingaben oder einem
  Fehler des Servers bleibt die Maske stehen und zeigt den Grund — ein Verlassen würde genau die
  Eingaben verwerfen, die gerade gesichert werden sollten.
- `speichereDetail(id, danach?)` reicht den Rückruf durch; der Anlege-Pfad einer neuen Abrechnung
  (create, dann update) ebenso.
- Bei **abgeschlossener** Abrechnung führt der Weg direkt zurück: Die Felder sind gesperrt, und der
  Server wiese das Schreiben ab.
- **Kein Bestätigungstext nach dem Verlassen:** Die Erfolgsmeldung entsteht in der Maske, die
  danach schliesst; die Liste zeigt keine Meldung. Sichtbar ist das Ergebnis an der Zeile.

**2. Schaltflächen nicht über die Zeile gezogen.** Am Ende der Maske ist der Modifier
`zev-form-actions--equal` entfernt — er setzt `flex: 1` auf jeden Button
(`design-system/.../form.css:124`) und zog die drei über die ganze Breite, während die obere Zeile
(`zev-button-row`) das nicht tut.

- **Kein CSS geschrieben:** Beide Klassen kommen aus dem Design System, es fiel nur ein Modifier
  weg. `zev-form-actions` bleibt für den unteren Bereich richtig — es bringt den oberen Abstand mit,
  den die Aktionszeile am Formularende braucht.

**Tests:** Sechs neue Unit-Tests (speichert vor dem Verlassen, bleibt bei ungültiger Eingabe, bleibt
bei Serverfehler, verlässt ohne Speichern bei abgeschlossener Abrechnung, legt eine neue Abrechnung
vorher an, kein `--equal` an der Aktionszeile). Der bestehende Test `should emit closed on the way
back to the list` bestand nach der Änderung weiterhin — aber aus einem anderen Grund, weil der
Mock synchron speichert; er dokumentiert das Speichern nicht und wird deshalb von den neuen Tests
ergänzt, nicht ersetzt.

**E2E unberührt:** Die vier Stellen, die den Rückweg klicken, tun das auf einer gültigen oder auf
einer gesperrten Abrechnung.

**Nachtrag zur Bezeichnung:** Die Schaltfläche heisst neu **„Speichern und zurück"** (V128, per
`UPDATE zev.translation` nach dem Muster von V113/V114). Der Text war im Betrieb über den
Übersetzungs-Editor schon geändert; ohne die Migration bekäme eine frisch aufgesetzte Datenbank
weiterhin den alten aus V122. Der Schlüssel `NK_ZURUECK_UEBERSICHT` behält seinen Namen.

## Nachtrag: Umlage pro Person

Neue Positionsart `UMLAGE_PERSON` neben der unveränderten `UMLAGE`. Verteilt nach Köpfen statt nach
Wohnungen; Nenner ist `Anzahl Personen x Tage`, Zähler `Tage(i) x Personen(i)`.

**Leitgedanke der Umsetzung:** Mit den Vorgaben (Anzahl Personen = Anzahl Wohnungen, 1 Person je
Mieter) muss die neue Art **identisch** zur alten rechnen. Das macht die Erweiterung rückwärtssicher
und die neue Art ohne Vorbereitung benutzbar; ein Unit-Test hält genau das fest
(`umlagePerson_OhneErfassteZahlen_RechnetWieUmlageProWohnung`).

### Datenbank
* `V135__Nk_Umlage_Pro_Person.sql`
  * `nk_abrechnung.anzahl_personen` (nullable anlegen → aus `anzahl_wohnungen` befüllen → `NOT NULL`
    → `CHECK > 0`). Der Backfill ist der Grund, dass bestehende Abrechnungen ihre Beträge behalten.
  * `ck_nk_position_art` **und** `ck_nk_position_felder` neu erzeugt. Beide zählen erlaubte Werte
    auf — eine neue Enum-Konstante braucht deshalb DDL, sonst scheitert erst das Speichern zur
    Laufzeit. `UMLAGE_PERSON` teilt den Feldbedarf mit `UMLAGE` (`art IN (...)`).
  * `zev.nk_person` samt Sequenz und Index; `ON DELETE CASCADE` auf die Abrechnung,
    `ON DELETE RESTRICT` auf den Mieter wie bei den drei übrigen Nebenkosten-Tabellen.
* `V136__Add_Nk_Umlage_Pro_Person_Translations.sql` — sechs Keys, `ON CONFLICT (key) DO NOTHING`.
  `NK_ART_UMLAGE` bleibt „Umlage": die bestehende Art wird **nicht** umbenannt.
* V134 war die höchste angewendete Migration (via `zev-db` geprüft).

### Backend
* `NkPositionsart.UMLAGE_PERSON`, `NkAbrechnung.anzahlPersonen`, Entity `NkPerson` +
  `NkPersonRepository`, DTO `NkPersonDTO`.
* `NkBerechnungService.berechne(...)` bekommt `List<NkPerson>`. `UMLAGE` und `UMLAGE_PERSON` teilen
  sich **einen** `case`-Zweig — sie rechnen identisch, nur der Verteilschlüssel unterscheidet sich.
  `setzeAbweichungen` wählt die Bezugsgrösse jetzt über ein `switch` (Prozentsumme / Personenanteil /
  Zeitanteil).
* `NkMieterAbrechnungDTO` führt `anzahlPersonen` und `personenTage` mit, `NkBerechnungDTO`
  zusätzlich `nennerPerson` und `summePersonenTage` — damit sich ein Anteil nachrechnen lässt.
* `NkAbrechnungService`: `ergaenzeAnzahlPersonen` (fehlt der Wert, gilt die Anzahl Wohnungen) läuft
  **vor** `pruefeKopf`, sonst scheiterte ein Aufrufer an einer Meldung, deren Antwort die Anzahl
  Wohnungen ist. `pruefeNennerPerson` prüft `Σ (Tage x Personen) <= Nenner`, aber **nur** wenn eine
  Position dieser Art vorhanden ist. `ersetzePersonen` speichert nur Abweichungen von der Vorgabe.
* `MieterService`: `nkPersonRepository.countByMieterId` in den Löschschutz aufgenommen.
* **Kein `@NotNull`** auf `anzahlPersonen`: Es griffe schon in der Eingangsvalidierung und würde
  einen Rumpf ohne das Feld mit 400 abweisen, bevor der Service die Vorgabe setzen kann. Zwei
  Controller-Tests haben genau das aufgedeckt.

### Frontend
* Modell: Enum-Wert, `NkAbrechnung.anzahlPersonen`, `NkPerson`, `NK_POSITIONSARTEN`,
  `NkMieterAbrechnung.anzahlPersonen/personenTage`, `NkBerechnung.nennerPerson/summePersonenTage`,
  `NkAbrechnungDetail.personen/anzahlPersonenVorschlag`. `NK_ARTEN_OHNE_EINHEIT` **unverändert** —
  `UMLAGE_PERSON` braucht wie `UMLAGE` eine Mengeneinheit.
* `nebenkosten-berechnung.ts`: dieselbe Erweiterung wie im Backend, damit die Sofortvorschau nicht
  auseinanderläuft. Die zwei neuen Parameter haben Vorgabewerte, damit bestehende Aufrufe und Tests
  gültig bleiben.
* Komponente: `personen`-Liste, `personFuer(mieterId)` (Bauart wie `akontoFuer`), Getter
  `hatPersonenumlage`. `uebernehme` legt für jeden Mieterblock einen Eintrag an — der Server
  speichert nur Abweichungen, `ngModel` braucht aber überall ein Objekt.
* Template: Beide Nenner-Felder in einer `.zev-form-row`; das Feld je Mieter nur bei
  `hatPersonenumlage`. **Kein neues CSS** — `.zev-form-row`, `.zev-form-group`, `.zev-form-hint`
  und `.zev-form-error` genügen.

### Tests
* `NkBerechnungServiceTest`: 54 (8 neu, u.a. Gleichheit mit der Wohnungsumlage, Verteilung nach
  Köpfen, Personen je Wohnung bei zwei Wohnungen, unverteilter Anteil, Nenner 0).
* `NkAbrechnungServiceTest`: 59 (5 neu, u.a. Nennerprüfung mit und ohne Personenumlage, keine Zeile
  bei der Vorgabe, Ergänzung der fehlenden Anzahl).
* Alle 34 `berechne(...)`-Aufrufe der Testsuite um das neue Argument erweitert — per Skript über die
  geklammerten Argumentgrenzen, nicht per Textsuche.
* Frontend: `nebenkosten-berechnung.spec.ts` 41 (6 neu), Formular-Spec 124 (10 neu), Fixtures der
  drei betroffenen Specs um die neuen Felder ergänzt.
* Gesamt: 1249 Backend, 1602 Frontend.

### Nachgereicht: zweiter switch über die Positionsart

Der erste Wurf war beim Speichern mit `NK_FEHLER_POSITION_ART` gescheitert. Ursache: `pruefePositionen`
in `NkAbrechnungService` hat einen **eigenen** `switch` über die Art, dessen `default`-Zweig genau
diese Meldung wirft. `UMLAGE_PERSON` fiel dort hinein — das Rechnen stimmte, das Speichern wurde
abgewiesen.

Übersehen wurde er, weil die Suche nach `NkPositionsart.` nicht greift: Ein `switch` benutzt **bare
case labels** (`case UMLAGE ->`). Wer die Art erweitert, muss nach `switch` **und** nach `case`
suchen — es gibt drei solche Stellen im Backend (Rechnen, Kontrollzahlen, Validierung) und eine im
Frontend.

Warum die neuen Tests es nicht gezeigt haben: Der einzige Test mit einer `UMLAGE_PERSON`-Position
erwartete eine Ausnahme aus der **Nennerprüfung** — und die läuft vor `pruefePositionen`, der Zweig
wurde also nie erreicht. Nachgezogen sind deshalb zwei Fälle, die den Pfad wirklich durchlaufen
(`saveAbrechnung_UmlageProPerson_WirdGespeichert`, `..._OhneTotalbetrag_ThrowsException`); beide
schlagen ohne den Fix mit `NK_FEHLER_POSITION_ART` fehl. Backend jetzt 1251 Tests.

### Nachgereicht: E2E-Helfer wählte die Positionsart über einen Index

Die E2E-Suite meldete **einen** Fehler — im Test für die **ANTEIL**-Position, die von dieser
Erweiterung gar nicht betroffen ist: erwartet `500.00`, erhalten `50'000.00` bei Einheit `m³`.

Ursache: `fuegePositionHinzu` in `tests/nebenkosten-abrechnung.spec.ts` wählte die Art über eine
hartcodierte Indextabelle (`{ UMLAGE: 0, VERBRAUCH: 1, ANTEIL: 2, ZUSCHLAG: 3 }`) in die Reihenfolge
von `NK_POSITIONSARTEN`. `UMLAGE_PERSON` steht dort auf Platz 1 — damit rutschte alles dahinter eins
weiter, und der ANTEIL-Test wählte fortan **VERBRAUCH**. Die 1000 landete als „Betrag pro Einheit",
die 50 als Menge: 50'000.00, mit der Standardeinheit `m³`. Der Fehler zeigte sich also an einem
Betrag, weit weg von seiner Ursache.

Nur ein Test schlug fehl, weil die Datei `mode: 'serial'` fährt: Nach dem Fehlschlag lief der Rest
der Gruppe nicht mehr (die acht „did not run" des Berichts). Die Tests für VERBRAUCH und ZUSCHLAG
hätten es genauso getroffen.

Behoben mit `waehleArt(select, art)`: Der technische Wert (`0: UMLAGE`) wird zur Laufzeit aus den
Optionen gelesen und über den **Namen** hinter dem Doppelpunkt gesucht — die Position ist
gleichgültig, eine weitere Art bricht den Helfer nicht mehr. 18 von 18 Tests der Datei grün.

**Dieselbe Falle steht noch in `tests/tarifpositionen.spec.ts`** (`EINHEIT_INDEX` für die
Mengeneinheit). Aktuell harmlos, weil `Mengeneinheit` unverändert ist — aber die nächste neue
Einheit bricht sie auf dieselbe Weise.

### Nachtrag: Spaltenbreite der Verteilart

Die Auswahl schnitt „Umlage pro Wohnung" ab. Ursache ist nicht die neue Art, sondern
`.zev-select { width: 100% }`: Ein Auswahlfeld hat damit keine Mindestbreite aus seinem Inhalt, und
bei automatischem Tabellenlayout fällt die Spalte auf die Breite ihrer Überschrift („Art")
zusammen. Vorher fiel es nicht auf, weil „Umlage" und „Verbrauch" kurz genug waren.

* `.nk-positionen__art-spalte { width: 13rem }` in der Komponenten-CSS, Klasse am `<th>`.
  Maßgebend ist nicht der deutsche Text („Umlage pro Wohnung", ~10rem), sondern der englische
  („Allocation per apartment", ~12.2rem) — samt Innenabstand und Aufklapp-Symbol.
* **Nicht** ins Design System: Das ist die Geometrie *dieser* Tabelle. Dieselbe Überlegung wie bei
  `.nk-positionen__griff-spalte`; die Tabellen-Komponente kennt nur `zev-table__checkbox-col`
  (2.5rem) und `zev-table__number`, beide passen hier nicht.
* `V138__Nk_Art_Umlage_Pro_Wohnung.sql` zieht die Beschriftung nach: Der Text war im Betrieb über
  den Übersetzungs-Editor schon auf „Umlage pro Wohnung" geändert, stand aber nur in **einer**
  Datenbank. Ohne die Migration bekäme eine frisch aufgesetzte weiterhin „Umlage" aus V120 — und
  die Spaltenbreite wäre dort auf einen Text ausgelegt, den es nicht gibt. Gleiches Muster wie
  V128. Beide `UPDATE`s prüfen den alten Wert, greifen auf der bestehenden Datenbank also ins Leere.
  Englisch mitgezogen: Neben „Allocation per person" verliert das blosse „Allocation" genau die
  Unterscheidung, um die es geht.

### Nachgereicht: E2E-Tests — und ein Fehler, den sie aufgedeckt haben

Drei Fälle in `tests/nebenkosten-abrechnung.spec.ts`:

1. *should hide the persons field until a per-person allocation exists* — das Feld je Mieter
   erscheint erst mit einer Position dieser Art, mit der Vorgabe 1.
2. *should distribute a per-person allocation like a per-apartment one by default* — beide Arten mit
   demselben Totalbetrag in **einer** Abrechnung; die verteilten Summen müssen gleich sein, vor und
   nach dem Speichern. Bewusst ein Vergleich zweier Zahlen derselben Abrechnung statt einer Zahl
   gegen eine Erwartung: Damit ist der Test unabhängig davon, wie viele Wohnungen und Mieter die
   Umgebung kennt — und er prüft zugleich, dass Vorschau und Backend übereinstimmen.
3. *should raise a tenant share with more persons and leave the apartment share alone* — mehr Köpfe
   heben den Anteil dieses Mieters an der Personenumlage, der an der Wohnungsumlage bleibt.

Selektoren: das Personenfeld über das `id`-Präfix (`input[id^="personen"]`) und nicht über die
Beschriftung — der Text kommt aus der Datenbank und lässt sich im Editor ändern.

**Fall 2 schlug beim ersten Lauf fehl** — und zwar zu Recht: Wohnungsumlage 90.90 verteilt,
Personenumlage 999.99. Ursache war ein Fehler gegen die Vorgabe „Default = Anzahl Wohnungen": Die
Maske übernahm den **Vorschlag des Servers** (Zahl der nebenkostenrelevanten Einheiten, hier 9) und
nicht den **Wert des Feldes** (im Test bewusst 99, damit etwas unverteilt bleibt). Damit liefen zwei
verschiedene Nenner, und eine Personenumlage verteilte anders als eine Wohnungsumlage, obwohl noch
keine Personenzahl erfasst war.

Behoben mit `onAnzahlWohnungenChange()` / `onAnzahlPersonenChange()` und dem Merker
`personenFolgtWohnungen`: In einer neuen Abrechnung zieht die Anzahl Personen nach, bis sie von Hand
gesetzt wird; eine gespeicherte Abrechnung trägt ihre eigene Zahl und folgt nicht mehr. Drei
Unit-Tests halten die drei Fälle fest (Frontend 1605).

**Bemerkenswert:** Der Fehler war mit Unit-Tests nicht zu sehen — dort war „Personen = Wohnungen"
in jedem Fixture ohnehin erfüllt. Erst eine Umgebung, in der die beiden Zahlen auseinanderliegen,
macht ihn sichtbar. Genau dafür ist der E2E-Test da.

### Offen
* **E2E-Verifikation der Korrektur:** Die drei Fälle laufen gegen den Container; die Korrektur
  liegt nur im Arbeitsverzeichnis. Nach einem Frontend-Rebuild ist der Lauf zu wiederholen. Sinnvoll wäre ein Fall, der eine Position „Umlage pro Person"
  anlegt, bei zwei Mietern unterschiedliche Personenzahlen erfasst und die Beträge prüft. Braucht
  einen Stack-Rebuild.

## Nachtrag: Abrechnung kopieren (FR-8)

### Backend
* `NkAbrechnungService.kopiereAbrechnung(id, bezeichnung)` — eine Transaktion: Kopf speichern, dann
  `kopierePositionen` / `kopiereZusaetze` / `kopiereAkonto` / `kopierePersonen`. Bewusst **ohne**
  `pruefeNichtAbgerechnet`: Die abgeschlossene Abrechnung des Vorjahres ist der Hauptfall. Die Kopie
  ist immer `abgerechnet = false`.
* Die erfassten Mengen hängen an der **Position**, nicht an der Abrechnung — sie müssen auf die neue
  Positions-ID zeigen, sonst gehörten sie weiterhin zum Original. Ein Test hält genau das fest.
* Serverseitig gekürzte Bezeichnung (`VARCHAR(150)`); ohne Kürzung scheiterte das Speichern erst in
  der Datenbank, mit einer Meldung, die niemandem hilft.
* `POST /{id}/kopie` mit optionalem Parameter `bezeichnung`, Antwort `201` und das vollständige
  Detail — die Maske kann damit direkt öffnen, ohne zweiten Aufruf.

### Mieter ausserhalb des Zeitraums
`saveAbrechnung` bildet aus `ladeMieter` die Menge der Mieter im Zeitraum und gibt sie an alle vier
`ersetze`-Methoden. Die löschen ohnehin alles und schreiben neu — was durch den Filter fällt, ist
damit weg. Umgesetzt im **Backend** und nicht in der Maske: Der Server ist massgebend, und die Regel
gilt damit unabhängig davon, was der Aufrufer schickt.

**Sechs bestehende Tests fielen dadurch durch** — und zu Recht: Sie richteten überhaupt keine Mieter
ein (`mieterRepository.findByZeitraumOverlapping` unstubbed → leere Liste) und speicherten Angaben
für frei gewählte Mieter-IDs wie `7L`. Angepasst auf die eingerichteten Mieter; der Filter selbst
blieb unverändert.

### Frontend
* Kebab-Eintrag `NK_KOPIEREN` mit Icon `copy` in **beiden** Menüs.
* `onKopieren` ruft den Endpunkt, setzt `selectedId` auf die neue ID und öffnet die Maske.
* `bezeichnungDerKopie` kürzt den **Namen**, nicht den Zusatz.
* Hinweis `NK_HINWEIS_MIETER_AUSSERHALB` bei den Angaben zum Zeitraum — die Folge muss dastehen,
  bevor gespeichert wird, nicht danach.
* Kein neues CSS: `.zev-form-hint` und die Kebab-Komponente genügen.

### i18n
`V139__Add_Nk_Abrechnung_Kopieren_Translations.sql` — fünf Keys, `ON CONFLICT (key) DO NOTHING`,
deutsche Texte mit Umlauten.

### Tests
* `NkAbrechnungServiceTest` 70 (8 neu: nicht vorhanden, Feature-Flag, Kopf inkl. „Kopie ist offen",
  Bezeichnung ohne Angabe, Kürzung, Positionen mit Mengen auf der neuen ID, Zusatz/Akonto/Personen,
  zwei Fälle zum Zeitraum-Filter).
* `NkAbrechnungControllerTest` 26 (4 neu: 201, optionaler Parameter, 404, 400).
* Frontend: Service-Spec (Parameter statt Rumpf), Listen-Spec (6 neu), Menü-Erwartungen angepasst.
* E2E: zwei Fälle — vollständige Kopie samt Umlage und Akonto, sowie das Verschwinden der Angaben
  nach dem Verschieben des Zeitraums.
  * Die Kopie wird im Test **umbenannt**, bevor sie stehen bleibt: Ihre Bezeichnung enthält die des
    Originals, und die Aufräumsuche `tr:has-text(...)` träfe beim Löschen beide Zeilen — der Lauf
    hätte Testdaten hinterlassen. Nebenbei prüft das den Speichervorgang auf der Kopie.
* Gesamt: 1260 Backend, 1613 Frontend.

### Zwei Testfunde aus dem E2E-Lauf

**Ein bestehender Test brach an der Menülänge.** `should offer the invoice run only on a closed
billing` prüfte `eintraege.length === 3` und `eintraege[2]` — mit „Kopieren" sind es vier, und der
gefährliche Eintrag steht auf Index 3. Der Test scheiterte damit an der *Anzahl* statt an seiner
Aussage („der gefährliche bleibt unten"). Neu geprüft über die **letzte** Position; ein weiterer
Eintrag im Menü bricht ihn nicht mehr. Dieselbe Sorte Brüchigkeit wie der frühere `ART_INDEX`.

**Der Kopier-Test war flaky.** `#bezeichnung` ist sichtbar, bevor die Maske ihr Detail geladen hat —
`inputValue()` las dann den leeren String. Behoben mit `toHaveValue(/^…/)`, das auf den **Wert**
wartet statt auf das Feld. Genau die Falle, die `oeffneNeueAbrechnung` im Kopf der Datei schon
dokumentiert; sie trifft jeden, der direkt nach dem Öffnen liest.

E2E nach dem Rebuild: 23 von 23 Fällen dieser Datei grün, Gesamtsuite 480 passed, 0 failed. Der
verbleibende Flake (`lizenzen.spec.ts`, Firefox, Suche) hat mit dieser Arbeit nichts zu tun.

## Nachtrag: Spaltenbreiten der Positionstabelle

* `.nk-positionen__bezeichnung-spalte { width: 100% }` am `<th>`. Bei automatischem Tabellenlayout
  heisst das nicht „die ganze Tabelle", sondern „so viel wie übrig ist": Die übrigen Spalten behalten
  ihre Breite, diese eine absorbiert den Rest. Ohne das verteilte der Browser die freie Breite
  gleichmässig, und das Feld blieb schmaler als sein möglicher Inhalt (150 Zeichen).
* `.nk-positionen__werte`: `repeat(3, 10rem)` → `repeat(3, 8rem)`.
* **Die Untergrenze setzen die Beschriftungen, nicht die Werte.** Gemessen an `--font-size-sm`
  (13px): „Betrag pro Einheit" rund 7.1rem, „Unit of measure" 5.9rem, „Mengeneinheit" 5.1rem — die
  Zahlen selbst brauchen weit weniger. Unter etwa 7.5rem bricht die längste Titelzeile um, und dann
  sitzen die Eingaben benachbarter Spalten nicht mehr auf einer Linie: genau der Fehler, den die
  Titelzeilen-Konstruktion (`nk-positionen__feld`) vermeidet. 8rem lässt rund 15px Reserve.
* Kein Design-System-Anteil: Spaltengeometrie **dieser** Tabelle, wie `griff-spalte` und
  `art-spalte`. Die Tabellen-Komponente kennt nur `zev-table__checkbox-col` und
  `zev-table__number`.
* Keine Migration, keine neuen Texte, keine Testanpassung — die E2E-Selektoren gehen über
  `.nk-positionen tbody tr` und Feldtypen, nicht über Spaltenbreiten.

**Nachgereicht: die Art-Spalte fiel dabei zusammen.** `width: 100%` an der Bezeichnung nimmt sich
alles und drückt die Nachbarn auf ihre **Minimalbreite**. Bei der Art ist die fast null, weil
`.zev-select` selbst `width: 100%` ist und aus seinem Inhalt keine Mindestbreite mitbringt — das
`width: 13rem` von gestern war nur eine Präferenz und wurde überstimmt. Behoben mit zusätzlichem
`min-width: 13rem`: Das ist die Untergrenze, die der Algorithmus einhalten muss.

Die Wertspalten waren nicht betroffen, und das bestätigt die Erklärung: Ihr Inhalt ist ein Grid mit
**festen** Spurbreiten (`repeat(3, 8rem)`) und hat damit eine echte Mindestbreite. Wo eine Zelle
ihre Breite aus dem Inhalt bezieht, greift der Effekt nicht.

## Nachtrag: E2E-Lückenschluss

Abgleich der Akzeptanzkriterien gegen die 23 bestehenden Fälle. Aufgenommen wurde, was **nur** E2E
zeigen kann — Arithmetik (Rundung, Kaskade, Zeitanteil) ist Sache der Unit-Tests und bleibt dort.

| Neuer Fall | Deckt ab |
|---|---|
| `should hide amount and unit on a surcharge and cascade on the line above` | ZUSCHLAG: nur Prozentsatz erfassbar, Betrag = 10 % der Zeile davor |
| `should reorder positions by drag and drop and change the cascade` | Drag & Drop, neue `reihenfolge`, geänderte Kaskade — vor **und** nach dem Speichern |
| `should reject saving when the number of apartments is too small` | `Σ Tage(i) > Nenner` wird abgewiesen, Meldung nennt die Miettage |
| `should add and remove an additional item of a tenant` | Zusatzposition hinzufügen, Betrag `2 × 25.00`, einzeln entfernen, nach dem Speichern weg |
| `should recalculate the allocation when the number of apartments changes` | Nenner-Änderung wirkt sofort, ohne Speichern |

**Der Zuschlag-Fall ist bewusst umgebungsunabhängig gebaut:** Geprüft wird nicht ein absoluter
Betrag, sondern das Verhältnis zur Umlagezeile derselben Abrechnung — 10 % davon. Damit ist der Test
unabhängig davon, wie viele Wohnungen und Mieter die Umgebung kennt. Dieselbe Überlegung wie beim
Vergleich Wohnungs- gegen Personenumlage.

**Beim Umordnen wird der Zuschlag nach oben gezogen**, nicht die Umlage nach unten: Steht keine Zeile
mehr vor ihm, rechnet er auf 0 — ein exakt prüfbarer Wert statt „irgendwie anders".

**Drag & Drop** braucht mehrere Mausbewegungen (`ziehePositionNachOben`): Ein einzelnes `move`
unterschreitet die Schwelle, ab der Angular CDK das Ziehen erkennt, und die Vorschau braucht Frames.
Dreimal wiederholt und in zwei vollen Läufen stabil.

**Nicht aufgenommen** und warum:
* *Ein Mieter in einer Abrechnung ist nicht löschbar* — würde die Sperre erzeugen, die dieser Suite
  schon Aufräumläufe gekostet hat (`ON DELETE RESTRICT` auf vier Tabellen). Backend-Tests deckt es ab.
* *Leerstate ohne Abrechnungen* — setzte voraus, alle Abrechnungen des Mandanten zu löschen.
* *Rundung, Zeitanteil, Kaskadenarithmetik* — Unit-Tests, dort ohne Stack und ohne Flake-Risiko.

Stand: 28 Fälle in dieser Datei, zwei volle Läufe grün.

## Nachtrag: Summe der Mietertotale

* Getter `summeMietertotal` in der Maske — abgeleitet, nicht zwischengespeichert: Eine Änderung an
  einer Position muss sie sofort mitnehmen. Gerundet mit `runde(…, 2)`, weil die Zeilenbeträge
  Gleitkommazahlen sind; ohne das stünde dort schon mal `1234.5600000000002`.
* Kein neues CSS-Muster: `.nk-total` gab es schon für die Totale **innerhalb** eines Mieterblocks
  (rechtsbündig, abgesetzt, halbfett). Neu ist nur der Modifier `.nk-total--mieter` mit Abstand nach
  oben — die Zeile steht zwischen zwei Panels, während die Totale im Block direkt an ihrer Tabelle
  hängen.
* `V140__Add_Nk_Summe_Kostentotal_Translation.sql`: ein Key, `NK_SUMME_KOSTENTOTAL` =
  „Kostentotal aller Mieter". Die Beschriftung spiegelt bewusst `NK_KOSTENTOTAL` der Mieterzeile —
  dieselbe Grösse, nur über alle Mieter summiert.

### Ortswahl
Gefragt war „oberhalb der Mieter, ganz rechts", mit der Rückfrage nach einem besseren Ort. Geprüft
und verworfen:

* **Als Fusszeile der Kontrollzahlen-Tabelle:** Deren Spalten bedeuten etwas *je Position*
  („Totalbetrag", „Summe verteilt"). Eine Zeile, deren Zelle eine andere Grösse trägt, führte in die
  Irre — die Summe der Mietertotale enthält auch Verbrauch, Zuschläge und Zusatzpositionen.
* **Hinter den Mieterblöcken:** Sie starten zugeklappt und sind bei dreissig Mietern mehrere
  Bildschirmseiten lang. Die Zahl fände dort niemand.
* **Im Kopf der Abrechnung:** Dort stehen Eingaben, nicht Ergebnisse.

Der gewünschte Ort ist also auch der beste: Er stellt die Zahl neben ihren Vergleichspartner.

### Tests
* Vier Unit-Tests (leer, Summenbildung, Rundung von Gleitkomma-Resten, folgt der Neuberechnung) —
  Maske 131 Tests.
* Ein E2E-Fall: Mit einer einzigen Umlage als einziger Kostenposition muss die Summe genau der
  verteilten Summe dieser Position entsprechen — exakt und unabhängig von der Umgebung. Geprüft wird
  auch, dass sie einer Betragsänderung ohne Speichern folgt.
* **Der E2E-Fall braucht einen Frontend-Rebuild**; gegen den alten Container fehlt
  `.nk-total--mieter` schlicht im DOM.

## Nachtrag: Mieter ohne nebenkostenrelevante Wohnung weglassen (FR-9)

Eine Zeile in `NkAbrechnungService.ladeMieter`: Wer keine gekennzeichnete `CONSUMER`-Einheit hat,
kommt nicht in die Liste. Die Zählung dafür gab es bereits — sie speist auch `Tage(i)`.

**Wirkt an beiden Stellen zugleich.** Der Rechnungslauf iteriert `detail.getBerechnung().getMieter()`
(`NkRechnungService`), also dieselbe Liste. Ein Filter im Laden genügt; es braucht keine zweite
Regel im Rechnungspfad, die man später auseinanderlaufen lassen könnte.

**Was ich vor der Umsetzung geprüft habe:** Die Spec hielt ausdrücklich das Gegenteil fest — „er
trägt keinen Umlageanteil, seine Verbrauchs- und Zusatzpositionen und sein Akonto rechnen aber normal
weiter". Der Satz ist ersetzt, nicht ergänzt. Der Verlust ist real, aber theoretisch: Der betroffene
Mieter ist in der Praxis der Eigentümer mit dem Allgemeinstrom-Messpunkt; Ladestrom läuft über
Tarifpositionen und nicht über die Nebenkosten. Wer einen solchen Mieter doch braucht, setzt das
Kennzeichen an einer seiner Einheiten — ein Hebel, der schon existiert.

**Der Nenner bleibt unberührt:** Er kommt aus dem erfassten Feld, nicht aus den Mietern. Der Test
prüft das mit (`3285` bleibt `3285`).

* `ohneWohnung` bleibt im **reinen** `NkBerechnungService`: Der Service ist ohne Datenbank prüfbar,
  der Fall ist dort mit Unit-Tests belegt, und die Verteidigung kostet nichts.
* **Der Hinweis `NK_HINWEIS_OHNE_WOHNUNG` in der Maske wurde dadurch unerreichbar** und ist
  entfernt — samt `hinweisOhneWohnungSichtbar`, `dismissHinweisOhneWohnung`, dem Set
  `hinweisOhneWohnungAusgeblendet` und dem zugehörigen Unit-Test.
* **Nicht** entfernt: das Feld `ohneWohnung` in `NkMieterAbrechnung` (Frontend) und
  `NkMieterAbrechnungDTO` (Backend). Das Modell spiegelt das DTO, und das DTO trägt den Wert
  weiterhin — es wird nur nicht mehr angezeigt. Ein Kommentar am Modellfeld sagt das.
* Der Übersetzungs-Schlüssel `NK_HINWEIS_OHNE_WOHNUNG` bleibt in der Datenbank stehen: unbenutzt,
  aber harmlos. Wer ihn los will, nimmt den Weg über `Specs/DeleteTranslations.md`.
* Tests: drei Fälle in `NkAbrechnungServiceTest` (keine Einheit, nur abgewähltes Kennzeichen, nur
  Einheit falschen Typs); der bestehende Fall zum Kennzeichen ist umbenannt und prüft jetzt die
  Abwesenheit statt „0 Tage". Backend 1266 grün.
* Keine Migration, kein Frontend-Code, kein CSS.

## Nachtrag: Zusammenstellung der Positionen (FR-10)

Die Tabelle zwischen den allgemeinen Positionen und den Mietern führt neu **jede** Positionsart auf,
zeigt je Position die Summe der Mengen und der Kosten und darunter die Gesamtsumme.

### Der Kern: die Identität mit dem Kostentotal
Gefordert war „diese muss mit dem Kostentotal aller Mieter übereinstimmen". Das gilt nur, wenn die
Übersicht **beide** Quellen der Mieterzeilen kennt: `kostentotal` summiert Positionen **und**
Zusatzpositionen (`zeilenQuellen` in `berechneMieter`). Die alte Tabelle kannte nur die verteilenden
Positionsarten — ihr fehlten `VERBRAUCH`, `ZUSCHLAG` und alle Zusatzpositionen.

Deshalb: eine Zeile je Position für alle fünf Arten, plus **eine** Sammelzeile für die
Zusatzpositionen. Eine Zeile je Zusatzposition wäre die Mieterliste ein zweites Mal — dieselbe
Bezeichnung kommt bei mehreren Mietern vor, und die Übersicht soll Positionen zeigen, nicht Mieter.

Die Identität gilt damit **per Konstruktion**, und je ein Test hält sie fest (Backend und Vorschau):
Beide summieren `kostentotal` über die Mieterblöcke und vergleichen mit `summeKosten` — bewusst mit
allen fünf Arten plus Zusatzposition im Aufbau. Zusätzlich zeigt die Maske eine Abweichung
**an** (`summenWeichenAb`), statt zwei Zahlen nebeneinander stehen zu lassen.

### Umbenennungen
Die Liste enthält jetzt auch `ZUSCHLAG`-Zeilen und eine Zusatz-Sammelzeile — `umlagen` wäre als Name
aktiv falsch und würde den nächsten Leser in die Irre führen. Deshalb durchgehend umbenannt:

| alt | neu |
|---|---|
| `NkUmlageInfoDTO` / `NkUmlageInfo` | `NkPositionSummeDTO` / `NkPositionSumme` |
| `NkBerechnung.umlagen` | `NkBerechnung.positionSummen` |
| `summeVerteilt` | `summeKosten` |
| `umlageInfoFuer` | `positionSummeFuer` |

Neu dazu: `summeMenge`, `einheit`, `zusatz` je Zeile und `summeKosten` am Ergebnis.

### Leer statt Null
Die Felder sind **`null`-bar** und nicht mit `0` vorbelegt. Ein Totalbetrag von `0.00` bei einer
Verbrauchsposition sähe aus wie ein vergessener Wert, obwohl die Art gar keinen kennt — dieselbe
Überlegung, aus der die Maske nicht zutreffende Eingabefelder ausblendet statt sie zu sperren. Dafür
gibt es `betragOderLeer()` neben dem bestehenden `betrag()`: Bei einem Total ist `null` → `0.00`
richtig (nichts verteilt ist null Franken), bei einer Art ohne Gesamtbetrag ist es falsch.

Aus demselben Grund lässt `merke()` eine **nicht erfasste** Menge unangetastet: Sonst stünde bei
einer Verbrauchsposition, für die noch niemand etwas eingetragen hat, eine `0` — und die sähe aus
wie eine gemessene Null.

### Gemischte Einheiten in der Sammelzeile
Zusatzpositionen verschiedener Mieter können verschiedene Mengeneinheiten tragen. `zusatzZeile()`
verfolgt die Einheit mit und lässt Menge **und** Einheit leer, sobald eine zweite auftaucht: „2 Stück
plus 3 m³" ist keine Menge, sondern zwei. Die Kosten bleiben summierbar, denn Franken sind Franken.
Innerhalb **einer** Position stellt sich die Frage nicht — sie hat genau eine Einheit.

### i18n — und ein eigener Regelbruch
Ich habe `NK_SUMME_KOSTEN_ABWEICHUNG` in Ersatzschreibung erfasst („zaehlen", „Zeilenbetraege",
„ueberein", „Uebersicht") — gegen die Regel, die ich in derselben Session in `generell.md` und die
Commands geschrieben hatte. Der User hat es korrigiert.

Ein anschliessender Abgleich **aller** Migrationen gegen die Regel fand fünf weitere Verstösse in
altem Bestand (V14, V17, V120): `STATISTIK_UEBERSICHT`, `ZEITRAUM_WAEHLEN`,
`WAEHLEN_SIE_EINEN_ZEITRAUM`, `ALLE_AUSWAEHLEN`, `NK_POSITION_HINZUFUEGEN`. Nachgezogen in
`V142__Uebersetzungen_Mit_Umlauten_Bestand.sql`.

**Die laufende Datenbank war sauber** — deshalb wäre der Fehler bei einer Prüfung gegen
`zev.translation` unentdeckt geblieben. Die Texte waren über die Übersetzungsverwaltung angepasst,
und das wirkt in genau einer Datenbank; eine frisch aufgesetzte bekäme weiterhin
„Statistik-Uebersicht". Dieselbe Lücke wie bei V128 und V138 — beim dritten Mal ist sie eine Regel
wert, und sie steht jetzt in `generell.md`: Geprüft wird gegen den letzten in einer Migration
deklarierten Wert je Key, nicht gegen die Datenbank.

Ein Suchmuster-Fehlalarm zum Merken: „ausschliesslich" enthält `schliess` und ist **korrekt**
(Schweizer `ss` statt `ß`). Wer nach Ersatzschreibung sucht, darf `ss`-Wörter nicht mitfangen.

`V141__Add_Nk_Positionsuebersicht_Translations.sql`: `NK_POSITION` (Spaltentitel, „Umlage" wäre jetzt
falsch), `NK_SUMME_KOSTEN`, `NK_ZUSATZPOSITIONEN` (Anzeigetext der Sammelzeile — sie kommt aus der
Maske, nicht vom Server, wie beim Zusatz „(Kopie)"), `NK_SUMME_KOSTEN_ABWEICHUNG` (Hinweis).
`NK_SUMME_VERTEILT` und `NK_UMLAGE` werden nicht mehr verwendet; die Schlüssel bleiben in der
Datenbank stehen (unbenutzt, aber harmlos — Weg dafür in `Specs/DeleteTranslations.md`).

### Kein CSS
`.nk-kontrolle`, `.zev-table` und `.number` genügen; `<tfoot>` ist Standard-HTML und erbt die
Tabellenformatierung. `.zev-text--danger` gab es schon für die Prozent-Abweichung.

### Tests
* `NkBerechnungServiceTest` 63 (9 neu): Identität mit dem Kostentotal, alle Arten plus Sammelzeile,
  keine Sammelzeile ohne Zusatzpositionen, Mengen bei Umlage und Verbrauch, leere Menge ohne
  Erfassung, Zuschlag/Anteil ohne Menge, gleiche und gemischte Einheiten.
* Vorschau-Spec 51 (10 neu) — dieselben Fälle, damit die beiden Seiten nicht auseinanderlaufen.
* Komponenten-Spec 138 (7 neu): `summenWeichenAb` und `betragOderLeer`.
* Gesamt: 1275 Backend, 1632 Frontend.

### Offen
* **E2E:** Der neue Fall `should sum quantities and costs per position and match the tenant total`
  prüft beide Mengenspalten, die Sammelzeile und die Identität — vor und nach dem Speichern. Er
  **scheitert gegen den laufenden Container**, weil dort die Verbrauchszeile in der Übersicht noch
  gar nicht existiert („element(s) not found"). Dasselbe gilt für die bestehenden Fälle, die
  `verteiltFuer` nutzen: Die Kostenspalte liegt jetzt an Index 4 statt 2. Die 17 Fälle, die die
  Übersicht nicht anfassen, sind grün geprüft. Nach einem Rebuild von Backend und Frontend ist der
  Lauf zu wiederholen.

## Nachtrag: Summe der Akontozahlungen und rechtsbündige Beträge

### Akonto total aller Mieter
Getter `summeAkontototal`, gleiche Bauart wie `summeMietertotal` (abgeleitet, gerundet). Steht direkt
unter der Kostensumme, weil die **Differenz** der beiden die Summe der Salden ist — was insgesamt
nachzuzahlen oder gutzuschreiben ist. Ein Unit-Test hält genau diese Beziehung fest, statt nur die
Summenbildung zu prüfen. `V143` bringt die Beschriftung.

### `class="number"` war app-weit toter Code
Die Beträge standen links, weil `.number` **nirgends** definiert ist — nicht im Design System, nicht
in der Komponente. Die Klasse tat schlicht nichts. Richtig ist `.zev-table__number`, die genau dafür
in der Tabellen-Komponente liegt und mit `th`/`td` qualifiziert ist, um die linksbündige Grundregel
`.zev-table th, .zev-table td` zu schlagen (der Kommentar dort erklärt es).

21 Tabellenzellen der NK-Maske umgestellt. **Kein neues CSS** für die Tabellen.

Die vier `<span class="number">` in den Totalzeilen bleiben: Dort sind es Flex-Container, und
`.zev-table__number` gilt für Tabellenzellen. Für sie gibt es jetzt eine **echte** Regel in der
Komponente — `min-width` plus `text-align: right`. Rechtsbündig war durch `justify-content: flex-end`
schon gegeben (die rechte Kante sass korrekt), die Mindestbreite richtet die **linke** aus. Erst
damit stehen die Beträge der beiden Totalzeilen als Kolonne untereinander.

**Korrektur einer eigenen Fehlmeldung:** Ich hatte gemeldet, `tarif-list.component.html` trage
„dieselbe tote Klasse". Das war falsch — dort **definiert die Komponente `.number` selbst**
(`text-align: right` plus `font-family: monospace`), die Ausrichtung funktionierte also. Dieselbe
Klassenbezeichnung, gegenteiliger Befund: In der NK-Maske war sie nirgends definiert.

Nachgezogen wurde die Tarifliste trotzdem, aber aus einem anderen Grund (s. eigener Nachtrag
unten): Die Kopfzeile der Preisspalte stand links, während die Zahlen darunter rechts standen.

### Tests
* Komponenten-Spec 142 (4 neu): leer, Summenbildung, Rundung, und die Beziehung
  „Kosten − Akonto = Σ Salden".
* E2E: zwei Fälle — die Akonto-Zeile mit Prüfung gegen den Saldo des Mieterblocks, und die
  Rechtsbündigkeit. Letztere prüft `toHaveCSS('text-align', 'right')`, also die **Wirkung**: Ein
  Klassenname allein sagt nichts darüber, ob eine Regel greift — genau das war der Fehler.
* Frontend 1636 grün; die 17 E2E-Fälle ohne Bezug zur Übersicht ebenfalls.

### Offen
* Die neuen E2E-Fälle brauchen den Rebuild — wie die Positionsübersicht aus FR-10.

## Nachtrag: Preisspalte der Tarifliste

Anlass war meine Fehlmeldung, die Tarifliste trage die tote `.number`-Klasse (s. oben). Beim
Nachziehen zeigte sich der echte Mangel: Die **Kopfzeile** „PREIS (CHF)" stand linksbündig, die
Zahlen darunter rechts.

* `<th>` der Preisspalte trägt jetzt `zev-table__number` mit — Titel und Kolonne fluchten.
* `<td>` von der lokalen `.number` auf `zev-table__number` umgestellt: Die Rechtsbündigkeit ist ein
  geteiltes Anliegen und gehört ins Design System, nicht in jede Komponente.
* Die lokale CSS-Regel behält **nur** noch das Monospace — die Ziffern stehen dadurch in einer
  Kolonne, auch wenn die Grundschrift proportional ist. Das ist eine Eigenschaft *dieser* Spalte und
  bleibt deshalb in der Komponente. Sie kommentarlos mit umzustellen hätte den Monospace-Satz still
  entfernt.
* Verifiziert: 53 Unit-Tests und **23 von 23** E2E-Fällen der Tarifverwaltung grün — diese laufen
  gegen den Container und brauchen keinen Rebuild, weil nur Frontend-Styling betroffen ist. Die
  Rechtsbündigkeit selbst ist erst nach einem Rebuild sichtbar.

## Nachtrag: zwei Testfunde nach dem Rebuild

Der erste Lauf gegen den neu gebauten Stack brachte zwei Fehlschläge — **beide in meinen Tests, nicht
in der Anwendung**.

### Drag & Drop zog ins Leere
`should reorder positions by drag and drop` scheiterte an einer unveränderten Reihenfolge. Isoliert
lief der Zug einwandfrei (dreimal wiederholt), im Suite-Lauf nie. Der Unterschied: Der Test öffnet
vorher einen **Mieterblock**, und Playwright scrollt dafür hin.

Gemessen: Nach dem Öffnen steht `scrollY = 829`, und die Positionszeilen liegen bei **y = −29** bzw.
**−96** — über dem Viewport. `page.mouse` arbeitet in Viewport-Koordinaten; ein Zug auf eine negative
Koordinate passiert einfach nicht. `boundingBox()` liefert solche Werte anstandslos, deshalb fiel es
nicht am Helfer auf, sondern erst an der Reihenfolge — weit weg von der Ursache.

Meine Änderungen haben die Maske länger gemacht (Übersicht mit `tfoot`, zwei Summenzeilen) und damit
eine **latente** Schwäche des Helfers freigelegt: Vorher blieb die Tabelle knapp im Bild.

Behoben mit `scrollIntoViewIfNeeded()` auf der **Zielzeile** (sie liegt oben, ist also der Rand des
Bereichs, der sichtbar sein muss) plus einer Prüfung, die mit klarer Meldung scheitert, wenn eine der
beiden Zeilen ausserhalb liegt. Dieselbe Falle wie beim ECharts-Zoom-Test, wo der Wheel-Event das
Diagramm nicht erreichte.

### Die Akonto-Summe prüfte eine falsche Beziehung
`should show the prepayment total below the cost total` verglich `Σ Kosten − Σ Akonto` mit dem Saldo
**eines** Mieterblocks. Das gilt nur bei genau einem Mieter; die Testumgebung hat mehrere, und die
übrigen bringen ihr Akonto aus dem Stammdatum mit. Erwartet 589.90, erhalten 509.10 — beide Zahlen
richtig, die Gleichung falsch gestellt.

Zusätzlich stimmte der Selektor nicht: `.zev-container > .nk-total` — die Wurzel der Maske heisst
`.form-container`, `.zev-container` gehört zur Liste darum herum.

Neu geschrieben als **Delta-Prüfung**: Eine Änderung von 12 × 50.00 auf 12 × 80.00 muss die Summe um
genau 360.00 erhöhen — unabhängig davon, was die übrigen Mieter mitbringen. Vor und nach dem
Speichern. Die Beziehung „Kosten − Akonto = Σ Salden" bleibt beim Unit-Test: Dort sind die Salden
**vorzeichenbehaftet**, während die Maske je Block den Betrag absolut mit Beschriftung zeigt.

## Nachtrag: Wohnung im Kopf des Mieterblocks (FR-11)

Hinter dem Mieternamen steht seine Wohnung in Klammern, in normaler Schriftstärke.

**Kein neues CSS.** `zev-text--normal` gibt es im Design System (`typography.css`) bereits. Nötig
ist es trotzdem: Die Kopfzeile steht auf `font-weight: 500`, ein `span` ohne eigene Angabe wäre
also weiterhin halbfett — „nicht fett" heisst hier ausdrücklich 400.

**Der Weg der Daten.** Die Namen entstehen dort, wo die Wohnungen ohnehin geprüft werden
(`NkAbrechnungService.ladeMieter` — `CONSUMER` **mit** `nebenkostenRelevant`), und reisen über
`NkMieterBasisDTO` → `NkMieterAbrechnungDTO` in den Block. Als **Liste**, nicht als fertiger Text:
Wie mehrere Namen aneinandergereiht werden, ist Sache der Anzeige (`einheitenText()` in der Maske).

Auf `NkMieterBasisDTO` bewusst **nicht** als Konstruktor-Parameter: Die Berechnung braucht die
Namen nicht, sie reicht sie nur durch. Ein siebter Parameter hätte rund dreissig
Test-Vorbelegungen angefasst, ohne dass dort etwas zu prüfen wäre.

**Eine Vereinfachung nebenbei.** `ladeMieter` führte zwei Strukturen: ein `Set` der relevanten
Einheiten und eine `Map` mit der Anzahl je Mieter. Jetzt gibt es **eine** `Map<Long, List<String>>`
— die Länge der Liste *ist* die Zahl der Wohnungen. Zwei Quellen für dieselbe Zahl könnten
auseinanderlaufen, und an dieser Zahl hängt der Umlageanteil. Ein Test hält beides zusammen:
`getAbrechnungDetail_ZweiWohnungen_BeideNamenUndDoppelteTage` prüft die Namen **und** die 730 Tage.

**Sortiert ausgegeben.** Die Reihenfolge der Zuordnungen aus der Datenbank ist nicht zugesichert;
ein Kopf, der bei jedem Laden anders aussieht, wirkt wie ein Fehler.

**Die Vorschau muss die Namen durchreichen.** Sie baut die Mieterblöcke bei jeder Eingabe neu auf
(`nebenkosten-berechnung.ts`). Ohne das Feld in `NkMieterTage` verschwände die Klammer beim ersten
Tastendruck und käme nach dem Speichern wieder — ein Flackern, das nach einem Fehler aussieht.
Deshalb liegt das Feld auch in der Projektion in `uebernehme()`.

Keine Migration: Die Klammer braucht keinen Text, und die Einheitennamen sind Stammdaten.

**Tests:** `NkAbrechnungServiceTest` 75 (3 neu: eine Wohnung, zwei Wohnungen samt Tagen, nicht
relevante Einheit bleibt draussen), `NkBerechnungServiceTest` 71 (2 neu: Durchreichen und leere
Liste statt `null`), Maskentests 145 (4 neu für `einheitenText`), Vorschau-Spec um den
Durchreich-Test erweitert. Das Typsystem erzwang die Ergänzung zweier Test-Vorbelegungen — genau
deshalb ist das Feld **nicht** optional deklariert.

## Nachtrag: Summe „Nicht verteilt" in der Fusszeile (FR-10)

Analog zur Kostensumme: `NkBerechnungDTO.summeNichtVerteilt`, gerechnet über die Zeilen der
Positionsübersicht.

**Nullen werden übersprungen, nicht als 0 gezählt.** `nichtVerteilt` ist in der einzelnen Zeile
`null`, wo die Positionsart den Begriff nicht kennt (Verbrauch, Zuschlag, Zusatzzeile). Im Backend
deshalb `.filter(Objects::nonNull)`, im Frontend-Spiegel `?? 0` statt `zahl(...)`. Das Ergebnis wäre
dasselbe — die Begründung nicht, und in JavaScript ist der Unterschied nicht nur akademisch:
`undefined` im `reduce` ergibt `NaN`, und ein `NaN` in der Fusszeile fällt erst auf dem Bildschirm
auf.

**Die Fusszeile zeigt `0.00`, die einzelne Zelle bleibt leer.** Kein Widerspruch zum Entscheid
„leere Zellen statt Nullen": In der Zeile einer Verbrauchsposition wäre `0.00` die Behauptung, es
sei nichts liegen geblieben, obwohl die Art nichts liegen lassen kann. In der Fusszeile ist es eine
gerechnete Aussage über alle Zeilen.

**Beschriftung der Fusszeile auf „Total" geändert.** Sie stand auf `NK_SUMME_KOSTEN` („Kosten") —
mit zwei Zahlen in der Zeile wäre das eine falsche Überschrift für die zweite. `TOTAL` existiert in
beiden Sprachen, also keine Migration.

**E2E: ein mehrdeutiger Locator war die eigentliche Falle.** `summeKosten(page)` gab
`.nk-kontrolle tfoot th.zev-table__number` zurück — mit der zweiten Summe trifft das zwei Zellen,
und `toHaveText` wirft im Strict Mode. Jetzt liefert `fusszeileZahlen(page)` beide, und
`summeKosten` / `summeNichtVerteilt` greifen `nth(0)` / `nth(1)`. Ein Index bleibt ein Index —
deshalb prüft der neue Test **beide** Zahlen zusammen: `Kosten + Nicht verteilt = 1'000.00`. Eine
Verwechslung fällt damit als falscher Betrag auf und nicht stillschweigend.

Der Test rechnet bewusst mit **Verhältnissen** statt absoluten Beträgen: Wie viele Mieter die
Umgebung kennt, weiss er nicht. Geprüft wird die Identität je Position und dass eine zweite Umlage
desselben Betrags beide Summen verdoppelt — vor und nach dem Speichern.

**Und die Identität hat drei Terme.** Die erste Fassung dieses Tests behauptete
`Kosten + Nicht verteilt = Totalbetrag` und scheiterte an einem Rappen: `999.99` statt `1'000.00`.
Richtig ist `Kosten + Nicht verteilt + Rundungsdifferenz = Totalbetrag`. `nichtVerteilt` rechnet
gegen den *exakt* verteilbaren Betrag, `summeKosten` ist die Summe der **einzeln gerundeten**
Mieterbeträge — genau dafür hat die Übersicht ihre dritte Spalte, die ich beim Aufschreiben der
Gleichung zwei Zellen weiter rechts stehen liess. Der Fehler lag in Test und Spec, nicht im Code;
beide sind richtiggestellt.

**Tests:** `NkBerechnungServiceTest` 74 (3 neu: Summe über zwei Umlagen bei halbem Nenner, `0.00`
statt `null` ohne Umlage, `0.00` bei voller Belegung), Vorschau-Spec 56 (1 neu), Maskentest-Fixture
um das Feld ergänzt. E2E-Test angelegt, aber noch nicht gelaufen — er braucht den neuen Stand im
Container.
