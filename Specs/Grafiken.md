# Grafiken

## 1. Ziel & Kontext - Warum wird das Feature benötigt?

* **Was soll erreicht werden:** Die **Messwerte-Grafik** (`/chart`) zeigt die 15-Minuten-Messwerte der Einheiten als Diagramme: eines je Einheit, eines je Messpunkt mit mehreren Einheiten (FR-8) oder alle gewählten Einheiten in einem Vergleich (FR-9). Auf Wunsch blendet sie den dynamischen Einspeisepreis ein (FR-10). Innerhalb des geladenen Zeitraums lässt sich zoomen.
* **Warum machen wir das:** Verbrauch, Produktion, Bezug und Rücklieferung sollen im zeitlichen Verlauf sichtbar und miteinander vergleichbar sein. Ein Quartal sind rund **8'600 Punkte je Einheit**, mehrere Einheiten gleichzeitig — ohne Zoom und Ausdünnung wäre das nicht lesbar.
* **Aktueller Stand:**
  - Die Diagramme zeichnet **ECharts** (`echarts` 6.1.0, Apache-2.0), dynamisch nachgeladen und damit ausserhalb des Initial-Bundles. Es ist die einzige Diagramm-Bibliothek der Anwendung.
  - Nachladen der Bibliothek, Farben aus den Design-Tokens, Zeit- und Preisformatierung sind gemeinsame Bausteine (FR-5); dieselben nutzen die Preiszeitreihe (`Specs/Preiszeitreihe.md`) und die Einspeisesteuerung (`Specs/Einspeisesteuerung.md`).

## 2. Funktionale Anforderungen (FR) - Was soll das System tun?

### FR-1: Ablauf / Flow

1. Benutzer öffnet **Messwerte-Grafik** (`/chart`, Permission `messwerte:read`).
2. Er wählt Einheiten und — wo vorhanden — Messpunkte mit mehreren Einheiten (FR-8; Mehrfachauswahl samt „Alle auswählen") und einen Zeitraum (Quartals-Schaltflächen, Datum von/bis, Blättern oder „Heute", FR-1a; `Datum von` zieht `Datum bis` auf das Monatsende nach).
3. Klick auf **Anzeigen** lädt die Messwerte je Einheit und zeichnet **ein Diagramm je Einheit** in einem eigenen Panel mit Titel `Name [Typ]`, danach **eines je gewähltem Messpunkt** (FR-8). Klick auf **Vergleich** zeichnet stattdessen **ein** Diagramm mit allen gewählten Einheiten (FR-9). Die Checkbox **Einspeisetarif** blendet in jedes Diagramm den dynamischen Einspeisepreis ein (FR-10). Blättern und „Heute" laden selbst, in der Ansicht, die gerade steht.
4. Eine Erfolgsmeldung nennt die Zahl der geladenen Datenpunkte und der Einheiten.
5. Innerhalb des geladenen Zeitraums kann der Benutzer zoomen und den Ausschnitt verschieben — ohne neuen Server-Aufruf.

### FR-1a: Bedienung wie in der Statistik

Schaltflächen und Datumsfelder sind angeordnet und verhalten sich wie auf der Statistik-Seite (`Specs/Statistik.md`, FR-2 bis FR-4) — **ohne** „PDF exportieren", die Grafik hat keinen Export. Wer beide Seiten nutzt, findet dieselben Knöpfe an derselben Stelle.

1. **„Anzeigen"** steht rechts neben den Quartalsknöpfen (Aktions-Slot des Quartal-Selektors). Gesperrt während des Ladens, ohne beide Daten oder ohne gewählte Einheit; während des Ladens mit dem Text `LADE_DATEN`.
2. In der Zeile der Datumsfelder stehen **◀ / ▶** (Icons `chevron-left`/`chevron-right`, Tooltip und `aria-label` aus `ZEITRAUM_ZURUECK`/`ZEITRAUM_VOR`) und danach **„Heute"** (Icon `calendar`, Text `HEUTE`).
3. **Blättern** verschiebt den Zeitraum um seine eigene Länge — dieselbe Regel wie in der Statistik (`blaettereZeitraum`, `Specs/Statistik.md`, FR-4.1) — und **lädt sofort**, in der Ansicht, die gerade steht (einzelne Diagramme oder Vergleich, FR-9).
4. **„Heute"** setzt `Datum von` und `Datum bis` auf das heutige lokale Datum und **lädt sofort**; auch ein zweiter Klick lädt (aktualisieren).
5. **Abweichung von der Statistik:** Blättern und „Heute" sind **ohne Auswahl gesperrt** (weder Einheit noch Messpunkt gewählt) — das sofortige Laden liefe sonst nur in die Fehlermeldung. Die Statistik kennt keine Einheitenauswahl.
6. Die Grafik zeigt die **Erfolgsmeldung** mit der Zahl der Datenpunkte (FR-1.4) — anders als in der Statistik, wo das Ergebnis selbst die Rückmeldung ist. Hier ist die Zahl eine eigene Information: Ein leerer Zeitraum ergibt ein leeres Diagramm, die Meldung sagt „0".

