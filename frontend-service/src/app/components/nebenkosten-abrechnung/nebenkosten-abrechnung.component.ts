import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NebenkostenService } from '../../services/nebenkosten.service';
import { NkAbrechnung } from '../../models/nebenkosten.model';
import { NebenkostenAbrechnungFormComponent } from '../nebenkosten-abrechnung-form/nebenkosten-abrechnung-form.component';
import { TranslatePipe } from '../../pipes/translate.pipe';
import { SwissDatePipe } from '../../pipes/swiss-date.pipe';
import { TranslationService } from '../../services/translation.service';
import { KebabMenuComponent, KebabMenuItem } from '../kebab-menu/kebab-menu.component';
import { ColumnResizeDirective } from '../../directives/column-resize.directive';
import { IconComponent } from '../icon/icon.component';

/**
 * Liste der Nebenkostenabrechnungen (Specs/Nebenkosten/Abrechnung.md, FR-1 und FR-7).
 *
 * Das Flag „abgerechnet" ist direkt in der Tabelle bedienbar. Nur das **Zurücksetzen** fragt nach:
 * Es öffnet eine abgeschlossene Abrechnung wieder zur Bearbeitung; das Abschliessen selbst ist
 * jederzeit umkehrbar und braucht keine Rückfrage.
 */
@Component({
  selector: 'app-nebenkosten-abrechnung',
  standalone: true,
  imports: [
    CommonModule,
    NebenkostenAbrechnungFormComponent,
    TranslatePipe,
    SwissDatePipe,
    KebabMenuComponent,
    ColumnResizeDirective,
    IconComponent
  ],
  templateUrl: './nebenkosten-abrechnung.component.html'
})
export class NebenkostenAbrechnungComponent implements OnInit {
  abrechnungen: NkAbrechnung[] = [];
  selectedId: number | null = null;
  showForm = false;
  message = '';
  messageType: 'success' | 'error' = 'success';
  messagePersistent = false;
  sortColumn: 'bezeichnung' | 'datumVon' | 'datumBis' | null = 'datumVon';
  sortDirection: 'asc' | 'desc' = 'desc';

  menuItems: KebabMenuItem[] = [
    { label: 'BEARBEITEN', action: 'edit', icon: 'edit-2' },
    { label: 'LOESCHEN', action: 'delete', danger: true, icon: 'trash-2' }
  ];

  constructor(
    private nebenkostenService: NebenkostenService,
    private translationService: TranslationService
  ) { }

  ngOnInit(): void {
    this.loadAbrechnungen();
  }

  loadAbrechnungen(): void {
    this.nebenkostenService.getAllAbrechnungen().subscribe({
      next: (data) => {
        this.abrechnungen = data;
      },
      error: () => {
        this.showMessage('NK_FEHLER_LADEN', 'error');
      }
    });
  }

  onCreateNew(): void {
    this.selectedId = null;
    this.showForm = true;
  }

  onEdit(abrechnung: NkAbrechnung): void {
    this.selectedId = abrechnung.id ?? null;
    this.showForm = true;
  }

  onDelete(id: number | undefined): void {
    if (!id) return;

    if (confirm(this.translationService.translate('NK_CONFIRM_LOESCHEN'))) {
      this.nebenkostenService.deleteAbrechnung(id).subscribe({
        next: () => {
          this.showMessage('NK_ABRECHNUNG_GELOESCHT', 'success');
          this.loadAbrechnungen();
        },
        error: (error) => {
          this.showMessage(error.error || 'NK_FEHLER_LOESCHEN', 'error');
        }
      });
    }
  }

  onMenuAction(action: string, abrechnung: NkAbrechnung): void {
    switch (action) {
      case 'edit':
        this.onEdit(abrechnung);
        break;
      case 'delete':
        this.onDelete(abrechnung.id);
        break;
    }
  }

  /**
   * Schaltet „abgerechnet" um. Beim Deaktivieren wird zurückgefragt; sagt der Benutzer ab, wird
   * die Liste neu geladen, damit die Checkbox nicht angehakt stehenbleibt.
   */
  onToggleAbgerechnet(abrechnung: NkAbrechnung, event: Event): void {
    const ziel = (event.target as HTMLInputElement).checked;
    if (!abrechnung.id) return;

    if (!ziel && !confirm(this.translationService.translate('NK_CONFIRM_FREIGEBEN'))) {
      this.loadAbrechnungen();
      return;
    }

    this.nebenkostenService.setAbgerechnet(abrechnung.id, ziel).subscribe({
      next: () => {
        this.showMessage(ziel ? 'NK_ABGERECHNET_GESETZT' : 'NK_ABGERECHNET_GELOEST', 'success');
        this.loadAbrechnungen();
      },
      error: (error) => {
        this.showMessage(error.error || 'NK_FEHLER_SPEICHERN', 'error');
        this.loadAbrechnungen();
      }
    });
  }

  /**
   * Die Maske hat gespeichert und bleibt offen.
   *
   * <p>Bewusst **ohne** eigene Meldung: Die Maske zeigt bereits eine, und beide lägen als
   * fixierte Elemente an derselben Stelle übereinander. Die Liste wird nur neu geladen, damit die
   * Zeile stimmt, wenn der Benutzer zurückkehrt.
   */
  onFormSaved(): void {
    this.loadAbrechnungen();
  }

  onFormClose(): void {
    this.showForm = false;
    this.selectedId = null;
    this.loadAbrechnungen();
  }

  onSort(column: 'bezeichnung' | 'datumVon' | 'datumBis'): void {
    if (this.sortColumn === column) {
      this.sortDirection = this.sortDirection === 'asc' ? 'desc' : 'asc';
    } else {
      this.sortColumn = column;
      this.sortDirection = 'asc';
    }

    this.abrechnungen.sort((a, b) => {
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

  dismissMessage(): void {
    this.message = '';
    this.messagePersistent = false;
  }

  private showMessage(message: string, type: 'success' | 'error'): void {
    this.message = message;
    this.messageType = type;
    this.messagePersistent = type === 'error';
    if (!this.messagePersistent) {
      setTimeout(() => {
        this.message = '';
      }, 5000);
    }
  }
}
