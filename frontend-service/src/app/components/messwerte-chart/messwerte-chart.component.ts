import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Chart, ChartConfiguration, registerables } from 'chart.js';
import { MesswerteService, MesswertData } from '../../services/messwerte.service';
import { EinheitService } from '../../services/einheit.service';
import { Einheit } from '../../models/einheit.model';
import { forkJoin } from 'rxjs';

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
  imports: [CommonModule, FormsModule],
  templateUrl: './messwerte-chart.component.html',
  styleUrls: ['./messwerte-chart.component.css']
})
export class MesswerteChartComponent implements OnInit {
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
    private einheitService: EinheitService
  ) {}

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
        this.showMessage('Fehler beim Laden der Einheiten: ' + error.message, 'error');
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

  onSubmit(): void {
    if (!this.dateFrom || !this.dateTo || this.selectedEinheitIds.size === 0) {
      this.showMessage('Bitte alle Felder ausfüllen und mindestens eine Einheit auswählen', 'error');
      return;
    }

    if (this.dateFrom > this.dateTo) {
      this.showMessage('Start-Datum muss vor End-Datum liegen', 'error');
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

        // Wait for DOM to update, then create charts
        setTimeout(() => {
          this.charts.forEach((chartData, index) => {
            const data = results[index];
            this.createChart(chartData, data);
          });
        }, 0);

        this.showMessage(`${totalDataPoints} Datenpunkte für ${this.charts.length} Einheit(en) geladen`, 'success');
        this.loading = false;
      },
      error: (error) => {
        this.showMessage(`Fehler beim Laden der Daten: ${error.message}`, 'error');
        this.loading = false;
      }
    });
  }

  private createChart(chartData: ChartData, data: MesswertData[]): void {
    const canvas = document.getElementById(`chart-${chartData.einheitId}`) as HTMLCanvasElement;
    if (!canvas) return;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const labels = data.map(d => new Date(d.zeit).toLocaleString('de-DE'));
    const values = data.map(d => d.total);

    const config: ChartConfiguration = {
      type: 'line',
      data: {
        labels: labels,
        datasets: [{
          label: 'Total (kWh)',
          data: values,
          borderColor: '#4CAF50',
          backgroundColor: 'rgba(76, 175, 80, 0.1)',
          tension: 0.1
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        scales: {
          y: {
            beginAtZero: true,
            title: {
              display: true,
              text: 'kWh'
            }
          },
          x: {
            title: {
              display: true,
              text: 'Zeit'
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
