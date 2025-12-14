import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TarifService } from '../../services/tarif.service';
import { Tarif, TarifTyp } from '../../models/tarif.model';
import { TarifFormComponent } from '../tarif-form/tarif-form.component';
import { TranslatePipe } from '../../pipes/translate.pipe';
import { TranslationService } from '../../services/translation.service';

@Component({
  selector: 'app-tarif-list',
  standalone: true,
  imports: [CommonModule, TarifFormComponent, TranslatePipe],
  templateUrl: './tarif-list.component.html',
  styleUrls: ['./tarif-list.component.css']
})
export class TarifListComponent implements OnInit {
  tarife: Tarif[] = [];
  selectedTarif: Tarif | null = null;
  showForm = false;
  message = '';
  messageType: 'success' | 'error' = 'success';
  sortColumn: 'bezeichnung' | 'tariftyp' | 'preis' | 'gueltigVon' | 'gueltigBis' | null = 'tariftyp';
  sortDirection: 'asc' | 'desc' = 'asc';

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

  formatDate(dateStr: string): string {
    if (!dateStr) return '-';
    const date = new Date(dateStr);
    return date.toLocaleDateString('de-CH');
  }

  formatPreis(preis: number): string {
    return preis.toFixed(5);
  }

  getTarifTypLabel(typ: TarifTyp): string {
    return typ === TarifTyp.ZEV ? 'ZEV (Solarstrom)' : 'VNB (Netzstrom)';
  }

  private showMessage(message: string, type: 'success' | 'error'): void {
    this.message = message;
    this.messageType = type;
    setTimeout(() => {
      this.message = '';
    }, 5000);
  }
}
