import { Component, OnDestroy, OnInit, ChangeDetectorRef, ChangeDetectionStrategy } from '@angular/core';
import { WithMessage } from '../../utils/with-message';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MesswerteService, MesswertData } from '../../services/messwerte.service';
import { Einheit, MesspunktGruppe } from '../../models/einheit.model';
import { MesspunktGruppePipe } from '../../pipes/messpunkt-gruppe.pipe';
import { forkJoin } from 'rxjs';
import { TranslatePipe } from '../../pipes/translate.pipe';
import { TranslationService } from '../../services/translation.service';
import { QuarterSelectorComponent } from '../quarter-selector/quarter-selector.component';
import { IconComponent } from '../icon/icon.component';
import { EinheitSelectorComponent } from '../einheit-selector/einheit-selector.component';
import { formatSwissNumber } from '../../utils/number-utils';
import { blaettereZeitraum, formatSwissDateTime, monatsende, toIsoDate, vorquartal } from '../../utils/date-utils';
import { ChartFarben, chartFarben } from '../../utils/chart-farben';
import { ladeECharts } from '../../utils/echarts-loader';

/** Länge eines Messintervalls: `messwerte.zeit` trägt den Beginn einer Viertelstunde. */
const INTERVALL_MS = 15 * 60 * 1000;

interface ChartData {
  /** Eindeutig je Diagramm: die Einheit-ID oder `messpunkt-<n>`. Teil der DOM-ID des Behälters. */
  schluessel: string;
  /** Eine Einheit — oder alle Einheiten eines Messpunkts (Specs/EChart.md, FR-8). */
  einheiten: Einheit[];
  /** Nur beim Diagramm eines Messpunkts gesetzt. */
  messpunkt?: string;
  /** ECharts-Instanz; `null`, solange nicht gezeichnet. */
  instanz: import('echarts/core').ECharts | null;
}

@Component({
  selector: 'app-messwerte-chart',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe, MesspunktGruppePipe, QuarterSelectorComponent, IconComponent, EinheitSelectorComponent],
  templateUrl: './messwerte-chart.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrls: ['./messwerte-chart.component.css']
})
export class MesswerteChartComponent extends WithMessage implements OnInit, OnDestroy {
  dateFrom: string = '';
  dateTo: string = '';
  selectedEinheiten: Einheit[] = [];
  /** Gewählte Messpunkte mit mehreren Einheiten — je einer ein zusätzliches Diagramm (FR-8). */
  selectedGruppen: MesspunktGruppe[] = [];
  loading = false;

  charts: ChartData[] = [];
  /** Solange die Bibliothek nachgeladen wird, zeigt jedes Panel einen Hinweis. */
  bibliothekLaedt = false;
  /** Nachladen gescheitert - einmal gemeldet, nicht je Einheit. */
  bibliothekFehlt = false;

  /**
   * ECharts wird **dynamisch** nachgeladen (`utils/echarts-loader.ts`): `/chart` ist eine eager
   * Route, ein statischer Import landete im Initial-Bundle und jede Seite der Anwendung lüde die
   * Bibliothek mit (Specs/EChart.md, NFR-1).
   */
  private echarts?: typeof import('echarts/core');
  /**
   * Der Lader als Feld, damit ein Test das Scheitern des Nachladens vorgeben kann. Das Modul
   * selbst lässt sich im Test nicht ersetzen, und ein echter Fehlschlag bräuchte einen fehlenden
   * Chunk.
   */
  private lade: typeof ladeECharts = ladeECharts;
  /** Je Diagramm ein Beobachter - ersetzt die frühere manuelle Canvas-Berechnung. */
  private observers = new Map<string, ResizeObserver>();

  constructor(
    private messwerteService: MesswerteService,
    private translationService: TranslationService,
    private cdr: ChangeDetectorRef
  ) { super(); }

  ngOnInit(): void {
    this.setDefaultDates();
  }

  ngOnDestroy(): void {
    this.gebeDiagrammeFrei();
  }

  /**
   * Gibt alle Instanzen und Beobachter frei.
   *
   * Nötig an zwei Stellen: beim Verlassen der Seite und vor jedem neuen „Anzeigen". Ohne die
   * Freigabe wächst der Speicher mit jedem Klick, denn die Seite erlaubt beliebig viele Diagramme.
   */
  private gebeDiagrammeFrei(): void {
    this.observers.forEach(observer => observer.disconnect());
    this.observers.clear();
    this.charts.forEach(chartData => chartData.instanz?.dispose());
  }

