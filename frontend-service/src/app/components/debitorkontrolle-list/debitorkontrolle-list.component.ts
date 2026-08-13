import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { WithMessage } from '../../utils/with-message';
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
export class DebitorkontrolleListComponent extends WithMessage implements OnInit {
  debitoren: Debitor[] = [];
  einheiten: Einheit[] = [];
  mieter: Mieter[] = [];
  selectedDebitor: Debitor | null = null;
  showForm = false;
  dateFrom: string = '';
  dateTo: string = '';

  selectedIds: Set<number> = new Set();

  sortColumn: 'mieterName' | 'betrag' | 'datumVon' | 'datumBis' | 'zahldatum' | 'status' | null = 'mieterName';
  sortDirection: 'asc' | 'desc' = 'asc';

  menuItemsOffen: KebabMenuItem[] = [
    { label: 'BEARBEITEN', action: 'edit', icon: 'edit-2' },
    { label: 'HEUTE', action: 'heute', icon: 'calendar' },
    { label: 'GESTERN', action: 'gestern', icon: 'calendar' },
    { label: 'LOESCHEN', action: 'delete', danger: true, icon: 'trash-2' }
  ];

  menuItemsBezahlt: KebabMenuItem[] = [
    { label: 'BEARBEITEN', action: 'edit', icon: 'edit-2' },
    { label: 'HEUTE', action: 'heute', icon: 'calendar' },
    { label: 'GESTERN', action: 'gestern', icon: 'calendar' },
    { label: 'ZAHLDATUM_LOESCHEN', action: 'zahldatumLoeschen', danger: true, icon: 'x' },
    { label: 'LOESCHEN', action: 'delete', danger: true, icon: 'trash-2' }
  ];

  getMenuItems(debitor: Debitor): KebabMenuItem[] {
    return debitor.zahldatum ? this.menuItemsBezahlt : this.menuItemsOffen;
  }

  constructor(
    private debitorService: DebitorService,
    private einheitService: EinheitService,
    private mieterService: MieterService,
    private translationService: TranslationService
  ) { super(); }

  ngOnInit(): void {
    this.setDefaultQuarter();
    this.loadEinheiten();
    this.loadMieter();
    this.loadDebitoren();
  }

  /**
   * Belegt den Zeitraum mit dem vorangehenden Quartal vor
   * (im Q1 wird Q4 des Vorjahres gesetzt).
   */
  private setDefaultQuarter(): void {
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
    this.selectedIds.clear();
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
      case 'heute': this.setZahldatum(debitor, 0); break;
      case 'gestern': this.setZahldatum(debitor, 1); break;
      case 'zahldatumLoeschen': this.setZahldatum(debitor, null); break;
    }
  }

  /**
   * Setzt das Zahldatum direkt (ohne Formular) und speichert sofort.
   * @param offsetDays Anzahl Tage in der Vergangenheit relativ zu heute
   *                   (0 = heute, 1 = gestern) oder null, um das Zahldatum zu löschen.
   */
  setZahldatum(debitor: Debitor, offsetDays: number | null): void {
    if (!debitor.id) return;

    let zahldatum: string | undefined;
    if (offsetDays === null) {
      zahldatum = undefined;
    } else {
      const date = new Date();
      date.setDate(date.getDate() - offsetDays);
      zahldatum = this.formatDate(date);
    }

    const updated: Debitor = { ...debitor, zahldatum };
    this.debitorService.updateDebitor(debitor.id, updated).subscribe({
      next: () => {
        this.showMessage('DEBITOR_AKTUALISIERT', 'success');
        this.loadDebitoren();
      },
      error: (error) => {
        this.showMessage(error.error || 'FEHLER_AKTUALISIEREN_DEBITOR', 'error');
      }
    });
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

  isSelected(id: number | undefined): boolean {
    return !!id && this.selectedIds.has(id);
  }

  allSelected(): boolean {
    return this.debitoren.length > 0 && this.selectedIds.size === this.debitoren.length;
  }

  someSelected(): boolean {
    return this.selectedIds.size > 0 && this.selectedIds.size < this.debitoren.length;
  }

  onToggleSelect(id: number | undefined): void {
    if (!id) return;
    if (this.selectedIds.has(id)) {
      this.selectedIds.delete(id);
    } else {
      this.selectedIds.add(id);
    }
  }

  onToggleSelectAll(): void {
    if (this.allSelected()) {
      this.selectedIds.clear();
    } else {
      this.debitoren.forEach(d => { if (d.id) this.selectedIds.add(d.id); });
    }
  }

  onDeleteSelected(): void {
    const ids = Array.from(this.selectedIds);
    if (ids.length === 0) return;
    const confirmation = `${this.translationService.translate('DEBITOREN_LOESCHEN_BESTAETIGUNG')} (${ids.length})`;
    if (!confirm(confirmation)) return;

    forkJoin(ids.map(id => this.debitorService.deleteDebitor(id))).subscribe({
      next: () => {
        this.showMessage('DEBITOREN_GELOESCHT', 'success');
        this.loadDebitoren();
      },
      error: () => {
        this.showMessage('FEHLER_SAMMEL_LOESCHEN_DEBITOR', 'error');
        this.loadDebitoren();
      }
    });
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

}
