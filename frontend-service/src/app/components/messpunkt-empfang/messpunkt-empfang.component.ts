import { Component, OnInit, inject } from '@angular/core';
import Keycloak from 'keycloak-js';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { WithMessage } from '../../utils/with-message';
import { MesspunktEmpfangService } from '../../services/messpunkt-empfang.service';
import { MesspunktEmpfang } from '../../models/messpunkt-empfang.model';
import { MAX_MESSPUNKT_LAENGE } from '../../models/einheit.model';
import { TranslatePipe } from '../../pipes/translate.pipe';
import { TranslationService } from '../../services/translation.service';
import { KebabMenuComponent, KebabMenuItem } from '../kebab-menu/kebab-menu.component';
import { ColumnResizeDirective } from '../../directives/column-resize.directive';
import { IconComponent } from '../icon/icon.component';
import { formatSwissDateTime } from '../../utils/date-utils';
import { formatSwissNumber } from '../../utils/number-utils';

type SortSpalte = 'messpunkt' | 'einheiten' | 'quelle' | 'letzterEmpfang' | 'anzahlMeldungen'
  | 'ersterEmpfang' | 'ergebnis';

/** Status-Farbe eines Badges (`.zev-status--…`). */
export type BadgeFarbe = 'success' | 'warning' | 'error';

/** Unter 5 Minuten gilt der Empfang als frisch (FR-2.5). */
const FRISCH_BIS_SEKUNDEN = 300;
/** Bis einschliesslich 60 Minuten gilt er als verzögert, darüber als ausgefallen (FR-2.5). */
const VERZOEGERT_BIS_SEKUNDEN = 3600;

/**
 * Farbe des Alters-Badges (Specs/Messpunkt-Empfang.md, FR-2.5): unter 300 s `success`, bis
 * einschliesslich 3600 s `warning`, darüber `error`.
 */
export function altersFarbe(sekunden: number): BadgeFarbe {
  if (sekunden < FRISCH_BIS_SEKUNDEN) {
    return 'success';
  }
  return sekunden <= VERZOEGERT_BIS_SEKUNDEN ? 'warning' : 'error';
}

/**
 * Key und Zahl des Alterstexts (FR-2.5), abgerundet: bis einschliesslich 3600 s in Minuten,
 * darunter 48 Stunden in Stunden, sonst in Tagen. Bei genau 3600 s also `vor 60 Min.`.
 */
export function altersText(sekunden: number): { key: string, wert: number } {
  if (sekunden <= VERZOEGERT_BIS_SEKUNDEN) {
    return { key: 'MESSPUNKT_EMPFANG_VOR_MINUTEN', wert: Math.floor(sekunden / 60) };
  }
  const stunden = Math.floor(sekunden / 3600);
  if (stunden < 48) {
    return { key: 'MESSPUNKT_EMPFANG_VOR_STUNDEN', wert: stunden };
  }
  return { key: 'MESSPUNKT_EMPFANG_VOR_TAGEN', wert: Math.floor(sekunden / 86400) };
}

/** Farbe des Ergebnis-Badges (FR-2.4); Kein Empfang ist rot. */
export function ergebnisFarbe(zeile: MesspunktEmpfang): BadgeFarbe {
  if (zeile.keinEmpfang || zeile.ergebnis === 'UNGUELTIG') {
    return 'error';
  }
  return zeile.ergebnis === 'GESPEICHERT' ? 'success' : 'warning';
}

/**
 * Text eines Payloads für die Anzeige (FR-2.9): gültiges JSON eingerückt, alles andere
 * unverändert. `null` heisst: kein Payload festgehalten.
 */
export function formatierePayload(payload: string | null): string | null {
  if (payload == null) {
    return null;
  }
  try {
    return JSON.stringify(JSON.parse(payload), null, 2);
  } catch {
    return payload;
  }
}

/**
 * Gruppe der Standardsortierung (FR-2.6): zuerst Messpunkte ohne Einheit, dann Einheiten ohne
 * Empfang, dann die übrigen.
 */
function gruppe(zeile: MesspunktEmpfang): number {
  if (zeile.keinEmpfang) {
    return 1;
  }
  return zeile.einheiten.length === 0 ? 0 : 2;
}

/**
 * Ansicht Messpunkt-Empfang (Specs/Messpunkt-Empfang.md, FR-2 und FR-3): Kommen die Daten eines
 * Zählers an, unter welchem Messpunkt, und sind sie gültig?
 */
