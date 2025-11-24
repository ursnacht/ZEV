import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { EinheitService } from '../../services/einheit.service';
import { Einheit } from '../../models/einheit.model';
import { EinheitFormComponent } from '../einheit-form/einheit-form.component';

@Component({
  selector: 'app-einheit-list',
  standalone: true,
  imports: [CommonModule, EinheitFormComponent],
  templateUrl: './einheit-list.component.html',
  styleUrls: ['./einheit-list.component.css']
})
export class EinheitListComponent implements OnInit {
  einheiten: Einheit[] = [];
  selectedEinheit: Einheit | null = null;
  showForm = false;
  message = '';
  messageType: 'success' | 'error' = 'success';

  constructor(private einheitService: EinheitService) {}

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

    if (confirm('Möchten Sie diese Einheit wirklich löschen?')) {
      this.einheitService.deleteEinheit(id).subscribe({
        next: () => {
          this.showMessage('Einheit erfolgreich gelöscht', 'success');
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
          this.showMessage('Einheit erfolgreich aktualisiert', 'success');
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
          this.showMessage('Einheit erfolgreich erstellt', 'success');
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

  private showMessage(message: string, type: 'success' | 'error'): void {
    this.message = message;
    this.messageType = type;
    setTimeout(() => {
      this.message = '';
    }, 5000);
  }
}