  onSelectionChange(einheiten: Einheit[]): void {
    this.selectedEinheiten = einheiten;
  }

  onGruppenChange(gruppen: MesspunktGruppe[]): void {
    this.selectedGruppen = gruppen;
  }

  /** Mindestens eine Einheit oder ein Messpunkt gewählt — sonst gibt es nichts zu zeichnen. */
  get hatAuswahl(): boolean {
    return this.selectedEinheiten.length > 0 || this.selectedGruppen.length > 0;
  }

  /**
   * Die Einheiten, deren Messwerte geladen werden: die gewählten und die der gewählten Messpunkte,
   * **jede einmal**. Wer Bezug, Rücklieferung und ihren Messpunkt wählt, löst zwei Abfragen aus,
   * nicht vier.
   */
  private zuLadendeEinheiten(): Einheit[] {
    const jeId = new Map<number, Einheit>();
    [...this.selectedEinheiten, ...this.selectedGruppen.flatMap(g => g.einheiten)]
      .forEach(e => jeId.set(e.id!, e));
    return [...jeId.values()];
  }

  /**
   * Belegt den Zeitraum mit dem vorangehenden Quartal vor
   * (im Q1 wird Q4 des Vorjahres gesetzt).
   */
  private setDefaultDates(): void {
    const { von, bis } = vorquartal();
    this.dateFrom = von;
    this.dateTo = bis;
  }

  onDateFromChange(): void {
    if (this.dateFrom) {
      this.dateTo = monatsende(this.dateFrom);
    }
  }

  onQuarterSelected(event: {von: string, bis: string}): void {
    this.dateFrom = event.von;
    this.dateTo = event.bis;
  }

  /**
   * Setzt den Zeitraum auf heute–heute und zeigt die Grafik sofort an — wie in der Statistik
   * (Specs/EChart.md, FR-1a). Auch ein zweiter Klick lädt: Für den laufenden Tag kommen laufend
   * Messwerte dazu.
   *
   * <p>Über {@link toIsoDate} statt {@code toISOString()}: Letzteres liefert das UTC-Datum, und
   * kurz nach Mitternacht stünde dann der Vortag im Feld.
   */
  onHeute(): void {
    const heute = toIsoDate(new Date());
    this.dateFrom = heute;
    this.dateTo = heute;
    this.onSubmit();
  }

  /**
   * Blättert den Zeitraum um seine eigene Länge und lädt sofort — dieselbe Regel wie in der
   * Statistik (`blaettereZeitraum`, Specs/Statistik.md, FR-4).
   */
  onBlaettern(richtung: 1 | -1): void {
    if (!this.kannBlaettern) {
      return;
    }
    const neu = blaettereZeitraum(this.dateFrom, this.dateTo, richtung);
    this.dateFrom = neu.von;
    this.dateTo = neu.bis;
    this.onSubmit();
  }

  /**
   * Blättern nur mit gültigem Zeitraum, einer Auswahl und nicht während des Ladens.
   * Ohne Auswahl liefe das sofortige Laden nur in die Fehlermeldung.
   */
  get kannBlaettern(): boolean {
    return !this.loading && this.hatAuswahl
      && !!this.dateFrom && !!this.dateTo && this.dateFrom <= this.dateTo;
  }

  onSubmit(): void {
    if (!this.dateFrom || !this.dateTo || !this.hatAuswahl) {
      this.showMessage(this.translationService.translate('BITTE_ALLE_FELDER_AUSFUELLEN'), 'error');
      return;
    }

    if (this.dateFrom > this.dateTo) {
      this.showMessage(this.translationService.translate('START_DATUM_MUSS_VOR_END_DATUM_LIEGEN'), 'error');
      return;
    }

    this.loading = true;
    this.gebeDiagrammeFrei();
    this.charts = [];

    // Auswahl festhalten: Sie kann sich aendern, waehrend die Antwort unterwegs ist.
    const einheiten = this.zuLadendeEinheiten();
    const einzelne = [...this.selectedEinheiten];
    const gruppen = [...this.selectedGruppen];
    const requests = einheiten.map(e =>
      this.messwerteService.getMesswerteByEinheit(e.id!, this.dateFrom, this.dateTo)
    );

    forkJoin(requests).subscribe({
      next: (results: MesswertData[][]) => {
        let totalDataPoints = 0;
        const datenJeEinheit = new Map<number, MesswertData[]>();

        results.forEach((data, index) => {
          totalDataPoints += data.length;
          datenJeEinheit.set(einheiten[index].id!, data);
        });

        // Erst je Einheit ein Diagramm, danach je Messpunkt eines - dieselbe Reihenfolge wie in
        // der Auswahl.
        this.charts = [
          ...einzelne.map(e => ({ schluessel: `${e.id}`, einheiten: [e], instanz: null })),
          ...gruppen.map((g, index) => ({
            schluessel: `messpunkt-${index}`, einheiten: g.einheiten, messpunkt: g.messpunkt, instanz: null
          }))
        ];

        this.cdr.detectChanges();
        void this.createChartsSequentially(datenJeEinheit);
        // Gezaehlt werden die geladenen Einheiten, nicht die Diagramme: Ein Messpunkt-Diagramm
        // laedt keine eigenen Daten.
        this.showMessage(`${totalDataPoints} ${this.translationService.translate('DATENPUNKTE_FUER')} ${einheiten.length} ${this.translationService.translate('EINHEITEN_GELADEN')}`, 'success');
        this.loading = false;
      },
      error: (error) => {
        this.showMessage(`${this.translationService.translate('FEHLER_BEIM_LADEN_DER_DATEN')}: ${error.message}`, 'error');
        this.loading = false;
      }
    });
  }