### FR-2: Darstellung

* **Zwei Datenreihen je Diagramm einer Einheit:** `Total` und `ZEV`, beide als **gefüllte Fläche** im gespeicherten Vorzeichen. Der ZEV-Anteil — der Teil, den die eigene Produktion deckt — liegt in der zweiten Farbe **über** der Fläche des Totals und überdeckt sie; was vom Total sichtbar bleibt, kommt aus dem Netz. Die Legende nennt je Reihe die Summe in kWh; hat eine Reihe im Zeitraum **keine Messwerte**, steht `(Σ –)` statt `(Σ 0.000 kWh)` — in jedem Diagramm.
  > Der gedeckte Verbrauch ist so auf einen Blick vom Netzbezug zu unterscheiden. Bei einem Produzenten liegen beide Flächen unter der Nulllinie: Der ZEV-Anteil ist dort der Teil der Produktion, der im ZEV verbraucht wird.
* **Stufenlinie** (`step: 'end'`): Ein Messwert gilt für die **ganze** Viertelstunde. Eine geglättete Linie behauptete einen stetigen Verlauf zwischen den Intervallen, den es nicht gibt.
* **Auch das letzte Intervall ist zu sehen:** Jede Reihe endet mit einem Abschlusspunkt am Ende ihres letzten Intervalls (Beginn + 15 Minuten) mit dessen Wert. Er ist kein Messwert: Der Tooltip überspringt ihn, und in die Summe der Legende geht er nicht ein.
  > Eine Stufenlinie zeichnet jeden Wert bis zum nächsten Punkt. Ohne Abschlusspunkt fehlte die letzte Viertelstunde, und eine Reihe mit einem einzigen Messwert bliebe leer.
* **Lücken stehen auf 0:** Fehlt zwischen zwei Messwerten einer Reihe eine Viertelstunde, liegt die Linie dort auf 0; der Tooltip nennt an dieser Stelle einen Halbgeviertstrich `–` statt `0.000 kWh`. Ein gemessener Wert 0 bleibt `0.000 kWh`. Vor dem ersten und nach dem letzten Messwert einer Reihe wird nichts ergänzt. Die Summe der Legende bleibt die der Messwerte. Gezählt wird in Ortszeit: Die Nacht der Zeitumstellung ergibt keine Lücke.
  > Eine Stufenlinie zöge sonst den letzten Wert über die ganze Lücke und zeigte Energie, die nie gemessen wurde. Am Strich erkennt der Benutzer, dass kein Messwert vorhanden ist. Zähler, die nur bei Energiefluss melden (z. B. der Bezug vom Netz), haben solche Lücken regelmässig.
* **Flächenfüllung nur für Total und ZEV-Anteil:** Gefüllt sind die beiden Reihen im Diagramm einer Einheit und der gedeckte Verbrauch im Vergleich (FR-9.3). Die Reihen je Einheit im Messpunkt-Diagramm und im Vergleich sowie der Einspeisetarif sind Linien ohne Füllung.
* **Legendensymbol nach Art der Reihe:** Eine gefüllte Reihe trägt in der Legende ein **gefülltes Rechteck**, eine Linie das Liniensymbol.
  > Im Vergleich haben die Linie einer Einheit und ihr gedeckter Verbrauch dieselbe Farbe; am Symbol ist zu sehen, welcher Eintrag zu welcher Kurve gehört.
* **x-Achse:** Zeit, **ohne Achsentitel**; **y-Achse:** Energie mit dem Titel `KWH` aus dem `TranslationService`.
  > Unter der Achse liegt der Zoom-Schieber, ein Titel stünde mitten in ihm; dass die Achse die Zeit zeigt, sagen die Uhrzeiten an ihr.
* **Zoom:** `dataZoom` mit `inside` (Mausrad/Touch) **und** `slider` — dieselbe Konfiguration wie bei der Preiszeitreihe.
* **Ausdünnung:** `sampling: 'lttb'`, dauerhaft aktiv (NFR-1) — bei 96 Punkten unschädlich, bei ~8'600 Punkten je Reihe und mehreren Diagrammen auf einer Seite nötig.
* **Modularer, dynamischer Import.** Der gemeinsame Loader registriert die **Vereinigung** aller von den Diagrammen benötigten Module — `LineChart`, `BarChart`, `GridComponent`, `TooltipComponent`, `DataZoomComponent`, `LegendComponent`, `MarkAreaComponent` (Zustandsbänder der Einspeisesteuerung), `CanvasRenderer` — und nicht je Aufrufer eine eigene Auswahl (FR-5). Ein nicht registriertes Modul **zeichnet stumm nichts**: ECharts meldet keinen Fehler, und eine je Aufrufer verschiedene Liste würde diese Falle vervielfachen. Der Preis ist ein etwas grösserer gemeinsamer Chunk — er wird ohnehin nur beim Öffnen eines Diagramms geladen.

