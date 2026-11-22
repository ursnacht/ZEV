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

### FR-1: Ablauf / Flow

1. Benutzer öffnet **Messwerte-Grafik** (`/chart`, Permission `messwerte:read`).
2. Er wählt Einheiten und — wo vorhanden — Messpunkte mit mehreren Einheiten (FR-8; Mehrfachauswahl samt „Alle auswählen") und einen Zeitraum (Quartals-Schaltflächen, Datum von/bis, Blättern oder „Heute", FR-1a; `Datum von` zieht `Datum bis` auf das Monatsende nach).
3. Klick auf **Anzeigen** lädt die Messwerte je Einheit und zeichnet **ein Diagramm je Einheit** in einem eigenen Panel mit Titel `Name [Typ]`, danach **eines je gewähltem Messpunkt** (FR-8). Klick auf **Vergleich** zeichnet stattdessen **ein** Diagramm mit allen gewählten Einheiten (FR-9). Die Checkbox **Einspeisetarif** blendet in jedes Diagramm den dynamischen Einspeisepreis ein (FR-10). Blättern und „Heute" laden selbst, in der Ansicht, die gerade steht.
4. Eine Erfolgsmeldung nennt die Zahl der geladenen Datenpunkte und der Einheiten.
5. Innerhalb des geladenen Zeitraums kann der Benutzer zoomen und den Ausschnitt verschieben — ohne neuen Server-Aufruf.

### FR-1a: Bedienung wie in der Statistik

Schaltflächen und Datumsfelder sind angeordnet und verhalten sich wie auf der Statistik-Seite (`Specs/Statistik.md`, FR-2 bis FR-4) — **ohne** „PDF exportieren", die Grafik hat keinen Export. Wer beide Seiten nutzt, findet dieselben Knöpfe an derselben Stelle (Hintergrund: Anhang A.1).

1. **„Anzeigen"** steht rechts neben den Quartalsknöpfen (Aktions-Slot des Quartal-Selektors). Gesperrt während des Ladens, ohne beide Daten oder ohne gewählte Einheit; während des Ladens mit dem Text `LADE_DATEN`.
2. In der Zeile der Datumsfelder stehen **◀ / ▶** (Icons `chevron-left`/`chevron-right`, Tooltip und `aria-label` aus `ZEITRAUM_ZURUECK`/`ZEITRAUM_VOR`) und danach **„Heute"** (Icon `calendar`, Text `HEUTE`).
3. **Blättern** verschiebt den Zeitraum um seine eigene Länge — dieselbe Regel wie in der Statistik (`blaettereZeitraum`, `Specs/Statistik.md`, FR-4.1) — und **lädt sofort**, in der Ansicht, die gerade steht (einzelne Diagramme oder Vergleich, FR-9).
4. **„Heute"** setzt `Datum von` und `Datum bis` auf das heutige lokale Datum und **lädt sofort**; auch ein zweiter Klick lädt (aktualisieren).
5. **Abweichung von der Statistik:** Blättern und „Heute" sind **ohne Auswahl gesperrt** (weder Einheit noch Messpunkt gewählt) — das sofortige Laden liefe sonst nur in die Fehlermeldung. Die Statistik kennt keine Einheitenauswahl.
6. Die **Erfolgsmeldung** mit der Zahl der Datenpunkte bleibt (FR-1.4) — anders als in der Statistik, wo das Ergebnis selbst die Rückmeldung ist. Hier ist die Zahl eine eigene Information: Ein leerer Zeitraum ergibt ein leeres Diagramm, die Meldung sagt „0".

### FR-2: Darstellung

* **Zwei Datenreihen je Diagramm, wie heute:** `Total` und `ZEV`, wobei die ZEV-Reihe **negativ** aufgetragen wird (Spiegelung nach unten). Die Legende nennt je Reihe die Summe in kWh.
* **Stufenlinie** (`step: 'end'`) statt der heutigen geglätteten Linie (`tension: 0.1`): Ein Messwert gilt für die **ganze** Viertelstunde. Die geglättete Linie behauptet einen stetigen Verlauf zwischen den Intervallen, den es nicht gibt. Das ändert das gewohnte Bild — bewusst.
* **Auch das letzte Intervall ist zu sehen:** Jede Reihe endet mit einem Abschlusspunkt am Ende ihres letzten Intervalls (Beginn + 15 Minuten) mit dessen Wert. Er ist kein Messwert: Der Tooltip überspringt ihn, und in die Summe der Legende geht er nicht ein.
  > Eine Stufenlinie zeichnet jeden Wert bis zum nächsten Punkt. Ohne Abschlusspunkt fehlte die letzte Viertelstunde, und eine Reihe mit einem einzigen Messwert blieb leer (Hintergrund: Anhang A.4).
* **Keine Flächenfüllung** (heute `fill: false`) — bleibt so.
* **x-Achse:** Zeit, **ohne Achsentitel**; **y-Achse:** Energie mit dem Titel `KWH` aus dem `TranslationService`.
  > Der Titel „Zeit“ stand mitten im Zoom-Schieber unter der Achse; dass sie die Zeit zeigt, sagen die Uhrzeiten an ihr (Hintergrund: Anhang A.3).
* **Zoom:** `dataZoom` mit `inside` (Mausrad/Touch) **und** `slider` — dieselbe Konfiguration wie bei der Preiszeitreihe.
* **Ausdünnung:** `sampling: 'lttb'` ab einer grossen Punktzahl (NFR-1). Bei ~8'600 Punkten je Reihe und mehreren Diagrammen auf einer Seite ist das kein Luxus.
* **Modularer, dynamischer Import** wie bei der Preiszeitreihe. Der gemeinsame Loader registriert die **Vereinigung** aller von den Diagrammen benötigten Module — `LineChart`, `BarChart`, `GridComponent`, `TooltipComponent`, `DataZoomComponent`, `LegendComponent`, `MarkAreaComponent` (Zustandsbänder der Einspeisesteuerung), `CanvasRenderer` — und nicht je Aufrufer eine eigene Auswahl (FR-5). Ein nicht registriertes Modul **zeichnet stumm nichts**: ECharts meldet keinen Fehler. Genau daran ist der Balken-Serientyp der Preiszeitreihe zuerst gescheitert, und eine je Aufrufer verschiedene Liste würde die Falle vervielfachen. Der Preis ist ein etwas grösserer gemeinsamer Chunk — er wird ohnehin nur beim Öffnen eines Diagramms geladen.

### FR-3: Zahlen und Texte