  /**
   * Lädt die Bibliothek **einmal** und zeichnet die Diagramme danach nacheinander.
   *
   * <p>Die Staffelung (100 ms, dann 50 ms je weiteres) stammt aus der Zeit vor ECharts und **bleibt**,
   * begruendet durch eine Messung mit „Alle auswaehlen" (16 Einheiten, 113'568 Datenpunkte):
   * Mit ECharts sind alle Diagramme nach 1'817 ms bemalt, davon sind 850 ms diese Staffelung — die
   * Zeichenzeit selbst liegt bei rund 60 ms je Diagramm. Alle 16 unmittelbar hintereinander zu
   * zeichnen hiesse, den Haupt-Thread rund eine Sekunde am Stueck zu blockieren. Gestaffelt
   * beantwortet die Maske einen Klick in 64 ms (vorher: 739 ms bei 9'078 ms Gesamtzeit).
   * Die Kette kostet also weniger als das, was sie staffelt.
   *
   * <p>Der Fehlschlag des Nachladens wird **einmal** gemeldet, nicht je Einheit: Bei „Alle
   * auswählen" stünden sonst zehn identische Meldungen.
   */
  private async createChartsSequentially(datenJeEinheit: Map<number, MesswertData[]>): Promise<void> {
    if (!await this.ladeBibliothek()) {
      return;
    }

    let index = 0;
    const createNext = () => {
      if (index >= this.charts.length) return;
      this.createChart(this.charts[index], datenJeEinheit);
      index++;
      if (index < this.charts.length) setTimeout(createNext, 50);
    };
    setTimeout(createNext, 100);
  }

  /** Lädt `echarts` beim ersten Zeichnen nach. `false`, wenn das misslingt. */
  private async ladeBibliothek(): Promise<boolean> {
    if (this.echarts) {
      return true;
    }
    if (this.bibliothekFehlt) {
      return false;
    }
    this.bibliothekLaedt = true;
    this.cdr.detectChanges();
    const geladen = await this.lade();
    this.bibliothekLaedt = false;
    if (!geladen) {
      this.bibliothekFehlt = true;
      this.showMessage(this.translationService.translate('DIAGRAMM_NICHT_LADBAR'), 'error');
      this.cdr.detectChanges();
      return false;
    }
    this.echarts = geladen;
    this.cdr.detectChanges();
    return true;
  }

  /**
   * Zeichnet ein Diagramm in seinen Behälter — für eine Einheit oder für einen Messpunkt.
   *
   * <p>Die Optionen sind mit Absicht denen der Preiszeitreihe nachgebaut — zwei Diagramme, die
   * gleich aussehen und gleich gebaut sind, sind zusammen billiger zu pflegen als zwei eigene Welten.
   */
  private createChart(chartData: ChartData, datenJeEinheit: Map<number, MesswertData[]>): void {
    const behaelter = document.getElementById(`chart-${chartData.schluessel}`);
    if (!behaelter || !this.echarts) {
      return;
    }

    const instanz = this.echarts.init(behaelter);
    chartData.instanz = instanz;

    // Ersetzt die frühere manuelle Canvas-Berechnung samt `responsive: false` (und dem Kommentar
    // "WICHTIG! Funktioniert mit true nicht."): ECharts misst selbst, sobald wir es anstossen.
    const observer = new ResizeObserver(() => instanz.resize());
    observer.observe(behaelter);
    this.observers.set(chartData.schluessel, observer);

    const daten = (einheit: Einheit) => datenJeEinheit.get(einheit.id!) ?? [];
    const optionen = chartData.messpunkt
      ? this.optionenMesspunkt(chartData.einheiten.map(einheit => ({ einheit, daten: daten(einheit) })))
      : this.optionen(daten(chartData.einheiten[0]));
    instanz.setOption(optionen, true);
  }

