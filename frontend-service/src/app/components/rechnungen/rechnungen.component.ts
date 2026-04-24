import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { WithMessage } from '../../utils/with-message';
import { RechnungService, GeneratedRechnung } from '../../services/rechnung.service';
import { Einheit } from '../../models/einheit.model';
import { TranslatePipe } from '../../pipes/translate.pipe';
import { TranslationService } from '../../services/translation.service';
import { QuarterSelectorComponent } from '../quarter-selector/quarter-selector.component';
import { IconComponent } from '../icon/icon.component';
import { EinheitSelectorComponent } from '../einheit-selector/einheit-selector.component';

@Component({
  selector: 'app-rechnungen',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe, QuarterSelectorComponent, IconComponent, EinheitSelectorComponent],
  templateUrl: './rechnungen.component.html',
  styleUrls: ['./rechnungen.component.css']
})
export class RechnungenComponent extends WithMessage implements OnInit {
  dateFrom: string = '';
  dateTo: string = '';
  selectedEinheitIds: Set<number> = new Set();
  generating = false;

  generatedRechnungen: GeneratedRechnung[] = [];

  constructor(
    private rechnungService: RechnungService,
    private translationService: TranslationService
  ) { super(); }

  ngOnInit(): void {
    this.setDefaultDates();
  }

  onSelectionChange(einheiten: Einheit[]): void {
    this.selectedEinheitIds = new Set(einheiten.map(e => e.id!));
  }

  private setDefaultDates(): void {
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

}
