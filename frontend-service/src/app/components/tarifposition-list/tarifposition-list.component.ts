import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { TarifpositionService } from '../../services/tarifposition.service';
import { TarifService } from '../../services/tarif.service';
import { EinheitService } from '../../services/einheit.service';
import { MieterService } from '../../services/mieter.service';
import { TranslationService } from '../../services/translation.service';
import { Tarifposition, Erfassungsart } from '../../models/tarifposition.model';
import { Tarif, TarifTyp, MANUELL_ERFASSTE_TARIFTYPEN, mengeneinheitKey } from '../../models/tarif.model';
import { Einheit, EinheitTyp } from '../../models/einheit.model';
import { TarifpositionFormComponent } from '../tarifposition-form/tarifposition-form.component';
import { TranslatePipe } from '../../pipes/translate.pipe';
import { KebabMenuComponent, KebabMenuItem } from '../kebab-menu/kebab-menu.component';
import { ColumnResizeDirective } from '../../directives/column-resize.directive';
import { SwissNumberPipe } from '../../pipes/swiss-number.pipe';
import { IconComponent } from '../icon/icon.component';

/** Sortierbare Spalten der Positionsliste. `betrag` ist berechnet, `quartal` umfasst Jahr + Quartal. */
export type TarifpositionSortColumn =
  'tarifBezeichnung' | 'quartal' | 'menge' | 'tarifPreis' | 'betrag' | 'erfassungsart';

/**
 * Erfassung manuell gepflegter Tarifpositionen je Einheit und Quartal (aktuell Ladestrom).
 *
 * Anker ist die **Einheit** vom Typ Ladestation (Specs/Ladestationen.md). Eigene Seite statt nur
 * eines Kebab-Eintrags in der Einheiten-Verwaltung: Die Route /einheiten verlangt `einheit:write`,
 * das ein `zev_user` nicht hat — er käme sonst gar nicht bis zur Erfassung.
 */
@Component({
  selector: 'app-tarifposition-list',
  standalone: true,
  imports: [CommonModule, FormsModule, TarifpositionFormComponent, TranslatePipe, SwissNumberPipe,
    KebabMenuComponent, ColumnResizeDirective, IconComponent],
  templateUrl: './tarifposition-list.component.html',
  styleUrls: ['./tarifposition-list.component.css']
})
export class TarifpositionListComponent implements OnInit {
  /** Merker für den weggeklickten Mehrfachverrechnungs-Hinweis (rein lokal, kein Backend). */
  private static readonly HINWEIS_STORAGE_KEY = 'zev.tarifposition.hinweisAusgeblendet';

  ladestationen: Einheit[] = [];
  selectedEinheitId: number | null = null;
  /**
   * Einheiten, denen mindestens ein Mieter zugeordnet ist. `null` = noch nicht (oder nicht)
   * ermittelbar; dann erscheint kein Hinweis, statt fälschlich „ohne Mieter" zu behaupten.
   */
  private einheitIdsMitMieter: Set<number> | null = null;
  positionen: Tarifposition[] = [];
  tarife: Tarif[] = [];
  selectedPosition: Tarifposition | null = null;
  showForm = false;
  message = '';
  messageType: 'success' | 'error' = 'success';
  messagePersistent = false;
  hinweisSichtbar = true;
  // Startwert entspricht der Reihenfolge, in der das Backend liefert (neuestes Quartal zuerst)
  sortColumn: TarifpositionSortColumn | null = 'quartal';
  sortDirection: 'asc' | 'desc' = 'desc';

  readonly Erfassungsart = Erfassungsart;

  menuItems: KebabMenuItem[] = [
    { label: 'BEARBEITEN', action: 'edit', icon: 'edit-2' },
    { label: 'KOPIEREN', action: 'copy', icon: 'copy' },
    { label: 'LOESCHEN', action: 'delete', danger: true, icon: 'trash-2' }
  ];

  constructor(
    private tarifpositionService: TarifpositionService,
    private tarifService: TarifService,
    private einheitService: EinheitService,
    private mieterService: MieterService,
    private translationService: TranslationService,
    private route: ActivatedRoute
  ) { }

  ngOnInit(): void {
    this.hinweisSichtbar = !this.leseHinweisAusgeblendet();
    this.loadLadestationen();
    this.loadTarife();
    this.loadMieterZuordnungen();
  }

  /**
   * Blendet den Mehrfachverrechnungs-Hinweis dauerhaft aus (pro Browser).
   * Der Hinweis ist eine einmalige Erklärung, keine Fehlermeldung – er muss nicht bei jedem
   * Seitenaufruf erneut weggeklickt werden.
   */
  dismissHinweis(): void {
    this.hinweisSichtbar = false;
    try {
      localStorage.setItem(TarifpositionListComponent.HINWEIS_STORAGE_KEY, 'true');
    } catch {
      // localStorage nicht verfügbar – der Hinweis erscheint dann beim nächsten Aufruf erneut
    }
  }