  /**
   * Diagramm-Optionen für eine Einheit.
   *
   * <p><b>Stufenlinie</b> (`step: 'end'`): Ein Messwert gilt für die **ganze** Viertelstunde. Die
   * frühere geglättete Linie (`tension: 0.1`) behauptete einen stetigen Verlauf zwischen den
   * Intervallen, den es nicht gibt.
   *
   * <p><b>Die ZEV-Reihe wird negativ aufgetragen</b> — wie bisher. Das spiegelt sie unter die
   * Nulllinie und macht Bezug und Eigenverbrauch auf einen Blick unterscheidbar.
   */
  private optionen(data: MesswertData[]): Record<string, unknown> {
    const farben = chartFarben();
    const kwh = this.translationService.translate('KWH');
    const zevReihe = data.map(d => [new Date(d.zeit).getTime(), -(d.zev ?? 0)]);
    const zevSumme = data.reduce((summe, d) => summe + (d.zev ?? 0), 0);

    return this.diagrammOptionen([
      this.reihe(this.legende('TOTAL', this.totalSumme(data), kwh), this.totalReihe(data), farben.primaer),
      this.reihe(this.legende('ZEV', zevSumme, kwh), zevReihe, farben.sekundaer)
    ], farben, kwh);
  }

  /**
   * Diagramm-Optionen für einen Messpunkt mit mehreren Einheiten (Specs/EChart.md, FR-8):
   * **eine Reihe je Einheit**, jeweils ihr `total` mit dem Vorzeichen, wie es gespeichert ist.
   *
   * <p>Bei Bezug und Rücklieferung ergibt das von selbst das gewünschte Bild: Der Bezug ist positiv,
   * die Rücklieferung negativ gespeichert — die eine Linie liegt über, die andere unter der
   * Nulllinie. Gespiegelt wird hier deshalb **nichts**, anders als bei der ZEV-Reihe.
   *
   * <p>Die ZEV-Reihe entfällt: Sie gehört zur einzelnen Einheit, und zwei davon machten aus dem
   * Vergleich vier Linien.
   *
   * <p>Die ersten beiden Reihen tragen die beiden Reihenfarben aus den Design-Tokens. Mehr als zwei
   * Einheiten an einem Messpunkt sind nicht vorgesehen; kämen sie vor, nimmt ECharts für die
   * weiteren seine eigene Palette — unterscheidbar, statt zweimal dieselbe Farbe.
   */
  private optionenMesspunkt(reihen: { einheit: Einheit; daten: MesswertData[] }[]): Record<string, unknown> {
    const farben = chartFarben();
    const reihenfarben = [farben.primaer, farben.sekundaer];
    const kwh = this.translationService.translate('KWH');

    return this.diagrammOptionen(reihen.map(({ einheit, daten }, index) => this.reihe(
      this.legendeText(einheit.name, this.totalSumme(daten), kwh),
      this.totalReihe(daten),
      reihenfarben[index])), farben, kwh);
  }

  /** `total` als Zeitreihe `[Zeitpunkt, Wert]`, im gespeicherten Vorzeichen. */
  private totalReihe(data: MesswertData[]): number[][] {
    return data.map(d => [new Date(d.zeit).getTime(), d.total ?? 0]);
  }

  private totalSumme(data: MesswertData[]): number {
    return data.reduce((summe, d) => summe + (d.total ?? 0), 0);
  }

