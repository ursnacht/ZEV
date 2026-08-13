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
import { TarifLuecke } from '../../models/tarif.model';
import { formatTarifLuecke } from '../../utils/tarif-luecke.util';

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
          this.translationService.translate('FEHLER_BEIM_GENERIEREN') + ': ' + this.buildErrorDetail(error),
          'error'
        );
        this.generating = false;
      }
    });
  }

  /**
   * Builds the error detail shown after the "FEHLER_BEIM_GENERIEREN" prefix.
   * For a tariff coverage gap (structured `luecken`), the message is assembled from
   * translation keys (same wording as the tariff management page). Otherwise the
   * backend message (or HTTP error) is used as a fallback.
   */
  private buildErrorDetail(error: { error?: { error?: string; luecken?: TarifLuecke[] }; message?: string }): string {
    const body = error?.error;
    if (body?.luecken && body.error) {
      const prefix = this.translationService.translate(body.error);
      const details = body.luecken
        .map(luecke => formatTarifLuecke(luecke, this.translationService))
        .join('; ');
      return `${prefix}: ${details}`;
    }
    return body?.error || error?.message || '';
  }

  onDownload(rechnung: GeneratedRechnung): void {
    this.rechnungService.downloadRechnung(rechnung.downloadKey, rechnung.filename);
  }

  formatBetrag(betrag: number): string {
    return betrag.toFixed(2);
  }

  getTotalBetrag(): number {
    return this.generatedRechnungen.reduce((sum, rechnung) => sum + rechnung.endBetrag, 0);
  }

}
