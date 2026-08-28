# Umsetzungsplan: EChart

## Zusammenfassung

Die Messwerte-Grafik (`/chart`) wird von chart.js auf ECharts umgestellt und chart.js danach aus dem
Projekt entfernt. Gemeinsame Bausteine — Nachladen der Bibliothek, Farben aus Design-Tokens,
Zeitformatierung — entstehen einmal und werden von der Messwerte-Grafik **und** der Preiszeitreihe
genutzt.

Der Umbau ist rein technisch: Die Bedienung bleibt gleich, neu ist allein das Zoomen. Nebenbei
verschwindet chart.js aus dem Initial-Bundle, und vier Regelverstösse in derselben Datei werden
behoben (`toLocaleString`, `toFixed`, hart kodierte Farben, hart kodierte Legendennamen).

Grundlage: `Specs/EChart.md`. **Kein Backend, keine Datenbank, keine Migration.**

---

## Betroffene Komponenten

### Neu

| Datei | Zweck |
|---|---|
| `frontend-service/src/app/utils/echarts-loader.ts` | dynamischer Import samt `core.use([...])` mit der **Vereinigung** aller Module beider Diagramme; gemerkt für weitere Aufrufe; `null` bei Fehlschlag |
| `frontend-service/src/app/utils/chart-farben.ts` | Diagrammfarben aus den Design-Tokens mit Rückfallwerten |

### Geändert

| Datei | Änderung |
|---|---|
| `components/messwerte-chart/messwerte-chart.component.ts` | Kern: ECharts statt chart.js, Stufenlinie, Zoom, `sampling`, Farben, Schweizer Format, i18n der Legende, `ResizeObserver`, `dispose()` |
| `components/messwerte-chart/messwerte-chart.component.html` | `<canvas id="chart-…">` → `<div>` als Diagramm-Behälter, Lade- und Fehlerzustand |
| `components/messwerte-chart/messwerte-chart.component.spec.ts` | Zeichnen stubben (jsdom hat kein Canvas), Optionen als reine Funktion prüfen |
| `components/preiszeitreihe-chart/preiszeitreihe-chart.component.ts` | nutzt die gemeinsamen Bausteine statt eigener Kopien (`ladeBibliothek`, `farben`, `formatiereZeit`) |
| `utils/date-utils.ts` | neu: `formatSwissDateTime()` (`dd.MM.yyyy HH:mm`) |
| `utils/date-utils.spec.ts` | Tests für die neue Funktion |
| `frontend-service/package.json` (+ `package-lock.json`) | `chart.js` entfernen |
| `tests/messwerte-grafik.spec.ts` | ein Test für das Zoomen; die 14 bestehenden bleiben **unverändert** |
| `tests/composite-roles.spec.ts` | ein Fall für `/chart` mit und ohne `messwerte:read` |
| `Specs/DarkMode.md` | Aussage „Charts (Chart.js) bleiben out of scope" nachziehen — Diagrammfarben kommen jetzt aus Tokens |

### Unverändert (bewusst)

Backend, API, DTOs, Datenbank, Übersetzungstabelle, Routing, Navigation, Statistik-Seite,
`zev-panel--chart` im Design System. Die historischen Pläne (`Vaadin-Frontend-Migration`,
`GUI-Tests_Umsetzungsplan`) nennen chart.js als damaligen Stand und bleiben als Zeitdokument stehen.

---

## Phasen-Tabelle