* **Alle** Zahlen im Schweizer Format über `formatSwissNumber()` aus `src/app/utils/number-utils.ts` — Achsenbeschriftung, Tooltip und die Summen in der Legende. **Kein** `toLocaleString()`, **kein** `toFixed()`, keine ECharts-eigene Locale-Formatierung.
* **Zeitpunkte** im Format `dd.MM.yyyy HH:mm`, manuell gebildet (kein `toLocaleString`).
* **Alle** Texte über den `TranslationService`, auch die Legendennamen und der Achsentitel: `TOTAL`, `ZEV`, `KWH`. Ebenso Lade- und Fehlerzustand des Diagramms: `DIAGRAMM_LAEDT` und `DIAGRAMM_NICHT_LADBAR`, die Bedienung (FR-1a): `ANZEIGEN`, `LADE_DATEN`, `ZEITRAUM_ZURUECK`, `ZEITRAUM_VOR`, `HEUTE`, und der Vergleich (FR-9): `GRAFIK_VERGLEICH`, `EINHEITEN`. `GRAFIK_VERGLEICH` kommt aus der Migration `V191`. Der Einspeisetarif (FR-10) nutzt `GRAFIK_EINSPEISETARIF` und `GRAFIK_EINSPEISETARIF_CHF_KWH` (beide `V192`) sowie `PREIS_CHF_KWH`, `KEINE_PREISE_VORHANDEN` und `PREISE_ABRUF_FEHLGESCHLAGEN`. Alle übrigen Keys kommen aus früheren Migrationen.
* **Aufgelöst wird beim Zeichnen.** Ein Sprachwechsel während ein Diagramm offen steht erneuert die Beschriftung **nicht**: `TranslationService.setLanguage()` setzt ein Signal, das Template-Texte erneuert — ein gezeichnetes Canvas nicht. Die Legende trägt bis zum nächsten Zeichnen die alte Sprache. Das ist heute genauso und bleibt eine **bekannte Einschränkung** (§5); sie gilt für beide Diagramme gleich, und wenn sie behoben wird, dann für beide gemeinsam.

### FR-4: Farben und Dark Mode

* Achsen-, Gitter- und Reihenfarben aus den Design-Tokens (`--color-*`), nicht hart kodiert. Die Neutraltöne kippen im Dark Mode (`--color-gray-600` ist dort hell) — genau deshalb kommen sie aus den Tokens.
* Die beiden Reihen bleiben visuell unterscheidbar: `Total` in der Primärfarbe, `ZEV` in der Sekundärfarbe (`--color-secondary`).
* Diagramme mit **einer Reihe je Einheit** (FR-8, FR-9) vergeben der Reihe nach die Reihenpalette aus `utils/chart-farben.ts` (`reihen`): Primär, Sekundär, Rot (`--color-danger`), Gelb, Orange und Violett (`--color-chart-*`). Es sind dieselben Tokens, die die Einspeisesteuerung zweckgebunden verwendet; hier gibt es weder Bänder noch benannte Grössen, die Reihen müssen sich nur voneinander unterscheiden. Eine siebte und weitere Reihe bekommt die Palette von ECharts — unterscheidbar, statt eine Farbe zweimal zu vergeben.

### FR-5: Gemeinsame Bausteine

Nachladen der Bibliothek, Farbzugriff und Zeitformatierung stehen **einmal** und werden von beiden Diagrammen genutzt:

| Baustein | Inhalt |
|---|---|
| `utils/echarts-loader.ts` | dynamischer Import samt `core.use([...])`, gemerkt für weitere Aufrufe; liefert `null`, wenn das Nachladen scheitert |
| `utils/chart-farben.ts` | Farben aus den Design-Tokens mit Rückfallwerten |
| `utils/chart-preis.ts` | Preis in CHF/kWh mit fünf Nachkommastellen (`formatPreis`) und Umrechnung der Preiszeitreihe in Datenpunkte (`preisPunkte`) — für Preiszeitreihe, Einspeisesteuerung und Messwerte-Grafik (FR-10) |
| `utils/date-utils.ts` (bestehend) | **Neu dort:** `formatSwissDateTime()` für `dd.MM.yyyy HH:mm`. Die Preiszeitreihe hat diese Formatierung heute privat in der Komponente (`formatiereZeit`); sie wandert hierher, damit sie nicht zweimal existiert — die Datei ist ohnehin die Heimat der Datumsformatierung |

Die Preiszeitreihe-Komponente wird auf diese Bausteine umgestellt. Ihre Tests müssen unverändert grün bleiben — sie sind der Nachweis, dass die Extraktion verhaltensneutral war.

### FR-6: chart.js entfernen

* Abhängigkeit `chart.js` aus `frontend-service/package.json` entfernen, Lockfile aktualisieren.
* Danach darf **kein** Vorkommen von `chart.js` mehr im Quellcode stehen.
* Seite **Lizenzen** und SBOM (`Specs/SBOM.md`) prüfen: chart.js verschwindet, ECharts bleibt.

### FR-7: Grössenanpassung

Das Diagramm füllt sein Panel und folgt Grössenänderungen über einen `ResizeObserver`. Die heutige manuelle Canvas-Berechnung samt `responsive: false` entfällt. Beim Verlassen der Seite wird die Instanz freigegeben (`dispose()`), der Observer abgemeldet.

Während die Bibliothek nachgeladen wird, zeigt das Panel `DIAGRAMM_LAEDT`; scheitert das Nachladen, erscheint `DIAGRAMM_NICHT_LADBAR` als Fehlermeldung und die Seite bleibt bedienbar. Beide Keys sind vorhanden.

### FR-8: Gemeinsames Diagramm je Messpunkt

Tragen **mehrere Einheiten denselben Messpunkt** — typisch Bezug und Rücklieferung desselben Zählers —, bietet die Auswahl dafür einen **zusätzlichen Eintrag** an, und er zeichnet beide in **einem** Diagramm.

1. **Wann es den Eintrag gibt:** je Messpunkt, den mindestens zwei Einheiten des Mandanten tragen. Verglichen wird der Messpunkt ohne Leerraum am Rand, sonst genau (Gross-/Kleinschreibung zählt). Einheiten ohne Messpunkt bilden keine Gruppe.
2. **Bezeichnung** in der Auswahl und als Panel-Titel: `<Einheit 1> [<Typ 1>] / <Einheit 2> [<Typ 2>] / [<Messpunkt>]`, z.B. `Bezug von BKW [Bezug] / Rücklieferung an BKW [Rücklieferung] / [Bilanz]`. Die Einheiten stehen in der Reihenfolge der Auswahlliste (Konsumenten zuerst, sonst nach Name — in jedem Browser gleich), der Typ ist übersetzt wie bei der einzelnen Einheit.
3. **Wo er steht:** in derselben Liste **nach** den Einheiten, sortiert nach Messpunkt. „Alle auswählen" schliesst die Einträge ein.
4. **Unabhängig von den Einheiten:** Der Eintrag wählt seine Einheiten nicht mit. Wer nur den Messpunkt wählt, bekommt **ein** Diagramm; wer zusätzlich die Einheiten wählt, bekommt deren Diagramme davor.
5. **Inhalt des Diagramms:** **eine Reihe je Einheit** mit ihrem `total`, im **gespeicherten Vorzeichen** — nichts wird gespiegelt. Der Bezug ist positiv gespeichert, die Rücklieferung negativ; die eine Linie liegt damit über, die andere unter der Nulllinie. Die Legende nennt je Reihe den **Namen der Einheit** und die Summe in kWh. Die **ZEV-Reihe entfällt** in diesem Diagramm.

   > Zwei Einheiten mit je `Total` und `ZEV` ergäben vier Linien; für Bezug und Rücklieferung ist `zev` ohnehin 0.