### FR-3: Zahlen und Texte

* **Alle** Zahlen im Schweizer Format über `formatSwissNumber()` aus `src/app/utils/number-utils.ts` — Achsenbeschriftung, Tooltip und die Summen in der Legende. **Kein** `toLocaleString()`, **kein** `toFixed()`, keine ECharts-eigene Locale-Formatierung.
* **Zeitpunkte** im Format `dd.MM.yyyy HH:mm`, manuell gebildet (kein `toLocaleString`).
* **Alle** Texte über den `TranslationService`, auch die Legendennamen und der Achsentitel: `TOTAL`, `ZEV`, `KWH`. Ebenso Lade- und Fehlerzustand des Diagramms: `DIAGRAMM_LAEDT` und `DIAGRAMM_NICHT_LADBAR`, die Bedienung (FR-1a): `ANZEIGEN`, `LADE_DATEN`, `ZEITRAUM_ZURUECK`, `ZEITRAUM_VOR`, `HEUTE`, und der Vergleich (FR-9): `GRAFIK_VERGLEICH`, `EINHEITEN`. `GRAFIK_VERGLEICH` kommt aus der Migration `V191`. Der Einspeisetarif (FR-10) nutzt `GRAFIK_EINSPEISETARIF` und `GRAFIK_EINSPEISETARIF_CHF_KWH` (beide `V192`) sowie `PREIS_CHF_KWH`, `KEINE_PREISE_VORHANDEN` und `PREISE_ABRUF_FEHLGESCHLAGEN`. Alle übrigen Keys stammen aus anderen Migrationen.
* **Aufgelöst wird beim Zeichnen.** Ein Sprachwechsel während ein Diagramm offen steht erneuert die Beschriftung **nicht**: `TranslationService.setLanguage()` setzt ein Signal, das Template-Texte erneuert — ein gezeichnetes Canvas nicht. Die Legende trägt bis zum nächsten Zeichnen die alte Sprache. Das ist eine **bekannte Einschränkung** (§5); sie gilt für alle Diagramme der Anwendung gleich, und wenn sie behoben wird, dann für alle gemeinsam.

### FR-4: Farben und Dark Mode

* Achsen-, Gitter- und Reihenfarben aus den Design-Tokens (`--color-*`), nicht hart kodiert. Die Neutraltöne kippen im Dark Mode (`--color-gray-600` ist dort hell) — genau deshalb kommen sie aus den Tokens.
* Die beiden Reihen bleiben visuell unterscheidbar: `Total` in der Primärfarbe, `ZEV` in der Sekundärfarbe (`--color-secondary`).
* Diagramme mit **einer Reihe je Einheit** (FR-8, FR-9) vergeben der Reihe nach die Reihenpalette aus `utils/chart-farben.ts` (`reihen`): Primär, Sekundär, Rot (`--color-danger`), Gelb, Orange und Violett (`--color-chart-*`). Es sind dieselben Tokens, die die Einspeisesteuerung zweckgebunden verwendet; hier gibt es weder Bänder noch benannte Grössen, die Reihen müssen sich nur voneinander unterscheiden. Eine siebte und weitere Reihe bekommt die Palette von ECharts — unterscheidbar, statt eine Farbe zweimal zu vergeben.

### FR-5: Gemeinsame Bausteine

Nachladen der Bibliothek, Farbzugriff, Zeit- und Preisformatierung stehen **einmal** und werden von allen Diagrammen genutzt (Messwerte-Grafik, Preiszeitreihe, Einspeisesteuerung):

| Baustein | Inhalt |
|---|---|
| `utils/echarts-loader.ts` | dynamischer Import samt `core.use([...])`, gemerkt für weitere Aufrufe; liefert `null`, wenn das Nachladen scheitert |
| `utils/chart-farben.ts` | Farben aus den Design-Tokens mit Rückfallwerten |
| `utils/chart-preis.ts` | Preis in CHF/kWh mit fünf Nachkommastellen (`formatPreis`) und Umrechnung der Preiszeitreihe in Datenpunkte (`preisPunkte`) — für Preiszeitreihe, Einspeisesteuerung und Messwerte-Grafik (FR-10) |
| `utils/date-utils.ts` | `formatSwissDateTime()` für `dd.MM.yyyy HH:mm` |

### FR-6: Entfallen

### FR-7: Grössenanpassung

Das Diagramm füllt sein Panel und folgt Grössenänderungen über einen `ResizeObserver`. Beim Verlassen der Seite wird die Instanz freigegeben (`dispose()`), der Observer abgemeldet.

Während die Bibliothek nachgeladen wird, zeigt das Panel `DIAGRAMM_LAEDT`; scheitert das Nachladen, erscheint `DIAGRAMM_NICHT_LADBAR` als Fehlermeldung und die Seite bleibt bedienbar.

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

   > Mit einer Einheit gäbe es nichts zu vergleichen.
