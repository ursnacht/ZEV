import { Component, OnInit, ChangeDetectorRef, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Chart, ChartConfiguration, registerables } from 'chart.js';
import { MesswerteService, MesswertData } from '../../services/messwerte.service';
import { EinheitService } from '../../services/einheit.service';
import { Einheit } from '../../models/einheit.model';
import { forkJoin } from 'rxjs';
import { TranslatePipe } from '../../pipes/translate.pipe';
import { TranslationService } from '../../services/translation.service';
import { QuarterSelectorComponent } from '../quarter-selector/quarter-selector.component';
import { IconComponent } from '../icon/icon.component';

Chart.register(...registerables);

interface ChartData {
  einheitId: number;
  einheitName: string;
  einheitTyp: string;
  chart: Chart | null;
}

@Component({
  selector: 'app-messwerte-chart',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe, QuarterSelectorComponent, IconComponent],
  templateUrl: './messwerte-chart.component.html',
  styleUrls: ['./messwerte-chart.component.css']
})
export class MesswerteChartComponent implements OnInit, OnDestroy {
  dateFrom: string = '';
  dateTo: string = '';
  selectedEinheitIds: Set<number> = new Set();
  einheiten: Einheit[] = [];
  loading = false;
  message = '';
  messageType: 'success' | 'error' | '' = '';
  charts: ChartData[] = [];

  constructor(
    private messwerteService: MesswerteService,
    private einheitService: EinheitService,
    private translationService: TranslationService,
    private cdr: ChangeDetectorRef
  ) { }

  ngOnDestroy(): void {
    // Cleanup all charts to prevent memory leaks
    this.charts.forEach(chartData => {
      if (chartData.chart) {
        chartData.chart.destroy();
      }
    });
  }

  ngOnInit(): void {
    this.loadEinheiten();
  }

  loadEinheiten(): void {
    this.einheitService.getAllEinheiten().subscribe({
      next: (data) => {
        this.einheiten = data.sort((a, b) => {
          const nameA = (a.name || '').toLowerCase();
          const nameB = (b.name || '').toLowerCase();
          return nameA.localeCompare(nameB);
        });
      },
      error: (error) => {
        this.showMessage(this.translationService.translate('FEHLER_BEIM_LADEN_DER_EINHEITEN') + ': ' + error.message, 'error');
      }
    });
  }

  onEinheitToggle(einheitId: number): void {
    if (this.selectedEinheitIds.has(einheitId)) {
      this.selectedEinheitIds.delete(einheitId);
    } else {
      this.selectedEinheitIds.add(einheitId);
    }
  }

  allSelected(): boolean {
    return this.einheiten.length > 0 && this.selectedEinheitIds.size === this.einheiten.length;
  }

  someSelected(): boolean {
    return this.selectedEinheitIds.size > 0 && this.selectedEinheitIds.size < this.einheiten.length;
  }

  onSelectAllToggle(): void {
    if (this.allSelected()) {
      this.selectedEinheitIds.clear();
    } else {
      this.einheiten.forEach(e => {
        if (e.id) {
          this.selectedEinheitIds.add(e.id);
        }
      });
    }
  }

  onDateFromChange(): void {
    if (this.dateFrom) {
      const date = new Date(this.dateFrom);
      const lastDay = new Date(date.getFullYear(), date.getMonth() + 1, 0);
      const year = lastDay.getFullYear();
      const month = String(lastDay.getMonth() + 1).padStart(2, '0');
      const day = String(lastDay.getDate()).padStart(2, '0');
      this.dateTo = `${year}-${month}-${day}`;
    }
  }

  onQuarterSelected(event: {von: string, bis: string}): void {
    this.dateFrom = event.von;
    this.dateTo = event.bis;
  }