6. **Alles Übrige wie im Diagramm einer Einheit** (FR-2 bis FR-4, FR-7): Stufenlinie, Zoom, Ausdünnung, Achsen, Schweizer Zahlenformat, Farben aus Design-Tokens (der Reihe nach die Reihenfarben, FR-4).
7. **Geladen wird jede Einheit einmal**, auch wenn sie einzeln **und** über ihren Messpunkt gewählt ist. Die Erfolgsmeldung zählt die geladenen **Einheiten** und ihre Datenpunkte, nicht die Diagramme.
8. Die Auswahl der **Rechnungen** (dieselbe Komponente) zeigt die Einträge **nicht**.

### FR-9: Vergleich – alle gewählten Einheiten in einem Diagramm

Neben „Anzeigen" steht die Schaltfläche **„Vergleich"** (`GRAFIK_VERGLEICH`). Sie zeichnet **ein** Diagramm mit allen gewählten Einheiten; die einzelnen Diagramme verschwinden.

1. **Wo sie steht:** im Aktions-Slot des Quartal-Selektors, rechts von „Anzeigen", als sekundäre Schaltfläche mit Icon `bar-chart-2`.
2. **Wann sie frei ist:** wie „Anzeigen" (beide Daten gesetzt, nicht beim Laden) und zusätzlich nur mit **mindestens zwei** Einheiten. Gezählt wird, was geladen würde: die gewählten Einheiten und die der gewählten Messpunkte, jede einmal.

   > Mit einer Einheit wäre der Vergleich ihr eigenes Diagramm ohne ZEV-Reihe.
3. **Was erscheint:** genau ein Panel mit dem Titel `Vergleich: <n> Einheiten`. Darin **eine Reihe je Einheit** mit ihrem `total`, benannt nach der Einheit samt Summe — derselbe Aufbau wie das Messpunkt-Diagramm (FR-8.5, FR-8.6), also ohne ZEV-Reihe.
   * **Produzenten stehen positiv:** Ihre Reihe und ihre Summe werden gespiegelt (gespeichert ist die Produktion negativ), damit die Produktion über der Nulllinie neben Verbrauch und Bezug steht und sich mit ihnen vergleichen lässt.
   * Alle übrigen Typen behalten das gespeicherte Vorzeichen: Rücklieferung und entladende Speicher liegen unter der Nulllinie.
   * Im Messpunkt-Diagramm (FR-8) wird nichts gespiegelt.
4. **Reihenfolge der Reihen:** wie in der Auswahlliste; die Einheiten eines gewählten Messpunkts folgen, sofern sie nicht schon selbst gewählt sind.
5. **„Anzeigen" bringt die einzelnen Diagramme zurück.** Es gibt immer nur eine der beiden Ansichten.
6. **Blättern und „Heute"** laden in der Ansicht, die gerade steht: Wer im Vergleich blättert, bleibt im Vergleich. Sind inzwischen weniger als zwei Einheiten gewählt, fällt die Ansicht auf die einzelnen Diagramme zurück.
7. **Geladen** wird wie bei „Anzeigen": jede Einheit einmal, die Erfolgsmeldung zählt Einheiten und Datenpunkte (FR-8.7).

### FR-10: Einspeisetarif einblenden

Rechts von „Vergleich" steht die Checkbox **„Einspeisetarif"** (`GRAFIK_EINSPEISETARIF`). Ist sie gesetzt, zeigt jedes Diagramm zusätzlich den dynamischen Einspeisepreis des gewählten Zeitraums.

1. **Quelle:** die Preiszeitreihe (`GET /api/preiszeitreihe?von=…&bis=…`, `Specs/Preiszeitreihe.md`) — Viertelstundenwerte in CHF/kWh.
2. **Wer die Checkbox sieht:** nur Benutzer mit der Permission `tarife:manage` und nur bei aktivem Feature-Flag `PREISZEITREIHE`. Beides verlangt auch der Endpunkt; ohne eines davon gibt es die Checkbox nicht.

   > Sichtbar, aber wirkungslos wäre schlimmer als nicht da: Jeder Klick liefe in ein 403. Wird das Flag abgeschaltet, während das Häkchen sitzt, ruft die Grafik keine Preise mehr ab.
3. **Wann geladen wird:** zusammen mit den Messwerten, **ein** Abruf je Laden für den Zeitraum `Datum von` bis `Datum bis` — nicht einer je Einheit. Ohne Häkchen wird nichts abgerufen.
4. **Umschalten bei stehenden Diagrammen** lädt sofort neu und zeichnet mit bzw. ohne Preis. Stehen noch keine Diagramme, merkt sich die Maske nur die Wahl; sie gilt für „Anzeigen", „Vergleich", Blättern und „Heute". Während des Ladens ist die Checkbox gesperrt.
5. **Darstellung:** in **jedem** Diagramm (Einheit, Messpunkt, Vergleich) eine zusätzliche Reihe `Einspeisetarif (CHF/kWh)` als **gestrichelte Stufenlinie** in neutralem Grau, auf einer **eigenen rechten y-Achse** mit dem Titel `Preis (CHF/kWh)`. Die Energie-Achse links und ihre Hilfslinien bleiben; die rechte Achse hat keine eigenen Hilfslinien. Auch die Preisreihe endet mit einem Abschlusspunkt (FR-2).

   > Energie und Preis haben keine gemeinsame Skala. Grau und gestrichelt, weil im Vergleich alle Reihenfarben vergeben sind und der Preis keine Energie ist.
6. **Tooltip:** Der Preis steht mit **fünf** Nachkommastellen wie in der Preiszeitreihe und ohne angehängtes `kWh`.
7. **Der Preis ist Beiwerk:** Scheitert sein Abruf, erscheinen die Diagramme trotzdem — ohne Preisreihe und mit einer Fehlermeldung: dem übersetzten Key des Backends (`{ error }`) oder dessen Klartext (z. B. bei einer Spanne über 366 Tage), sonst `PREISE_ABRUF_FEHLGESCHLAGEN`. Liefert der Abruf **keine** Preise, erscheint die Warnung `KEINE_PREISE_VORHANDEN`. In beiden Fällen ersetzt die Meldung die Erfolgsmeldung mit der Zahl der Datenpunkte.

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)

