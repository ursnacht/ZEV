# Umsetzungsplan: Grafiken

## Zusammenfassung

Die Messwerte-Grafik (`/chart`) wird von chart.js auf ECharts umgestellt und chart.js danach aus dem
Projekt entfernt. Gemeinsame Bausteine — Nachladen der Bibliothek, Farben aus Design-Tokens,
Zeitformatierung — entstehen einmal und werden von der Messwerte-Grafik **und** der Preiszeitreihe
genutzt.

Der Umbau ist rein technisch: Die Bedienung bleibt gleich, neu ist allein das Zoomen. Nebenbei
verschwindet chart.js aus dem Initial-Bundle, und vier Regelverstösse in derselben Datei werden
behoben (`toLocaleString`, `toFixed`, hart kodierte Farben, hart kodierte Legendennamen).

Grundlage: `Specs/Grafiken.md`. **Kein Backend, keine Datenbank, keine Migration** – für den
Bibliothekswechsel. Spätere Nachträge stehen am Ende; der vom 22.11.2026 bringt eine
Übersetzungs-Migration.

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
(`Specs/Grafiken.md`, NFR-1). Fällt die Ersparnis deutlich kleiner aus, zieht etwas anderes chart.js
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
  Diagramm sonst unsichtbar. Sie sind die einzigen erlaubten Farb-Literale (`Specs/Grafiken.md`, AK).
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
  Anpassung zu begründen (`Specs/Grafiken.md`, §6).
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

Aus `Specs/Grafiken.md` §8 — **alle Annahmen sind bestätigt** (28.08.2026):

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

---

## Nachtrag 06.11.2026: Bedienung wie in der Statistik (FR-1a)

Schaltflächen und Datumsfelder der Messwerte-Grafik sind an die Statistik angeglichen, ohne PDF-Export.

| Datei | Änderung |
|---|---|
| `messwerte-chart.component.html` | „Anzeigen" (`#grafikAnzeigen`) in den Aktions-Slot des Quartal-Selektors; in der Datumszeile ◀ / ▶ (`#zeitraumZurueck`/`#zeitraumVor`) und „Heute" (`#zeitraumHeute`) |
| `messwerte-chart.component.ts` | `onHeute()`, `onBlaettern()`, `kannBlaettern` — Regel aus `blaettereZeitraum` (`utils/date-utils.ts`), keine Kopie |
| `messwerte-chart.component.spec.ts` | `describe('Bedienung wie Statistik')`, 10 Tests |
| `tests/messwerte-grafik.spec.ts` | E2E „should page the period and jump to today, loading immediately" |

* **Abweichungen von der Statistik:** Blättern und „Heute" sind ohne gewählte Einheit gesperrt. Die
  Erfolgsmeldung mit der Zahl der Datenpunkte bleibt.
* **Keine Migration:** `ZEITRAUM_ZURUECK`, `ZEITRAUM_VOR` (V181) und `HEUTE` (V65) existieren.
* **Kein neues CSS:** Aktions-Slot (`.zev-quarter-selector__actions`), `zev-date-range-row` und
  `zev-button-group` kommen aus dem Design System.

---

## Nachtrag 20.11.2026: Gemeinsames Diagramm je Messpunkt (FR-8)

Tragen mehrere Einheiten denselben Messpunkt (z.B. Bezug / Rücklieferung), bietet die Auswahl der
Messwerte-Grafik einen Zusatzeintrag an; er zeichnet ein Diagramm mit einer Reihe je Einheit.