  private leseHinweisAusgeblendet(): boolean {
    try {
      return localStorage.getItem(TarifpositionListComponent.HINWEIS_STORAGE_KEY) === 'true';
    } catch {
      return false;
    }
  }

  loadLadestationen(): void {
    this.einheitService.getAllEinheiten().subscribe({
      next: (data) => {
        this.ladestationen = data
          .filter(e => e.typ === EinheitTyp.LADESTATION)
          .sort((a, b) => a.name.localeCompare(b.name));
        // Vorauswahl aus dem Kebab-Sprung der Einheiten-Verwaltung (?einheitId=…)
        const param = this.route.snapshot.queryParamMap.get('einheitId');
        const vorauswahl = param ? Number(param) : null;
        if (vorauswahl && this.ladestationen.some(e => e.id === vorauswahl)) {
          this.selectedEinheitId = vorauswahl;
          this.loadPositionen();
        }
      },
      error: () => this.showMessage('FEHLER_LADEN_EINHEITEN', 'error')
    });
  }

  loadTarife(): void {
    this.tarifService.getAllTarife().subscribe({
      next: (data) => {
        this.tarife = data.filter(t => MANUELL_ERFASSTE_TARIFTYPEN.includes(t.tariftyp as TarifTyp));
      },
      error: () => this.showMessage('FEHLER_LADEN_TARIFE', 'error')
    });
  }

  /**
   * Lädt die Einheiten-Zuordnungen aller Mieter für den Hinweis „Einheit ohne Mieter".
   *
   * Eigener Abruf statt eines Flags an der Einheit: Die Zuordnung liegt in `mieter_einheit`, und
   * die Einheiten-API müsste sie sonst für jede Liste mitzählen. Ein Fehlschlag bleibt bewusst
   * still — der Hinweis ist eine Zusatzinformation und darf die Erfassung weder blockieren noch
   * mit einer Fehlermeldung überdecken.
   */
  loadMieterZuordnungen(): void {
    this.mieterService.getAllMieter().subscribe({
      next: (mieter) => {
        this.einheitIdsMitMieter = new Set(mieter.flatMap(m => m.einheitIds ?? []));
      },
      error: () => {
        this.einheitIdsMitMieter = null;
      }
    });
  }

  loadPositionen(): void {
    if (!this.selectedEinheitId) {
      this.positionen = [];
      return;
    }
    this.tarifpositionService.getByEinheit(this.selectedEinheitId).subscribe({
      next: (data) => {
        this.positionen = data;
        this.sortiere();
      },
      error: () => this.showMessage('FEHLER_LADEN_TARIFPOSITIONEN', 'error')
    });
  }

  onEinheitChange(): void {
    this.showForm = false;
    this.selectedPosition = null;
    this.dismissMessage();
    this.loadPositionen();
  }

  onCreateNew(): void {
    this.selectedPosition = null;
    this.showForm = true;
  }

  onEdit(position: Tarifposition): void {
    this.selectedPosition = { ...position };
    this.showForm = true;
  }

  /**
   * Öffnet das Formular mit den Werten der Position, aber ohne ID – gespeichert wird eine neue
   * Position. Jahr/Quartal bleiben bewusst unverändert (analog Tarife): Da je Mieter, Quartal und
   * Tariftyp nur eine Position zulässig ist, muss der Zeitraum ohnehin bewusst gewählt werden;
   * eine unveränderte Kopie weist der Server mit der Duplikat-Meldung ab.
   */
  onCopy(position: Tarifposition): void {
    const { id, ...positionOhneId } = position;
    this.selectedPosition = { ...positionOhneId } as Tarifposition;
    this.showForm = true;
  }

  onDelete(id: number | undefined): void {
    if (!id) return;

    if (confirm(this.translationService.translate('CONFIRM_DELETE_TARIFPOSITION'))) {
      this.tarifpositionService.deleteTarifposition(id).subscribe({
        next: () => {
          this.showMessage('TARIFPOSITION_GELOESCHT', 'success');
          this.loadPositionen();
        },
        error: (error) => this.showMessage(this.fehlertext(error, 'FEHLER_LOESCHEN_TARIFPOSITION'), 'error')
      });
    }
  }

  onMenuAction(action: string, position: Tarifposition): void {
    switch (action) {
      case 'edit':
        this.onEdit(position);
        break;
      case 'copy':
        this.onCopy(position);
        break;
      case 'delete':
        this.onDelete(position.id);
        break;
    }
  }

