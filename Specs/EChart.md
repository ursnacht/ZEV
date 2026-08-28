# EChart

## 1. Ziel & Kontext - Warum wird das Feature benötigt?

* **Was soll erreicht werden:** Die **Messwerte-Grafik** (`/chart`) wird von **chart.js auf ECharts** umgestellt — dieselbe Bibliothek, die schon die dynamische Preiszeitreihe zeichnet. Danach ist chart.js aus dem Projekt entfernt. Gemeinsame Bausteine (Nachladen der Bibliothek, Farben aus Design-Tokens, Zeitformatierung) nutzen beide Diagramme.
* **Warum machen wir das:** Nicht wegen „eine Bibliothek weniger" — das allein trägt die Entscheidung nicht. Es sind vier nachprüfbare Gründe:
  1. **Initial-Bundle.** chart.js wird in `messwerte-chart.component.ts` **statisch** importiert, und `/chart` ist eine **eager** Route (`app.routes.ts:30`). Die Bibliothek liegt damit im `main`-Bundle — dem Bundle, das mit ~983 kB über der Warnschwelle von `angular.json` (`maximumWarning: 1mb`) liegt, und zwar schon vor der Preiszeitreihe. Jede Seite der Anwendung lädt chart.js mit, auch wer nie ein Diagramm öffnet. ECharts wird dagegen dynamisch nachgeladen; nach dem Wechsel verschwindet chart.js aus `main` und das Diagramm teilt sich den bestehenden ECharts-Chunk.
  2. **Zoomen.** Die Messwerte-Grafik zeigt 15-Minuten-Werte über ein Quartal: rund **8'600 Punkte je Einheit**, mehrere Einheiten gleichzeitig. Es gibt heute **keine** Möglichkeit, hineinzuzoomen. Genau hier fehlt sie mehr als bei der Preiszeitreihe (dort sind es 96 bis 2'976 Punkte).
  3. **Vier Regelverstösse in derselben Datei**, die ohnehin behoben werden müssen und beim Umbau kaum Zusatzaufwand kosten:
     * `toLocaleString('de-DE')` für die Achsenbeschriftung — `Specs/generell.md` verbietet das ausdrücklich (Laufzeit-Locale).
     * `toFixed(3)` in den Legenden-Summen — kein Schweizer Format, kein Hochkomma als Tausendertrennzeichen.
     * Farben hart kodiert (`#4CAF50`, `#2196F3`) — keine Design-Tokens, Dark Mode unversorgt.
     * Legendentexte `Total` und `ZEV` hart kodiert im TypeScript — nicht über den `TranslationService`, obwohl die Keys `TOTAL` und `ZEV` längst existieren.
  4. **Ein Workaround verschwindet.** Im Code steht `responsive: false` mit dem Kommentar *„WICHTIG! Funktioniert mit true nicht."* samt manueller Grössenberechnung des Canvas. Die ECharts-Komponente löst dasselbe mit einem `ResizeObserver` — und sie existiert schon.
* **Aktueller Stand:**
  - **chart.js steckt in genau einer Komponente:** `messwerte-chart.component.ts` (202 Zeilen, davon ~50 Diagramm-Konfiguration), ein Liniendiagramm mit zwei Datenreihen, keine Plugins. `statistik` und `rechnungen` verwenden nur die Panel-Klasse `zev-panel--chart`, kein chart.js.
  - Die Seite hat seit `tests/messwerte-grafik.spec.ts` ein **E2E-Netz** (14 Tests, beide Browser), inklusive eines Pixel-Vergleichs zwischen einem Zeitraum mit und einem ohne Messwerte. Ohne dieses Netz wäre der Umbau nicht verantwortbar.
  - Die Unit-Spec (`messwerte-chart.component.spec.ts`, 18 Tests) lädt mit `of([])` — die Diagramm-Erzeugung wird dort **nie** ausgeführt.
  - ECharts (`echarts` 6.1.0, Apache-2.0) ist seit `Specs/Preiszeitreihe.md` im Projekt und wird dort dynamisch nachgeladen.

## 2. Funktionale Anforderungen (FR) - Was soll das System tun?

### FR-1: Ablauf / Flow — unverändert

Die Bedienung bleibt **exakt** wie heute; der Wechsel ist für den Benutzer nur an der Darstellung und am neuen Zoom erkennbar.