| Datei | Änderung |
|---|---|
| `models/einheit.model.ts` | `MesspunktGruppe` |
| `utils/messpunkt-gruppen.ts` (+ Spec) | neu: `messpunktGruppen()` — Gruppen ab zwei Einheiten je Messpunkt |
| `pipes/messpunkt-gruppe.pipe.ts` (+ Spec) | neu: `Einheit [Typ] / Einheit [Typ] / [Messpunkt]`; übersetzt den Typ über die `EinheitTypPipe` |
| `components/einheit-selector/` | `@Input() mitMesspunktGruppen`, `@Output() gruppenChange`, Einträge `#messpunkt-gruppe-<n>` nach den Einheiten; „Alle auswählen" schliesst sie ein |
| `messwerte-chart.component.ts` | `ChartData` trägt `schluessel`, `einheiten`, `messpunkt`; jede Einheit wird einmal geladen; `optionenMesspunkt()`; gemeinsamer Teil der Optionen in `diagrammOptionen()` |
| `messwerte-chart.component.html` | Titel über die Pipe, Behälter `#chart-<schluessel>` (für Einheiten weiterhin `#chart-<id>`) |
| `tests/messwerte-grafik.spec.ts` | E2E „should offer and draw a combined chart for units sharing a metering point" — mit **vorgegebenen Daten** (`page.route`), damit er nicht an den Stammdaten des Mandanten hängt |

* **Rechnungen unverändert:** Die Einträge erscheinen nur mit `mitMesspunktGruppen`.
* **Keine Migration, kein neuer Übersetzungs-Key:** Die Bezeichnung besteht aus Namen, übersetztem Typ und Messpunkt.
* **Kein neues CSS:** Die Einträge nutzen `zev-checkbox-item`.
* **Kein Backend:** Geladen wird weiterhin über `GET /api/messwerte/by-einheit`, je Einheit einmal.

**Zwei Befunde aus der Prüfung, beide behoben:**

* **Sortierung der Einheiten-Auswahl war browserabhängig.** Der Vergleich in `einheit-selector`
  lieferte für zwei Nicht-Konsumenten verschiedenen Typs in beiden Richtungen 1. Firefox zeigte die
  Rücklieferung vor dem Bezug, Chromium umgekehrt — aufgefallen erst am E2E-Test, weil die
  Bezeichnung des Messpunkts die Reihenfolge sichtbar macht. Jetzt: Konsumenten zuerst, innerhalb
  beider Gruppen nach Name. Das gilt auch für die Rechnungen.
* **Legende bei schmaler Breite.** Mit den Namen der Einheiten brach sie bei 390 px auf zwei Zeilen
  um und lag über Achsentitel und Zeichenfläche. Sie ist jetzt einzeilig und blättert
  (`legend.type: 'scroll'`), in beiden Diagrammarten.

---

## Nachtrag 22.11.2026: Vergleich – alle gewählten Einheiten in einem Diagramm (FR-9)

Neue Schaltfläche „Vergleich" neben „Anzeigen": ein Diagramm mit einer Reihe je gewählter Einheit,
die einzelnen Diagramme verschwinden.

| Datei | Änderung |
|---|---|
| `V191__Add_Grafik_Vergleich_Translation.sql` | neu: Key `GRAFIK_VERGLEICH` („Vergleich" / „Comparison") |
| `messwerte-chart.component.ts` | `modus` (`einzeln`/`vergleich`), `onVergleich()`, `kannVergleichen`; `onSubmit()` und der Vergleich teilen sich `ladeUndZeichne()`; Blättern und „Heute" laden in der stehenden Ansicht, mit Rückfall auf `einzeln` bei weniger als zwei Einheiten; Reihenpalette für Diagramme mit einer Reihe je Einheit |
| `messwerte-chart.component.html` | Schaltfläche `#grafikVergleich` im Aktions-Slot; Panel-Titel des Vergleichs |
| `utils/chart-farben.ts` (+ Spec) | `reihen`: Palette aus sechs Tokens für Diagramme mit einer Reihe je Einheit |
| `messwerte-chart.component.spec.ts` | `describe('Vergleich')` (u.a. Sperre beim Laden, Rückkehr zu den einzelnen Diagrammen, Rückfall bei verkleinerter Auswahl), ein Test in `createChart`, Farbtest auf sechs Reihen erweitert |
| `tests/messwerte-grafik.spec.ts` | E2E „should draw all selected units into one comparison chart" |