| Status | Phase                          | Beschreibung                                                                                                    |
|--------|--------------------------------|-----------------------------------------------------------------------------------------------------------------|
|  [x]   | 1. Ausgangsmessung             | Produktionsbau **vor** dem Umbau: Grösse von `main-*.js` und Chunk-Liste notieren                               |
|  [x]   | 2. Gemeinsame Bausteine        | `utils/echarts-loader.ts`, `utils/chart-farben.ts`, `formatSwissDateTime()` in `utils/date-utils.ts`             |
|  [x]   | 3. Preiszeitreihe umstellen    | Nutzt die Bausteine; ihre Tests (37 Unit, 13 E2E) müssen **unverändert** grün bleiben                            |
|  [x]   | 4. Messwerte-Grafik: Template  | `<canvas>` → `<div>`, Lade- und Fehlerzustand über `DIAGRAMM_LAEDT` / `DIAGRAMM_NICHT_LADBAR`                    |
|  [x]   | 5. Messwerte-Grafik: Komponente| ECharts-Optionen, Stufenlinie, zwei Reihen (ZEV negativ), Zoom, `sampling`, Farben, Format, i18n, ResizeObserver |
|  [x]   | 6. chart.js entfernen          | `package.json` und Lockfile; danach findet `grep -r "chart.js" src` nichts mehr                                  |
|  [x]   | 7. Bundle-Messung              | Produktionsbau **nach** dem Umbau; Differenz gegen Phase 1 im Plan festhalten                                   |
|  [x]   | 8. Lizenzen und SBOM           | Seite `/lizenzen` und `Specs/SBOM.md`: chart.js verschwunden, ECharts vorhanden                                  |
|  [x]   | 9. Sequenziellen Aufbau prüfen | Gemessen, Kette **bleibt** — Begründung samt Zahlen im Code und unten                                            |
|  [x]   | 10. Doku nachziehen            | `Specs/DarkMode.md` (Charts nicht mehr out of scope)                                                             |
|  [x]   | 11. Frontend-Unit-Tests        | `/4_frontend-unit-tests` — Komponenten-Spec umstellen, Bausteine testen                                          |
|  [x]   | 12. E2E-Tests                  | `/5_e2e-tests` — Zoom-Test ergänzen, `composite-roles` erweitern, volle Suite                                    |

---

## Phase 1: Ausgangsmessung

`cd frontend-service && npx ng build` — und **notieren**, bevor irgendetwas geändert wird:

* Grösse von `main-*.js` (Rohgrösse und Transfergrösse),
* „Initial total",
* die Lazy-Chunks (ECharts liegt schon dort).

Bekannter Stand aus dem letzten Bau: `main` **983.46 kB**, Initial total **1.05 MB**, Warnschwelle
`maximumWarning: 1mb` seit je überschritten. Erwartung nach dem Umbau: **70–200 kB** weniger
(`Specs/EChart.md`, NFR-1). Fällt die Ersparnis deutlich kleiner aus, zieht etwas anderes chart.js
herein — dann ist das zu klären und nicht wegzurunden.

---

## Phase 2: Gemeinsame Bausteine

### 2.1 `utils/echarts-loader.ts`

```ts
/** Geladene Bibliothek, über alle Aufrufer geteilt. */
let echarts: typeof import('echarts/core') | null = null;

export async function ladeECharts(): Promise<typeof import('echarts/core') | null> { … }
```

* Registriert die **Vereinigung** aller Module beider Diagramme: `LineChart`, `BarChart`,
  `GridComponent`, `TooltipComponent`, `DataZoomComponent`, `LegendComponent`, `CanvasRenderer`.
  **Nicht** je Aufrufer eine eigene Auswahl: Ein nicht registriertes Modul zeichnet stumm nichts,
  ECharts meldet keinen Fehler — genau daran ist der Balken-Serientyp der Preiszeitreihe zuerst
  gescheitert. Eine Liste je Aufrufer würde die Falle vervielfachen.
* Merkt das Ergebnis im Modulzustand: Der zweite Aufruf lädt nicht erneut. Zwei Diagramme auf einer
  Seite (heute nicht der Fall, morgen möglich) teilen sich damit einen Ladevorgang.
* Liefert `null`, wenn der Import scheitert — der Aufrufer zeigt dann `DIAGRAMM_NICHT_LADBAR`.
  Bewusst **kein** Werfen: Ein fehlgeschlagener Chunk-Download ist ein erwartbarer Betriebsfall,
  keine Ausnahme im Programmablauf.