1. Benutzer öffnet **Messwerte-Grafik** (`/chart`, Permission `messwerte:read`).
2. Er wählt Einheiten (Mehrfachauswahl samt „Alle auswählen") und einen Zeitraum (Quartals-Schaltflächen oder Datum von/bis; `Datum von` zieht `Datum bis` auf das Monatsende nach).
3. Klick auf **Anzeigen** lädt die Messwerte je Einheit und zeichnet **ein Diagramm je Einheit** in einem eigenen Panel mit Titel `Name [Typ]`.
4. Eine Erfolgsmeldung nennt die Zahl der geladenen Datenpunkte und der Einheiten.
5. **Neu:** Innerhalb des geladenen Zeitraums kann der Benutzer zoomen und den Ausschnitt verschieben — ohne neuen Server-Aufruf.

### FR-2: Darstellung

* **Zwei Datenreihen je Diagramm, wie heute:** `Total` und `ZEV`, wobei die ZEV-Reihe **negativ** aufgetragen wird (Spiegelung nach unten). Die Legende nennt je Reihe die Summe in kWh.
* **Stufenlinie** (`step: 'end'`) statt der heutigen geglätteten Linie (`tension: 0.1`): Ein Messwert gilt für die **ganze** Viertelstunde. Die geglättete Linie behauptet einen stetigen Verlauf zwischen den Intervallen, den es nicht gibt. Das ändert das gewohnte Bild — bewusst.
* **Keine Flächenfüllung** (heute `fill: false`) — bleibt so.
* **x-Achse:** Zeit; **y-Achse:** Energie. Beide Achsentitel aus dem `TranslationService` — `ZEIT` und `KWH` (beide Keys vorhanden).
* **Zoom:** `dataZoom` mit `inside` (Mausrad/Touch) **und** `slider` — dieselbe Konfiguration wie bei der Preiszeitreihe.
* **Ausdünnung:** `sampling: 'lttb'` ab einer grossen Punktzahl (NFR-1). Bei ~8'600 Punkten je Reihe und mehreren Diagrammen auf einer Seite ist das kein Luxus.
* **Modularer, dynamischer Import** wie bei der Preiszeitreihe. Der gemeinsame Loader registriert die **Vereinigung** aller von beiden Diagrammen benötigten Module — `LineChart`, `BarChart`, `GridComponent`, `TooltipComponent`, `DataZoomComponent`, `LegendComponent`, `CanvasRenderer` — und nicht je Aufrufer eine eigene Auswahl (FR-5). Ein nicht registriertes Modul **zeichnet stumm nichts**: ECharts meldet keinen Fehler. Genau daran ist der Balken-Serientyp der Preiszeitreihe zuerst gescheitert, und eine je Aufrufer verschiedene Liste würde die Falle vervielfachen. Der Preis ist ein etwas grösserer gemeinsamer Chunk — er wird ohnehin nur beim Öffnen eines Diagramms geladen.

### FR-3: Zahlen und Texte

* **Alle** Zahlen im Schweizer Format über `formatSwissNumber()` aus `src/app/utils/number-utils.ts` — Achsenbeschriftung, Tooltip und die Summen in der Legende. **Kein** `toLocaleString()`, **kein** `toFixed()`, keine ECharts-eigene Locale-Formatierung.
* **Zeitpunkte** im Format `dd.MM.yyyy HH:mm`, manuell gebildet (kein `toLocaleString`).
* **Alle** Texte über den `TranslationService`, auch die Legendennamen: `TOTAL`, `ZEV`, `ZEIT`, `KWH`. Ebenso Lade- und Fehlerzustand des Diagramms: `DIAGRAMM_LAEDT` und `DIAGRAMM_NICHT_LADBAR`. **Alle diese Keys sind vorhanden** (in der Übersetzungstabelle geprüft) — es braucht **keine neue Migration**.
* **Aufgelöst wird beim Zeichnen.** Ein Sprachwechsel während ein Diagramm offen steht erneuert die Beschriftung **nicht**: `TranslationService.setLanguage()` setzt ein Signal, das Template-Texte erneuert — ein gezeichnetes Canvas nicht. Die Legende trägt bis zum nächsten Zeichnen die alte Sprache. Das ist heute genauso und bleibt eine **bekannte Einschränkung** (§5); sie gilt für beide Diagramme gleich, und wenn sie behoben wird, dann für beide gemeinsam.

### FR-4: Farben und Dark Mode

* Achsen-, Gitter- und Reihenfarben aus den Design-Tokens (`--color-*`), nicht hart kodiert. Die Neutraltöne kippen im Dark Mode (`--color-gray-600` ist dort hell) — genau deshalb kommen sie aus den Tokens.
* Die beiden Reihen bleiben visuell unterscheidbar: `Total` in der Primärfarbe, `ZEV` in der Sekundärfarbe (`--color-secondary`).

### FR-5: Gemeinsame Bausteine

Nachladen der Bibliothek, Farbzugriff und Zeitformatierung stehen **einmal** und werden von beiden Diagrammen genutzt:

| Baustein | Inhalt |
|---|---|
| `utils/echarts-loader.ts` | dynamischer Import samt `core.use([...])`, gemerkt für weitere Aufrufe; liefert `null`, wenn das Nachladen scheitert |
| `utils/chart-farben.ts` | Farben aus den Design-Tokens mit Rückfallwerten |
| `utils/date-utils.ts` (bestehend) | **Neu dort:** `formatSwissDateTime()` für `dd.MM.yyyy HH:mm`. Die Preiszeitreihe hat diese Formatierung heute privat in der Komponente (`formatiereZeit`); sie wandert hierher, damit sie nicht zweimal existiert — die Datei ist ohnehin die Heimat der Datumsformatierung |

Die Preiszeitreihe-Komponente wird auf diese Bausteine umgestellt. Ihre Tests müssen unverändert grün bleiben — sie sind der Nachweis, dass die Extraktion verhaltensneutral war.

### FR-6: chart.js entfernen

* Abhängigkeit `chart.js` aus `frontend-service/package.json` entfernen, Lockfile aktualisieren.
* Danach darf **kein** Vorkommen von `chart.js` mehr im Quellcode stehen.
* Seite **Lizenzen** und SBOM (`Specs/SBOM.md`) prüfen: chart.js verschwindet, ECharts bleibt.

### FR-7: Grössenanpassung

Das Diagramm füllt sein Panel und folgt Grössenänderungen über einen `ResizeObserver`. Die heutige manuelle Canvas-Berechnung samt `responsive: false` entfällt. Beim Verlassen der Seite wird die Instanz freigegeben (`dispose()`), der Observer abgemeldet.

Während die Bibliothek nachgeladen wird, zeigt das Panel `DIAGRAMM_LAEDT`; scheitert das Nachladen, erscheint `DIAGRAMM_NICHT_LADBAR` als Fehlermeldung und die Seite bleibt bedienbar. Beide Keys sind vorhanden.

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)