**Verhalten bleibt**
* [x] Die **14 ursprünglichen E2E-Tests** in `tests/messwerte-grafik.spec.ts` laufen mit **unveränderten Testrümpfen** grün — insbesondere der Pixel-Vergleich „Zeitraum mit Messwerten bemalt mehr als einer ohne". Angepasst sind nur zwei Helfer (Warten auf die erste Einheit, Schaltfläche `#grafikAnzeigen`); die später ergänzten Tests der Datei laufen ebenfalls grün. (E2E: messwerte-grafik.spec.ts)
* [x] Die Datenauswahl bleibt: Einheiten-Mehrfachauswahl, „Alle auswählen", Quartals-Schaltflächen, `Datum von` zieht `Datum bis` auf das Monatsende, gesperrte Schaltfläche ohne Auswahl, Fehlermeldung bei vertauschtem Zeitraum. (Test: einheit-selector.component.spec.ts › EinheitSelectorComponent › onSelectAllToggle (default - alle) › should select all einheiten including producers; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › onQuarterSelected › should set dateFrom and dateTo from event; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › onDateFromChange › should set dateTo to last day of dateFrom month; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › onSubmit › should show error when dateFrom is after dateTo; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Bedienung wie Statistik › should keep the submit button disabled until a unit is selected; E2E: messwerte-grafik.spec.ts › Messwerte-Grafik - Auswahl und Validierung › should keep the submit button disabled without a unit selection; E2E: messwerte-grafik.spec.ts › Messwerte-Grafik - Auswahl und Validierung › should select every unit via the select-all checkbox)
* [x] Ein Panel je gewählter Einheit mit Titel `Name [Typ]`; die Erfolgsmeldung nennt Datenpunkte und Einheiten. (Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › onSubmit › should create one chart entry per selected einheit; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Messpunkt-Diagramm › should title the messpunkt panel with both einheiten and the messpunkt; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Messpunkt-Diagramm › should load an einheit only once when it is selected alone and as part of its messpunkt; E2E: messwerte-grafik.spec.ts › Messwerte-Grafik - Diagramm › should draw one chart per selected unit; E2E: messwerte-grafik.spec.ts › Messwerte-Grafik - Diagramm › should report the number of loaded data points)
* [x] Beide Datenreihen erscheinen, die ZEV-Reihe negativ aufgetragen. (Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › optionen › should build two series, the ZEV one mirrored below zero; E2E: messwerte-grafik.spec.ts › Messwerte-Grafik - Diagramm › should paint more when the period contains measurements)

**Bedienung wie Statistik (FR-1a)**
* [x] „Anzeigen" steht im Quartal-Selektor rechts neben den Quartalsknöpfen; in der Zeile der Datumsfelder steht kein „Anzeigen". (Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Bedienung wie Statistik › should place the submit button next to the quarter buttons; E2E: messwerte-grafik.spec.ts › Messwerte-Grafik - Diagramm › should page the period and jump to today, loading immediately)
* [x] In der Zeile der Datumsfelder stehen ◀ / ▶ und „Heute" in dieser Reihenfolge, **kein** „PDF exportieren". (Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Bedienung wie Statistik › should offer paging and today in the date row, without pdf export)
* [x] ◀ / ▶ tragen die Icons `chevron-left`/`chevron-right` und die übersetzte Beschriftung (`ZEITRAUM_ZURUECK`/`ZEITRAUM_VOR`) als Tooltip und `aria-label`; „Heute" zeigt `HEUTE`. (Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Bedienung wie Statistik › should render chevron icons with translated title and aria-label)
* [x] Blättern verschiebt den Zeitraum um seine Länge (Quartal → nächstes Quartal, Tag → Vortag) und lädt sofort die Messwerte. (Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Bedienung wie Statistik › onBlaettern › should move a quarter forward and load immediately; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Bedienung wie Statistik › onBlaettern › should move a day backwards; E2E: messwerte-grafik.spec.ts › Messwerte-Grafik - Diagramm › should page the period and jump to today, loading immediately)
* [x] „Heute" setzt beide Daten auf das **lokale** Tagesdatum und lädt sofort; ein zweiter Klick lädt erneut. (Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Bedienung wie Statistik › onHeute › should set both dates to today and load immediately; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Bedienung wie Statistik › onHeute › should use the local date shortly after midnight; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Bedienung wie Statistik › onHeute › should reload when today is already shown; E2E: messwerte-grafik.spec.ts › Messwerte-Grafik - Diagramm › should page the period and jump to today, loading immediately)
* [x] Blättern und „Heute" sind ohne Auswahl (weder Einheit noch Messpunkt) und während des Ladens gesperrt; Blättern zusätzlich bei ungültigem Zeitraum (von > bis). (Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Bedienung wie Statistik › should disable paging and today without a unit or while loading; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Bedienung wie Statistik › onBlaettern › should not move without a unit, while loading or with an invalid range; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Messpunkt-Diagramm › should enable the submit button with only a messpunkt selected)

**Messpunkt mit mehreren Einheiten (FR-8)**
* [x] Je Messpunkt, den mindestens zwei Einheiten tragen, entsteht eine Gruppe; Einheiten ohne Messpunkt und einmalig vergebene Messpunkte bilden keine. Verglichen wird ohne Leerraum am Rand, aber mit Gross-/Kleinschreibung. (Test: messpunkt-gruppen.spec.ts › Messpunkt-Gruppen Utils › messpunktGruppen › should group two einheiten sharing a messpunkt; Test: messpunkt-gruppen.spec.ts › Messpunkt-Gruppen Utils › messpunktGruppen › should not build a group for a messpunkt used only once; Test: messpunkt-gruppen.spec.ts › Messpunkt-Gruppen Utils › messpunktGruppen › should ignore einheiten without a messpunkt; Test: messpunkt-gruppen.spec.ts › Messpunkt-Gruppen Utils › messpunktGruppen › should compare the messpunkt without surrounding whitespace but case-sensitively)
* [x] Die Auswahl der Messwerte-Grafik zeigt je Gruppe einen Eintrag **nach** den Einheiten, beschriftet `Einheit [Typ] / Einheit [Typ] / [Messpunkt]`; ohne die Einstellung (Rechnungen) erscheint er nicht. (Test: einheit-selector.component.spec.ts › EinheitSelectorComponent › Messpunkt-Gruppen › should offer one entry per shared messpunkt, labelled with units and messpunkt; Test: einheit-selector.component.spec.ts › EinheitSelectorComponent › Messpunkt-Gruppen › should offer no group unless enabled; Test: messpunkt-gruppe.pipe.spec.ts › MesspunktGruppePipe › transform › should join the einheiten and append the messpunkt; E2E: messwerte-grafik.spec.ts › Messwerte-Grafik - Diagramm › should offer and draw a combined chart for units sharing a metering point)
* [x] Der Eintrag wählt seine Einheiten nicht mit; „Alle auswählen" schliesst ihn ein und hebt ihn wieder auf. (Test: einheit-selector.component.spec.ts › EinheitSelectorComponent › Messpunkt-Gruppen › should emit the selected groups and leave the units untouched; Test: einheit-selector.component.spec.ts › EinheitSelectorComponent › Messpunkt-Gruppen › should include the groups in select-all)
* [x] Mit nur einem gewählten Messpunkt ist „Anzeigen" freigegeben, beide Einheiten werden geladen, und es entsteht **ein** Panel mit dem Titel `Einheit [Typ] / Einheit [Typ] / [Messpunkt]`. (Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Messpunkt-Diagramm › should enable the submit button with only a messpunkt selected; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Messpunkt-Diagramm › should load both einheiten of a selected messpunkt and add one chart for it; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Messpunkt-Diagramm › should title the messpunkt panel with both einheiten and the messpunkt; E2E: messwerte-grafik.spec.ts › Messwerte-Grafik - Diagramm › should offer and draw a combined chart for units sharing a metering point)
* [x] Das Messpunkt-Diagramm zeigt eine Reihe je Einheit mit deren `total` im gespeicherten Vorzeichen, benannt nach der Einheit samt Summe im Schweizer Format, in zwei verschiedenen Token-Farben und **ohne** ZEV-Reihe; Achsen, Zoom und Stufenlinie sind dieselben wie im Diagramm einer Einheit. (Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Messpunkt-Diagramm › should draw one series per einheit with its stored sign and without a ZEV series; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Messpunkt-Diagramm › should share axes, zoom and step line with the single-unit chart)
* [x] Eine Einheit, die einzeln und über ihren Messpunkt gewählt ist, wird **einmal** geladen; die Diagramme der Einheiten stehen vor denen der Messpunkte, und die Erfolgsmeldung zählt die geladenen Einheiten. (Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Messpunkt-Diagramm › should load an einheit only once when it is selected alone and as part of its messpunkt)