3. **Was erscheint:** genau ein Panel mit dem Titel `Vergleich: <n> Einheiten`. Darin **eine Reihe je Einheit** mit ihrem `total`, benannt nach der Einheit samt Summe — derselbe Aufbau wie das Messpunkt-Diagramm (FR-8.5, FR-8.6), mit diesen Abweichungen:
   * **Produzenten stehen positiv:** Ihre Reihe und ihre Summe werden gespiegelt (gespeichert ist die Produktion negativ), damit die Produktion über der Nulllinie neben Verbrauch und Bezug steht und sich mit ihnen vergleichen lässt.
   * Alle übrigen Typen behalten das gespeicherte Vorzeichen: Rücklieferung und entladende Speicher liegen unter der Nulllinie.
   * Im Messpunkt-Diagramm (FR-8) wird nichts gespiegelt.
   * **Gedeckter Verbrauch:** Jeder **Konsument** mit einem ZEV-Anteil im Zeitraum erhält zusätzlich eine gefüllte Fläche `<Name> – ZEV` samt Summe, in der Farbe seiner Linie. Die Linie zeigt den ganzen Verbrauch, gefüllt ist der Teil, den die eigene Produktion deckt. Die Flächen stehen in Legende und Tooltip nach den Reihen der Einheiten. Konsumenten ohne ZEV-Anteil und alle übrigen Typen erhalten keine Fläche.
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

**Auswahl und Diagramme**
* [x] Die Datenauswahl umfasst: Einheiten-Mehrfachauswahl, „Alle auswählen", Quartals-Schaltflächen, `Datum von` zieht `Datum bis` auf das Monatsende, gesperrte Schaltfläche ohne Auswahl, Fehlermeldung bei vertauschtem Zeitraum. (Test: einheit-selector.component.spec.ts › EinheitSelectorComponent › onSelectAllToggle (default - alle) › should select all einheiten including producers; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › onQuarterSelected › should set dateFrom and dateTo from event; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › onDateFromChange › should set dateTo to last day of dateFrom month; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › onSubmit › should show error when dateFrom is after dateTo; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Bedienung wie Statistik › should keep the submit button disabled until a unit is selected; E2E: messwerte-grafik.spec.ts › Messwerte-Grafik - Auswahl und Validierung › should keep the submit button disabled without a unit selection; E2E: messwerte-grafik.spec.ts › Messwerte-Grafik - Auswahl und Validierung › should select every unit via the select-all checkbox)
* [x] Ein Panel je gewählter Einheit mit Titel `Name [Typ]`; die Erfolgsmeldung nennt Datenpunkte und Einheiten. (Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › onSubmit › should create one chart entry per selected einheit; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Messpunkt-Diagramm › should title the messpunkt panel with both einheiten and the messpunkt; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Messpunkt-Diagramm › should load an einheit only once when it is selected alone and as part of its messpunkt; E2E: messwerte-grafik.spec.ts › Messwerte-Grafik - Diagramm › should draw one chart per selected unit; E2E: messwerte-grafik.spec.ts › Messwerte-Grafik - Diagramm › should report the number of loaded data points)
* [x] Im Diagramm einer Einheit erscheinen `Total` und `ZEV` als zwei gefüllte Flächen in zwei Farben, beide im gespeicherten Vorzeichen; die ZEV-Fläche ist dichter als die des Totals. Bei einem Produzenten liegen beide unter der Nulllinie. (Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › optionen › should build two filled series, the ZEV share inside the total; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › optionen › should keep the stored sign of a producer, total and ZEV share both below zero; E2E: messwerte-grafik.spec.ts › Messwerte-Grafik - Diagramm › should paint more when the period contains measurements)

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
* [x] Das Vergleichsdiagramm zeigt je Einheit eine Reihe mit ihrem `total` unter ihrem Namen samt Summe. Die Reihen der **Produzenten** stehen dabei positiv — Werte und Summe gespiegelt —, alle übrigen Typen im gespeicherten Vorzeichen; im Messpunkt-Diagramm wird nichts gespiegelt. (Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › createChart › should draw one series per einheit into the comparison chart; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Vergleich › should mirror only producers, including the sum, and leave a zero untouched; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Vergleich › should not mirror producers unless asked to; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › createChart › should keep the stored sign of a producer in a messpunkt chart)
* [x] Im Vergleich erhält jeder Konsument mit ZEV-Anteil im Zeitraum nach den Reihen der Einheiten eine gefüllte Fläche `<Name> – ZEV` samt Summe in der Farbe seiner Linie (für die ersten sechs Einheiten, siehe Abschnitt 5); Produzenten und Konsumenten ohne ZEV-Anteil erhalten keine, und das Messpunkt-Diagramm bleibt ohne Flächen. (Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Vergleich › should add the covered share of each consumer as an area in its colour, after all lines; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Vergleich › should not add covered areas to a messpunkt chart; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › createChart › should draw one series per einheit into the comparison chart)
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