**Verhalten bleibt**
* [ ] Die **14 bestehenden E2E-Tests** in `tests/messwerte-grafik.spec.ts` laufen **unverändert** grün — insbesondere der Pixel-Vergleich „Zeitraum mit Messwerten bemalt mehr als einer ohne".
* [ ] Die Bedienung ist unverändert: Einheiten-Mehrfachauswahl, „Alle auswählen", Quartals-Schaltflächen, `Datum von` zieht `Datum bis` auf das Monatsende, gesperrte Schaltfläche ohne Auswahl, Fehlermeldung bei vertauschtem Zeitraum.
* [ ] Ein Panel je gewählter Einheit mit Titel `Name [Typ]`; die Erfolgsmeldung nennt Datenpunkte und Einheiten.
* [ ] Beide Datenreihen erscheinen, die ZEV-Reihe negativ aufgetragen.

**Neu und geändert**
* [ ] Mausrad/Touch zoomt innerhalb des geladenen Zeitraums, der Slider verschiebt den Ausschnitt — **ohne** neuen HTTP-Aufruf.
* [ ] Die Linie ist eine **Stufenlinie**, ohne Flächenfüllung.
* [ ] Zahlen in Achsenbeschriftung, Tooltip und Legende stehen im Schweizer Format (Punkt als Dezimal-, Hochkomma als Tausendertrennzeichen). In `messwerte-chart.component.ts` kommt weder `toLocaleString` noch `toFixed` vor; die Formatierung läuft ausschliesslich über `formatSwissNumber`. (Projektweit sind beide zulässig — `number-utils.ts` verwendet `toFixed` selbst, und genau dort gehört es hin.)
* [ ] Die Legendennamen kommen aus dem `TranslationService` (`TOTAL`, `ZEV`) und stehen beim Zeichnen in der aktiven Sprache. **Nicht** gefordert ist, dass ein Sprachwechsel ein bereits gezeichnetes Diagramm erneuert (FR-3, bekannte Einschränkung).
* [ ] Achsen-, Gitter- und Reihenfarben stammen aus Design-Tokens. **Keine Diagrammfarbe** ist ein Literal; die Rückfallwerte im gemeinsamen Farb-Baustein sind erlaubt und erwünscht (ohne sie wäre das Diagramm unsichtbar, falls ein Token fehlt).
* [ ] Die Achsentitel kommen aus `ZEIT` und `KWH`, nicht aus festem Text.
* [ ] Das Diagramm folgt einer Grössenänderung des Panels (kein `responsive: false`, keine manuelle Canvas-Berechnung).
* [ ] Beim Verlassen der Seite wird die ECharts-Instanz freigegeben und der `ResizeObserver` abgemeldet.