* **Kein neues CSS:** Schaltfläche und Slot kommen aus dem Design System.
* **Kein Backend ausser der Übersetzung:** Geladen wird wie bisher je Einheit.
* **Gezeichnet wird mit `optionenMesspunkt()`** – der Vergleich ist dasselbe Diagramm wie das eines
  Messpunkts, nur mit frei gewählten Einheiten.
* **Braucht einen Neustart des Backends**, damit Flyway `V191` ausführt; vorher zeigt die
  Schaltfläche den rohen Key.

**Drei Befunde aus Sichtprüfung und E2E-Lauf, alle behoben:**

* **Der Ausblend-Timer einer älteren Erfolgsmeldung löschte eine neuere Meldung** (`utils/with-message.ts`,
  Basis von 14 Komponenten). Wer innert fünf Sekunden zweimal etwas auslöste, sah die zweite
  Meldung nur kurz; eine Fehlermeldung, die auf einen Erfolg folgte, verschwand von selbst.
  Aufgefallen am neuen E2E-Test: „Anzeigen" → „Vergleich" → „Anzeigen" liess die dritte Meldung so
  früh verschwinden, dass der Test sie in Firefox einmal verpasste. Jetzt bricht jede neue Meldung
  und jedes Wegklicken den laufenden Timer ab (`with-message.spec.ts`, neu).
* **Legende über dem Achsentitel:** Blättert die Legende (Vergleich mit vielen Einheiten, schmale
  Anzeige), steht sie über die ganze Breite und lag auf „kWh". Der obere Rand der Zeichenfläche
  ist von 40 auf 55 gesetzt, für beide Diagrammarten.
* **Uhrzeiten liefen bei 390 px ineinander:** `axisLabel.hideOverlap` an der Zeitachse.

---

## Nachtrag 22.11.2026: Einspeisetarif einblenden (FR-10)

Checkbox „Einspeisetarif" rechts von „Vergleich": blendet den dynamischen Einspeisepreis des
gewählten Zeitraums als zusätzliche Linie mit eigener Achse in jedes Diagramm ein.

| Datei | Änderung |
|---|---|
| `V192__Add_Grafik_Einspeisetarif_Translations.sql` | neu: `GRAFIK_EINSPEISETARIF`, `GRAFIK_EINSPEISETARIF_CHF_KWH` |
| `messwerte-chart.component.ts` | `darfEinspeisetarif` (Permission `tarife:manage`), `mitEinspeisetarif`, `onEinspeisetarifToggle()`; Preisabruf im selben `forkJoin` wie die Messwerte, mit `catchError`; `preisAchse()`, `preisReihe()`; Tooltip formatiert den Preis mit fünf Stellen |
| `messwerte-chart.component.html` | Checkbox `#grafikEinspeisetarif` im Aktions-Slot, `@if (darfEinspeisetarif)` und `*appFeature="'PREISZEITREIHE'"` |
| `messwerte-chart.component.spec.ts` | `describe('Einspeisetarif')`, ein Test in `createChart`; Provider für `Keycloak`, `PreiszeitreiheService`, `FeatureFlagService` |
| `tests/messwerte-grafik.spec.ts` | E2E „should show the feed-in tariff for the selected period" (Flag und Preise im Browser vorgegeben) |
| `Specs/Berechtigungen.md` | Fussnote zur Messwerte-Grafik |
| `utils/chart-preis.ts` (+ Spec) | neu: `formatPreis`, `preisPunkte`; Preiszeitreihe-Chart und Einspeisesteuerung nutzen sie statt eigener Kopien (verhaltensneutral, ihre Tests unverändert grün) |