**Darstellung**
* [x] Mausrad/Touch zoomt innerhalb des geladenen Zeitraums, der Slider verschiebt den Ausschnitt — **ohne** neuen HTTP-Aufruf. (Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › optionen › should offer zoom inside and as a slider; E2E: messwerte-grafik.spec.ts › Messwerte-Grafik - Diagramm › should zoom with the mouse wheel without asking the server)
* [x] Jede Reihe ist eine **Stufenlinie**; eine Fläche bis zur Nulllinie trägt sie nur, wo sie verlangt ist. (Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › reihe › should build a step line with sampling and without symbols; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › reihe › should fill the area down to zero only when an opacity is given)
* [x] Fehlende Viertelstunden zwischen zwei Messwerten stehen mit 0 in der Reihe (jede einzeln, als Lücke gekennzeichnet); der Tooltip nennt dort `–`, bei einem gemessenen Wert 0 weiterhin `0.000 kWh`; lückenlose Reihen, die Ränder und die Summe bleiben unverändert — im Diagramm einer Einheit wie je Einheit im Vergleich samt gedecktem Verbrauch; die Zeitumstellung auf Winterzeit erzeugt keine Lücke. (Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Lücken › should fill every missing interval between two values with zero; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Lücken › should not add points to a series without gaps; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Lücken › should fill gaps per einheit in a comparison, also for a mirrored producer; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Lücken › should show a dash instead of zero in the tooltip of a filled interval; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Lücken › should keep a measured zero as a number in the tooltip; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Lücken › should not invent a gap in the night of the change to winter time; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Lücken › should fill a real gap across the change to winter time; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Lücken › should fill gaps in the covered share of a comparison)
* [x] Eine Reihe ohne Messwerte im Zeitraum trägt in der Legende `(Σ –)`; eine Summe 0 aus vorhandenen Messwerten bleibt `(Σ 0.000 kWh)`. (Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › optionen › should show a dash instead of a sum when there are no measurements; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › optionen › should keep a sum of zero as a number when measurements exist; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Messpunkt-Diagramm › should draw an empty series when an einheit has no measurements; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Vergleich › should show a dash as the sum of a producer without measurements)
* [x] Eine gefüllte Reihe trägt in der Legende ein gefülltes Rechteck, eine Linie das Liniensymbol; kein Eintrag fehlt. (Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Vergleich › should mark filled series with a filled legend symbol and list every series; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Vergleich › should keep the einspeisetarif in the legend, as a line, and mark total and ZEV as filled)
* [x] Jede Reihe mit Messwerten endet mit einem Abschlusspunkt 15 Minuten nach ihrem letzten Messwert und mit dessen Wert, sodass auch ein einzelner Messwert als Stufe gezeichnet wird; eine leere Reihe bleibt leer. Der Tooltip zeigt den Abschlusspunkt nicht, und die Summe der Legende zählt ihn nicht. (Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Abschlusspunkt › should draw a single measurement as one quarter-hour step; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Abschlusspunkt › should extend every series by the end of its last interval; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Abschlusspunkt › should not add a point to an empty series; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Abschlusspunkt › should leave the closing point out of the tooltip)
* [x] Zahlen in Achsenbeschriftung, Tooltip und Legende stehen im Schweizer Format (Punkt als Dezimal-, Hochkomma als Tausendertrennzeichen). In `messwerte-chart.component.ts` kommt weder `toLocaleString` noch `toFixed` vor; die Formatierung läuft ausschliesslich über `formatSwissNumber`. (Projektweit sind beide zulässig — `number-utils.ts` verwendet `toFixed` selbst, und genau dort gehört es hin.) (Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › legende › should combine the translated name with the swiss formatted sum; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Abschlusspunkt › should leave the closing point out of the tooltip; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Achsenbeschriftung › should format the y axis labels in Swiss format; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Achsenbeschriftung › should format tooltip values from 1000 with the thousands separator)
* [x] Die Legendennamen kommen aus dem `TranslationService` (`TOTAL`, `ZEV`) und stehen beim Zeichnen in der aktiven Sprache. **Nicht** gefordert ist, dass ein Sprachwechsel ein bereits gezeichnetes Diagramm erneuert (FR-3, bekannte Einschränkung). (Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › legende › should combine the translated name with the swiss formatted sum; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Abschlusspunkt › should draw a single measurement as one quarter-hour step)
* [x] Achsen-, Gitter- und Reihenfarben stammen aus Design-Tokens. **Keine Diagrammfarbe** ist ein Literal; die Rückfallwerte im gemeinsamen Farb-Baustein sind erlaubt und erwünscht (ohne sie wäre das Diagramm unsichtbar, falls ein Token fehlt). (Test: chart-farben.spec.ts › chartFarben › should prefer the token over the fallback; Test: chart-farben.spec.ts › chartFarben › should fall back when a token is missing; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Messpunkt-Diagramm › should draw one series per einheit with its stored sign and without a ZEV series)
* [x] Der Titel der y-Achse kommt aus `KWH`, nicht aus festem Text; die Zeitachse trägt keinen Titel. (Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › optionen › should name the y axis from the translation service and leave the time axis untitled)
* [x] Das Diagramm folgt einer Grössenänderung des Panels. (Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › createChart › should observe the container and resize the chart when it changes)
* [x] Beim Verlassen der Seite wird die ECharts-Instanz freigegeben und der `ResizeObserver` abgemeldet. (Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › ngOnDestroy › should dispose every instance and disconnect every observer)