  /** Was beide Diagrammarten teilen: Achsen, Tooltip, Legende, Zoom — nur die Reihen unterscheiden sich. */
  private diagrammOptionen(series: Record<string, unknown>[], farben: ChartFarben,
                           kwh: string): Record<string, unknown> {
    return {
      animation: false,
      grid: { left: 70, right: 20, top: 40, bottom: 70 },
      // `scroll`: Die Legende bleibt einzeilig und blaettert, wenn sie nicht passt. Beim
      // Messpunkt-Diagramm stehen die Namen der Einheiten darin; auf schmalen Anzeigen brach sie
      // sonst auf zwei Zeilen um und lag ueber dem Achsentitel und der Zeichenflaeche.
      legend: { type: 'scroll', top: 0, textStyle: { color: farben.text } },
      tooltip: {
        trigger: 'axis',
        formatter: (alle: { seriesName: string; value: number[] }[]) => {
          // Der Abschlusspunkt (siehe mitAbschluss) ist kein Messwert und hat keinen Tooltip.
          const params = alle.filter(p => !p.value[2]);
          if (params.length === 0) {
            return '';
          }
          const kopf = formatSwissDateTime(new Date(params[0].value[0]));
          const zeilen = params.map(p =>
            `${p.seriesName}: ${formatSwissNumber(p.value[1], 3)} ${kwh}`);
          return [kopf, ...zeilen].join('<br>');
        }
      },
      // Kein Achsentitel: "Zeit" stand mitten im Zoom-Schieber darunter, und dass die Achse die
      // Zeit zeigt, sagen die Uhrzeiten an ihr ohnehin (Specs/EChart.md, FR-2).
      xAxis: {
        type: 'time',
        axisLine: { lineStyle: { color: farben.achse } },
        axisLabel: { color: farben.text }
      },
      yAxis: {
        type: 'value',
        name: kwh,
        nameTextStyle: { color: farben.text },
        axisLine: { lineStyle: { color: farben.achse } },
        axisLabel: {
          color: farben.text,
          formatter: (wert: number) => formatSwissNumber(wert, 3)
        },
        splitLine: { lineStyle: { color: farben.gitter } }
      },
      dataZoom: [{ type: 'inside' }, { type: 'slider', bottom: 10 }],
      series
    };
  }

  /**
   * Legendenname samt Summe, z.B. `Total (Σ 1'234.567 kWh)`.
   *
   * Der Name kommt aus dem `TranslationService` (die Keys `TOTAL` und `ZEV` sind vorhanden), die
   * Summe im Schweizer Format über `formatSwissNumber` — früher stand hier fester Text und
   * eine feste Rundung ohne Tausendertrennzeichen.
   */
  private legende(key: string, summe: number, kwh: string): string {
    return this.legendeText(this.translationService.translate(key), summe, kwh);
  }

  /** Wie {@link legende}, aber mit fertigem Namen — beim Messpunkt-Diagramm ist das der Name der Einheit. */
  private legendeText(name: string, summe: number, kwh: string): string {
    return `${name} (Σ ${formatSwissNumber(summe, 3)} ${kwh})`;
  }

  /**
   * Eine Datenreihe.
   *
   * `sampling: 'lttb'` ist dauerhaft aktiv: Ein Quartal sind bis zu 8'640 Punkte je Reihe, und bei
   * „Alle auswählen" liegen mehrere Diagramme gleichzeitig auf der Seite. Bei kleinen Mengen ist die
   * Ausdünnung unschädlich, bei grossen ist sie der Unterschied zwischen flüssig und zäh.
   */
  private reihe(name: string, daten: number[][], farbe?: string): Record<string, unknown> {
    return {
      name,
      type: 'line',
      step: 'end',
      showSymbol: false,
      connectNulls: false,
      sampling: 'lttb',
      // Ohne Farbe (dritte und weitere Reihe eines Messpunkts) waehlt ECharts aus seiner Palette.
      lineStyle: farbe ? { color: farbe, width: 2 } : { width: 2 },
      ...(farbe ? { itemStyle: { color: farbe } } : {}),
      data: this.mitAbschluss(daten)
    };
  }

  /**
   * Hängt an eine Reihe den **Abschlusspunkt** an: das Ende des letzten Intervalls, mit dessen Wert.
   *
   * Eine Stufenlinie (`step: 'end'`) zeichnet jeden Wert als waagrechtes Stück bis zum **nächsten**
   * Punkt. Der letzte Punkt hat keinen nächsten: Seine Viertelstunde fehlte im Bild, und eine
   * Reihe mit einem einzigen Messwert zeichnete gar nichts — obwohl die Legende eine Summe
   * nannte. Belegt am 21.11.2026 an einer frisch angelegten Einheit mit genau einem Intervall.
   *
   * Der Punkt trägt als drittes Element eine `1`, damit der Tooltip ihn überspringt: Er ist kein
   * Messwert. In die Summe der Legende geht er nicht ein, sie wird aus den Messwerten gebildet.
   */
  private mitAbschluss(daten: number[][]): number[][] {
    if (daten.length === 0) {
      return daten;
    }
    const letzter = daten[daten.length - 1];
    return [...daten, [letzter[0] + INTERVALL_MS, letzter[1], 1]];
  }

}
