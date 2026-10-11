import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CdkDrag, CdkDragDrop, CdkDragHandle, CdkDropList, moveItemInArray } from '@angular/cdk/drag-drop';
import { TarifService } from '../../services/tarif.service';
import { Tarif, TarifTyp, ValidationResult, preisEinheitKey } from '../../models/tarif.model';
import { formatTarifLuecke } from '../../utils/tarif-luecke.util';
import { TarifFormComponent } from '../tarif-form/tarif-form.component';
import { TranslatePipe } from '../../pipes/translate.pipe';
import { SwissDatePipe } from '../../pipes/swiss-date.pipe';
import { TranslationService } from '../../services/translation.service';
import { KebabMenuComponent, KebabMenuItem } from '../kebab-menu/kebab-menu.component';
import { ColumnResizeDirective } from '../../directives/column-resize.directive';
import { formatSwissNumber } from '../../utils/number-utils';
import { IconComponent } from '../icon/icon.component';
import { PreiszeitreiheChartComponent } from '../preiszeitreihe-chart/preiszeitreihe-chart.component';
import { FeatureFlagDirective } from '../../directives/feature-flag.directive';

/** Panel der Tarifverwaltung (Specs/Tarifverwaltung.md, FR-4). */
export type TarifGruppe = 'zukuenftig' | 'aktuell' | 'alt';

@Component({
  selector: 'app-tarif-list',
  standalone: true,
  imports: [CommonModule, TarifFormComponent, TranslatePipe, SwissDatePipe, KebabMenuComponent,
    ColumnResizeDirective, IconComponent, PreiszeitreiheChartComponent, FeatureFlagDirective,
    CdkDropList, CdkDrag, CdkDragHandle],
  templateUrl: './tarif-list.component.html',
  styleUrls: ['./tarif-list.component.css']
})
export class TarifListComponent implements OnInit {
  tarife: Tarif[] = [];

  /** Die drei Panels, je in der Reihenfolge der Rechnung. Neu gebildet bei jedem Laden. */
  gruppen: Record<TarifGruppe, Tarif[]> = { zukuenftig: [], aktuell: [], alt: [] };

  /** Reihenfolge der Panels auf der Seite. */
  readonly gruppenReihenfolge: TarifGruppe[] = ['zukuenftig', 'aktuell', 'alt'];

  /** Beim Oeffnen der Seite ist nur das aktuelle Jahr aufgeklappt. */
  offen: Record<TarifGruppe, boolean> = { zukuenftig: false, aktuell: true, alt: false };

  /** Das aktuelle Jahr - Grenze zwischen den Panels und Titel des mittleren. */
  readonly jahr = new Date().getFullYear();

  selectedTarif: Tarif | null = null;
  showForm = false;
  message = '';
  messageType: 'success' | 'error' = 'success';
  messagePersistent = false;
  validationErrors: string[] = [];

  menuItems: KebabMenuItem[] = [
    { label: 'BEARBEITEN', action: 'edit', icon: 'edit-2' },
    { label: 'KOPIEREN', action: 'copy', icon: 'copy' },
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
        this.gruppen = this.gruppiere(data);
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

  onCopy(tarif: Tarif): void {
    // Kopie ohne ID erstellen - wird als neuer Tarif behandelt
    const { id, ...tarifOhneId } = tarif;
    this.selectedTarif = { ...tarifOhneId } as Tarif;
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
          this.showMessage(error.error?.error || error.error || 'FEHLER_LOESCHEN_TARIF', 'error');
        }
      });
    }
  }

  onMenuAction(action: string, tarif: Tarif): void {
    switch (action) {
      case 'edit':
        this.onEdit(tarif);
        break;
      case 'copy':
        this.onCopy(tarif);
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

  /**
   * Teilt die Tarife nach ihrer Gueltigkeit auf die drei Panels auf:
   * aktuelles Jahr = gilt an mindestens einem Tag des Jahres; zukuenftig = beginnt danach;
   * frueher = endete davor. Die Reihenfolge innerhalb eines Panels ist die vom Server
   * (Reihenfolge der Rechnung).
   */
  gruppiere(tarife: Tarif[]): Record<TarifGruppe, Tarif[]> {
    const jahresbeginn = `${this.jahr}-01-01`;
    const jahresende = `${this.jahr}-12-31`;
    const gruppen: Record<TarifGruppe, Tarif[]> = { zukuenftig: [], aktuell: [], alt: [] };
    for (const tarif of tarife) {
      if (tarif.gueltigVon > jahresende) {
        gruppen.zukuenftig.push(tarif);
      } else if (tarif.gueltigBis < jahresbeginn) {
        gruppen.alt.push(tarif);
      } else {
        gruppen.aktuell.push(tarif);
      }
    }
    return gruppen;
  }

  toggleGruppe(gruppe: TarifGruppe): void {
    this.offen[gruppe] = !this.offen[gruppe];
  }

  /** Uebersetzungs-Key des Panel-Titels. */
  gruppenTitel(gruppe: TarifGruppe): string {
    return gruppe === 'zukuenftig' ? 'TARIFE_ZUKUENFTIG'
      : gruppe === 'alt' ? 'TARIFE_ALT' : 'TARIFE_AKTUELLES_JAHR';
  }

  /**
   * Zeile innerhalb eines Panels verschoben: sofort anzeigen, dann speichern. Gemeldet wird nur
   * die eine Verschiebung - nach oben "vor" den neuen Nachfolger, nach unten "hinter" den neuen
   * Vorgaenger. Schlaegt das Speichern fehl, wird neu geladen: Die Liste zeigt dann wieder den
   * gespeicherten Stand.
   */
  onDrop(gruppe: TarifGruppe, event: CdkDragDrop<Tarif[]>): void {
    if (event.previousIndex === event.currentIndex) {
      return;
    }
    const liste = this.gruppen[gruppe];
    moveItemInArray(liste, event.previousIndex, event.currentIndex);
    const tarif = liste[event.currentIndex];
    const ziel = event.currentIndex < event.previousIndex
      ? { vor: liste[event.currentIndex + 1].id! }
      : { nach: liste[event.currentIndex - 1].id! };
    this.tarifService.verschiebeTarif(tarif.id!, ziel).subscribe({
      next: () => {
        this.showMessage('TARIF_REIHENFOLGE_GESPEICHERT', 'success');
        this.loadTarife();
      },
      error: () => {
        this.showMessage('FEHLER_REIHENFOLGE_TARIF', 'error');
        this.loadTarife();
      }
    });
  }

  formatPreis(preis: number): string {
    return formatSwissNumber(preis, 5);
  }

  /** Übersetzungs-Key der Preis-Einheit („CHF pro …") dieser Zeile. */
  preisEinheit(tarif: Tarif): string {
    return preisEinheitKey(tarif.tariftyp as TarifTyp, tarif.mengeneinheit);
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
      this.validationErrors = result.luecken.map(periode =>
        `${periode.periode}: ${periode.luecken
          .map(luecke => formatTarifLuecke(luecke, this.translationService))
          .join('; ')}`);
    }
  }

  dismissMessage(): void {
    this.message = '';
    this.validationErrors = [];
    this.messagePersistent = false;
  }

  private showMessage(message: string, type: 'success' | 'error', persistent?: boolean): void {
    this.message = message;
    this.messageType = type;
    // Error messages are persistent by default, success messages auto-dismiss
    const isPersistent = persistent !== undefined ? persistent : type === 'error';
    this.messagePersistent = isPersistent;
    if (!isPersistent) {
      this.validationErrors = [];
      setTimeout(() => {
        this.message = '';
      }, 5000);
    }
  }
}
