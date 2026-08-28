import { Component, OnDestroy, OnInit, ChangeDetectorRef } from '@angular/core';
import { WithMessage } from '../../utils/with-message';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MesswerteService, MesswertData } from '../../services/messwerte.service';
import { Einheit } from '../../models/einheit.model';
import { EinheitTypPipe } from '../../pipes/einheit-typ.pipe';
import { forkJoin } from 'rxjs';
import { TranslatePipe } from '../../pipes/translate.pipe';
import { TranslationService } from '../../services/translation.service';
import { QuarterSelectorComponent } from '../quarter-selector/quarter-selector.component';
import { IconComponent } from '../icon/icon.component';
import { EinheitSelectorComponent } from '../einheit-selector/einheit-selector.component';
import { formatSwissNumber } from '../../utils/number-utils';
import { formatSwissDateTime } from '../../utils/date-utils';
import { chartFarben } from '../../utils/chart-farben';
import { ladeECharts } from '../../utils/echarts-loader';

interface ChartData {
  einheitId: number;
  einheitName: string;
  einheitTyp: string;
  /** ECharts-Instanz; `null`, solange nicht gezeichnet. */
  instanz: import('echarts/core').ECharts | null;
}

@Component({
  selector: 'app-messwerte-chart',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe, EinheitTypPipe, QuarterSelectorComponent, IconComponent, EinheitSelectorComponent],
  templateUrl: './messwerte-chart.component.html',
  styleUrls: ['./messwerte-chart.component.css']
})
export class MesswerteChartComponent extends WithMessage implements OnInit, OnDestroy {
  dateFrom: string = '';
  dateTo: string = '';
  selectedEinheiten: Einheit[] = [];
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
  /** Je Diagramm ein Beobachter - ersetzt die frühere manuelle Canvas-Berechnung. */
  private observers = new Map<number, ResizeObserver>();

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

  /**
   * Belegt den Zeitraum mit dem vorangehenden Quartal vor
   * (im Q1 wird Q4 des Vorjahres gesetzt).
   */
  private setDefaultDates(): void {
    const now = new Date();
    let year = now.getFullYear();
    let quarter = Math.ceil((now.getMonth() + 1) / 3) - 1;
    if (quarter < 1) {
      quarter = 4;
      year--;
    }
    const startMonth = (quarter - 1) * 3;
    this.dateFrom = this.formatDate(new Date(year, startMonth, 1));
    this.dateTo = this.formatDate(new Date(year, startMonth + 3, 0));
  }