### 2.2 `utils/chart-farben.ts`

```ts
export interface ChartFarben {
  achse: string; text: string; gitter: string; primaer: string; sekundaer: string;
}
export function chartFarben(): ChartFarben { … }
```

* Liest die Design-Tokens über `getComputedStyle(document.documentElement)`.
* **Rückfallwerte sind erwünscht** (`--color-gray-500` → `#cccccc` usw.): Fehlt ein Token, wäre das
  Diagramm sonst unsichtbar. Sie sind die einzigen erlaubten Farb-Literale (`Specs/EChart.md`, AK).
* `sekundaer` ist neu gegenüber der Preiszeitreihe — die Messwerte-Grafik braucht eine zweite
  Reihenfarbe (`--color-secondary`, entspricht dem heutigen `#2196F3`).

### 2.3 `formatSwissDateTime()` in `utils/date-utils.ts`

`dd.MM.yyyy HH:mm`, manuell gebildet — **kein** `toLocaleString` (`Specs/generell.md`). Die
Preiszeitreihe gibt ihre private `formatiereZeit` hierher ab; die Datei ist ohnehin die Heimat der
Datumsformatierung und hat schon `formatSwissDate`, `parseSwissDate`, `swissToIsoDate`.

---

## Phase 3: Preiszeitreihe umstellen

`preiszeitreihe-chart.component.ts` verliert drei private Bausteine und nutzt die gemeinsamen:

| vorher | nachher |
|---|---|
| `ladeBibliothek()` mit vier `import()`-Aufrufen und `core.use([...])` | `ladeECharts()` |
| `farben()` mit `getComputedStyle` und fünf Tokens | `chartFarben()` |
| `formatiereZeit()` | `formatSwissDateTime()` |

**Diese Phase ist verhaltensneutral, und das ist prüfbar:** Die 37 Unit-Tests und 13 E2E-Tests der
Preiszeitreihe müssen **ohne Anpassung** grün bleiben. Muss ein Test angepasst werden, war die
Extraktion nicht neutral — dann ist erst die Ursache zu klären, nicht der Test.

Ein Test wird trotzdem berührt: Der Unit-Test stubbt heute `component['zeichne']`. Das bleibt
möglich; die Farbfunktion wird nicht mehr über die Komponente aufgerufen, sondern importiert —
falls ein Test sie mockt, ist der Mock auf das Modul umzustellen.

---

## Phase 4: Messwerte-Grafik — Template

```html
<div class="zev-panel zev-panel--chart">
  <h3 class="zev-panel__title">{{ chartData.einheitName }} [{{ chartData.einheitTyp | einheitTyp }}]</h3>
  <div class="zev-panel__content">
    <div [id]="'chart-' + chartData.einheitId" class="messwerte-chart__diagramm"></div>
  </div>
</div>
```

* `<canvas>` wird zum `<div>`: ECharts legt sein Canvas selbst darin an. Die ID bleibt (`chart-<id>`),
  damit die Komponente den Behälter weiterhin findet.
* **Randbedingung:** Die E2E-Tests zählen `.zev-panel--chart canvas`. Erzeugt ECharts genau ein
  Canvas je Instanz, bleiben sie unverändert grün — sonst ist der **Test** anzupassen und die
  Anpassung zu begründen (`Specs/EChart.md`, §6).
* Lade- und Fehlerzustand über die vorhandenen Keys `DIAGRAMM_LAEDT` und `DIAGRAMM_NICHT_LADBAR`.

---

## Phase 5: Messwerte-Grafik — Komponente

Ersetzt `createChart()` (chart.js) durch die ECharts-Variante. Aufbau wie in
`preiszeitreihe-chart.component.ts`, damit beide Diagramme gleich gebaut sind.

**Serien** (zwei je Diagramm, Reihenfolge wie heute):