**Vergleich (FR-9)**
* [x] „Vergleich" steht im Quartal-Selektor neben „Anzeigen" und ist gesperrt, solange weniger als zwei Einheiten zu laden wären; ein gewählter Messpunkt mit zwei Einheiten genügt. (Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Vergleich › should offer the button next to the submit button, disabled below two einheiten; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Vergleich › should disable the comparison while loading or without a complete period; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Vergleich › should enable the comparison for a single messpunkt with two einheiten; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Vergleich › should do nothing without two einheiten; E2E: messwerte-grafik.spec.ts › Messwerte-Grafik - Diagramm › should draw all selected units into one comparison chart)
* [x] Ein Klick auf „Vergleich" ersetzt die einzelnen Diagramme durch **ein** Panel `#chart-vergleich` mit dem Titel `Vergleich: <n> Einheiten`, das alle gewählten Einheiten in der Reihenfolge der Auswahl trägt. (Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Vergleich › should replace the single charts by one chart with all selected einheiten; E2E: messwerte-grafik.spec.ts › Messwerte-Grafik - Diagramm › should draw all selected units into one comparison chart)
* [x] Das Vergleichsdiagramm zeigt je Einheit eine Reihe mit ihrem `total` unter ihrem Namen samt Summe, ohne ZEV-Reihe. Die Reihen der **Produzenten** stehen dabei positiv — Werte und Summe gespiegelt —, alle übrigen Typen im gespeicherten Vorzeichen; im Messpunkt-Diagramm wird nichts gespiegelt. (Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › createChart › should draw one series per einheit into the comparison chart; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Vergleich › should mirror only producers, including the sum, and leave a zero untouched; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Vergleich › should not mirror producers unless asked to; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › createChart › should keep the stored sign of a producer in a messpunkt chart)
* [x] Eine Einheit, die einzeln und über ihren Messpunkt gewählt ist, wird für den Vergleich einmal geladen und einmal gezeichnet; die Meldung zählt die geladenen Einheiten. (Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Vergleich › should load each einheit once, also when it is selected through its messpunkt too)
* [x] „Anzeigen" bringt die einzelnen Diagramme zurück; Blättern und „Heute" bleiben in der Ansicht, die gerade steht – im Vergleich nur, solange mindestens zwei Einheiten gewählt sind, sonst erscheinen die einzelnen Diagramme. (Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Vergleich › should return to the single charts on Anzeigen; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Vergleich › should keep the comparison when paging and jumping to today; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Vergleich › should stay with the single charts when paging after returning from the comparison; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Vergleich › should fall back to the single chart when the selection shrank below two einheiten; E2E: messwerte-grafik.spec.ts › Messwerte-Grafik - Diagramm › should draw all selected units into one comparison chart)
* [x] Diagramme mit einer Reihe je Einheit vergeben sechs verschiedene Token-Farben; eine siebte Reihe trägt keine eigene Farbe (Palette der Bibliothek). (Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Messpunkt-Diagramm › should give six series their own token colour and leave a seventh to the library; Test: chart-farben.spec.ts › chartFarben › should offer a palette of six distinct colours for charts with one series per unit)

**Einspeisetarif (FR-10)**
* [x] Die Checkbox „Einspeisetarif" steht rechts von „Vergleich", ist anfangs nicht gesetzt und erscheint nur mit der Permission `tarife:manage` und aktivem Feature-Flag `PREISZEITREIHE`. (Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Einspeisetarif › should offer the checkbox right of the comparison button, unchecked; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Einspeisetarif › should hide the checkbox without the permission tarife:manage; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Einspeisetarif › should hide the checkbox while the feature PREISZEITREIHE is off; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Einspeisetarif › should disable the checkbox while loading; E2E: messwerte-grafik.spec.ts › Messwerte-Grafik - Diagramm › should show the feed-in tariff for the selected period)
* [x] Ohne Häkchen wird kein Preis abgerufen; mit Häkchen genau einmal je Laden für den gewählten Zeitraum, auch im Vergleich. (Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Einspeisetarif › should not ask for prices while the checkbox is off; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Einspeisetarif › should load the prices of the period once, together with the measurements; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Einspeisetarif › should ask for the prices once in the comparison too; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Einspeisetarif › should not ask for prices once the feature is switched off, even with the box ticked; E2E: messwerte-grafik.spec.ts › Messwerte-Grafik - Diagramm › should show the feed-in tariff for the selected period)
* [x] Umschalten bei stehenden Diagrammen lädt sofort neu, mit bzw. ohne Preis; ohne Diagramme wird nur die Wahl gemerkt. (Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Einspeisetarif › should reload with and without prices when toggled while charts are shown; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Einspeisetarif › should only remember the choice while no chart is shown; E2E: messwerte-grafik.spec.ts › Messwerte-Grafik - Diagramm › should show the feed-in tariff for the selected period)
* [x] Der Preis erscheint in Diagrammen einer Einheit wie in solchen mit einer Reihe je Einheit als zusätzliche, gestrichelte Stufenlinie `Einspeisetarif (CHF/kWh)` auf einer eigenen rechten Achse `Preis (CHF/kWh)`, mit Abschlusspunkt; ohne Preise bleibt das Diagramm unverändert (eine Achse). (Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Einspeisetarif › should add the tariff as a dashed step line on its own right axis; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Einspeisetarif › should leave the chart unchanged without prices; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Einspeisetarif › should add the tariff to charts with one series per einheit too; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Einspeisetarif › should draw the tariff in neutral grey and keep the grid to the energy axis; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › createChart › should draw the loaded tariff into the chart)
* [x] Im Tooltip steht der Preis mit fünf Nachkommastellen und ohne `kWh`. (Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Einspeisetarif › should format the tariff in the tooltip as a price with five decimals)
* [x] Scheitert der Preisabruf, erscheinen die Diagramme trotzdem, mit dem Key oder Klartext des Backends als Fehlermeldung bzw. `PREISE_ABRUF_FEHLGESCHLAGEN`; liefert er keine Preise, erscheint die Warnung `KEINE_PREISE_VORHANDEN`. (Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Einspeisetarif › should still show the measurements and report the cause when the prices fail; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Einspeisetarif › should show the key of the backend when the failure carries one; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Einspeisetarif › should fall back to PREISE_ABRUF_FEHLGESCHLAGEN when the failure carries no text; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Einspeisetarif › should warn when the period has no prices; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Einspeisetarif › should still show the comparison when the prices fail)