  onFormSubmit(position: Tarifposition): void {
    if (position.id) {
      this.tarifpositionService.updateTarifposition(position.id, position).subscribe({
        next: () => {
          this.showMessage('TARIFPOSITION_AKTUALISIERT', 'success');
          this.showForm = false;
          this.loadPositionen();
        },
        error: (error) => this.showMessage(this.fehlertext(error, 'FEHLER_AKTUALISIEREN_TARIFPOSITION'), 'error')
      });
    } else {
      this.tarifpositionService.createTarifposition(position).subscribe({
        next: () => {
          this.showMessage('TARIFPOSITION_ERSTELLT', 'success');
          this.showForm = false;
          this.loadPositionen();
        },
        error: (error) => this.showMessage(this.fehlertext(error, 'FEHLER_ERSTELLEN_TARIFPOSITION'), 'error')
      });
    }
  }

  onFormCancel(): void {
    this.showForm = false;
    this.selectedPosition = null;
  }

  onSort(column: TarifpositionSortColumn): void {
    if (this.sortColumn === column) {
      this.sortDirection = this.sortDirection === 'asc' ? 'desc' : 'asc';
    } else {
      this.sortColumn = column;
      this.sortDirection = 'asc';
    }
    this.sortiere();
  }

  /**
   * Sortiert die geladenen Positionen nach der aktuellen Spalte. Wird auch nach jedem Neuladen
   * aufgerufen, damit eine gewählte Sortierung das Erfassen oder Löschen einer Position überlebt.
   */
  private sortiere(): void {
    const column = this.sortColumn;
    if (!column) {
      return;
    }
    this.positionen.sort((a, b) => {
      let aValue = this.sortWert(a, column);
      let bValue = this.sortWert(b, column);

      if (aValue === null || aValue === undefined) return 1;
      if (bValue === null || bValue === undefined) return -1;

      if (typeof aValue === 'string' && typeof bValue === 'string') {
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

  private sortWert(position: Tarifposition, column: TarifpositionSortColumn): string | number | undefined {
    switch (column) {
      // Jahr und Quartal stehen in einer Spalte - zusammengesetzt sortieren, sonst landete
      // Q1/2027 vor Q4/2026
      case 'quartal':
        return (position.jahr ?? 0) * 4 + (position.quartal ?? 0);
      case 'betrag':
        return this.berechneBetrag(position);
      default:
        return position[column];
    }
  }

  /**
   * Wahr, wenn der gewählten Ladestation kein Mieter zugeordnet ist. Positionen sind dann zwar
   * erfassbar, erscheinen aber auf keiner Rechnung — die entsteht je Mieter (Specs/Ladestationen.md,
   * Edge Case „Ladestations-Einheit ohne zugeordneten Mieter").
   */
  get einheitOhneMieter(): boolean {
    return this.selectedEinheitId !== null
      && this.einheitIdsMitMieter !== null
      && !this.einheitIdsMitMieter.has(this.selectedEinheitId);
  }

  /** Messpunkt (RFID) der gewählten Ladestation – belegt die Quell-Referenz vor. */
  get selectedEinheitMesspunkt(): string {
    return this.ladestationen.find(e => e.id === this.selectedEinheitId)?.messpunkt ?? '';
  }

  /** Uebersetzungs-Key der Mengeneinheit einer Zeile (kWh bzw. Monate). */
  mengeneinheit(position: Tarifposition): string {
    return mengeneinheitKey(position.tarifTyp);
  }

  /** Monate werden ganzzahlig gezeigt - "3.000 Monate" waere unsinnig. */
  mengeNachkommastellen(position: Tarifposition): number {
    return position.tarifTyp === TarifTyp.GRUNDGEBUEHR ? 0 : 3;
  }

  berechneBetrag(position: Tarifposition): number {
    return (position.menge ?? 0) * (position.tarifPreis ?? 0);
  }

  /**
   * Fehlertext aus der Server-Antwort. Die Bean-Validation des DTO liefert eine Map
   * `{ feld: meldung }` — die direkt anzuzeigen ergäbe „[object Object]".
   */
  private fehlertext(error: { error?: unknown }, fallback: string): string {
    const body = error?.error;
    if (typeof body === 'string' && body.trim()) {
      return body;
    }
    if (body && typeof body === 'object') {
      const meldungen = Object.values(body as Record<string, unknown>)
        .filter((v): v is string => typeof v === 'string');
      if (meldungen.length > 0) {
        return meldungen.join('; ');
      }
    }
    return fallback;
  }

  dismissMessage(): void {
    this.message = '';
    this.messagePersistent = false;
  }

  private showMessage(message: string, type: 'success' | 'error', persistent?: boolean): void {
    this.message = message;
    this.messageType = type;
    const isPersistent = persistent !== undefined ? persistent : type === 'error';
    this.messagePersistent = isPersistent;
    if (!isPersistent) {
      setTimeout(() => {
        this.message = '';
      }, 5000);
    }
  }
}