| Reihe | Wert | Farbe | Legende |
|---|---|---|---|
| Total | `d.total ?? 0` | `primaer` | `TOTAL (Σ … kWh)` |
| ZEV | `-(d.zev ?? 0)` — **negativ**, wie heute | `sekundaer` | `ZEV (Σ … kWh)` |

* **Stufenlinie** (`step: 'end'`), **keine** Flächenfüllung, `showSymbol: false`,
  `connectNulls: false`.
* `sampling: 'lttb'` dauerhaft (Annahme 2): bei 96 Punkten unschädlich, bei 8'640 nötig.
* `dataZoom: [{ type: 'inside' }, { type: 'slider' }]` — dieselbe Konfiguration wie die
  Preiszeitreihe.
* **Summen in der Legende** im Schweizer Format: `formatSwissNumber(summe, 3)`. Der heutige
  `toFixed(3)` verschwindet.
* **Achsen:** x als `type: 'time'`, y mit Titel `KWH`; Achsenbeschriftung über `formatSwissNumber`.
  Der heutige `toLocaleString('de-DE')` verschwindet.
* **Tooltip:** Zeitpunkt über `formatSwissDateTime`, Werte über `formatSwissNumber`.
* **Legendennamen** über `TranslationService.translate('TOTAL' | 'ZEV')` — die Keys existieren
  (geprüft). Aufgelöst wird **beim Zeichnen**; ein Sprachwechsel erneuert ein offenes Diagramm nicht
  (bekannte Einschränkung, gilt für beide Diagramme).
* **Farben** aus `chartFarben()`; kein Farb-Literal in der Komponente.
* **`ResizeObserver`** je Instanz; `responsive: false` und die manuelle Canvas-Berechnung samt
  Kommentar „WICHTIG! Funktioniert mit true nicht." entfallen.
* **`ngOnDestroy`:** jede Instanz `dispose()`, jeden Observer abmelden. Die Seite erlaubt beliebig
  viele Diagramme; ohne Freigabe wächst der Speicher bei jedem „Anzeigen".
* **Fehlschlag des Nachladens:** einmal melden, nicht je Einheit — sonst stehen bei „Alle auswählen"
  zehn identische Fehlermeldungen.

---

## Phase 6–8: Abhängigkeit, Messung, Lizenzen

* **6:** `npm uninstall chart.js`. Danach muss `grep -rn "chart.js" frontend-service/src` leer sein;
  `package-lock.json` mit committen.
* **7:** Produktionsbau und Vergleich mit Phase 1. Ergebnis als Tabelle in diesen Plan (vorher /
  nachher / Differenz), wie bei der Preiszeitreihe.
* **8:** `/lizenzen` öffnen: chart.js weg, ECharts (Apache-2.0) da. `Specs/SBOM.md` prüfen.

---

## Phase 9: Sequenziellen Aufbau prüfen

Heute werden die Diagramme mit einer `setTimeout`-Kette nacheinander erzeugt
(`createChartsSequentially`: 100 ms, dann 50 ms je weiteres), weil chart.js bei vielen Diagrammen
die Maske blockierte.

1. Mit „Alle auswählen" über ein Quartal messen: Bleibt die Maske ohne die Kette bedienbar?
2. **Bleibt sie**, kann die Kette entfallen — dann verliert aber der E2E-Helfer
   `warteAufGezeichnetesDiagramm` seine Grundlage (er pollt genau auf diese Verzögerung). Er bleibt
   korrekt, wird nur überflüssig; sein Kommentar ist dann zu berichtigen, damit er niemanden in die
   Irre führt.
3. **Bleibt sie nicht**, bleibt die Kette — mit einem Kommentar, der die Messung nennt statt einer
   Vermutung.

Annahme bis zur Messung: Kette beibehalten.

---

## Validierungen

### Frontend — bestehend, unverändert

