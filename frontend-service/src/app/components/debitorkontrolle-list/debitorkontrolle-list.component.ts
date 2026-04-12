import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DebitorService } from '../../services/debitor.service';
import { EinheitService } from '../../services/einheit.service';
import { MieterService } from '../../services/mieter.service';
import { Debitor } from '../../models/debitor.model';
import { Einheit, EinheitTyp } from '../../models/einheit.model';
import { Mieter } from '../../models/mieter.model';
import { DebitorkontrolleFormComponent } from '../debitorkontrolle-form/debitorkontrolle-form.component';
import { QuarterSelectorComponent } from '../quarter-selector/quarter-selector.component';
import { TranslatePipe } from '../../pipes/translate.pipe';
import { SwissDatePipe } from '../../pipes/swiss-date.pipe';
import { TranslationService } from '../../services/translation.service';
import { KebabMenuComponent, KebabMenuItem } from '../kebab-menu/kebab-menu.component';
import { ColumnResizeDirective } from '../../directives/column-resize.directive';
import { IconComponent } from '../icon/icon.component';

@Component({
  selector: 'app-debitorkontrolle-list',
  standalone: true,
  imports: [
    CommonModule, FormsModule, DebitorkontrolleFormComponent,
    QuarterSelectorComponent, TranslatePipe, SwissDatePipe,
    KebabMenuComponent, ColumnResizeDirective, IconComponent
  ],
  templateUrl: './debitorkontrolle-list.component.html',
  styleUrls: ['./debitorkontrolle-list.component.css']
})
export class DebitorkontrolleListComponent implements OnInit {
  debitoren: Debitor[] = [];
  einheiten: Einheit[] = [];
  mieter: Mieter[] = [];
  selectedDebitor: Debitor | null = null;
  showForm = false;
  dateFrom: string = '';
  dateTo: string = '';
  message = '';
  messageType: 'success' | 'error' = 'success';
  sortColumn: 'mieterName' | 'betrag' | 'datumVon' | 'datumBis' | 'zahldatum' | 'status' | null = 'mieterName';
  sortDirection: 'asc' | 'desc' = 'asc';

  menuItems: KebabMenuItem[] = [
    { label: 'BEARBEITEN', action: 'edit', icon: 'edit-2' },
    { label: 'LOESCHEN', action: 'delete', danger: true, icon: 'trash-2' }
  ];

  constructor(
    private debitorService: DebitorService,
    private einheitService: EinheitService,
    private mieterService: MieterService,
    private translationService: TranslationService
  ) {}

  ngOnInit(): void {
    this.setDefaultQuarter();
    this.loadEinheiten();
    this.loadMieter();
    this.loadDebitoren();
  }

  private setDefaultQuarter(): void {
    const now = new Date();
    const quarter = Math.floor(now.getMonth() / 3);
    const year = now.getFullYear();
    const quarterStart = new Date(year, quarter * 3, 1);
    const quarterEnd = new Date(year, quarter * 3 + 3, 0);
    this.dateFrom = this.formatDate(quarterStart);
    this.dateTo = this.formatDate(quarterEnd);
  }

  private formatDate(date: Date): string {
    return date.toISOString().split('T')[0];
  }

  loadEinheiten(): void {
    this.einheitService.getAllEinheiten().subscribe({
      next: (data) => {
        this.einheiten = data.filter(e => e.typ === EinheitTyp.CONSUMER);
      },
      error: () => {
        this.showMessage('FEHLER_LADEN_EINHEITEN', 'error');
      }
    });
  }

  loadMieter(): void {
    this.mieterService.getAllMieter().subscribe({
      next: (data) => { this.mieter = data; },
      error: () => {
        this.showMessage('FEHLER_LADEN_MIETER', 'error');
      }
    });
  }

  loadDebitoren(): void {
    if (!this.dateFrom || !this.dateTo) return;
    this.debitorService.getDebitoren(this.dateFrom, this.dateTo).subscribe({
      next: (data) => { this.debitoren = data; this.applySorting(); },
      error: () => {
        this.showMessage('FEHLER_LADEN_DEBITOREN', 'error');
      }
    });
  }