**Sicherheit**
* [x] `/chart` ist mit der Permission `messwerte:read` erreichbar und ohne sie gesperrt (`composite-roles.spec.ts` deckt das mit einem Fall ab). (E2E: composite-roles.spec.ts › zev_user kann die Messwerte-Grafik ansehen (messwerte:read))

**Bundle**
* [x] ECharts liegt **nicht** im Initial-Bundle, sondern in eigenen Chunks (dynamischer Import). (Nachweis: utils/echarts-loader.ts:37; Grafiken_Umsetzungsplan.md, „Bundle (Phasen 1 und 7)“)

**Gemeinsame Bausteine**
* [x] Nachladen der Bibliothek, Farben und Zeitformatierung stehen **einmal** im Projekt und werden von allen Diagrammen genutzt. (Test: echarts-loader.spec.ts › ladeECharts › should register the modules of both charts; Test: chart-farben.spec.ts › chartFarben › should deliver a colour for every purpose; Test: date-utils.spec.ts › Date Utils › formatSwissDateTime › should format a Date object)

**Fehlerfälle**
* [x] Scheitert das Nachladen der Bibliothek, erscheint eine Fehlermeldung, und die Seite bleibt bedienbar (Auswahl, Zeitraum, Navigation). (Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Nachladen scheitert › should report DIAGRAMM_NICHT_LADBAR once and keep the page usable; Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › Nachladen scheitert › should not try to load again after a failure)
* [x] Ein Zeitraum ohne Messwerte führt zu einer Meldung mit `0` Datenpunkten; das Panel entsteht, das Diagramm bleibt leer, die Seite bleibt bedienbar. (Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › optionen › should work with an empty data set; E2E: messwerte-grafik.spec.ts › Messwerte-Grafik - Diagramm › should stay usable for a period without measurements)
* [x] Ein Fehler beim Laden der Messwerte zeigt eine lesbare Meldung (kein `[object Object]`). (Test: messwerte-chart.component.spec.ts › MesswerteChartComponent › onSubmit › should show a readable error when loading fails)

## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Performance
* Ein Quartal sind bis zu **8'640** Punkte je Reihe (90 × 96), zwei Reihen je Diagramm, mehrere Diagramme gleichzeitig. Mit `sampling: 'lttb'` bleibt die Darstellung flüssig; ohne Ausdünnung wäre sie es bei „Alle auswählen" nicht.
* Die Diagramme werden **nacheinander** aufgebaut (`setTimeout`-Kette), damit die Maske bei vielen Einheiten nicht blockiert. Auf diese Zeichenverzögerung wartet der E2E-Helfer `warteAufGezeichnetesDiagramm`; fällt der verzögerte Aufbau weg, ist er mitzuprüfen.
* ECharts liegt **nicht** im Initial-Bundle; die Bibliothek wird erst beim Öffnen eines Diagramms nachgeladen.

### NFR-2: Sicherheit
* Route `/chart` verlangt die Permission `messwerte:read` (`AuthGuard`, `app.routes.ts`) — Fachrollen `zev_user`, `org_admin`, `zev_admin`. Die Daten kommen über `GET /api/messwerte/by-einheit` mit `hasAuthority('messwerte:read')`.
* Keine eigenen Endpunkte und keine eigenen Berechtigungen. Der Einspeisetarif (FR-10) nutzt den Endpunkt `GET /api/preiszeitreihe` mit dessen Absicherung: Permission `tarife:manage` und Feature-Flag `PREISZEITREIHE`. Die Checkbox erscheint nur, wenn beides erfüllt ist; massgeblich bleibt die Prüfung im Backend. `zev_user` sieht die Grafik damit ohne Einspeisetarif.
* Der dynamische Import lädt ausschliesslich lokal gebündelte Chunks — **kein** CDN, keine externe Quelle.

### NFR-3: Kompatibilität
* **Kein eigenes Schema.** Zwei Migrationen mit Übersetzungs-Keys: `V191` (`GRAFIK_VERGLEICH`, FR-9) und `V192` (`GRAFIK_EINSPEISETARIF`, `GRAFIK_EINSPEISETARIF_CHF_KWH`, FR-10); alle übrigen Keys stammen aus anderen Features.
* Reines Frontend-Feature: kein eigener Backend-Code, keine eigene API, keine eigenen DTOs.

## 5. Edge Cases & Fehlerbehandlung