| Regel | Ort | Reaktion |
|---|---|---|
| Mindestens eine Einheit gewählt | `onSubmit()` / `[disabled]` am Submit | Schaltfläche gesperrt; bei Umgehung Meldung `BITTE_ALLE_FELDER_AUSFUELLEN` |
| `dateFrom` und `dateTo` gesetzt | dito | dito |
| `dateFrom <= dateTo` | `onSubmit()` | Meldung `START_DATUM_MUSS_VOR_END_DATUM_LIEGEN`, **kein** Server-Aufruf |
| `dateFrom` gesetzt | `onDateFromChange()` | `dateTo` wird auf das Monatsende gezogen |

### Frontend — neu (technische Wächter)

| Regel | Ort | Reaktion |
|---|---|---|
| Bibliothek geladen | `ladeECharts()` | `null` → Meldung `DIAGRAMM_NICHT_LADBAR`, Seite bleibt bedienbar |
| Behälter vorhanden und gemessen | vor `init()` | ohne Behälter kein Zeichnen (kein Absturz) |
| Instanz freigegeben | `ngOnDestroy` | `dispose()` + Observer abmelden |
| Alle benötigten Module registriert | `echarts-loader.ts` | Vereinigung beider Diagramme an **einer** Stelle |

### Backend

Keine. Es gibt keine Backend-Änderung; `GET /api/messwerte/by-einheit` bleibt unverändert
(`hasAuthority('messwerte:read')`).

---

## Offene Punkte / Annahmen

Aus `Specs/EChart.md` §8 — **alle Annahmen sind bestätigt** (28.08.2026):

1. **Sequenzieller Aufbau** bleibt zunächst; die Messung in Phase 9 entscheidet. Fällt er weg, ist
   der E2E-Helfer `warteAufGezeichnetesDiagramm` mitzuprüfen.
2. **`sampling: 'lttb'`** dauerhaft aktiv.
3. **Dark-Mode-Wechsel** bei offenem Diagramm wirkt erst beim nächsten Zeichnen — bekannte
   Einschränkung, für beide Diagramme gleich.
4. **Summen** bleiben im Legendentext.
5. **Bundle-Ersparnis** wird gemessen, nicht geschätzt (Phasen 1 und 7).

Zusätzliche Annahmen dieses Plans:

6. **Stufenlinie** (bestätigte Entscheidung): Ein Messwert gilt für die ganze Viertelstunde. Das
   ändert das gewohnte Bild — wer die alte Optik erwartet, wird den Unterschied sehen.
7. **Die Reihenfolge der Phasen ist nicht beliebig.** Erst die Bausteine, dann die Preiszeitreihe
   (verhaltensneutral, mit ihren Tests als Beweis), erst danach die Messwerte-Grafik. Umgekehrt
   liesse sich nicht mehr unterscheiden, ob ein Fehler aus der Extraktion oder aus dem Umbau kommt.
8. **`chart-farben.ts` bekommt `sekundaer` gleich mit**, obwohl die Preiszeitreihe es nicht braucht.
   Ein zweiter Durchgang für ein Feld wäre teurer als das Feld.
9. **Kein Zwischenzustand mit beiden Bibliotheken.** chart.js wird in Phase 6 entfernt, nicht
   „später irgendwann" — sonst bleibt die Abhängigkeit im Bundle und der Hauptgrund des Umbaus
   verfehlt.

---

## Ergebnis der Umsetzung

### Bundle (Phasen 1 und 7)

Produktionsbau je vor und nach dem Umbau, gleiche Maschine, gleicher Befehl:

| | `main-*.js` (roh) | `main-*.js` (Transfer) | Initial total | Budget-Warnung |
|---|---|---|---|---|
| **vorher** | 984.01 kB | 231.43 kB | 1.05 MB | ja, 52.99 kB über 1 MB |
| **nachher** | **781.14 kB** | **171.72 kB** | **850.12 kB** | **keine** |
| Differenz | **−202.87 kB** | −59.71 kB | −~200 kB | — |

