/**
 * Nachladen von ECharts für die Diagramme der Anwendung
 * (`Specs/EChart.md`, FR-5).
 *
 * **Warum dynamisch:** `/chart` und `/tarife` sind eager Routen. Ein statischer Import landete im
 * Initial-Bundle, und jede Seite der Anwendung lüde die Bibliothek mit — auch wer nie ein Diagramm
 * öffnet. So liegt sie in eigenen Chunks und wird erst beim ersten Zeichnen geholt.
 *
 * **Warum an einer Stelle:** Der Modulsatz wird **einmal** registriert, als Vereinigung dessen, was
 * alle Diagramme brauchen. Eine Liste je Aufrufer wäre die gefährlichere Variante: Ein nicht
 * registrierter Serientyp zeichnet **stumm nichts** — ECharts meldet ihn nicht als Fehler. Genau
 * daran ist der Balken-Serientyp der Preiszeitreihe zuerst gescheitert, und mit einer Liste je
 * Aufrufer gäbe es diese Falle mehrfach.
 */

type EChartsCore = typeof import('echarts/core');

/** Einmal geladen, über alle Aufrufer geteilt. */
let echarts: EChartsCore | null = null;

/** Merkt einen Fehlschlag, damit nicht bei jedem Zeichnen erneut geladen wird. */
let fehlgeschlagen = false;

/**
 * Lädt ECharts nach und registriert die benötigten Module.
 *
 * @returns Die Bibliothek, oder `null`, wenn das Nachladen scheitert
 */
export async function ladeECharts(): Promise<EChartsCore | null> {
  if (echarts) {
    return echarts;
  }
  if (fehlgeschlagen) {
    return null;
  }
  try {
    const [core, charts, komponenten, renderer] = await Promise.all([
      import('echarts/core'),
      import('echarts/charts'),
      import('echarts/components'),
      import('echarts/renderers')
    ]);
    core.use([
      // Vereinigung aller Diagramme: Linie (Messwerte, Preiszeitreihe) und Balken (Preiszeitreihe).
      charts.LineChart,
      charts.BarChart,
      komponenten.GridComponent,
      komponenten.TooltipComponent,
      komponenten.DataZoomComponent,
      komponenten.LegendComponent,
      renderer.CanvasRenderer
    ]);
    echarts = core;
    return echarts;
  } catch {
    // Bewusst kein Werfen: Ein fehlgeschlagener Chunk-Download ist ein Betriebsfall, kein
    // Programmfehler. Der Aufrufer zeigt DIAGRAMM_NICHT_LADBAR und bleibt bedienbar.
    fehlgeschlagen = true;
    return null;
  }
}

/**
 * Setzt den gemerkten Zustand zurück — **nur für Tests**.
 *
 * Ohne das trägt ein Test den Ladezustand des vorherigen in sich, und ein Fehlschlag-Test würde
 * alle folgenden Tests derselben Datei mit `null` bedienen.
 */
export function setzeEChartsZustandZurueck(): void {
  echarts = null;
  fehlgeschlagen = false;
}