**Neu und geändert**
* [x] Mausrad/Touch zoomt innerhalb des geladenen Zeitraums, der Slider verschiebt den Ausschnitt — **ohne** neuen HTTP-Aufruf. (Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › optionen › should offer zoom inside and as a slider; E2E: messwerte-grafik.spec.ts › Messwerte-Grafik - Diagramm › should zoom with the mouse wheel without asking the server)
* [x] Die Linie ist eine **Stufenlinie**, ohne Flächenfüllung. (Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › reihe › should build a step line with sampling and without symbols)
* [x] Jede Reihe mit Messwerten endet mit einem Abschlusspunkt 15 Minuten nach ihrem letzten Messwert und mit dessen Wert, sodass auch ein einzelner Messwert als Stufe gezeichnet wird; eine leere Reihe bleibt leer. Der Tooltip zeigt den Abschlusspunkt nicht, und die Summe der Legende zählt ihn nicht. (Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Abschlusspunkt › should draw a single measurement as one quarter-hour step; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Abschlusspunkt › should extend every series by the end of its last interval; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Abschlusspunkt › should not add a point to an empty series; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Abschlusspunkt › should leave the closing point out of the tooltip)
* [x] Zahlen in Achsenbeschriftung, Tooltip und Legende stehen im Schweizer Format (Punkt als Dezimal-, Hochkomma als Tausendertrennzeichen). In `messwerte-chart.component.ts` kommt weder `toLocaleString` noch `toFixed` vor; die Formatierung läuft ausschliesslich über `formatSwissNumber`. (Projektweit sind beide zulässig — `number-utils.ts` verwendet `toFixed` selbst, und genau dort gehört es hin.) (Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › legende › should combine the translated name with the swiss formatted sum; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Abschlusspunkt › should leave the closing point out of the tooltip; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Achsenbeschriftung › should format the y axis labels in Swiss format; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Achsenbeschriftung › should format tooltip values from 1000 with the thousands separator)
* [x] Die Legendennamen kommen aus dem `TranslationService` (`TOTAL`, `ZEV`) und stehen beim Zeichnen in der aktiven Sprache. **Nicht** gefordert ist, dass ein Sprachwechsel ein bereits gezeichnetes Diagramm erneuert (FR-3, bekannte Einschränkung). (Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › legende › should combine the translated name with the swiss formatted sum; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Abschlusspunkt › should draw a single measurement as one quarter-hour step)
* [x] Achsen-, Gitter- und Reihenfarben stammen aus Design-Tokens. **Keine Diagrammfarbe** ist ein Literal; die Rückfallwerte im gemeinsamen Farb-Baustein sind erlaubt und erwünscht (ohne sie wäre das Diagramm unsichtbar, falls ein Token fehlt). (Test: chart-farben.spec.ts › chartFarben › should prefer the token over the fallback; Test: chart-farben.spec.ts › chartFarben › should fall back when a token is missing; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Messpunkt-Diagramm › should draw one series per einheit with its stored sign and without a ZEV series)
* [x] Der Titel der y-Achse kommt aus `KWH`, nicht aus festem Text; die Zeitachse trägt keinen Titel. (Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › optionen › should name the y axis from the translation service and leave the time axis untitled)
* [x] Das Diagramm folgt einer Grössenänderung des Panels (kein `responsive: false`, keine manuelle Canvas-Berechnung). (Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › createChart › should observe the container and resize the chart when it changes)
* [x] Beim Verlassen der Seite wird die ECharts-Instanz freigegeben und der `ResizeObserver` abgemeldet. (Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › ngOnDestroy › should dispose every instance and disconnect every observer)

**Sicherheit unverändert**
* [x] `/chart` bleibt mit der Permission `messwerte:read` erreichbar und ohne sie gesperrt (`composite-roles.spec.ts` deckt das mit einem Fall ab). (E2E: composite-roles.spec.ts › zev_user kann die Messwerte-Grafik ansehen (messwerte:read))

**Bundle und Abhängigkeit**
* [x] `chart.js` steht nicht mehr in `package.json`, und `grep -r "chart.js" frontend-service/src` findet nichts.
* [x] Nach dem Produktionsbau ist `main-*.js` **kleiner** als vorher; die Zahl wird gemessen und im Umsetzungsplan festgehalten. (Nachweis: EChart_Umsetzungsplan.md, „Bundle (Phasen 1 und 7)“)
* [x] ECharts liegt weiterhin **nicht** im Initial-Bundle, sondern in eigenen Chunks (dynamischer Import). (Nachweis: utils/echarts-loader.ts:37; EChart_Umsetzungsplan.md, „Bundle (Phasen 1 und 7)“)
* [x] Auf der Seite **Lizenzen** erscheint chart.js nicht mehr. (Nachweis: src/assets/frontend-licenses.json)

**Gemeinsame Bausteine**
* [x] Nachladen der Bibliothek, Farben und Zeitformatierung stehen **einmal** im Projekt und werden von beiden Diagrammen genutzt. (Test: echarts-loader.spec.ts › ladeECharts › should register the modules of both charts; Test: chart-farben.spec.ts › chartFarben › should deliver a colour for every purpose; Test: date-utils.spec.ts › Date Utils › formatSwissDateTime › should format a Date object)
* [x] Die Tests der Preiszeitreihe (Unit und E2E) laufen nach der Umstellung unverändert grün. (Test: preiszeitreihe-chart.component.spec.ts; E2E: preiszeitreihe.spec.ts)

**Fehlerfälle**
* [x] Scheitert das Nachladen der Bibliothek, erscheint eine Fehlermeldung, und die Seite bleibt bedienbar (Auswahl, Zeitraum, Navigation). (Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Nachladen scheitert › should report DIAGRAMM_NICHT_LADBAR once and keep the page usable; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Nachladen scheitert › should not try to load again after a failure)
* [x] Ein Zeitraum ohne Messwerte führt zu einer Meldung mit `0` Datenpunkten; das Panel entsteht, das Diagramm bleibt leer, die Seite bleibt bedienbar. (Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › optionen › should work with an empty data set; E2E: messwerte-grafik.spec.ts › Messwerte-Grafik - Diagramm › should stay usable for a period without measurements)
* [x] Ein Fehler beim Laden der Messwerte zeigt eine lesbare Meldung (kein `[object Object]`). (Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › onSubmit › should show a readable error when loading fails)

## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Performance
* Ein Quartal sind bis zu **8'640** Punkte je Reihe (90 × 96), zwei Reihen je Diagramm, mehrere Diagramme gleichzeitig. Mit `sampling: 'lttb'` bleibt die Darstellung flüssig; ohne Ausdünnung wäre sie es bei „Alle auswählen" nicht.
* Die Diagramme werden weiterhin **nacheinander** aufgebaut (heute `setTimeout`-Kette), damit die Maske bei vielen Einheiten nicht blockiert. Ob das mit ECharts noch nötig ist, entscheidet die Messung (§8).
* Das Initial-Bundle wird **kleiner**, nicht grösser (siehe Akzeptanzkriterien). Erwartung: chart.js schlägt im Bundle grob mit **70–200 kB** zu Buche (roh, nach Tree-Shaking) — das ist die Grössenordnung, an der die Messung zu beurteilen ist. Fällt die Ersparnis deutlich kleiner aus, ist zu prüfen, ob noch etwas anderes chart.js hereinzieht. Zahlenwerte kommen aus dem Produktionsbau vor und nach dem Umbau.

