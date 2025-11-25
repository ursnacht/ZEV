import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MesswerteService, CalculationResponse } from '../../services/messwerte.service';

@Component({
  selector: 'app-solar-calculation',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './solar-calculation.component.html',
  styleUrls: ['./solar-calculation.component.css']
})
export class SolarCalculationComponent {
  dateFrom: string = '';
  dateTo: string = '';
  calculating = false;
  message = '';
  messageType: 'success' | 'error' | '' = '';
  result: CalculationResponse | null = null;

  constructor(private messwerteService: MesswerteService) {}

  onSubmit(): void {
    if (!this.dateFrom || !this.dateTo) {
      this.showMessage('Bitte beide Daten ausfüllen', 'error');
      return;
    }

    if (this.dateFrom > this.dateTo) {
      this.showMessage('Start-Datum muss vor End-Datum liegen', 'error');
      return;
    }

    this.calculating = true;
    this.result = null;

    this.messwerteService.calculateDistribution(this.dateFrom, this.dateTo).subscribe({
      next: (response) => {
        if (response.status === 'success') {
          this.result = response;
          this.showMessage('Berechnung erfolgreich abgeschlossen!', 'success');
        } else {
          this.showMessage(`Fehler: ${response.message}`, 'error');
        }
        this.calculating = false;
      },
      error: (error) => {
        this.showMessage(`Fehler: ${error.message}`, 'error');
        this.calculating = false;
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
