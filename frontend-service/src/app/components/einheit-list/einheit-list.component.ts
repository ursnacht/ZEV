import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { EinheitService } from '../../services/einheit.service';
import { Einheit } from '../../models/einheit.model';
import { EinheitFormComponent } from '../einheit-form/einheit-form.component';
import { TranslatePipe } from '../../pipes/translate.pipe';
import { TranslationService } from '../../services/translation.service';

@Component({
  selector: 'app-einheit-list',
  standalone: true,
  imports: [CommonModule, EinheitFormComponent, TranslatePipe],
  templateUrl: './einheit-list.component.html',
  styleUrls: ['./einheit-list.component.css']
})
export class EinheitListComponent implements OnInit {
  einheiten: Einheit[] = [];
  selectedEinheit: Einheit | null = null;
  showForm = false;
  message = '';
  messageType: 'success' | 'error' = 'success';
  sortColumn: 'id' | 'name' | 'typ' | null = 'name';
  sortDirection: 'asc' | 'desc' = 'asc';

  constructor(
    private einheitService: EinheitService,
    private translationService: TranslationService
  ) { }

  ngOnInit(): void {
    this.loadEinheiten();
  }

  loadEinheiten(): void {
    this.einheitService.getAllEinheiten().subscribe({
      next: (data) => {
        this.einheiten = data;
      },
      error: (error) => {
        this.showMessage('Fehler beim Laden der Einheiten: ' + error.message, 'error');
      }
    });
  }

  onCreateNew(): void {
    this.selectedEinheit = null;
    this.showForm = true;
  }

  onEdit(einheit: Einheit): void {
    this.selectedEinheit = { ...einheit };
    this.showForm = true;
  }

  onDelete(id: number | undefined): void {
    if (!id) return;

    if (confirm(this.translationService.translate('CONFIRM_DELETE_EINHEIT'))) {
      this.einheitService.deleteEinheit(id).subscribe({
        next: () => {
          this.showMessage(this.translationService.translate('EINHEIT_GELOESCHT'), 'success');
          this.loadEinheiten();
        },
        error: (error) => {
          this.showMessage('Fehler beim Löschen: ' + error.message, 'error');
        }
      });
    }
  }

  onFormSubmit(einheit: Einheit): void {
    if (einheit.id) {
      this.einheitService.updateEinheit(einheit.id, einheit).subscribe({
        next: () => {
          this.showMessage(this.translationService.translate('EINHEIT_AKTUALISIERT'), 'success');
          this.showForm = false;
          this.loadEinheiten();
        },
        error: (error) => {
          this.showMessage('Fehler beim Aktualisieren: ' + error.message, 'error');
        }
      });
    } else {
      this.einheitService.createEinheit(einheit).subscribe({
        next: () => {
          this.showMessage(this.translationService.translate('EINHEIT_ERSTELLT'), 'success');
          this.showForm = false;
          this.loadEinheiten();
        },
        error: (error) => {
          this.showMessage('Fehler beim Erstellen: ' + error.message, 'error');
        }
      });
    }
  }

  onFormCancel(): void {
    this.showForm = false;
    this.selectedEinheit = null;
  }

  onSort(column: 'id' | 'name' | 'typ'): void {
    if (this.sortColumn === column) {
      this.sortDirection = this.sortDirection === 'asc' ? 'desc' : 'asc';
    } else {
      this.sortColumn = column;
      this.sortDirection = 'asc';
    }

    this.einheiten.sort((a, b) => {
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

  private showMessage(message: string, type: 'success' | 'error'): void {
    this.message = message;
    this.messageType = type;
    setTimeout(() => {
      this.message = '';
    }, 5000);
  }
}