* **Kein neuer Endpunkt, kein neues CSS:** `PreiszeitreiheService.getPunkte()` und `zev-checkbox-item` bestehen.
* **Braucht einen Neustart des Backends** für `V192`.
* **Aus dem Vorlagen-Check:** Fehlertext nach der Projektregel (`error.error?.error`, übersetzt), das Flag
  `PREISZEITREIHE` gehört zusätzlich zur Wache vor dem Preisabruf, gemeinsame y-Achsen-Basis
  (`wertAchse`).

**Zwei Befunde aus Sichtprüfung und E2E-Lauf, beide behoben:**

* **Fünf bestehende E2E-Tests schlugen fehl**, weil ihr Selektor für die Einträge der
  Einheiten-Auswahl (`.zev-checkbox-item:not(…--select-all)`) auch die neue Checkbox traf: „Alle
  auswählen" galt als unvollständig, die Zahl der Einträge stimmte nicht. Der Selektor ist jetzt
  auf `app-einheit-selector` eingegrenzt und steht einmal als Konstante (`AUSWAHL_EINTRAG`,
  `AUSWAHL_KAESTCHEN`) statt sechsfach ausgeschrieben.
* **Seitlicher Überlauf bei 390 px:** Mit drei Elementen im Aktions-Slot war die Zeile breiter als
  die Seite. `.zev-quarter-selector__actions` bricht jetzt um (`flex-wrap`, Design System).

---

## Nachtrag 22.11.2026: Produzenten im Vergleich positiv (FR-9.3)

Im Vergleichsdiagramm werden Reihe und Summe der Produzenten gespiegelt, damit die Produktion über
der Nulllinie neben Verbrauch und Bezug steht.

| Datei | Änderung |
|---|---|
| `messwerte-chart.component.ts` | `optionenMesspunkt(…, produzentenPositiv)`: spiegelt Einheiten vom Typ `PRODUCER`, `createChart` übergibt `true` nur für den Vergleich; `gespiegelt()` lässt eine 0 unverändert |
| `messwerte-chart.component.spec.ts` | zwei Tests in `Vergleich`, einer in `createChart` (Messpunkt bleibt ungespiegelt); der Vergleichs-Test in `createChart` arbeitet mit negativ gespeicherter Produktion |

* **Nur Darstellung:** kein Backend, keine Migration, keine neuen Texte.
* **Rücklieferung und Speicher** behalten ihr Vorzeichen; das Messpunkt-Diagramm ist unverändert.

---

## Nachtrag 22.11.2026: Gedeckter Verbrauch als Fläche (FR-2, FR-9.3)

Auf Wunsch des Benutzers (Vorbild: Tagesansicht des Solar-Log) bekommt der Verbrauch eine andere
Farbe, wo ihn die eigene Produktion deckt. Vorher zeigte das Diagramm einer Einheit `Total` und `ZEV`
als Linien ohne Füllung, die ZEV-Reihe unter die Nulllinie gespiegelt; der Vergleich kannte keine
ZEV-Reihe. Die Kriterien dazu lauteten „Beide Datenreihen erscheinen, die ZEV-Reihe negativ
aufgetragen" und „Die Linie ist eine Stufenlinie, ohne Flächenfüllung".

| Datei | Änderung |
|---|---|
| `messwerte-chart.component.ts` | `optionen()`: Total und ZEV als Flächen im gespeicherten Vorzeichen; `gedeckteFlaechen()`: im Vergleich je Konsument mit ZEV-Anteil eine Fläche in seiner Farbe, nach den Linien; `reihe(…, flaeche)` mit optionaler Deckkraft; Parameter `produzentenPositiv` heisst neu `vergleich` |
| `messwerte-chart.component.spec.ts` | Tests in `optionen`, `reihe` und `Vergleich` angepasst bzw. ergänzt |

* **Quelle** ist der gespeicherte `zev`-Wert je Viertelstunde — es wird nichts neu gerechnet.
* **Farben** bleiben: Total in der Primär-, ZEV in der Sekundärfarbe (FR-4).
* **Kein Backend, keine Migration, keine neuen Texte** (`ZEV` ist vorhanden).
* Die Spec führt auf Wunsch des Benutzers keinen Anhang mehr; die ersetzte Fassung steht deshalb hier.

