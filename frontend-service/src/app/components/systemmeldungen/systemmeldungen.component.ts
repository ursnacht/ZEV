import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import Keycloak from 'keycloak-js';
import { WithMessage } from '../../utils/with-message';
import { SystemmeldungService, SystemmeldungQuery } from '../../services/systemmeldung.service';
import { ErledigtFilter, MeldungLevel, Systemmeldung } from '../../models/systemmeldung.model';
import { TranslatePipe } from '../../pipes/translate.pipe';
import { KebabMenuComponent, KebabMenuItem } from '../kebab-menu/kebab-menu.component';
import { IconComponent } from '../icon/icon.component';

/**
 * Systemmeldungen-Seite: persistente Betriebsmeldungen mit Filter (Erledigt/Kategorie/Level),
 * serverseitiger Sortierung + Paginierung (analog Datenbank-Ansicht), Erledigt-Toggle und Löschen.
 * Verwalten (Toggle/Löschen) nur mit {@code systemmeldungen:manage}.
 */
@Component({
  selector: 'app-systemmeldungen',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe, KebabMenuComponent, IconComponent],
  templateUrl: './systemmeldungen.component.html',
  styleUrls: ['./systemmeldungen.component.css']
})
export class SystemmeldungenComponent extends WithMessage implements OnInit {
  meldungen: Systemmeldung[] = [];
  kategorien: string[] = [];
  loading = false;

  // Filter
  erledigtFilter: ErledigtFilter = 'OFFENE';
  kategorieFilter = '';        // '' = alle
  levelFilter: '' | MeldungLevel = '';  // '' = alle

  // Sortierung / Paginierung (serverseitig)
  sortSpalte = 'zuletztAufgetreten';
  sortRichtung: 'ASC' | 'DESC' = 'DESC';
  page = 0;
  size = 50;
  hatMehr = false;

  readonly levelOptionen: MeldungLevel[] = ['INFO', 'WARN', 'ERROR'];

  /** Verwalten (erledigt umschalten, löschen) nur mit systemmeldungen:manage. */
  readonly canManage = inject(Keycloak).hasRealmRole('systemmeldungen:manage');

  menuItems: KebabMenuItem[] = [
    { label: 'LOESCHEN', action: 'delete', danger: true, icon: 'trash-2' }
  ];

  constructor(private systemmeldungService: SystemmeldungService) { super(); }

  ngOnInit(): void {
    this.loadKategorien();
    this.load();
  }

  load(): void {
    this.loading = true;
    this.systemmeldungService.getSeite(this.buildQuery()).subscribe({
      next: (seite) => {
        this.meldungen = seite.items;
        this.hatMehr = seite.hatMehr;
        this.loading = false;
      },
      error: () => {
        this.meldungen = [];
        this.hatMehr = false;
        this.loading = false;
        this.showMessage('SYSTEMMELDUNGEN_FEHLER', 'error');
      }
    });
  }

  loadKategorien(): void {
    this.systemmeldungService.getKategorien().subscribe({
      next: (kategorien) => this.kategorien = kategorien,
      error: () => { /* Filter-Optionen sind optional */ }
    });
  }

  private buildQuery(): SystemmeldungQuery {
    return {
      erledigt: this.erledigtFilter === 'ALLE' ? undefined : this.erledigtFilter === 'ERLEDIGTE',
      kategorie: this.kategorieFilter || undefined,
      level: this.levelFilter || undefined,
      page: this.page,
      size: this.size,
      sortSpalte: this.sortSpalte,
      sortRichtung: this.sortRichtung
    };
  }

  /** Filterwechsel setzt auf Seite 0 zurück und lädt neu. */
  onFilterChange(): void {
    this.page = 0;
    this.load();
  }

  onSort(spalte: string): void {
    if (this.sortSpalte === spalte) {
      this.sortRichtung = this.sortRichtung === 'ASC' ? 'DESC' : 'ASC';
    } else {
      this.sortSpalte = spalte;
      this.sortRichtung = 'ASC';
    }
    this.page = 0;
    this.load();
  }

  onToggleErledigt(meldung: Systemmeldung): void {
    this.systemmeldungService.setErledigt(meldung.id, !meldung.erledigt).subscribe({
      next: () => {
        this.showMessage(meldung.erledigt ? 'SYSTEMMELDUNG_WIEDER_OFFEN' : 'SYSTEMMELDUNG_ERLEDIGT', 'success');
        this.load();
      },
      error: (error) => this.showMessage(error.error?.error || 'SYSTEMMELDUNGEN_FEHLER', 'error')
    });
  }

  onDelete(id: number): void {
    this.systemmeldungService.deleteSystemmeldung(id).subscribe({
      next: () => {
        this.showMessage('SYSTEMMELDUNG_GELOESCHT', 'success');
        this.load();
      },
      error: () => this.showMessage('SYSTEMMELDUNGEN_FEHLER', 'error')
    });
  }

  onMenuAction(action: string, meldung: Systemmeldung): void {
    if (action === 'delete') {
      this.onDelete(meldung.id);
    }
  }

  onVorherigeSeite(): void {
    if (this.page > 0) {
      this.page--;
      this.load();
    }
  }

  onNaechsteSeite(): void {
    if (this.hatMehr) {
      this.page++;
      this.load();
    }
  }

  /** CSS-Statusklasse je Schweregrad für das Level-Badge. */
  levelClass(level: MeldungLevel): string {
    switch (level) {
      case 'ERROR': return 'zev-status--error';
      case 'WARN': return 'zev-status--warning';
      default: return 'zev-status--info';
    }
  }
}
