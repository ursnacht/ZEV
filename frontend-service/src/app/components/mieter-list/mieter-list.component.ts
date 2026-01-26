import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MieterService } from '../../services/mieter.service';
import { EinheitService } from '../../services/einheit.service';
import { Mieter } from '../../models/mieter.model';
import { Einheit, EinheitTyp } from '../../models/einheit.model';
import { MieterFormComponent } from '../mieter-form/mieter-form.component';
import { TranslatePipe } from '../../pipes/translate.pipe';
import { SwissDatePipe } from '../../pipes/swiss-date.pipe';
import { TranslationService } from '../../services/translation.service';
import { KebabMenuComponent, KebabMenuItem } from '../kebab-menu/kebab-menu.component';
import { ColumnResizeDirective } from '../../directives/column-resize.directive';
import { IconComponent } from '../icon/icon.component';

@Component({
  selector: 'app-mieter-list',
  standalone: true,
  imports: [CommonModule, MieterFormComponent, TranslatePipe, SwissDatePipe, KebabMenuComponent, ColumnResizeDirective, IconComponent],
  templateUrl: './mieter-list.component.html',
  styleUrls: ['./mieter-list.component.css']
})
export class MieterListComponent implements OnInit {
  mieter: Mieter[] = [];
  einheiten: Einheit[] = [];
  selectedMieter: Mieter | null = null;
  showForm = false;
  message = '';
  messageType: 'success' | 'error' = 'success';
  sortColumn: 'einheitId' | 'name' | 'strasse' | 'plzOrt' | 'mietbeginn' | 'mietende' | null = 'einheitId';
  sortDirection: 'asc' | 'desc' = 'asc';

  menuItems: KebabMenuItem[] = [
    { label: 'BEARBEITEN', action: 'edit', icon: 'edit-2' },
    { label: 'KOPIEREN', action: 'copy', icon: 'copy' },
    { label: 'LOESCHEN', action: 'delete', danger: true, icon: 'trash-2' }
  ];

  constructor(
    private mieterService: MieterService,
    private einheitService: EinheitService,
    private translationService: TranslationService
  ) { }

  ngOnInit(): void {
    this.loadEinheiten();
    this.loadMieter();
  }

  loadEinheiten(): void {
    this.einheitService.getAllEinheiten().subscribe({
      next: (data) => {
        // Only CONSUMER units can have tenants
        this.einheiten = data.filter(e => e.typ === EinheitTyp.CONSUMER);
        this.applySorting();
      },
      error: () => {
        this.showMessage('FEHLER_LADEN_EINHEITEN', 'error');
      }
    });
  }

  loadMieter(): void {
    this.mieterService.getAllMieter().subscribe({
      next: (data) => {
        this.mieter = data;
        this.applySorting();
      },
      error: () => {
        this.showMessage('FEHLER_LADEN_MIETER', 'error');
      }
    });
  }

  getEinheitName(einheitId: number): string {
    const einheit = this.einheiten.find(e => e.id === einheitId);
    return einheit ? einheit.name : `ID: ${einheitId}`;
  }

  onCreateNew(): void {
    this.selectedMieter = null;
    this.showForm = true;
  }

  onEdit(mieter: Mieter): void {
    this.selectedMieter = { ...mieter };
    this.showForm = true;
  }

  onCopy(mieter: Mieter): void {
    const { id, ...mieterOhneId } = mieter;
    this.selectedMieter = { ...mieterOhneId } as Mieter;
    this.showForm = true;
  }

  onDelete(id: number | undefined): void {
    if (!id) return;

    if (confirm(this.translationService.translate('MIETER_LOESCHEN_BESTAETIGUNG'))) {
      this.mieterService.deleteMieter(id).subscribe({
        next: () => {
          this.showMessage('MIETER_GELOESCHT', 'success');
          this.loadMieter();
        },
        error: () => {
          this.showMessage('FEHLER_LOESCHEN_MIETER', 'error');
        }
      });
    }
  }

  onMenuAction(action: string, mieter: Mieter): void {
    switch (action) {
      case 'edit':
        this.onEdit(mieter);
        break;
      case 'copy':
        this.onCopy(mieter);
        break;
      case 'delete':
        this.onDelete(mieter.id);
        break;
    }
  }

  onFormSubmit(mieter: Mieter): void {
    if (mieter.id) {
      this.mieterService.updateMieter(mieter.id, mieter).subscribe({
        next: () => {
          this.showMessage('MIETER_AKTUALISIERT', 'success');
          this.showForm = false;
          this.loadMieter();
        },
        error: (error) => {
          const errorMsg = error.error || 'FEHLER_AKTUALISIEREN_MIETER';
          this.showMessage(errorMsg, 'error');
        }
      });
    } else {
      this.mieterService.createMieter(mieter).subscribe({
        next: () => {
          this.showMessage('MIETER_ERSTELLT', 'success');
          this.showForm = false;
          this.loadMieter();
        },
        error: (error) => {
          const errorMsg = error.error || 'FEHLER_ERSTELLEN_MIETER';
          this.showMessage(errorMsg, 'error');
        }
      });
    }
  }

  onFormCancel(): void {
    this.showForm = false;
    this.selectedMieter = null;
  }

  onSort(column: 'einheitId' | 'name' | 'strasse' | 'plzOrt' | 'mietbeginn' | 'mietende'): void {
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

    this.mieter.sort((a, b) => {
      let aValue: any;
      let bValue: any;

      if (column === 'plzOrt') {
        aValue = `${a.plz || ''} ${a.ort || ''}`.trim();
        bValue = `${b.plz || ''} ${b.ort || ''}`.trim();
      } else if (column === 'einheitId') {
        aValue = this.getEinheitName(a.einheitId);
        bValue = this.getEinheitName(b.einheitId);
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

      if (aValue < bValue) {
        return this.sortDirection === 'asc' ? -1 : 1;
      }
      if (aValue > bValue) {
        return this.sortDirection === 'asc' ? 1 : -1;
      }
      return 0;
    });
  }

  formatPlzOrt(mieter: Mieter): string {
    const parts = [mieter.plz, mieter.ort].filter(p => p);
    return parts.join(' ');
  }

  private showMessage(message: string, type: 'success' | 'error'): void {
    this.message = message;
    this.messageType = type;
    if (type === 'success') {
      setTimeout(() => {
        this.message = '';
      }, 5000);
    }
  }

  dismissMessage(): void {
    this.message = '';
  }
}
