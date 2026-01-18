import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { StatistikService } from '../../services/statistik.service';
import { Statistik, MonatsStatistik, TagMitAbweichung } from '../../models/statistik.model';
import { TranslatePipe } from '../../pipes/translate.pipe';
import { SwissDatePipe } from '../../pipes/swiss-date.pipe';
import { TranslationService } from '../../services/translation.service';
import { QuarterSelectorComponent } from '../quarter-selector/quarter-selector.component';
import { IconComponent } from '../icon/icon.component';

@Component({
  selector: 'app-statistik',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe, SwissDatePipe, QuarterSelectorComponent, IconComponent],
  templateUrl: './statistik.component.html',
  styleUrls: ['./statistik.component.css']
})
export class StatistikComponent implements OnInit {
  dateFrom: string = '';
  dateTo: string = '';
  loading = false;
  message = '';
  messageType: 'success' | 'error' | '' = '';

  statistik: Statistik | null = null;
  expandedMonths: Set<number> = new Set();
  expandedGlobalDetails = false;

  constructor(
    private statistikService: StatistikService,
    private translationService: TranslationService
  ) { }

  ngOnInit(): void {
    this.setDefaultDateRange();
  }

  private setDefaultDateRange(): void {
    const now = new Date();
    const previousMonth = new Date(now.getFullYear(), now.getMonth() - 1, 1);
    const lastDayPrevMonth = new Date(now.getFullYear(), now.getMonth(), 0);

    this.dateFrom = this.formatDate(previousMonth);
    this.dateTo = this.formatDate(lastDayPrevMonth);
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
      const lastDay = new Date(date.getFullYear(), date.getMonth() + 1, 0);
      this.dateTo = this.formatDate(lastDay);
    }
  }

  onQuarterSelected(event: {von: string, bis: string}): void {
    this.dateFrom = event.von;
    this.dateTo = event.bis;
  }

  onSubmit(): void {
    if (!this.dateFrom || !this.dateTo) {
      this.showMessage(this.translationService.translate('BITTE_ALLE_FELDER_AUSFUELLEN'), 'error');
      return;
    }

    if (this.dateFrom > this.dateTo) {
      this.showMessage(this.translationService.translate('START_DATUM_MUSS_VOR_END_DATUM_LIEGEN'), 'error');
      return;
    }

    this.loading = true;
    this.statistik = null;
    this.expandedMonths.clear();

    this.statistikService.getStatistik(this.dateFrom, this.dateTo).subscribe({
      next: (data) => {
        this.statistik = data;
        this.loading = false;
        this.showMessage(
          `${data.monate.length} ${this.translationService.translate('MONATE_GELADEN')}`,
          'success'
        );
      },
      error: (error) => {
        this.showMessage(
          `${this.translationService.translate('FEHLER_BEIM_LADEN_DER_DATEN')}: ${error.message}`,
          'error'
        );
        this.loading = false;
      }
    });
  }

  exportPdf(): void {
    if (!this.dateFrom || !this.dateTo) {
      this.showMessage(this.translationService.translate('BITTE_ZUERST_STATISTIK_LADEN'), 'error');
      return;
    }

    const sprache = this.translationService.getCurrentLanguage();
    this.statistikService.exportPdf(this.dateFrom, this.dateTo, sprache).subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `statistik_${this.dateFrom}_${this.dateTo}.pdf`;
        link.click();
        window.URL.revokeObjectURL(url);
      },
      error: (error) => {
        this.showMessage(
          `${this.translationService.translate('FEHLER_BEIM_EXPORT')}: ${error.message}`,
          'error'
        );
      }
    });
  }

  toggleMonthDetails(index: number): void {
    if (this.expandedMonths.has(index)) {
      this.expandedMonths.delete(index);
    } else {
      this.expandedMonths.add(index);
    }
  }

  isMonthExpanded(index: number): boolean {
    return this.expandedMonths.has(index);
  }

  toggleGlobalDetails(): void {
    this.expandedGlobalDetails = !this.expandedGlobalDetails;
  }

  getMonthName(monat: number): string {
    const monthNames = [
      'JANUAR', 'FEBRUAR', 'MAERZ', 'APRIL', 'MAI', 'JUNI',
      'JULI', 'AUGUST', 'SEPTEMBER', 'OKTOBER', 'NOVEMBER', 'DEZEMBER'
    ];
    return this.translationService.translate(monthNames[monat - 1]);
  }

  getStatusClass(vollstaendig: boolean): string {
    return vollstaendig ? 'zev-status-dot--success' : 'zev-status-dot--error';
  }

  getComparisonStatusClass(isEqual: boolean): string {
    return isEqual ? 'zev-status-dot--success' : 'zev-status-dot--error';
  }

  formatNumber(value: number | null | undefined): string {
    if (value === null || value === undefined) {
      return '-';
    }
    return value.toFixed(3);
  }

  formatDifferenz(value: number | null | undefined): string {
    if (value === null || value === undefined) {
      return '-';
    }
    const prefix = value >= 0 ? '+' : '';
    return `${prefix}${value.toFixed(3)}`;
  }

  hasAbweichungen(monat: MonatsStatistik): boolean {
    return !monat.summenCDGleich || !monat.summenCEGleich || !monat.summenDEGleich;
  }

  getBarWidth(value: number, monat: MonatsStatistik): number {
    const maxValue = Math.max(
      monat.summeProducerTotal || 0,
      monat.summeConsumerTotal || 0,
      monat.summeProducerZev || 0,
      monat.summeConsumerZev || 0,
      monat.summeConsumerZevCalculated || 0
    );
    if (maxValue === 0) return 0;
    return (value / maxValue) * 100;
  }

  getBarColor(type: 'A' | 'B' | 'C' | 'D' | 'E'): string {
    const colors: Record<string, string> = {
      'A': '#4CAF50',  // Producer Total - Grün
      'B': '#2196F3',  // Consumer Total - Blau
      'C': '#FF9800',  // Producer ZEV - Orange
      'D': '#9C27B0',  // Consumer ZEV - Lila
      'E': '#00BCD4'   // Consumer ZEV Calculated - Cyan
    };
    return colors[type] || '#999';
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