@Component({
  selector: 'app-messpunkt-empfang',
  standalone: true,
  imports: [CommonModule, TranslatePipe, KebabMenuComponent, ColumnResizeDirective, IconComponent],
  templateUrl: './messpunkt-empfang.component.html',
  styleUrls: ['./messpunkt-empfang.component.css']
})
export class MesspunktEmpfangComponent extends WithMessage implements OnInit {
  zeilen: MesspunktEmpfang[] = [];
  geladen = false;

  /** `null` = Standardsortierung nach Gruppen (FR-2.6). */
  sortColumn: SortSpalte | null = null;
  sortDirection: 'asc' | 'desc' = 'asc';

  /**
   * Einheit anlegen und Löschen verlangen `einheit:write`; die letzte Meldung ansehen kann jeder
   * mit Zugriff auf die Seite (FR-2.8).
   */
  readonly canWrite = inject(Keycloak).hasRealmRole('einheit:write');

  /**
   * Die geöffnete Meldung (FR-2.9) – höchstens eine. `text` ist `null`, solange geladen wird oder
   * wenn kein Payload festgehalten ist (`geladen` unterscheidet die beiden Fälle).
   */
  offeneMeldung: { messpunkt: string, text: string | null, geladen: boolean } | null = null;

  /** Kebab-Einträge je Zeile, einmal je Laden berechnet statt bei jeder Change Detection. */
  private menues = new Map<MesspunktEmpfang, KebabMenuItem[]>();

  constructor(
    private messpunktEmpfangService: MesspunktEmpfangService,
    private translationService: TranslationService,
    private router: Router
  ) { super(); }

  ngOnInit(): void {
    this.loadMesspunktEmpfang();
  }

  loadMesspunktEmpfang(): void {
    // Nach dem Neuladen kann die offene Meldung veraltet sein (FR-2.9)
    this.offeneMeldung = null;
    this.messpunktEmpfangService.getAllMesspunktEmpfang().subscribe({
      next: (data) => {
        this.zeilen = data;
        this.menues = new Map(data.map(zeile => [zeile, this.baueMenue(zeile)]));
        this.geladen = true;
        this.applySorting();
      },
      error: () => {
        this.geladen = true;
        this.showMessage('FEHLER_BEIM_LADEN_DER_DATEN', 'error');
      }
    });
  }

  onDelete(zeile: MesspunktEmpfang): void {
    if (zeile.id == null) return;

    if (confirm(this.translationService.translate('MESSPUNKT_EMPFANG_LOESCHEN_BESTAETIGEN'))) {
      this.messpunktEmpfangService.deleteMesspunktEmpfang(zeile.id).subscribe({
        next: () => {
          this.showMessage('MESSPUNKT_EMPFANG_GELOESCHT', 'success');
          this.loadMesspunktEmpfang();
        },
        error: (error) => {
          this.showMessage(error.error?.error || 'MESSPUNKT_EMPFANG_FEHLER_LOESCHEN', 'error');
        }
      });
    }
  }

  /**
   * Lädt die letzte Meldung und zeigt sie unter der Zeile (FR-2.9). Eine bereits offene Meldung
   * wird ersetzt.
   */
  onLetzteMeldung(zeile: MesspunktEmpfang): void {
    if (zeile.id == null) return;

    this.offeneMeldung = { messpunkt: zeile.messpunkt, text: null, geladen: false };
    this.messpunktEmpfangService.getLetzteMeldung(zeile.id).subscribe({
      next: (meldung) => {
        // Inzwischen eine andere Zeile gewaehlt oder geschlossen: Antwort verwerfen
        if (this.offeneMeldung?.messpunkt !== zeile.messpunkt) return;
        this.offeneMeldung = { messpunkt: zeile.messpunkt, text: formatierePayload(meldung.payload), geladen: true };
      },
      error: () => {
        this.offeneMeldung = null;
        this.showMessage('FEHLER_BEIM_LADEN_DER_DATEN', 'error');
      }
    });
  }

  onMeldungSchliessen(): void {
    this.offeneMeldung = null;
  }

  /** Öffnet die Einheitenverwaltung mit vorausgefülltem Messpunkt (Einheitenverwaltung FR-8). */
  onEinheitAnlegen(zeile: MesspunktEmpfang): void {
    this.router.navigate(['/einheiten'], { queryParams: { neu: 1, messpunkt: zeile.messpunkt } });
  }

  onMenuAction(action: string, zeile: MesspunktEmpfang): void {
    switch (action) {
      case 'letzte-meldung':
        this.onLetzteMeldung(zeile);
        break;
      case 'einheit-anlegen':
        this.onEinheitAnlegen(zeile);
        break;
      case 'delete':
        this.onDelete(zeile);
        break;
    }
  }

