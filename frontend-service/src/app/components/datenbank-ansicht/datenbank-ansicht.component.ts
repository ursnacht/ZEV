import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { WithMessage } from '../../utils/with-message';
import { DatenbankService } from '../../services/datenbank.service';
import { DatenbankAbfrageResponse } from '../../models/datenbank.model';
import { TranslatePipe } from '../../pipes/translate.pipe';
import { IconComponent } from '../icon/icon.component';

/**
 * Generische, read-only Datenbank-Ansicht (nur zev_admin, Permission {@code datenbank:read}).
 * Eingebettet in die Einstellungen-Seite. Auswahl einer zev-Tabelle, optionaler WHERE-Filter,
 * spaltenunabhängige Anzeige mit Pagination.
 */
@Component({
  selector: 'app-datenbank-ansicht',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe, IconComponent],
  templateUrl: './datenbank-ansicht.component.html',
  styleUrls: ['./datenbank-ansicht.component.css']
})
export class DatenbankAnsichtComponent extends WithMessage implements OnInit {
  tabellen: string[] = [];
  selectedTabelle = '';
  whereClause = '';
  result: DatenbankAbfrageResponse | null = null;
  page = 0;
  size = 50;
  loading = false;

  constructor(private datenbankService: DatenbankService) { super(); }

  ngOnInit(): void {
    this.loadTabellen();
  }

  loadTabellen(): void {
    this.datenbankService.getTabellen().subscribe({
      next: (tabellen) => this.tabellen = tabellen,
      error: () => this.showMessage('DATENBANK_FEHLER', 'error')
    });
  }

  onAnzeigen(): void {
    this.page = 0;
    this.abfrage();
  }

  onVorherigeSeite(): void {
    if (this.page > 0) {
      this.page--;
      this.abfrage();
    }
  }

  onNaechsteSeite(): void {
    if (this.result?.hatMehr) {
      this.page++;
      this.abfrage();
    }
  }

  private abfrage(): void {
    if (!this.selectedTabelle) {
      return;
    }
    this.loading = true;
    this.dismissMessage();
    this.datenbankService.abfrage({
      tabelle: this.selectedTabelle,
      where: this.whereClause?.trim() || undefined,
      page: this.page,
      size: this.size
    }).subscribe({
      next: (result) => {
        this.result = result;
        this.loading = false;
      },
      error: (error) => {
        this.result = null;
        this.loading = false;
        this.showMessage(error.error || 'DATENBANK_FEHLER', 'error');
      }
    });
  }
}