Das liegt am oberen Ende der erwarteten Spanne (70–200 kB) — und wichtiger als die Zahl: **die
Budget-Warnung ist weg.** Sie bestand seit langem und wurde nicht durch dieses Vorhaben verursacht,
verschwindet aber mit ihm. ECharts liegt weiterhin in eigenen Lazy-Chunks (`charts` 261.88 kB,
`components` 261.96 kB, `renderers` 34.14 kB, `core` 4.68 kB) und wird erst beim Öffnen eines
Diagramms geholt.

### Verhaltensneutralität der Extraktion (Phase 3)

Die 37 Unit-Tests der Preiszeitreihe laufen **ohne eine einzige Anpassung** grün. Damit ist belegt,
was der Plan verlangt hat: Die gemeinsamen Bausteine ändern das Verhalten nicht. Möglich wurde das
durch eine bewusst kleine Schnittstelle — `serie()` bekommt weiter nur `{ linie }` und nicht die
ganze Palette, obwohl `chartFarben()` mehr liefert.

### Lizenzen (Phase 8)

`npm run generate-licenses` neu erzeugt: chart.js ist aus `src/assets/frontend-licenses.json`
verschwunden (23 Einträge statt 24), `echarts` steht drin. Der Eintrag wäre sonst stehen geblieben —
die Datei ist generiert, aber eingecheckt.

### Phase 9: Messung entschieden — die Staffelung bleibt

Dieselbe Messung vor und nach dem Umbau, „Alle auswählen" mit **16 Einheiten und 113'568
Datenpunkten**, gegen den laufenden Container:

| | chart.js | ECharts |
|---|---|---|
| Meldung „… geladen" nach | 729 ms | **342 ms** |
| **alle 16 Diagramme bemalt** nach | 9'078 ms | **1'817 ms** |
| Klick auf ein Quartal beantwortet in | 739 ms | **64 ms** |

Das Zeichnen ist rund **fünfmal schneller**, und die Maske antwortet auf einen Klick in 64 statt
739 ms.

**Entscheidung: Die `setTimeout`-Kette bleibt** — begründet durch genau diese Zahlen. Von den
1'817 ms sind 850 ms die Staffelung selbst (100 + 15 × 50 ms); die reine Zeichenzeit liegt bei rund
**60 ms je Diagramm**. Alle 16 unmittelbar hintereinander zu zeichnen hiesse, den Haupt-Thread etwa
eine Sekunde **am Stück** zu blockieren. Gestaffelt bleibt die Maske durchgehend bedienbar. Die
Kette kostet damit weniger als das, was sie staffelt — und ihr Kommentar im Code nennt jetzt die
Messung statt einer Vermutung.

Damit bleibt auch der E2E-Helfer `warteAufGezeichnetesDiagramm` sinnvoll: Er wartet auf das fertige
Bild und nicht auf eine Frist, und die Verzögerung, die ihn nötig macht, existiert weiterhin.

**Eine Falle beim Messen, festgehalten für den nächsten:** Der erste Lauf meldete „0 Einheiten",
obwohl 16 geladen wurden. `#einheit-select-all` steht **statisch** im Template und ist auch sichtbar,
solange die Einheiten noch nicht geladen sind — ein Zähler direkt danach liest 0. Erst das Warten auf
die erste echte Einheit macht die Messung gültig.

---

## Phase 11: Frontend-Unit-Tests

**27 neue Tests**; Suite danach **1571 in 59 Dateien** (vorher 1544 in 57).

