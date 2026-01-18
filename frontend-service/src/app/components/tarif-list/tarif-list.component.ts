import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TarifService } from '../../services/tarif.service';
import { Tarif, TarifTyp, ValidationResult } from '../../models/tarif.model';
import { TarifFormComponent } from '../tarif-form/tarif-form.component';
import { TranslatePipe } from '../../pipes/translate.pipe';
import { SwissDatePipe } from '../../pipes/swiss-date.pipe';
import { TranslationService } from '../../services/translation.service';
import { KebabMenuComponent, KebabMenuItem } from '../kebab-menu/kebab-menu.component';
import { ColumnResizeDirective } from '../../directives/column-resize.directive';
import { IconComponent } from '../icon/icon.component';

@Component({
  selector: 'app-tarif-list',
  standalone: true,
  imports: [CommonModule, TarifFormComponent, TranslatePipe, SwissDatePipe, KebabMenuComponent, ColumnResizeDirective, IconComponent],
  templateUrl: './tarif-list.component.html',
  styleUrls: ['./tarif-list.component.css']
})
export class TarifListComponent implements OnInit {
  tarife: Tarif[] = [];
  selectedTarif: Tarif | null = null;
  showForm = false;
  message = '';
  messageType: 'success' | 'error' = 'success';
  messagePersistent = false;
  validationErrors: string[] = [];
  sortColumn: 'bezeichnung' | 'tariftyp' | 'preis' | 'gueltigVon' | 'gueltigBis' | null = 'tariftyp';
  sortDirection: 'asc' | 'desc' = 'asc';

  menuItems: KebabMenuItem[] = [
    { label: 'BEARBEITEN', action: 'edit', icon: 'edit-2' },
    { label: 'LOESCHEN', action: 'delete', danger: true, icon: 'trash-2' }
  ];

  constructor(
    private tarifService: TarifService,
    private translationService: TranslationService
  ) { }

  ngOnInit(): void {
    this.loadTarife();
  }

  loadTarife(): void {
    this.tarifService.getAllTarife().subscribe({
      next: (data) => {
        this.tarife = data;
      },
      error: (error) => {
        this.showMessage('FEHLER_LADEN_TARIFE', 'error');
      }
    });
  }

  onCreateNew(): void {
    this.selectedTarif = null;
    this.showForm = true;
  }

  onEdit(tarif: Tarif): void {
    this.selectedTarif = { ...tarif };
    this.showForm = true;
  }

  onDelete(id: number | undefined): void {
    if (!id) return;

    if (confirm(this.translationService.translate('CONFIRM_DELETE_TARIF'))) {
      this.tarifService.deleteTarif(id).subscribe({
        next: () => {
          this.showMessage('TARIF_GELOESCHT', 'success');
          this.loadTarife();
        },
        error: (error) => {
          this.showMessage('FEHLER_LOESCHEN_TARIF', 'error');
        }
      });
    }
  }

  onMenuAction(action: string, tarif: Tarif): void {
    switch (action) {
      case 'edit':
        this.onEdit(tarif);
        break;
      case 'delete':
        this.onDelete(tarif.id);
        break;
    }
  }

  onFormSubmit(tarif: Tarif): void {
    if (tarif.id) {
      this.tarifService.updateTarif(tarif.id, tarif).subscribe({
        next: () => {
          this.showMessage('TARIF_AKTUALISIERT', 'success');
          this.showForm = false;
          this.loadTarife();
        },
        error: (error) => {
          const errorMsg = error.error || 'FEHLER_AKTUALISIEREN_TARIF';
          this.showMessage(errorMsg, 'error');
        }
      });
    } else {
      this.tarifService.createTarif(tarif).subscribe({
        next: () => {
          this.showMessage('TARIF_ERSTELLT', 'success');
          this.showForm = false;
          this.loadTarife();
        },
        error: (error) => {
          const errorMsg = error.error || 'FEHLER_ERSTELLEN_TARIF';
          this.showMessage(errorMsg, 'error');
        }
      });
    }
  }

  onFormCancel(): void {
    this.showForm = false;
    this.selectedTarif = null;
  }

  onSort(column: 'bezeichnung' | 'tariftyp' | 'preis' | 'gueltigVon' | 'gueltigBis'): void {
    if (this.sortColumn === column) {
      this.sortDirection = this.sortDirection === 'asc' ? 'desc' : 'asc';
    } else {
      this.sortColumn = column;
      this.sortDirection = 'asc';
    }

    this.tarife.sort((a, b) => {
      let aValue: any = a[column];
      let bValue: any = b[column];

      if (aValue === null || aValue === undefined) return 1;
      if (bValue === null || bValue === undefined) return -1;

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

  formatPreis(preis: number): string {
    return preis.toFixed(5);
  }

  getTarifTypLabel(typ: TarifTyp): string {
    return typ === TarifTyp.ZEV ? 'ZEV (Solarstrom)' : 'VNB (Netzstrom)';
  }

  onValidateQuartale(): void {
    this.tarifService.validateQuartale().subscribe({
      next: (result) => this.handleValidationResult(result),
      error: () => this.showMessage('FEHLER_VALIDIERUNG', 'error')
    });
  }

  onValidateJahre(): void {
    this.tarifService.validateJahre().subscribe({
      next: (result) => this.handleValidationResult(result),
      error: () => this.showMessage('FEHLER_VALIDIERUNG', 'error')
    });
  }

  private handleValidationResult(result: ValidationResult): void {
    if (result.valid) {
      this.showMessage('VALIDIERUNG_ERFOLGREICH', 'success');
      this.validationErrors = [];
    } else {
      this.showMessage('VALIDIERUNG_FEHLER', 'error', true);
      this.validationErrors = result.errors;
    }
  }

  dismissMessage(): void {
    this.message = '';
    this.validationErrors = [];
    this.messagePersistent = false;
  }

  private showMessage(message: string, type: 'success' | 'error', persistent: boolean = false): void {
    this.message = message;
    this.messageType = type;
    this.messagePersistent = persistent;
    if (!persistent) {
      this.validationErrors = [];
      setTimeout(() => {
        this.message = '';
      }, 5000);
    }
  }
}
