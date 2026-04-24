import { Component, OnDestroy, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Chart, ChartConfiguration, registerables } from 'chart.js';
import { MesswerteService, MesswertData } from '../../services/messwerte.service';
import { Einheit } from '../../models/einheit.model';
import { forkJoin } from 'rxjs';
import { TranslatePipe } from '../../pipes/translate.pipe';
import { TranslationService } from '../../services/translation.service';
import { QuarterSelectorComponent } from '../quarter-selector/quarter-selector.component';
import { IconComponent } from '../icon/icon.component';
import { EinheitSelectorComponent } from '../einheit-selector/einheit-selector.component';

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
  imports: [CommonModule, FormsModule, TranslatePipe, QuarterSelectorComponent, IconComponent, EinheitSelectorComponent],
  templateUrl: './messwerte-chart.component.html',
  styleUrls: ['./messwerte-chart.component.css']
})
export class MesswerteChartComponent implements OnDestroy {
  dateFrom: string = '';
  dateTo: string = '';
  selectedEinheiten: Einheit[] = [];
  loading = false;
  message = '';
  messageType: 'success' | 'error' | '' = '';
  charts: ChartData[] = [];

  constructor(
    private messwerteService: MesswerteService,
    private translationService: TranslationService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnDestroy(): void {
    this.charts.forEach(chartData => { if (chartData.chart) chartData.chart.destroy(); });
  }

  onSelectionChange(einheiten: Einheit[]): void {
    this.selectedEinheiten = einheiten;
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
    if (!this.dateFrom || !this.dateTo || this.selectedEinheiten.length === 0) {
      this.showMessage(this.translationService.translate('BITTE_ALLE_FELDER_AUSFUELLEN'), 'error');
      return;
    }

    if (this.dateFrom > this.dateTo) {
      this.showMessage(this.translationService.translate('START_DATUM_MUSS_VOR_END_DATUM_LIEGEN'), 'error');
      return;
    }

    this.loading = true;
    this.charts.forEach(chartData => { if (chartData.chart) chartData.chart.destroy(); });
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
          this.charts.push({ einheitId: einheit.id!, einheitName: einheit.name || '', einheitTyp: einheit.typ || '', chart: null });
        });

        this.cdr.detectChanges();
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

  private createChartsSequentially(results: MesswertData[][]): void {
    let index = 0;
    const createNext = () => {
      if (index >= this.charts.length) return;
      this.createChart(this.charts[index], results[index]);
      index++;
      if (index < this.charts.length) setTimeout(createNext, 50);
    };
    setTimeout(createNext, 100);
  }

  private createChart(chartData: ChartData, data: MesswertData[]): void {
    const canvas = document.getElementById(`chart-${chartData.einheitId}`) as HTMLCanvasElement;
    if (!canvas) return;

    const parent = canvas.parentElement;
    if (parent) {
      canvas.width = parent.clientWidth || 800;
      canvas.height = parent.clientHeight || 400;
    }

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const labels = data.map(d => new Date(d.zeit).toLocaleString('de-DE'));
    const totalValues = data.map(d => d.total ?? 0);
    const zevValues = data.map(d => -(d.zevCalculated ?? 0));
    const totalSum = data.reduce((sum, d) => sum + (d.total ?? 0), 0);
    const zevSum = data.reduce((sum, d) => sum + (d.zevCalculated ?? 0), 0);

    const config: ChartConfiguration = {
      type: 'line',
      data: {
        labels,
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
        // WICHTIG! Funktioniert mit true nicht.
        responsive: false,
        maintainAspectRatio: false,
        scales: {
          y: { title: { display: true, text: 'kWh' } },
          x: { title: { display: true, text: this.translationService.translate('ZEIT') } }
        }
      }
    };

    chartData.chart = new Chart(ctx, config);
  }

  private showMessage(message: string, type: 'success' | 'error'): void {
    this.message = message;
    this.messageType = type;
    setTimeout(() => { this.message = ''; this.messageType = ''; }, 5000);
  }
}