| Fall | Erwartetes Verhalten |
|---|---|
| Zeitraum ohne Messwerte | Meldung mit `0` Datenpunkten, Panel und leeres Diagramm, Seite bedienbar |
| Reihe mit fehlenden Viertelstunden zwischen zwei Messwerten | Die Linie fällt in der Lücke auf 0 und der Tooltip nennt dort `–`; die Summe ändert sich nicht (FR-2) |
| Lückenlose Reihe über die Umstellung auf Winterzeit (02:45 → 03:00, 75 Minuten) | Keine Lücke, es wird nichts ergänzt — gezählt wird in Ortszeit |
| Reihe endet früher als eine andere im selben Diagramm (oder beginnt später) | Sie bleibt dort leer — ergänzt wird nur zwischen ihren eigenen Messwerten |
| Einheit ohne Messwerte, andere mit | Jedes Diagramm zeigt seinen eigenen Bestand; das leere bleibt leer, ohne Fehler |
| „Alle auswählen" bei vielen Einheiten | Jedes Diagramm wird gezeichnet; Ausdünnung und sequenzieller Aufbau halten die Maske bedienbar |
| Sehr grosser Zeitraum (mehrere Quartale) | Wird geladen und gezeichnet; die Lesbarkeit übernimmt `dataZoom` |
| Nachladen der Bibliothek scheitert (Netz, Chunk fehlt) | Fehlermeldung, kein Diagramm, Seite bleibt bedienbar |
| Fehler beim Laden der Messwerte | Lesbare Fehlermeldung, kein halb gezeichnetes Diagramm |
| Vertauschter Zeitraum | Fehlermeldung, kein Server-Aufruf, kein Diagramm; ◀ / ▶ gesperrt |
| Blättern oder „Heute" ohne Auswahl | Schaltflächen gesperrt, kein Server-Aufruf (FR-1a.5) |
| Kein Messpunkt mit mehreren Einheiten | Die Auswahl zeigt nur die Einheiten |
| Messpunkt gewählt, eine seiner Einheiten ohne Messwerte | Das Diagramm zeigt die andere Reihe; die leere steht mit `(Σ –)` in der Legende |
| Mehr als zwei Einheiten am selben Messpunkt | Ein Eintrag und ein Diagramm mit einer Reihe je Einheit, in den Reihenfarben aus FR-4 |
| Vergleich mit mehr als sechs Einheiten | Die ersten sechs Reihen tragen die Token-Farben, weitere die Palette von ECharts; die Legende blättert, wenn sie nicht in eine Zeile passt |
| „Vergleich" mit nur einer Einheit | Schaltfläche gesperrt (FR-9.2) |
| Auswahl nach einem Vergleich auf eine Einheit verkleinert, dann geblättert | Es erscheint das einzelne Diagramm dieser Einheit; die Ansicht steht danach wieder auf „einzeln" (FR-9.6) |
| Produzent im Vergleich, der netto bezieht (z. B. Wechselrichter lädt die Batterie aus dem Netz, `total` positiv) | Gespiegelt wie jeder Wert des Produzenten: Die Reihe liegt in diesen Intervallen unter der Nulllinie |
| Konsument im Vergleich ohne ZEV-Anteil im Zeitraum (z. B. nachts, oder Verteilung noch nicht gerechnet) | Nur seine Linie, keine Fläche und kein Legendeneintrag `– ZEV` |
| Vergleich mit mehr als sechs Einheiten, Konsument ab der siebten mit ZEV-Anteil | Seine Fläche bekommt wie seine Linie eine Farbe aus der Palette von ECharts — nicht zwingend dieselbe |
| Zwei Einheiten mit demselben Namen im Vergleich | Beide Reihen tragen denselben Legendentext; unterscheidbar sind sie nur an der Farbe |
| Einspeisetarif verlangt, Zeitraum ohne Preise | Diagramme ohne Preisreihe, Warnung `KEINE_PREISE_VORHANDEN` (FR-10.7) |
| Einspeisetarif verlangt, Zeitraum länger als 366 Tage | Die Preiszeitreihe lehnt ab; Diagramme ohne Preisreihe, Fehlermeldung mit dem Text des Backends |
| Einspeisetarif verlangt, Preise decken nur einen Teil des Zeitraums | Die Preislinie steht nur dort, wo es Preise gibt |
| Benutzer ohne `tarife:manage` oder Flag `PREISZEITREIHE` aus | Keine Checkbox, kein Preisabruf; die Grafik zeigt nur die Energie |
| Gleicher Messpunkt, unterschiedlich geschrieben (`BKW` / `bkw`) | Keine Gruppe — es sind zwei Messpunkte, wie in der Einheitenverwaltung |
| Panel wird in der Grösse verändert (`resize: vertical`) | Diagramm folgt über den `ResizeObserver` |
| Sprachwechsel bei offenem Diagramm | Beschriftung und Legende bleiben bis zum nächsten Zeichnen in der alten Sprache. **Bekannte Einschränkung**, gilt für alle Diagramme der Anwendung (FR-3) |
| Wechsel des Dark Mode bei offenem Diagramm | Die Farben werden beim Zeichnen aus den Tokens gelesen; ein Wechsel wirkt erst beim nächsten Zeichnen. **Bekannte Einschränkung**, gilt für die Preiszeitreihe genauso |
| Seite verlassen und zurückkehren | Instanz freigegeben, neue Instanz gezeichnet; kein Speicherzuwachs bei wiederholtem Wechsel |