| Datei | vorher | nachher | Schwerpunkt |
|---|---|---|---|
| `components/messwerte-chart/…spec.ts` | 12 | **24** | Diagramm-Optionen als reine Funktion: zwei Reihen mit gespiegelter ZEV-Reihe, Zeitachse, Zoom, Achsentitel aus dem `TranslationService`, leerer Datensatz; Legende im Schweizer Format samt negativer Summe; Stufenlinie mit `sampling` und ohne `areaStyle`; `ngOnDestroy` gibt Instanzen **und** Beobachter frei; Fehlerpfad ohne `[object Object]` |
| `utils/echarts-loader.spec.ts` | — | **6** | Bibliothek wird geladen, **gemerkt** (zweiter Aufruf liefert dieselbe Instanz), Zustand zurücksetzbar — und: **existieren die registrierten Exporte?** |
| `utils/chart-farben.spec.ts` | — | **5** | Token schlägt Rückfallwert, Rückfallwert greift wenn das Token fehlt, gelesen wird bei **jedem** Aufruf, die zwei Reihenfarben unterscheiden sich |
| `utils/date-utils.spec.ts` | 14 | **+5** | `formatSwissDateTime`: ISO-String, `Date`, führende Nullen, Fehlwert, unlesbarer Wert |

### Zwei Entscheidungen

1. **Das Zeichnen ist gestubbt** (`createChartsSequentially`), und zwar **vor** dem ersten
   `detectChanges`. Ohne den Stub liefe nach dem Testende ein `setTimeout` in eine echte Zeichnung —
   in jsdom ohne Canvas, mit einem Fehler, den niemand mehr zuordnet. Die Optionen werden stattdessen
   als reine Funktion geprüft.
2. **Der Loader lädt im Test wirklich.** Das ist Absicht: So prüft der Test, dass die registrierten
   Module unter den erwarteten Namen existieren. Ein umbenannter oder vergessener Export fällt dort
   auf — und nicht erst, wenn ein Diagramm stumm leer bleibt.

---

## Phase 12: E2E-Tests

**Die 14 bestehenden Tests laufen gegen ECharts — 28 von 28 grün** (beide Browser). Damit ist das
stärkste Akzeptanzkriterium eingelöst. Dazu **ein neuer Test** (Zoom) und **einer** in
`composite-roles.spec.ts`.

### Eine Einschränkung, die zur Zusicherung gehört

Das Kriterium lautete „laufen **unverändert** grün". Wörtlich stimmt das nicht mehr: Der Helfer
`oeffneGrafik` musste seine Wartebedingung ändern. Der Grund liegt aber **nicht** in der Migration,
sondern in einem Fehler des Tests selbst:

`#einheit-select-all` steht **statisch** im Template und ist auch sichtbar, solange die Einheiten
noch nicht geladen sind. Ein `count()` direkt danach liest 0 — der Test
`should select every unit via the select-all checkbox` war deshalb flaky (Firefox), und **dieselbe
Falle hat zuvor die Messung in Phase 9 verdorben** („0 Einheiten" bei 16 geladenen). Der Helfer
wartet jetzt auf die erste echte Einheit. Geändert wurde eine **Wartebedingung, keine Zusicherung**.

### Neu: Zoom

Der Zoom ist die einzige neue Funktion des Umbaus, und er ist jetzt geprüft: Mausrad über dem
Diagramm, Bild ändert sich (Pixelzahl), **kein** `/api/messwerte`-Aufruf.

Zwei Anläufe waren nötig: Ein Mausrad-Ereignis erreicht ECharts nur an einer **sichtbaren** Stelle.
Ohne `scrollIntoViewIfNeeded()` liegt das Diagramm unter dem Formular ausserhalb des Sichtfensters,
und das Rad scrollt bloss die Seite — der Test schlug fehl, obwohl der Zoom funktioniert. Damit ist
auch die Lücke geschlossen, die bei der Preiszeitreihe noch als „nicht automatisiert" vermerkt war.

### Berechtigung

`composite-roles.spec.ts` prüft `/chart` mit `messwerte:read` (`zev_user`): Route erreichbar **und**
Maske bedienbar. Der Fall belegt, dass die Absicherung den Umbau unverändert überlebt hat.

| Suite | Ergebnis |
|---|---|
| `messwerte-grafik.spec.ts` (14 + 1) | 30/30, beide Browser |
| `composite-roles.spec.ts` (11 + 1) | 12/12 |
| Frontend-Unit-Suite | 1571 in 59 Dateien |