  private formatDate(date: Date): string {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  onDateFromChange(): void {
    if (this.dateFrom) {
      const date = new Date(this.dateFrom);
      this.dateTo = this.formatDate(new Date(date.getFullYear(), date.getMonth() + 1, 0));
    }
  }

  onQuarterSelected(event: {von: string, bis: string}): void {
    this.dateFrom = event.von;
    this.dateTo = event.bis;
  }

  onSubmit(): void {
    if (!this.dateFrom || !this.dateTo || this.selectedEinheiten.length === 0) {
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

    const requests = this.selectedEinheiten.map(e =>
      this.messwerteService.getMesswerteByEinheit(e.id!, this.dateFrom, this.dateTo)
    );

    forkJoin(requests).subscribe({
      next: (results: MesswertData[][]) => {
        let totalDataPoints = 0;

        results.forEach((data, index) => {
          const einheit = this.selectedEinheiten[index];
          totalDataPoints += data.length;
          this.charts.push({ einheitId: einheit.id!, einheitName: einheit.name || '', einheitTyp: einheit.typ || '', instanz: null });
        });

        this.cdr.detectChanges();
        void this.createChartsSequentially(results);
        this.showMessage(`${totalDataPoints} ${this.translationService.translate('DATENPUNKTE_FUER')} ${this.charts.length} ${this.translationService.translate('EINHEITEN_GELADEN')}`, 'success');
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
   * <p>Die Staffelung (100 ms, dann 50 ms je weiteres) stammt aus der chart.js-Zeit und **bleibt**,
   * begruendet durch eine Messung mit „Alle auswaehlen" (16 Einheiten, 113'568 Datenpunkte):
   * Mit ECharts sind alle Diagramme nach 1'817 ms bemalt, davon sind 850 ms diese Staffelung — die
   * Zeichenzeit selbst liegt bei rund 60 ms je Diagramm. Alle 16 unmittelbar hintereinander zu
   * zeichnen hiesse, den Haupt-Thread rund eine Sekunde am Stueck zu blockieren. Gestaffelt
   * beantwortet die Maske einen Klick in 64 ms (chart.js: 739 ms bei 9'078 ms Gesamtzeit).
   * Die Kette kostet also weniger als das, was sie staffelt.
   *
   * <p>Der Fehlschlag des Nachladens wird **einmal** gemeldet, nicht je Einheit: Bei „Alle
   * auswählen" stünden sonst zehn identische Meldungen.
   */
  private async createChartsSequentially(results: MesswertData[][]): Promise<void> {
    if (!await this.ladeBibliothek()) {
      return;
    }

    let index = 0;
    const createNext = () => {
      if (index >= this.charts.length) return;
      this.createChart(this.charts[index], results[index]);
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
    const geladen = await ladeECharts();
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
   * Zeichnet ein Diagramm in den Behälter der Einheit.
   *
   * <p>Die Optionen sind mit Absicht denen der Preiszeitreihe nachgebaut — zwei Diagramme, die
   * gleich aussehen und gleich gebaut sind, sind zusammen billiger zu pflegen als zwei eigene Welten.
   */
  private createChart(chartData: ChartData, data: MesswertData[]): void {
    const behaelter = document.getElementById(`chart-${chartData.einheitId}`);
    if (!behaelter || !this.echarts) {
      return;
    }

    const instanz = this.echarts.init(behaelter);
    chartData.instanz = instanz;

    // Ersetzt die frühere manuelle Canvas-Berechnung samt `responsive: false` (und dem Kommentar
    // "WICHTIG! Funktioniert mit true nicht."): ECharts misst selbst, sobald wir es anstossen.
    const observer = new ResizeObserver(() => instanz.resize());
    observer.observe(behaelter);
    this.observers.set(chartData.einheitId, observer);

    instanz.setOption(this.optionen(data), true);
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
    const totalReihe = data.map(d => [new Date(d.zeit).getTime(), d.total ?? 0]);
    const zevReihe = data.map(d => [new Date(d.zeit).getTime(), -(d.zev ?? 0)]);
    const totalSumme = data.reduce((summe, d) => summe + (d.total ?? 0), 0);
    const zevSumme = data.reduce((summe, d) => summe + (d.zev ?? 0), 0);
    const kwh = this.translationService.translate('KWH');

    return {
      animation: false,
      grid: { left: 70, right: 20, top: 40, bottom: 70 },
      legend: { top: 0, textStyle: { color: farben.text } },
      tooltip: {
        trigger: 'axis',
        formatter: (params: { seriesName: string; value: [number, number] }[]) => {
          if (params.length === 0) {
            return '';
          }
          const kopf = formatSwissDateTime(new Date(params[0].value[0]));
          const zeilen = params.map(p =>
            `${p.seriesName}: ${formatSwissNumber(p.value[1], 3)} ${kwh}`);
          return [kopf, ...zeilen].join('<br>');
        }
      },
      xAxis: {
        type: 'time',
        name: this.translationService.translate('ZEIT'),
        nameLocation: 'middle',
        nameGap: 30,
        nameTextStyle: { color: farben.text },
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
      series: [
        this.reihe(this.legende('TOTAL', totalSumme, kwh), totalReihe, farben.primaer),
        this.reihe(this.legende('ZEV', zevSumme, kwh), zevReihe, farben.sekundaer)
      ]
    };
  }

  /**
   * Legendenname samt Summe, z.B. `Total (Σ 1'234.567 kWh)`.
   *
   * Der Name kommt aus dem `TranslationService` (die Keys `TOTAL` und `ZEV` sind vorhanden), die
   * Summe im Schweizer Format über `formatSwissNumber` — früher stand hier fester Text und
   * `toFixed(3)`.
   */
  private legende(key: string, summe: number, kwh: string): string {
    return `${this.translationService.translate(key)} (Σ ${formatSwissNumber(summe, 3)} ${kwh})`;
  }

  /**
   * Eine Datenreihe.
   *
   * `sampling: 'lttb'` ist dauerhaft aktiv: Ein Quartal sind bis zu 8'640 Punkte je Reihe, und bei
   * „Alle auswählen" liegen mehrere Diagramme gleichzeitig auf der Seite. Bei kleinen Mengen ist die
   * Ausdünnung unschädlich, bei grossen ist sie der Unterschied zwischen flüssig und zäh.
   */
  private reihe(name: string, daten: number[][], farbe: string): Record<string, unknown> {
    return {
      name,
      type: 'line',
      step: 'end',
      showSymbol: false,
      connectNulls: false,
      sampling: 'lttb',
      lineStyle: { color: farbe, width: 2 },
      itemStyle: { color: farbe },
      data: daten
    };
  }

}
