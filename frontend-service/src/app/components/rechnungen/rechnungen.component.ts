import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { EinheitService } from '../../services/einheit.service';
import { RechnungService, GeneratedRechnung } from '../../services/rechnung.service';
import { Einheit, EinheitTyp } from '../../models/einheit.model';
import { TranslatePipe } from '../../pipes/translate.pipe';
import { TranslationService } from '../../services/translation.service';
import { QuarterSelectorComponent } from '../quarter-selector/quarter-selector.component';
import { IconComponent } from '../icon/icon.component';

@Component({
  selector: 'app-rechnungen',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe, QuarterSelectorComponent, IconComponent],
  templateUrl: './rechnungen.component.html',
  styleUrls: ['./rechnungen.component.css']
})
export class RechnungenComponent implements OnInit {
  dateFrom: string = '';
  dateTo: string = '';
  selectedEinheitIds: Set<number> = new Set();
  consumers: Einheit[] = [];
  loading = false;
  generating = false;
  message = '';
  messageType: 'success' | 'error' | '' = '';
  generatedRechnungen: GeneratedRechnung[] = [];

  constructor(
    private einheitService: EinheitService,
    private rechnungService: RechnungService,
    private translationService: TranslationService
  ) {}

  ngOnInit(): void {
    this.loadConsumers();
    this.setDefaultDates();
  }

  private setDefaultDates(): void {
    // Default to previous month
    const now = new Date();
    const firstDayPrevMonth = new Date(now.getFullYear(), now.getMonth() - 1, 1);
    const lastDayPrevMonth = new Date(now.getFullYear(), now.getMonth(), 0);

    this.dateFrom = this.formatDate(firstDayPrevMonth);
    this.dateTo = this.formatDate(lastDayPrevMonth);
  }

  private formatDate(date: Date): string {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  loadConsumers(): void {
    this.loading = true;
    this.einheitService.getAllEinheiten().subscribe({
      next: (data) => {
        // Filter only consumers and sort by name
        this.consumers = data
          .filter(e => e.typ === EinheitTyp.CONSUMER)
          .sort((a, b) => (a.name || '').localeCompare(b.name || ''));
        this.loading = false;
      },
      error: (error) => {
        this.showMessage(
          this.translationService.translate('FEHLER_BEIM_LADEN_DER_EINHEITEN') + ': ' + error.message,
          'error'
        );
        this.loading = false;
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
    return this.consumers.length > 0 && this.selectedEinheitIds.size === this.consumers.length;
  }

  someSelected(): boolean {
    return this.selectedEinheitIds.size > 0 && this.selectedEinheitIds.size < this.consumers.length;
  }

  onSelectAllToggle(): void {
    if (this.allSelected()) {
      this.selectedEinheitIds.clear();
    } else {
      this.consumers.forEach(e => {
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
      this.dateTo = this.formatDate(lastDay);
    }
  }

  onQuarterSelected(event: {von: string, bis: string}): void {
    this.dateFrom = event.von;
    this.dateTo = event.bis;
  }

  canGenerate(): boolean {
    return !!this.dateFrom && !!this.dateTo && this.selectedEinheitIds.size > 0 && !this.generating;
  }

  onGenerate(): void {
    if (!this.canGenerate()) {
      this.showMessage(this.translationService.translate('BITTE_ALLE_FELDER_AUSFUELLEN'), 'error');
      return;
    }

    if (this.dateFrom > this.dateTo) {
      this.showMessage(this.translationService.translate('START_DATUM_MUSS_VOR_END_DATUM_LIEGEN'), 'error');
      return;
    }

    this.generating = true;
    this.generatedRechnungen = [];

    const request = {
      von: this.dateFrom,
      bis: this.dateTo,
      einheitIds: Array.from(this.selectedEinheitIds),
      sprache: this.translationService.getCurrentLanguage()
    };

    this.rechnungService.generateRechnungen(request).subscribe({
      next: (response) => {
        this.generatedRechnungen = response.rechnungen;
        this.showMessage(
          `${response.count} ${this.translationService.translate('RECHNUNGEN_GENERIERT')}`,
          'success'
        );
        this.generating = false;
      },
      error: (error) => {
        this.showMessage(
          this.translationService.translate('FEHLER_BEIM_GENERIEREN') + ': ' + (error.error?.error || error.message),
          'error'
        );
        this.generating = false;
      }
    });
  }

  onDownload(rechnung: GeneratedRechnung): void {
    this.rechnungService.downloadRechnung(rechnung.downloadKey, rechnung.filename);
  }

  formatBetrag(betrag: number): string {
    return betrag.toFixed(2);
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