## 6. Abhängigkeiten & betroffene Funktionalität

**Voraussetzungen**
* `echarts` 6.1.0.
* Messwerte je Einheit: `MesswerteService` (`GET /api/messwerte/by-einheit`).
* Design-Tokens (`design-system/src/tokens/tokens.css`), `formatSwissNumber`, `TranslationService`.
* Für den Einspeisetarif (FR-10): `PreiszeitreiheService` (`GET /api/preiszeitreihe`, `Specs/Preiszeitreihe.md`), Feature-Flag `PREISZEITREIHE` (`FeatureFlagService`, `*appFeature`), Permission `tarife:manage` aus dem Keycloak-Token (`Specs/Berechtigungen.md`, Fussnote ¹⁰).

**Beteiligter Code**

| Datei | Aufgabe |
|---|---|
| `components/messwerte-chart/` | Maske und Diagramme: Auswahl, Zeitraum, Laden, ECharts-Optionen, `ResizeObserver` |
| `components/einheit-selector/` | Auswahl der Einheiten, mit Zusatzeinträgen je Messpunkt (`mitMesspunktGruppen`); in den Rechnungen ohne sie |
| `utils/echarts-loader.ts`, `utils/chart-farben.ts`, `utils/chart-preis.ts`, `utils/date-utils.ts` | gemeinsame Bausteine (FR-5) |
| `utils/messpunkt-gruppen.ts`, `pipes/messpunkt-gruppe.pipe.ts`, `models/einheit.model.ts` (`MesspunktGruppe`) | Gruppen je Messpunkt bilden und bezeichnen (FR-8) |
| `components/preiszeitreihe-chart/`, `components/einspeisesteuerung/` | nutzen dieselben Bausteine |
| `V191`, `V192` (Migrationen) | Übersetzungs-Keys für Vergleich und Einspeisetarif |

**Datenmigration**
* Keine.

**Tests**
* Frontend-Unit: `messwerte-chart.component.spec.ts`, `einheit-selector.component.spec.ts` und die Specs der gemeinsamen Bausteine. jsdom hat kein Canvas: Das Zeichnen ist gestubbt, geprüft werden die Optionen.
* E2E: `tests/messwerte-grafik.spec.ts`, darunter ein Pixel-Vergleich zwischen einem Zeitraum mit und einem ohne Messwerte.
  * **Randbedingung:** Die Tests zählen `.zev-panel--chart canvas`. Das setzt voraus, dass ECharts **genau ein** `<canvas>` je Instanz erzeugt. Legt eine neue Version mehrere Render-Layer an, ist der Test anzupassen und die Anpassung zu begründen.

## 7. Abgrenzung / Out of Scope

* **Nur die Messwerte-Grafik.** Die Diagramme der Preiszeitreihe (`Specs/Preiszeitreihe.md`) und der Einspeisesteuerung (`Specs/Einspeisesteuerung.md`) sind in ihren eigenen Specs beschrieben, die Statistik-Seite in `Specs/Statistik.md`.
* **Kein Export** (auch nicht der PDF-Export der Statistik) und **keine Kennzahlen**.
* **Kein Saldo je Messpunkt.** Das Messpunkt-Diagramm zeigt die Einheiten nebeneinander; eine verrechnete Reihe (Bezug minus Rücklieferung) gibt es nicht.
* **Keine Summe im Vergleich.** Das Vergleichsdiagramm (FR-9) zeigt die Einheiten nebeneinander; eine aufsummierte Reihe über alle gibt es nicht.
* **Kein Mischbetrieb.** Vergleich und einzelne Diagramme stehen nie gleichzeitig auf der Seite.
* **Nur der dynamische Einspeisepreis** (FR-10). Die festen Tarife der Tarifverwaltung (ZEV, VNB) zeigt die Grafik nicht, ebenso keinen Ertrag in Franken (Energie × Preis).
* **Kein Abruf bei der Preisquelle.** Die Grafik liest die gespeicherte Preiszeitreihe; aktualisiert wird sie in der Tarifverwaltung bzw. durch den Job.
* **Kein eigenes Backend:** keine eigenen Endpunkte, DTOs oder Tabellen.
* **Keine Obergrenze für den Zeitraum.** Anders als die Preiszeitreihe (366 Tage) kennt diese Seite keine Grenze. Wer „Alle auswählen" über mehrere Jahre wählt, erzeugt eine grosse Antwort — die Lesbarkeit übernimmt `dataZoom`, die Ausdünnung `sampling`.
* **Keine Bildvergleichstests** (Screenshot-Diffs) — der Pixelvergleich der E2E-Tests genügt.

## 8. Offene Fragen

Keine.