---

## Nachtrag 24.11.2026: Legendensymbol nach Art der Reihe (FR-2)

Im Vergleich trugen die Linie einer Einheit und ihr gedeckter Verbrauch dasselbe Legendensymbol in
derselben Farbe. Gefüllte Reihen bekommen in der Legende ein gefülltes Rechteck.

| Datei | Änderung |
|---|---|
| `messwerte-chart.component.ts` | `diagrammOptionen()`: `legend.data` aus allen Reihen, Flächen mit `icon: 'roundRect'` |
| `messwerte-chart.component.spec.ts` | zwei Tests in `Vergleich` |

* Gilt für alle Diagramme der Komponente — auch `Total` und `ZEV` im Diagramm einer Einheit tragen das Rechteck.
* **Nur Darstellung:** kein Backend, keine Migration, keine neuen Texte.

---

## Nachtrag 24.11.2026: Lücken stehen auf 0 (FR-2)

Die Stufenlinie zog den letzten Wert über fehlende Viertelstunden hinweg (gesehen am Bezug vom Netz,
der nur bei Energiefluss Werte liefert). Fehlende Intervalle zwischen zwei Messwerten werden beim
Aufbau der Reihe mit 0 gefüllt.

| Datei | Änderung |
|---|---|
| `messwerte-chart.component.ts` | `zeitreihe()`: füllt jedes fehlende Intervall zwischen zwei Messwerten mit `[Zeit, 0]` (seit dem folgenden Nachtrag mit Kennzeichen) |
| `messwerte-chart.component.spec.ts` | neue Gruppe `Lücken` mit sechs Tests |

* Gilt für alle Messreihen (Total, ZEV, je Einheit im Messpunkt-Diagramm und im Vergleich), nicht für den Einspeisetarif.
* Die Ränder (vor dem ersten, nach dem letzten Messwert) bleiben leer; die Summen ändern sich nicht.
* Weitergezählt wird in Ortszeit (`naechstesIntervall()`): Mit festen 15 Minuten in Millisekunden bekam jede lückenlose Reihe in der Nacht der Umstellung auf Winterzeit vier erfundene Nullen (Befund des Akzeptanzkriterien-Checks).
* **Nur Darstellung:** kein Backend, keine Migration, keine neuen Texte.

---

## Nachtrag 24.11.2026: Strich statt 0, wo kein Messwert vorhanden ist (FR-2)

Eine gefüllte Lücke und eine Reihe ohne Messwerte sahen aus wie eine gemessene Null. Der Tooltip
nennt in einer Lücke `–`, die Legende einer Reihe ohne Messwerte `(Σ –)`.

| Datei | Änderung |
|---|---|
| `messwerte-chart.component.ts` | Kennzeichen `PUNKT_ABSCHLUSS`/`PUNKT_LUECKE` im dritten Element eines Punkts, `KEIN_WERT`; Tooltip schreibt den Strich; `summe()` liefert `null` ohne Messwerte, `legendeText()` schreibt dann `(Σ -)`; das Spiegeln behält das Kennzeichen |
| `messwerte-chart.component.spec.ts` | zwei weitere Tests in `Lücken` (Tooltip), zwei in `optionen`, einer in `Vergleich`; `Messpunkt-Diagramm` angepasst |

* Die Linie liegt in der Lücke weiterhin auf 0; ein gemessener Wert 0 und eine Summe 0 aus Messwerten bleiben Zahlen.
* Der Strich ist ein Zeichen, kein Text — kein Übersetzungs-Key, keine Migration.
* Halbgeviertstrich `–` wie in der Einspeisesteuerung statt des zuerst verlangten Bindestrichs: `(Σ -)` wirkte neben negativen Summen wie ein Minus ohne Zahl (Hinweis des Vorlagen-Checks, vom Benutzer bestätigt).