  onQuarterSelected(event: { von: string; bis: string }): void {
    this.dateFrom = event.von;
    this.dateTo = event.bis;
    this.loadDebitoren();
  }

  onDateChange(): void {
    this.loadDebitoren();
  }

  onCreateNew(): void {
    this.selectedDebitor = null;
    this.showForm = true;
  }

  onEdit(debitor: Debitor): void {
    this.selectedDebitor = { ...debitor };
    this.showForm = true;
  }

  onDelete(id: number | undefined): void {
    if (!id) return;
    if (confirm(this.translationService.translate('DEBITOR_LOESCHEN_BESTAETIGUNG'))) {
      this.debitorService.deleteDebitor(id).subscribe({
        next: () => {
          this.showMessage('DEBITOR_GELOESCHT', 'success');
          this.loadDebitoren();
        },
        error: () => {
          this.showMessage('FEHLER_LOESCHEN_DEBITOR', 'error');
        }
      });
    }
  }

  onMenuAction(action: string, debitor: Debitor): void {
    switch (action) {
      case 'edit': this.onEdit(debitor); break;
      case 'delete': this.onDelete(debitor.id); break;
    }
  }

  onFormSubmit(debitor: Debitor): void {
    if (debitor.id) {
      this.debitorService.updateDebitor(debitor.id, debitor).subscribe({
        next: () => {
          this.showMessage('DEBITOR_AKTUALISIERT', 'success');
          this.showForm = false;
          this.loadDebitoren();
        },
        error: (error) => {
          this.showMessage(error.error || 'FEHLER_AKTUALISIEREN_DEBITOR', 'error');
        }
      });
    } else {
      this.debitorService.createDebitor(debitor).subscribe({
        next: () => {
          this.showMessage('DEBITOR_ERSTELLT', 'success');
          this.showForm = false;
          this.loadDebitoren();
        },
        error: (error) => {
          this.showMessage(error.error || 'FEHLER_ERSTELLEN_DEBITOR', 'error');
        }
      });
    }
  }

  onFormCancel(): void {
    this.showForm = false;
    this.selectedDebitor = null;
  }

  onSort(column: 'mieterName' | 'betrag' | 'datumVon' | 'datumBis' | 'zahldatum' | 'status'): void {
    if (this.sortColumn === column) {
      this.sortDirection = this.sortDirection === 'asc' ? 'desc' : 'asc';
    } else {
      this.sortColumn = column;
      this.sortDirection = 'asc';
    }
    this.applySorting();
  }

  private applySorting(): void {
    const column = this.sortColumn;
    if (!column) return;

    this.debitoren.sort((a, b) => {
      let aValue: any;
      let bValue: any;

      if (column === 'status') {
        aValue = this.isOffen(a) ? 1 : 0;
        bValue = this.isOffen(b) ? 1 : 0;
      } else {
        aValue = (a as any)[column];
        bValue = (b as any)[column];
      }

      if (aValue === null || aValue === undefined || aValue === '') return 1;
      if (bValue === null || bValue === undefined || bValue === '') return -1;

      if (typeof aValue === 'string') {
        aValue = aValue.toLowerCase();
        bValue = bValue.toLowerCase();
      }

      if (aValue < bValue) return this.sortDirection === 'asc' ? -1 : 1;
      if (aValue > bValue) return this.sortDirection === 'asc' ? 1 : -1;
      return 0;
    });
  }

  isOffen(debitor: Debitor): boolean {
    return !debitor.zahldatum;
  }

  getEinheitName(mieterId: number): string {
    const m = this.mieter.find(x => x.id === mieterId);
    if (!m) return '';
    const e = this.einheiten.find(x => x.id === m.einheitId);
    return e ? e.name : '';
  }

  showMessage(message: string, type: 'success' | 'error'): void {
    this.message = message;
    this.messageType = type;
    if (type === 'success') {
      setTimeout(() => { this.message = ''; }, 5000);
    }
  }

  dismissMessage(): void {
    this.message = '';
  }
}
