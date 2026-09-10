import { ladeECharts, setzeEChartsZustandZurueck } from './echarts-loader';

/**
 * Tests für `echarts-loader.ts`.
 *
 * <p>Hier wird die Bibliothek **wirklich** geladen — das ist der Punkt: Der Test prüft damit, dass
 * die registrierten Module unter den erwarteten Namen existieren. Ein umbenannter oder vergessener
 * Export fällt hier auf und nicht erst, wenn ein Diagramm stumm leer bleibt (ECharts meldet einen
 * nicht registrierten Serientyp nicht als Fehler).
 *
 * <p>Gezeichnet wird nichts: `init()` bräuchte ein gemessenes Element mit Canvas, das jsdom nicht
 * hat. Registrierung und Merken lassen sich davon unabhängig prüfen.
 */
/**
 * Grosszuegiges Zeitlimit fuer die ganze Datei statt der Vitest-Vorgabe von 5 s.
 *
 * <p>Jeder Test hier laedt ECharts **wirklich** - dynamische Imports einer grossen Bibliothek, die
 * Vite beim ersten Zugriff noch transformiert. Allein gemessen dauert der kalte Ladevorgang gut
 * 6 s; im vollen Lauf konkurriert er zusaetzlich mit den uebrigen 58 Testdateien. Die Datei war
 * damit flaky: allein gruen, in der Suite gelegentlich `Test timed out in 5000ms`.
 *
 * <p><b>Nicht die Loesung: den echten Import durch einen Mock ersetzen.</b> Genau das echte Laden
 * ist der Zweck dieser Tests - sie pruefen, dass die registrierten Module unter den erwarteten
 * Namen existieren. Mit einem Mock wuerde ein umbenannter Export erst auffallen, wenn ein Diagramm
 * stumm leer bleibt.
 */
describe('ladeECharts', { timeout: 20_000 }, () => {

  beforeEach(() => {
    setzeEChartsZustandZurueck();
  });

  it('should load the library', async () => {
    const echarts = await ladeECharts();

    expect(echarts).not.toBeNull();
    expect(typeof echarts?.init).toBe('function');
    expect(typeof echarts?.use).toBe('function');
  });

  it('should remember the library instead of loading twice', async () => {
    const ersteAntwort = await ladeECharts();
    const zweiteAntwort = await ladeECharts();

    // Dieselbe Instanz: Zwei Diagramme auf einer Seite teilen sich einen Ladevorgang.
    expect(zweiteAntwort).toBe(ersteAntwort);
  });

  it('should register the modules of both charts', async () => {
    // Der eigentliche Nutzen dieses Tests: Existieren die Exporte, die der Loader registriert?
    const [charts, komponenten, renderer] = await Promise.all([
      import('echarts/charts'),
      import('echarts/components'),
      import('echarts/renderers')
    ]);

    expect(charts.LineChart).toBeDefined();
    expect(charts.BarChart).toBeDefined();
    expect(komponenten.GridComponent).toBeDefined();
    expect(komponenten.TooltipComponent).toBeDefined();
    expect(komponenten.DataZoomComponent).toBeDefined();
    expect(komponenten.LegendComponent).toBeDefined();
    expect(renderer.CanvasRenderer).toBeDefined();
  });

  it('should register without throwing', async () => {
    // `use` doppelt aufzurufen ist erlaubt und darf nicht scheitern - der Loader kann in einer
    // frisch zurueckgesetzten Umgebung mehrfach laufen.
    setzeEChartsZustandZurueck();
    await expect(ladeECharts()).resolves.not.toBeNull();
    setzeEChartsZustandZurueck();
    await expect(ladeECharts()).resolves.not.toBeNull();
  });

  it('should forget the library after a reset', async () => {
    const vorher = await ladeECharts();
    setzeEChartsZustandZurueck();

    const nachher = await ladeECharts();

    // Nach dem Zuruecksetzen wird neu geladen - fuer Tests, damit ein Fehlschlag-Test die
    // folgenden nicht mit `null` bedient.
    expect(nachher).not.toBeNull();
    expect(typeof nachher?.init).toBe('function');
    expect(vorher).not.toBeNull();
  });
});