  onSubmit(): void {
    if (!this.dateFrom || !this.dateTo || this.selectedEinheitIds.size === 0) {
      this.showMessage(this.translationService.translate('BITTE_ALLE_FELDER_AUSFUELLEN'), 'error');
      return;
    }

    if (this.dateFrom > this.dateTo) {
      this.showMessage(this.translationService.translate('START_DATUM_MUSS_VOR_END_DATUM_LIEGEN'), 'error');
      return;
    }

    this.loading = true;

    // Destroy existing charts
    this.charts.forEach(chartData => {
      if (chartData.chart) {
        chartData.chart.destroy();
      }
    });
    this.charts = [];

    // Create observables for all selected einheiten
    const requests = Array.from(this.selectedEinheitIds).map(einheitId =>
      this.messwerteService.getMesswerteByEinheit(einheitId, this.dateFrom, this.dateTo)
    );

    // Load data for all selected einheiten in parallel
    forkJoin(requests).subscribe({
      next: (results: MesswertData[][]) => {
        let totalDataPoints = 0;

        results.forEach((data, index) => {
          const einheitId = Array.from(this.selectedEinheitIds)[index];
          const einheit = this.einheiten.find(e => e.id === einheitId);

          if (!einheit) return;

          totalDataPoints += data.length;

          // Create chart data object
          const chartData: ChartData = {
            einheitId: einheitId,
            einheitName: einheit.name || '',
            einheitTyp: einheit.typ || '',
            chart: null
          };

          this.charts.push(chartData);
        });

        // Trigger change detection to ensure DOM is updated
        this.cdr.detectChanges();

        // Create charts sequentially with delays to prevent browser freeze
        this.createChartsSequentially(results);

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
   * Create charts one by one with small delays to prevent browser freeze
   */
  private createChartsSequentially(results: MesswertData[][]): void {
    let index = 0;

    const createNext = () => {
      if (index >= this.charts.length) return;

      const chartData = this.charts[index];
      const data = results[index];
      this.createChart(chartData, data);

      index++;

      // Use setTimeout to give browser time to render
      if (index < this.charts.length) {
        setTimeout(createNext, 50);
      }
    };

    // Start after a small delay to ensure DOM is ready
    setTimeout(createNext, 100);
  }

  private createChart(chartData: ChartData, data: MesswertData[]): void {
    const canvas = document.getElementById(`chart-${chartData.einheitId}`) as HTMLCanvasElement;
    if (!canvas) {
      return;
    }

    // Set canvas dimensions explicitly from parent container
    const parent = canvas.parentElement;
    if (parent) {
      const parentWidth = parent.clientWidth || 800;
      const parentHeight = parent.clientHeight || 400;
      canvas.width = parentWidth;
      canvas.height = parentHeight;
    }

    const ctx = canvas.getContext('2d');
    if (!ctx) {
      return;
    }

    const labels = data.map(d => new Date(d.zeit).toLocaleString('de-DE'));
    const totalValues = data.map(d => d.total ?? 0);
    const zevValues = data.map(d => -(d.zevCalculated ?? 0)); // Negative for downward display

    // Calculate sums for legend
    const totalSum = data.reduce((sum, d) => sum + (d.total ?? 0), 0);
    const zevSum = data.reduce((sum, d) => sum + (d.zevCalculated ?? 0), 0);

    const config: ChartConfiguration = {
      type: 'line',
      data: {
        labels: labels,
        datasets: [
          {
            label: `Total (Σ ${totalSum.toFixed(3)} kWh)`,
            data: totalValues,
            borderColor: '#4CAF50',
            backgroundColor: 'rgba(76, 175, 80, 0.1)',
            tension: 0.1,
            fill: false
          },
          {
            label: `ZEV Calculated (Σ ${zevSum.toFixed(3)} kWh)`,
            data: zevValues,
            borderColor: '#2196F3',
            backgroundColor: 'rgba(33, 150, 243, 0.1)',
            tension: 0.1,
            fill: false
          }
        ]
      },
      options: {
        responsive: false,
        maintainAspectRatio: false,
        scales: {
          y: {
            title: {
              display: true,
              text: 'kWh'
            }
          },
          x: {
            title: {
              display: true,
              text: this.translationService.translate('ZEIT')
            }
          }
        }
      }
    };

    chartData.chart = new Chart(ctx, config);
  }

  private showMessage(message: string, type: 'success' | 'error'): void {
    this.message = message;
    this.messageType = type;
    setTimeout(() => {
      this.message = '';
      this.messageType = '';
    }, 5000);
  }
}