**Sicherheit unverändert**
* [ ] `/chart` bleibt mit der Permission `messwerte:read` erreichbar und ohne sie gesperrt (`composite-roles.spec.ts` deckt das mit einem Fall ab).

**Bundle und Abhängigkeit**
* [ ] `chart.js` steht nicht mehr in `package.json`, und `grep -r "chart.js" frontend-service/src` findet nichts.
* [ ] Nach dem Produktionsbau ist `main-*.js` **kleiner** als vorher; die Zahl wird gemessen und im Umsetzungsplan festgehalten.
* [ ] ECharts liegt weiterhin **nicht** im Initial-Bundle, sondern in eigenen Chunks (dynamischer Import).
* [ ] Auf der Seite **Lizenzen** erscheint chart.js nicht mehr.

**Gemeinsame Bausteine**
* [ ] Nachladen der Bibliothek, Farben und Zeitformatierung stehen **einmal** im Projekt und werden von beiden Diagrammen genutzt.
* [ ] Die Tests der Preiszeitreihe (37 Unit, 13 E2E) laufen nach der Umstellung unverändert grün.

**Fehlerfälle**
* [ ] Scheitert das Nachladen der Bibliothek, erscheint eine Fehlermeldung, und die Seite bleibt bedienbar (Auswahl, Zeitraum, Navigation).
* [ ] Ein Zeitraum ohne Messwerte führt zu einer Meldung mit `0` Datenpunkten; das Panel entsteht, das Diagramm bleibt leer, die Seite bleibt bedienbar.
* [ ] Ein Fehler beim Laden der Messwerte zeigt eine lesbare Meldung (kein `[object Object]`).

## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Performance
* Ein Quartal sind bis zu **8'640** Punkte je Reihe (90 × 96), zwei Reihen je Diagramm, mehrere Diagramme gleichzeitig. Mit `sampling: 'lttb'` bleibt die Darstellung flüssig; ohne Ausdünnung wäre sie es bei „Alle auswählen" nicht.
* Die Diagramme werden weiterhin **nacheinander** aufgebaut (heute `setTimeout`-Kette), damit die Maske bei vielen Einheiten nicht blockiert. Ob das mit ECharts noch nötig ist, entscheidet die Messung (§8).
* Das Initial-Bundle wird **kleiner**, nicht grösser (siehe Akzeptanzkriterien). Erwartung: chart.js schlägt im Bundle grob mit **70–200 kB** zu Buche (roh, nach Tree-Shaking) — das ist die Grössenordnung, an der die Messung zu beurteilen ist. Fällt die Ersparnis deutlich kleiner aus, ist zu prüfen, ob noch etwas anderes chart.js hereinzieht. Zahlenwerte kommen aus dem Produktionsbau vor und nach dem Umbau.

