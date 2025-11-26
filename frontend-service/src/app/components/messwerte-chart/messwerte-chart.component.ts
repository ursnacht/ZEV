import { Component, OnInit, ViewChild, ElementRef, AfterViewInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Chart, ChartConfiguration, registerables } from 'chart.js';
import { MesswerteService, MesswertData } from '../../services/messwerte.service';
import { EinheitService } from '../../services/einheit.service';
import { Einheit } from '../../models/einheit.model';

Chart.register(...registerables);

@Component({
  selector: 'app-messwerte-chart',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './messwerte-chart.component.html',
  styleUrls: ['./messwerte-chart.component.css']
})
export class MesswerteChartComponent implements OnInit, AfterViewInit {
  @ViewChild('chartCanvas') chartCanvas!: ElementRef<HTMLCanvasElement>;

  dateFrom: string = '';
  dateTo: string = '';
  einheitId: number | null = null;
  einheiten: Einheit[] = [];
  loading = false;
  message = '';
  messageType: 'success' | 'error' | '' = '';
  chart: Chart | null = null;

  constructor(
    private messwerteService: MesswerteService,
    private einheitService: EinheitService
  ) {}

  ngOnInit(): void {
    this.loadEinheiten();
  }

  ngAfterViewInit(): void {
    this.initChart();
  }

  loadEinheiten(): void {
    this.einheitService.getAllEinheiten().subscribe({
      next: (data) => {
        this.einheiten = data.sort((a, b) => {
          const nameA = (a.name || '').toLowerCase();
          const nameB = (b.name || '').toLowerCase();
          return nameA.localeCompare(nameB);
        });
        if (this.einheiten.length > 0) {
          this.einheitId = this.einheiten[0].id || null;
        }
      },
      error: (error) => {
        this.showMessage('Fehler beim Laden der Einheiten: ' + error.message, 'error');
      }
    });
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

  initChart(): void {
    const ctx = this.chartCanvas.nativeElement.getContext('2d');
    if (ctx) {
      const config: ChartConfiguration = {
        type: 'line',
        data: {
          labels: [],
          datasets: [{
            label: 'Total (kWh)',
            data: [],
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
      this.chart = new Chart(ctx, config);
    }
  }

  onSubmit(): void {
    if (!this.dateFrom || !this.dateTo || !this.einheitId) {
      this.showMessage('Bitte alle Felder ausfüllen', 'error');
      return;
    }

    if (this.dateFrom > this.dateTo) {
      this.showMessage('Start-Datum muss vor End-Datum liegen', 'error');
      return;
    }

    this.loading = true;

    this.messwerteService.getMesswerteByEinheit(this.einheitId, this.dateFrom, this.dateTo).subscribe({
      next: (data: MesswertData[]) => {
        if (data.length === 0) {
          this.showMessage('Keine Daten für den ausgewählten Zeitraum gefunden', 'error');
          this.loading = false;
          return;
        }

        const labels = data.map(d => new Date(d.zeit).toLocaleString('de-DE'));
        const values = data.map(d => d.total);

        if (this.chart) {
          this.chart.data.labels = labels;
          this.chart.data.datasets[0].data = values;
          this.chart.update();
        }

        this.showMessage(`${data.length} Datenpunkte geladen`, 'success');
        this.loading = false;
      },
      error: (error) => {
        this.showMessage(`Fehler beim Laden der Daten: ${error.message}`, 'error');
        this.loading = false;
      }
    });
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
