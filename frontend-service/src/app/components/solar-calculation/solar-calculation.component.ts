import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MesswerteService, CalculationResponse } from '../../services/messwerte.service';
import { TranslatePipe } from '../../pipes/translate.pipe';
import { TranslationService } from '../../services/translation.service';
import { QuarterSelectorComponent } from '../quarter-selector/quarter-selector.component';

@Component({
  selector: 'app-solar-calculation',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe, QuarterSelectorComponent],
  templateUrl: './solar-calculation.component.html',
  styleUrls: ['./solar-calculation.component.css']
})
export class SolarCalculationComponent {
  dateFrom: string = '';
  dateTo: string = '';
  algorithm: string = 'PROPORTIONAL';
  calculating = false;
  message = '';
  messageType: 'success' | 'error' | '' = '';
  result: CalculationResponse | null = null;

  constructor(
    private messwerteService: MesswerteService,
    private translationService: TranslationService
  ) { }

  onDateFromChange(): void {
    if (this.dateFrom) {
      const date = new Date(this.dateFrom);
      // Get last day of the month
      const lastDay = new Date(date.getFullYear(), date.getMonth() + 1, 0);
      // Format as YYYY-MM-DD using local date components
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
    if (!this.dateFrom || !this.dateTo) {
      this.showMessage(this.translationService.translate('BITTE_BEIDE_DATEN_AUSFUELLEN'), 'error');
      return;
    }

    if (this.dateFrom > this.dateTo) {
      this.showMessage(this.translationService.translate('START_VOR_END_DATUM'), 'error');
      return;
    }

    this.calculating = true;
    this.result = null;

    this.messwerteService.calculateDistribution(this.dateFrom, this.dateTo, this.algorithm).subscribe({
      next: (response) => {
        if (response.status === 'success') {
          this.result = response;
          this.showMessage(this.translationService.translate('BERECHNUNG_ERFOLGREICH'), 'success');
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