### NFR-2: Sicherheit
* **Unverändert:** Route `/chart` verlangt die Permission `messwerte:read` (`AuthGuard`, `app.routes.ts`) — Fachrollen `zev_user`, `org_admin`, `zev_admin`. Die Daten kommen weiterhin über `GET /api/messwerte/by-einheit` mit `hasAuthority('messwerte:read')`.
* Keine neuen Endpunkte, keine neuen Berechtigungen, kein Feature-Flag.
* Der dynamische Import lädt ausschliesslich lokal gebündelte Chunks — **kein** CDN, keine externe Quelle.

### NFR-3: Kompatibilität
* **Keine Datenbank-Änderung, keine Migration, keine neuen Übersetzungs-Keys** (`TOTAL`, `ZEV`, `ZEIT` existieren).
* Keine Änderung an Backend, API oder DTOs.
* Reiner Frontend-Umbau: Ein Rückweg ist ein einzelner Commit. Es gibt keinen migrierten Datenbestand, der einen Rückweg blockieren könnte.
* Die Statistik-Seite und alle übrigen Masken bleiben unberührt.

## 5. Edge Cases & Fehlerbehandlung

| Fall | Erwartetes Verhalten |
|---|---|
| Zeitraum ohne Messwerte | Meldung mit `0` Datenpunkten, Panel und leeres Diagramm, Seite bedienbar |
| Einheit ohne Messwerte, andere mit | Jedes Diagramm zeigt seinen eigenen Bestand; das leere bleibt leer, ohne Fehler |
| „Alle auswählen" bei vielen Einheiten | Jedes Diagramm wird gezeichnet; Ausdünnung und sequenzieller Aufbau halten die Maske bedienbar |
| Sehr grosser Zeitraum (mehrere Quartale) | Wird geladen und gezeichnet; die Lesbarkeit übernimmt `dataZoom` |
| Nachladen der Bibliothek scheitert (Netz, Chunk fehlt) | Fehlermeldung, kein Diagramm, Seite bleibt bedienbar |
| Fehler beim Laden der Messwerte | Lesbare Fehlermeldung, kein halb gezeichnetes Diagramm |
| Vertauschter Zeitraum | Wie heute: Fehlermeldung, kein Server-Aufruf, kein Diagramm |
| Panel wird in der Grösse verändert (`resize: vertical`) | Diagramm folgt über den `ResizeObserver` |
| Sprachwechsel bei offenem Diagramm | Beschriftung und Legende bleiben bis zum nächsten Zeichnen in der alten Sprache. **Bekannte Einschränkung**, heute genauso, gilt für beide Diagramme (FR-3) |
| Wechsel des Dark Mode bei offenem Diagramm | Die Farben werden beim Zeichnen aus den Tokens gelesen; ein Wechsel wirkt erst beim nächsten Zeichnen. **Bekannte Einschränkung**, gilt heute für die Preiszeitreihe genauso (§8) |
| Seite verlassen und zurückkehren | Instanz freigegeben, neue Instanz gezeichnet; kein Speicherzuwachs bei wiederholtem Wechsel |

## 6. Abhängigkeiten & betroffene Funktionalität

**Voraussetzungen**
* `echarts` 6.1.0 im Projekt (aus `Specs/Preiszeitreihe.md`).
* **`tests/messwerte-grafik.spec.ts`** (14 Tests) als Netz — ohne diese Tests wäre der Umbau nicht verantwortbar. Sie sind der Grund, warum diese Spec überhaupt geschrieben werden kann.
  * **Randbedingung:** Die Tests zählen `.zev-panel--chart canvas`. Die Zusicherung „unverändert grün" gilt, solange ECharts **genau ein** `<canvas>` je Instanz erzeugt. Legt es mehrere Render-Layer an, ist der **Test** anzupassen — und die Anpassung ist zu begründen, nicht die Zusicherung zu beugen.
* Design-Tokens (`design-system/src/tokens/tokens.css`), `formatSwissNumber`, `TranslationService`.

**Betroffener Code**
| Datei | Änderung |
|---|---|
| `components/messwerte-chart/messwerte-chart.component.ts` | Kern des Umbaus: ECharts statt chart.js, Formatierung, Farben, Zoom, `ResizeObserver` |
| `components/messwerte-chart/messwerte-chart.component.html` | `<canvas>` → `<div>` als Diagramm-Behälter |
| `components/messwerte-chart/messwerte-chart.component.spec.ts` | Zeichnen stubben (jsdom hat kein Canvas), Optionen als reine Funktion prüfen — Vorbild `preiszeitreihe-chart.component.spec.ts` |
| `components/preiszeitreihe-chart/preiszeitreihe-chart.component.ts` | nutzt die gemeinsamen Bausteine statt eigener Kopien |
| `utils/echarts-loader.ts`, `utils/chart-farben.ts` | neu |
| `frontend-service/package.json` (+ Lockfile) | `chart.js` entfernen |