  onSort(column: SortSpalte): void {
    if (this.sortColumn === column) {
      this.sortDirection = this.sortDirection === 'asc' ? 'desc' : 'asc';
    } else {
      this.sortColumn = column;
      this.sortDirection = 'asc';
    }
    this.applySorting();
  }

  menuItemsFuer(zeile: MesspunktEmpfang): KebabMenuItem[] {
    return this.menues.get(zeile) ?? [];
  }

  einheitenText(zeile: MesspunktEmpfang): string {
    return zeile.einheiten.length > 0 ? zeile.einheiten.join(', ') : '–';
  }

  quelleText(zeile: MesspunktEmpfang): string {
    return zeile.quelle == null ? '–'
      : this.translationService.translate('MESSPUNKT_EMPFANG_QUELLE_' + zeile.quelle);
  }

  stand(wert: number | null): string {
    return wert == null ? '–' : formatSwissNumber(wert, 3);
  }

  zeitpunkt(wert: string | null, mitSekunden: boolean): string {
    return formatSwissDateTime(wert, '–', mitSekunden);
  }

  ergebnisKey(zeile: MesspunktEmpfang): string {
    return zeile.keinEmpfang ? 'MESSPUNKT_EMPFANG_KEIN_EMPFANG' : 'MESSPUNKT_EMPFANG_' + zeile.ergebnis;
  }

  ergebnisFarbe(zeile: MesspunktEmpfang): BadgeFarbe {
    return ergebnisFarbe(zeile);
  }

  altersFarbe(zeile: MesspunktEmpfang): BadgeFarbe {
    return altersFarbe(zeile.sekundenSeitEmpfang ?? 0);
  }

  alter(zeile: MesspunktEmpfang): string {
    const { key, wert } = altersText(zeile.sekundenSeitEmpfang ?? 0);
    return this.translationService.translate(key).replace('{0}', String(wert));
  }

  /**
   * Einträge je Zeile (FR-2.8): **Letzte Meldung anzeigen** wo es eine Empfangszeile gibt, für
   * jeden; mit `einheit:write` zusätzlich **Einheit anlegen** (nur ohne Einheit und mit höchstens
   * 50 Zeichen — sonst schnitte das Formular den Messpunkt still ab) und **Löschen**.
   */
  private baueMenue(zeile: MesspunktEmpfang): KebabMenuItem[] {
    const items: KebabMenuItem[] = [];
    if (zeile.id != null) {
      items.push({ label: 'MESSPUNKT_EMPFANG_LETZTE_MELDUNG', action: 'letzte-meldung', icon: 'eye' });
    }
    if (!this.canWrite) {
      return items;
    }
    if (zeile.einheiten.length === 0 && zeile.messpunkt.length <= MAX_MESSPUNKT_LAENGE) {
      items.push({ label: 'MESSPUNKT_EMPFANG_EINHEIT_ANLEGEN', action: 'einheit-anlegen', icon: 'plus' });
    }
    if (zeile.id != null) {
      items.push({ label: 'LOESCHEN', action: 'delete', danger: true, icon: 'trash-2' });
    }
    return items;
  }

  private applySorting(): void {
    const column = this.sortColumn;
    if (!column) {
      this.zeilen.sort((a, b) => gruppe(a) - gruppe(b)
        // Kein Empfang alphabetisch, die übrigen nach letztem Empfang absteigend
        || (a.keinEmpfang ? a.messpunkt.localeCompare(b.messpunkt)
          : (a.sekundenSeitEmpfang ?? 0) - (b.sekundenSeitEmpfang ?? 0)));
      return;
    }

    this.zeilen.sort((a, b) => {
      const aValue = this.sortWert(a, column);
      const bValue = this.sortWert(b, column);

      // Leere Werte ans Ende - `== null` faengt auch das null, das das Backend schickt
      if (aValue == null || aValue === '') return 1;
      if (bValue == null || bValue === '') return -1;

      if (aValue < bValue) {
        return this.sortDirection === 'asc' ? -1 : 1;
      }
      if (aValue > bValue) {
        return this.sortDirection === 'asc' ? 1 : -1;
      }
      return 0;
    });
  }

  private sortWert(zeile: MesspunktEmpfang, column: SortSpalte): string | number | null {
    switch (column) {
      case 'einheiten':
        return zeile.einheiten.join(', ').toLowerCase();
      case 'quelle':
        return zeile.quelle;
      case 'ergebnis':
        return zeile.keinEmpfang ? 'KEIN_EMPFANG' : zeile.ergebnis;
      case 'messpunkt':
        return zeile.messpunkt.toLowerCase();
      default:
        return zeile[column];
    }
  }
}