### NFR-2: Sicherheit
* **Unverändert:** Route `/chart` verlangt die Permission `messwerte:read` (`AuthGuard`, `app.routes.ts`) — Fachrollen `zev_user`, `org_admin`, `zev_admin`. Die Daten kommen weiterhin über `GET /api/messwerte/by-einheit` mit `hasAuthority('messwerte:read')`.
* Keine neuen Endpunkte und keine neuen Berechtigungen. Der Einspeisetarif (FR-10) nutzt den bestehenden Endpunkt `GET /api/preiszeitreihe` mit dessen Absicherung: Permission `tarife:manage` und Feature-Flag `PREISZEITREIHE`. Die Checkbox erscheint nur, wenn beides erfüllt ist; massgeblich bleibt die Prüfung im Backend. `zev_user` sieht die Grafik damit ohne Einspeisetarif.
* Der dynamische Import lädt ausschliesslich lokal gebündelte Chunks — **kein** CDN, keine externe Quelle.

### NFR-3: Kompatibilität
* **Keine Änderung am Schema.** Zwei Migrationen mit Übersetzungs-Keys: `V191` (`GRAFIK_VERGLEICH`, FR-9) und `V192` (`GRAFIK_EINSPEISETARIF`, `GRAFIK_EINSPEISETARIF_CHF_KWH`, FR-10); alle übrigen Keys existieren.
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
| Vertauschter Zeitraum | Wie heute: Fehlermeldung, kein Server-Aufruf, kein Diagramm; ◀ / ▶ gesperrt |
| Blättern oder „Heute" ohne Auswahl | Schaltflächen gesperrt, kein Server-Aufruf (FR-1a.5) |
| Kein Messpunkt mit mehreren Einheiten | Die Auswahl zeigt nur die Einheiten, wie bisher |
| Messpunkt gewählt, eine seiner Einheiten ohne Messwerte | Das Diagramm zeigt die andere Reihe; die leere steht mit Summe 0 in der Legende |
| Mehr als zwei Einheiten am selben Messpunkt | Ein Eintrag und ein Diagramm mit einer Reihe je Einheit, in den Reihenfarben aus FR-4 |
| Vergleich mit mehr als sechs Einheiten | Die ersten sechs Reihen tragen die Token-Farben, weitere die Palette von ECharts; die Legende blättert, wenn sie nicht in eine Zeile passt |
| „Vergleich" mit nur einer Einheit | Schaltfläche gesperrt (FR-9.2) |
| Auswahl nach einem Vergleich auf eine Einheit verkleinert, dann geblättert | Es erscheint das einzelne Diagramm dieser Einheit; die Ansicht steht danach wieder auf „einzeln" (FR-9.6) |
| Produzent im Vergleich, der netto bezieht (z. B. Wechselrichter lädt die Batterie aus dem Netz, `total` positiv) | Gespiegelt wie jeder Wert des Produzenten: Die Reihe liegt in diesen Intervallen unter der Nulllinie |
| Zwei Einheiten mit demselben Namen im Vergleich | Beide Reihen tragen denselben Legendentext; unterscheidbar sind sie nur an der Farbe |
| Einspeisetarif verlangt, Zeitraum ohne Preise | Diagramme ohne Preisreihe, Warnung `KEINE_PREISE_VORHANDEN` (FR-10.7) |
| Einspeisetarif verlangt, Zeitraum länger als 366 Tage | Die Preiszeitreihe lehnt ab; Diagramme ohne Preisreihe, Fehlermeldung mit dem Text des Backends |
| Einspeisetarif verlangt, Preise decken nur einen Teil des Zeitraums | Die Preislinie steht nur dort, wo es Preise gibt |
| Benutzer ohne `tarife:manage` oder Flag `PREISZEITREIHE` aus | Keine Checkbox, kein Preisabruf; die Grafik funktioniert wie zuvor |
| Gleicher Messpunkt, unterschiedlich geschrieben (`BKW` / `bkw`) | Keine Gruppe — es sind zwei Messpunkte, wie in der Einheitenverwaltung |
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
* Für den Einspeisetarif (FR-10): `PreiszeitreiheService` (`GET /api/preiszeitreihe`, `Specs/Preiszeitreihe.md`), Feature-Flag `PREISZEITREIHE` (`FeatureFlagService`, `*appFeature`), Permission `tarife:manage` aus dem Keycloak-Token (`Specs/Berechtigungen.md`, Fussnote ¹⁰).

**Betroffener Code**
| Datei | Änderung |
|---|---|
| `components/messwerte-chart/messwerte-chart.component.ts` | Kern des Umbaus: ECharts statt chart.js, Formatierung, Farben, Zoom, `ResizeObserver` |
| `components/messwerte-chart/messwerte-chart.component.html` | `<canvas>` → `<div>` als Diagramm-Behälter |
| `components/messwerte-chart/messwerte-chart.component.spec.ts` | Zeichnen stubben (jsdom hat kein Canvas), Optionen als reine Funktion prüfen — Vorbild `preiszeitreihe-chart.component.spec.ts` |
| `components/preiszeitreihe-chart/preiszeitreihe-chart.component.ts` | nutzt die gemeinsamen Bausteine statt eigener Kopien |
| `utils/echarts-loader.ts`, `utils/chart-farben.ts` | neu |
| `utils/messpunkt-gruppen.ts`, `pipes/messpunkt-gruppe.pipe.ts` | neu: Gruppen je Messpunkt bilden und bezeichnen (FR-8) |
| `components/einheit-selector/` | Zusatzeinträge je Messpunkt, abschaltbar (`mitMesspunktGruppen`); Rechnungen unverändert |
| `models/einheit.model.ts` | neu: `MesspunktGruppe` |
| `utils/chart-preis.ts` | neu: gemeinsame Preis-Bausteine (FR-5, FR-10) |
| `components/preiszeitreihe-chart/`, `components/einspeisesteuerung/` | nutzen `formatPreis` und `preisPunkte` statt eigener Kopien — verhaltensneutral |
| `V191`, `V192` (Migrationen) | Übersetzungs-Keys für Vergleich und Einspeisetarif |
| `frontend-service/package.json` (+ Lockfile) | `chart.js` entfernen |

**Datenmigration**
* Keine.

**Tests**
* Frontend-Unit: `messwerte-chart.component.spec.ts` umstellen (18 Tests), neue Tests für die gemeinsamen Bausteine.
* E2E: Die 14 ursprünglichen Tests in `messwerte-grafik.spec.ts` bleiben in ihren Rümpfen **unverändert** — das ist Absicht und gleichzeitig das stärkste Akzeptanzkriterium. Ergänzt wird je ein Test für Zoom, Bedienung (FR-1a), Messpunkt-Diagramm (FR-8), Vergleich (FR-9) und Einspeisetarif (FR-10).

## 7. Abgrenzung / Out of Scope