**Datenmigration**
* Keine.

**Tests**
* Frontend-Unit: `messwerte-chart.component.spec.ts` umstellen (18 Tests), neue Tests für die gemeinsamen Bausteine.
* E2E: `messwerte-grafik.spec.ts` bleibt **unverändert** — das ist Absicht und gleichzeitig das stärkste Akzeptanzkriterium. Ergänzt wird nur ein Test für das Zoomen.

## 7. Abgrenzung / Out of Scope

* **Keine neuen Funktionen** in der Messwerte-Grafik: kein Export, keine Kennzahlen, keine zusätzlichen Reihen, keine Änderung der Datenauswahl.
* **Keine Änderung an Backend, API oder Datenbank.**
* **Kein Umbau der Statistik-Seite** und keiner anderen Maske; `zev-panel--chart` bleibt wie es ist.
* **Keine fachliche Änderung an der Preiszeitreihe** — dort wird ausschliesslich auf die gemeinsamen Bausteine umgestellt.
* **Kein Feature-Flag.** Der Umbau ist entweder fertig oder nicht; ein Flag für zwei Diagramm-Bibliotheken parallel wäre teurer als der Umbau selbst.
* **Keine Obergrenze für den Zeitraum.** Anders als die Preiszeitreihe (366 Tage) kennt diese Seite keine Grenze, und das bleibt **bewusst** so: Die Datenauswahl wird nicht angefasst. Wer „Alle auswählen" über mehrere Jahre wählt, erzeugt eine grosse Antwort — die Lesbarkeit übernimmt `dataZoom`, die Ausdünnung `sampling`. Eine Grenze wäre eine eigene fachliche Entscheidung, keine Folge des Bibliothekswechsels.
* **Keine Bildvergleichstests** (Screenshot-Diffs) — der Pixelvergleich der bestehenden E2E-Tests genügt.

## 8. Offene Fragen

> **Alle Annahmen sind bestätigt** (28.08.2026). Keine Frage blockiert die Umsetzung; die
> Antwort steht je Frage hinter `-->`.

* **Sequenzieller Aufbau weiterhin nötig?** Heute werden die Diagramme mit `setTimeout` nacheinander erzeugt, weil chart.js bei vielen Diagrammen die Maske blockierte. Ob ECharts das auch braucht, zeigt erst die Messung bei „Alle auswählen". --> **Annahme bestätigt:** erst beibehalten, nach der Messung entscheiden. Fällt der verzögerte Aufbau weg, verschwindet auch die Zeichenverzögerung, auf die der E2E-Helfer `warteAufGezeichnetesDiagramm` heute wartet — der ist dann mitzuprüfen.
* **Ab welcher Punktzahl `sampling: 'lttb'`?** --> **Annahme bestätigt:** dauerhaft aktiv; die Ausdünnung ist bei 96 Punkten unschädlich und bei 8'640 nötig.
* **Dark-Mode-Wechsel bei offenem Diagramm** wirkt erst beim nächsten Zeichnen. Soll das Diagramm auf den Wechsel reagieren (Beobachter auf `data-theme`)? --> **Annahme bestätigt:** nein, wie bei der Preiszeitreihe; bleibt als bekannte Einschränkung dokumentiert. Falls später doch, dann für beide Diagramme gemeinsam.
* **Legende bei vielen Reihen:** Zwei Reihen je Diagramm sind gesetzt; die Legende zeigt die Summen. Bleibt die Summe im Legendentext oder wandert sie in den Panel-Titel? --> **Annahme bestätigt:** bleibt im Legendentext wie heute.
* **Erwartete Bundle-Ersparnis** ist noch nicht gemessen (chart.js liegt als 6.2 MB im `node_modules`, davon landet nur ein Teil im Bundle). --> Wird im Umsetzungsplan mit Zahlen vor und nach dem Umbau belegt.
