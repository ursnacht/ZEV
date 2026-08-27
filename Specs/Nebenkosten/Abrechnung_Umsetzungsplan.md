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