* **Keine weiteren Funktionen** in der Messwerte-Grafik über Zoom, die Bedienung aus FR-1a, das Messpunkt-Diagramm aus FR-8 und den Vergleich aus FR-9 hinaus: kein Export (auch nicht der PDF-Export der Statistik), keine Kennzahlen, keine Änderung des Zeitraums oder der geladenen Daten.
* **Kein Saldo je Messpunkt.** Das Messpunkt-Diagramm zeigt die Einheiten nebeneinander; eine verrechnete Reihe (Bezug minus Rücklieferung) gibt es nicht.
* **Keine Summe im Vergleich.** Das Vergleichsdiagramm (FR-9) zeigt die Einheiten nebeneinander; eine aufsummierte Reihe über alle gibt es nicht.
* **Kein Mischbetrieb.** Vergleich und einzelne Diagramme stehen nie gleichzeitig auf der Seite.
* **Nur der dynamische Einspeisepreis** (FR-10). Die festen Tarife der Tarifverwaltung (ZEV, VNB) zeigt die Grafik nicht, ebenso keinen Ertrag in Franken (Energie × Preis).
* **Kein Abruf bei der Preisquelle.** Die Grafik liest die gespeicherte Preiszeitreihe; aktualisiert wird sie in der Tarifverwaltung bzw. durch den Job.
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

## Anhang A – Entscheidungen und Verlauf

### A.1 Bedienung wie in der Statistik (FR-1, FR-1a, 06.11.2026)

Vorher galt FR-1 „Ablauf / Flow — unverändert": *„Die Bedienung bleibt **exakt** wie heute; der Wechsel ist für den Benutzer nur an der Darstellung und am neuen Zoom erkennbar."* Das betraf den Bibliothekswechsel von chart.js auf ECharts und ist mit ihm erfüllt. „Anzeigen" stand damals in der Zeile der Datumsfelder, Blättern und „Heute" gab es nicht. Das Kriterium dazu lautete „Die Bedienung ist unverändert: …".

Am 06.11.2026 wurde die Bedienung auf Wunsch des Benutzers an die Statistik angeglichen, damit beide Seiten mit Zeitraumwahl gleich bedient werden. Den PDF-Export der Statistik übernimmt die Grafik bewusst nicht.

### A.2 Gemeinsames Diagramm je Messpunkt (FR-8, 20.11.2026)

Vorher zeichnete die Seite ausschliesslich ein Diagramm je Einheit, und §7 schloss „zusätzliche Reihen" und jede „Änderung der Datenauswahl" aus. Bezug und Rücklieferung desselben Zählers liessen sich nur in zwei Panels untereinander vergleichen.

Am 20.11.2026 kam auf Wunsch des Benutzers der Zusatzeintrag je Messpunkt dazu. Zwei Festlegungen stammen aus der Umsetzung, nicht aus dem Wunsch: Das Diagramm zeigt je Einheit nur `total` (ohne ZEV-Reihe), und „Alle auswählen" schliesst die Messpunkt-Einträge ein — Letzteres, damit „alle" weiterhin alle Kästchen der Liste meint.

### A.3 Kein Titel an der Zeitachse (FR-2, 21.11.2026)

Vorher trug die x-Achse den Titel „Zeit“ (`ZEIT`), mittig unter der Achse. Dort liegt auch der Zoom-Schieber; der Titel stand mitten in ihm und war kaum lesbar. Das Kriterium lautete „Die Achsentitel kommen aus `ZEIT` und `KWH`“. Der Titel ist am 21.11.2026 auf Wunsch des Benutzers entfallen. Der Übersetzungs-Key `ZEIT` bleibt bestehen.

### A.4 Abschlusspunkt der Stufenlinie (FR-2, 21.11.2026)

Vorher endete jede Reihe mit ihrem letzten Messwert. Weil die Stufenlinie einen Wert erst bis zum nächsten Punkt zeichnet, fehlte die letzte Viertelstunde im Bild. Aufgefallen ist es am 21.11.2026 an der frisch angelegten Einheit „Verbrauch gesamt (Solar-Log)“: Sie hatte genau einen Messwert (23:15, 0.140 kWh), die Legende nannte die Summe, das Diagramm blieb leer.

### A.5 Vergleich aller gewählten Einheiten (FR-9, 22.11.2026)

Vorher liessen sich mehrere Einheiten nur dann in einem Diagramm sehen, wenn sie denselben Messpunkt trugen (FR-8); §7 schloss „frei zusammengestellte Diagramme" ausdrücklich aus. Am 22.11.2026 kam auf Wunsch des Benutzers die Schaltfläche dazu, die alle gewählten Einheiten in einem Diagramm zeigt. Der Wunsch nannte sie „Vereinigung"; gewählt wurde „Vergleich", weil das Diagramm die Einheiten nebeneinander stellt und nichts zusammenzählt.

Aus der Umsetzung stammen: die Mindestzahl von zwei Einheiten, dass Blättern und „Heute" in der stehenden Ansicht bleiben, und die sechs Reihenfarben. Bis dahin gab es für Diagramme mit einer Reihe je Einheit nur zwei Token-Farben; eine dritte Einheit am selben Messpunkt bekam die Palette von ECharts.

`NFR-3` lautete „keine Migration, keine neuen Übersetzungs-Keys" — das galt für den Bibliothekswechsel und gilt mit `V191` nicht mehr wörtlich.

### A.6 Einspeisetarif in der Messwerte-Grafik (FR-10, 22.11.2026)

Vorher zeigte die Grafik ausschliesslich Energie; den Einspeisepreis gab es nur in der Tarifverwaltung (Preiszeitreihe) und in der Einspeisesteuerung. `NFR-2` lautete „Keine neuen Endpunkte, keine neuen Berechtigungen, kein Feature-Flag" — die Seite hing an keinem Flag. `NFR-3` nannte `V191` als einzige Migration. Am 22.11.2026 kam auf Wunsch des Benutzers die Checkbox dazu.

Aus der Umsetzung stammen: „Einspeisetarif" meint den dynamischen Einspeisepreis der Preiszeitreihe (einen anderen Einspeisetarif kennt die Anwendung nicht); die Bindung der Checkbox an `tarife:manage` und das Flag `PREISZEITREIHE`, weil der Endpunkt beides verlangt; die Darstellung als graue, gestrichelte Linie auf eigener Achse; und dass ein gescheiterter Preisabruf die Messwerte nicht mitreisst.

### A.7 Produzenten im Vergleich positiv (FR-9.3, 22.11.2026)

Vorher zeigte der Vergleich jede Einheit im gespeicherten Vorzeichen: „Produzenten, Rücklieferung und entladende Speicher liegen damit unter der Nulllinie." Das Kriterium lautete „… je Einheit eine Reihe mit ihrem `total` unter ihrem Namen samt Summe, ohne ZEV-Reihe". Im ersten Gebrauch (Verbrauch gesamt, Bezug und PV-Anlage in einem Diagramm) lag die Produktion als einzige Kurve unter der Nulllinie und liess sich mit den beiden anderen nicht vergleichen. Auf Wunsch des Benutzers stehen die Produzenten seither positiv.

Aus der Umsetzung stammt die Abgrenzung: gespiegelt werden nur Einheiten vom Typ Produzent, nicht Rücklieferung und Speicher, und nur im Vergleich, nicht im Messpunkt-Diagramm.
