import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { WithMessage } from '../../utils/with-message';
import { StatistikService } from '../../services/statistik.service';
import { Statistik, MonatsStatistik, TagMitAbweichung, EinheitSummen } from '../../models/statistik.model';
import { TranslatePipe } from '../../pipes/translate.pipe';
import { EinheitTypPipe } from '../../pipes/einheit-typ.pipe';
import { SwissDatePipe } from '../../pipes/swiss-date.pipe';
import { formatSwissNumber } from '../../utils/number-utils';
import { TranslationService } from '../../services/translation.service';
import { QuarterSelectorComponent } from '../quarter-selector/quarter-selector.component';
import { IconComponent } from '../icon/icon.component';

/** Eine Kennzahl-Zeile für die tabellarische Darstellung (Bezeichnung | Wert | Einheit). */
export interface KennzahlZeile {
  labelKey: string;
  hintKey: string;
  value: string;
  unit: string;
  /** Geschätzter Wert (Residuum der Energiebilanz) - wird als solcher gekennzeichnet. */
  berechnet: boolean;
  /** Der zugrunde liegende Messwert hat Lücken - der Wert ist verzerrt. */
  luecke?: boolean;
  /** Erklärt die Verzerrung; die beiden Seiten kippen in **entgegengesetzte** Richtungen. */
  lueckeHintKey?: string;
}

@Component({
  selector: 'app-statistik',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe, EinheitTypPipe, SwissDatePipe, QuarterSelectorComponent, IconComponent],
  templateUrl: './statistik.component.html',
  styleUrls: ['./statistik.component.css']
})
export class StatistikComponent extends WithMessage implements OnInit {
  dateFrom: string = '';
  dateTo: string = '';
  loading = false;


  statistik: Statistik | null = null;
  expandedMonths: Set<number> = new Set();
  /**
   * Aufgeklappte Monats-Panels. Ein Quartal liefert drei Monate, jeder mit Balken-Tabelle,
   * Kennzahlen, Vergleichen und Summen pro Einheit - aufgeklappt sind das mehrere
   * Bildschirmseiten. Deshalb starten alle zugeklappt; die Kopfzeile zeigt Monat, Zeitraum und
   * Datenstatus, damit die geschlossene Ansicht trotzdem etwas aussagt.
   */
  expandedMonthPanels: Set<number> = new Set();

  /**
   * Schlüssel des Gesamt-Panels in {@link expandedMonthPanels} und {@link expandedMonths}.
   *
   * <p>Monate tragen ihren Index ab 0; das Gesamt-Panel teilt sich dieselben Mengen mit −1 und
   * braucht so keine eigene Zustandslogik.
   */
  readonly GESAMT = -1;
  expandedGlobalDetails = false;

  /** Aktiver Verteilmodus ist BILANZ (Modus-abhängige Anzeige/Hinweise). */
  get isBilanz(): boolean {
    return this.statistik?.verteilmodus === 'BILANZ';
  }

  constructor(
    private statistikService: StatistikService,
    private translationService: TranslationService
  ) { super(); }

  ngOnInit(): void {
    this.setDefaultDateRange();
  }

  /**
   * Belegt den Zeitraum mit dem vorangehenden Quartal vor
   * (im Q1 wird Q4 des Vorjahres gesetzt).
   */
  private setDefaultDateRange(): void {
    const now = new Date();
    let year = now.getFullYear();
    let quarter = Math.ceil((now.getMonth() + 1) / 3) - 1;
    if (quarter < 1) {
      quarter = 4;
      year--;
    }
    const startMonth = (quarter - 1) * 3;
    this.dateFrom = this.formatDate(new Date(year, startMonth, 1));
    this.dateTo = this.formatDate(new Date(year, startMonth + 3, 0));
  }

  private formatDate(date: Date): string {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  onDateFromChange(): void {
    if (this.dateFrom) {
      const date = new Date(this.dateFrom);
      const lastDay = new Date(date.getFullYear(), date.getMonth() + 1, 0);
      this.dateTo = this.formatDate(lastDay);
    }
  }

  onQuarterSelected(event: {von: string, bis: string}): void {
    this.dateFrom = event.von;
    this.dateTo = event.bis;
  }

  /**
   * Setzt den Zeitraum auf heute–heute (Specs/Statistik.md, FR „Heute").
   *
   * <p><b>Lädt nicht</b> — wie der Quartal-Selektor. Ausgelöst wird weiterhin mit „Anzeigen",
   * damit sich alle Wege, einen Zeitraum zu wählen, gleich verhalten.
   *
   * <p>Über {@link formatDate} statt {@code toISOString()}: Letzteres liefert das UTC-Datum, und
   * kurz nach Mitternacht stünde dann der Vortag im Feld.
   */
  onHeute(): void {
    const heute = this.formatDate(new Date());
    this.dateFrom = heute;
    this.dateTo = heute;
  }

  /** Ist der Zeitraum bereits heute–heute? Dann wäre „Heute" folgenlos und ist deaktiviert. */
  get zeigtHeute(): boolean {
    const heute = this.formatDate(new Date());
    return this.dateFrom === heute && this.dateTo === heute;
  }

  onSubmit(): void {
    if (!this.dateFrom || !this.dateTo) {
      this.showMessage(this.translationService.translate('BITTE_ALLE_FELDER_AUSFUELLEN'), 'error');
      return;
    }

    if (this.dateFrom > this.dateTo) {
      this.showMessage(this.translationService.translate('START_DATUM_MUSS_VOR_END_DATUM_LIEGEN'), 'error');
      return;
    }

    this.loading = true;
    this.statistik = null;
    this.expandedMonths.clear();
    this.expandedMonthPanels.clear();
    // Das Gesamt-Panel startet AUFGEKLAPPT, die Monate zugeklappt: Die Summe über den Zeitraum ist
    // meist das, wonach man sucht - die Monate sind die Aufschlüsselung dazu.
    this.expandedMonthPanels.add(this.GESAMT);

    this.statistikService.getStatistik(this.dateFrom, this.dateTo).subscribe({
      next: (data) => {
        this.statistik = data;
        this.loading = false;
        this.showMessage(
          `${data.monate.length} ${this.translationService.translate('MONATE_GELADEN')}`,
          'success'
        );
      },
      error: (error) => {
        this.showMessage(
          `${this.translationService.translate('FEHLER_BEIM_LADEN_DER_DATEN')}: ${error.message}`,
          'error'
        );
        this.loading = false;
      }
    });
  }

  exportPdf(): void {
    if (!this.dateFrom || !this.dateTo) {
      this.showMessage(this.translationService.translate('BITTE_ZUERST_STATISTIK_LADEN'), 'error');
      return;
    }

    const sprache = this.translationService.getCurrentLanguage();
    this.statistikService.exportPdf(this.dateFrom, this.dateTo, sprache).subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `statistik_${this.dateFrom}_${this.dateTo}.pdf`;
        link.click();
        window.URL.revokeObjectURL(url);
      },
      error: (error) => {
        this.showMessage(
          `${this.translationService.translate('FEHLER_BEIM_EXPORT')}: ${error.message}`,
          'error'
        );
      }
    });
  }

  /**
   * CSV-Download der 15-Min-Werte einer Consumer-Einheit für den Monat. Der Dateiname wird
   * benutzerfreundlich aus Einheiten-Name + Monat gebildet (bereinigt).
   */
  /**
   * Zeitraum für den CSV-Dateinamen: der Monat (`2024-02`), wenn `von` und `bis` darin liegen,
   * sonst `von_bis`.
   *
   * <p>Das Gesamt-Panel verwendet dieselbe Download-Schaltfläche wie ein Monat. Mit dem Monat von
   * `von` allein hiesse ein Quartalsexport `…_2024-07.csv`, als enthielte er nur den Juli.
   */
  private zeitraumImDateinamen(monat: MonatsStatistik): string {
    const vonMonat = monat.von.substring(0, 7);
    return vonMonat === monat.bis.substring(0, 7) ? vonMonat : `${monat.von}_${monat.bis}`;
  }

  onDownloadCsv(monat: MonatsStatistik, einheit: EinheitSummen): void {
    const sprache = this.translationService.getCurrentLanguage();
    this.statistikService.exportCsv(einheit.einheitId, monat.von, monat.bis, sprache).subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        const name = einheit.einheitName.replace(/[^A-Za-z0-9._-]/g, '_');
        link.download = `verbrauch_${name}_${this.zeitraumImDateinamen(monat)}.csv`;
        link.click();
        window.URL.revokeObjectURL(url);
      },
      error: () => this.showMessage(this.translationService.translate('EXPORT_CSV_FEHLER'), 'error')
    });
  }

  toggleMonthPanel(index: number): void {
    if (this.expandedMonthPanels.has(index)) {
      this.expandedMonthPanels.delete(index);
    } else {
      this.expandedMonthPanels.add(index);
    }
  }

  isMonthPanelExpanded(index: number): boolean {
    return this.expandedMonthPanels.has(index);
  }

  toggleMonthDetails(index: number): void {
    if (this.expandedMonths.has(index)) {
      this.expandedMonths.delete(index);
    } else {
      this.expandedMonths.add(index);
    }
  }

  isMonthExpanded(index: number): boolean {
    return this.expandedMonths.has(index);
  }

  toggleGlobalDetails(): void {
    this.expandedGlobalDetails = !this.expandedGlobalDetails;
  }

  getMonthName(monat: number): string {
    const monthNames = [
      'JANUAR', 'FEBRUAR', 'MAERZ', 'APRIL', 'MAI', 'JUNI',
      'JULI', 'AUGUST', 'SEPTEMBER', 'OKTOBER', 'NOVEMBER', 'DEZEMBER'
    ];
    return this.translationService.translate(monthNames[monat - 1]);
  }

  getStatusClass(vollstaendig: boolean): string {
    return vollstaendig ? 'zev-status-dot--success' : 'zev-status-dot--error';
  }

  getComparisonStatusClass(isEqual: boolean): string {
    return isEqual ? 'zev-status-dot--success' : 'zev-status-dot--error';
  }

  /**
   * Zahl im Schweizer Format: Punkt als Dezimal-, Hochkomma (') als Tausendertrennzeichen,
   * locale-unabhängig. Delegiert an den geteilten Helfer in `utils/number-utils`.
   */
  private formatSwissNumber(value: number, decimals = 3): string {
    return formatSwissNumber(value, decimals);
  }

  formatNumber(value: number | null | undefined): string {
    if (value === null || value === undefined) {
      return '-';
    }
    return this.formatSwissNumber(value);
  }

  /** Bilanz-Typen (Netzanschluss): nur `total` ist fachlich relevant, zev/zev_berechnet nicht. */
  isBilanzTyp(einheitTyp: string): boolean {
    return einheitTyp === 'BEZUG' || einheitTyp === 'RUECKLIEFERUNG';
  }

  formatDifferenz(value: number | null | undefined): string {
    if (value === null || value === undefined) {
      return '-';
    }
    const prefix = value >= 0 ? '+' : '';
    return `${prefix}${this.formatSwissNumber(value)}`;
  }

  /**
   * Baut die Kennzahlen-Zeilen eines Monats für die tabellarische Darstellung
   * (Bezeichnung | Wert rechtsbündig | Einheit linksbündig). Batterie-Kennzahlen nur, wenn
   * die dafür nötigen Bilanz-Daten vorhanden sind (`batterieKennzahlenVerfuegbar`).
   *
   * <p>Die **gemessenen** Werte stehen direkt hinter ihren gerechneten Gegenstücken, damit die
   * Differenz beim Lesen auffällt: Sie ist der Verbrauchsanteil, der weder direkt aus der PV noch
   * aus dem Netz kam - typischerweise die Batterie-Entladung.
   */
  getKennzahlen(monat: MonatsStatistik): KennzahlZeile[] {
    const zeilen: KennzahlZeile[] = [
      this.gerechneteZeile('KENNZAHL_AUTARKIEGRAD', monat.autarkiegrad, monat.verteilungLueckenhaft)
    ];
    if (monat.bilanzKennzahlenVerfuegbar) {
      zeilen.push(this.gemesseneZeile('KENNZAHL_AUTARKIEGRAD_GEMESSEN',
        monat.autarkiegradGemessen, monat.bilanzBezugLueckenhaft));
    }
    zeilen.push(
      this.percentZeile('KENNZAHL_EIGENVERBRAUCHSQUOTE', monat.eigenverbrauchsquote, false),
      this.gerechneteZeile('KENNZAHL_NETZBEZUGSQUOTE', monat.netzbezugsquote,
        monat.verteilungLueckenhaft)
    );
    if (monat.bilanzKennzahlenVerfuegbar) {
      zeilen.push(this.gemesseneZeile('KENNZAHL_NETZBEZUGSQUOTE_GEMESSEN',
        monat.netzbezugsquoteGemessen, monat.bilanzBezugLueckenhaft));
    }
    zeilen.push(
      this.percentZeile('KENNZAHL_EINSPEISEQUOTE', monat.einspeisequote, false),
      this.kwhZeile('KENNZAHL_ZEV_EIGENVERBRAUCH', monat.zevEigenverbrauch, false)
    );
    if (monat.batterieKennzahlenVerfuegbar) {
      // Stammen die Werte aus der Speicher-Einheit, sind sie gemessen und NICHT berechnet - die
      // Kennzeichnung entfaellt. Sie waere dort schlicht falsch: Ein Zaehlerstand ist kein
      // Residuum der Bilanz.
      const berechnet = !monat.batterieGemessen;
      zeilen.push(
        this.signedKwhZeile('KENNZAHL_BATTERIE_NETTO', monat.batterieNetto, berechnet),
        this.kwhZeile('KENNZAHL_BATTERIE_GELADEN', monat.batterieGeladen, berechnet),
        this.kwhZeile('KENNZAHL_BATTERIE_ENTLADEN', monat.batterieEntladen, berechnet),
        this.percentZeile('KENNZAHL_BATTERIE_WIRKUNGSGRAD', monat.batterieWirkungsgrad, berechnet)
      );
    }
    return zeilen;
  }

  /**
   * Aus dem gemessenen Netzbezug abgeleitete Quote. Fehlen Intervalle, fehlt deren Netzbezug in
   * der Summe - der Wert faellt **zu hoch** aus (bzw. die Netzbezugsquote zu tief).
   */
  private gemesseneZeile(labelKey: string, value: number | null, luecke: boolean): KennzahlZeile {
    return {
      ...this.percentZeile(labelKey, value, false),
      luecke,
      lueckeHintKey: 'KENNZAHL_LUECKE_MESSUNG_HINWEIS'
    };
  }

  /**
   * Aus dem ZEV-Anteil der Konsumenten gerechnete Quote. Im Bilanzmodus stammt dieser Anteil
   * ebenfalls aus den Bilanzdaten: Ein uebersprungenes Intervall laesst die Konsumenten ohne
   * `zev` zurueck, ihr Verbrauch zaehlt aber weiter - er schlaegt **voll** als Netzbezug zu Buche.
   * Der Wert faellt damit **zu tief** aus, und zwar staerker als der gemessene zu hoch.
   */
  private gerechneteZeile(labelKey: string, value: number | null, luecke: boolean): KennzahlZeile {
    return {
      ...this.percentZeile(labelKey, value, false),
      luecke,
      lueckeHintKey: 'KENNZAHL_LUECKE_VERTEILUNG_HINWEIS'
    };
  }

  private percentZeile(labelKey: string, value: number | null, berechnet: boolean): KennzahlZeile {
    return {
      labelKey,
      hintKey: labelKey + '_HINWEIS',
      value: value === null || value === undefined ? '–' : this.formatSwissNumber(value * 100, 1),
      unit: value === null || value === undefined ? '' : '%',
      berechnet
    };
  }

  private kwhZeile(labelKey: string, value: number | null, berechnet: boolean): KennzahlZeile {
    return {
      labelKey,
      hintKey: labelKey + '_HINWEIS',
      value: value === null || value === undefined ? '–' : this.formatSwissNumber(value),
      unit: value === null || value === undefined ? '' : 'kWh',
      berechnet
    };
  }

  private signedKwhZeile(labelKey: string, value: number | null, berechnet: boolean): KennzahlZeile {
    return {
      labelKey,
      hintKey: labelKey + '_HINWEIS',
      value: value === null || value === undefined
        ? '–'
        : (value >= 0 ? '+' : '') + this.formatSwissNumber(value),
      unit: value === null || value === undefined ? '' : 'kWh',
      berechnet
    };
  }

  hasAbweichungen(monat: MonatsStatistik): boolean {
    return !monat.summenCDGleich || !monat.summenCEGleich || !monat.summenDEGleich;
  }

  getBarWidth(value: number, monat: MonatsStatistik): number {
    const maxValue = Math.max(
      monat.summeProducerTotal || 0,
      monat.summeConsumerTotal || 0,
      monat.summeProducerZev || 0,
      monat.summeConsumerZev || 0,
      monat.summeConsumerZevCalculated || 0,
      monat.bilanzBezug || 0,
      monat.bilanzRuecklieferung || 0
    );
    if (maxValue === 0) return 0;
    return (value / maxValue) * 100;
  }

  getBarColor(type: 'A' | 'B' | 'C' | 'D' | 'E' | 'F' | 'G' | 'H' | 'I'): string {
    const colors: Record<string, string> = {
      'A': '#4CAF50',  // Producer Total - Grün
      'B': '#2196F3',  // Consumer Total - Blau
      'C': '#FF9800',  // Producer ZEV - Orange
      'D': '#9C27B0',  // Consumer ZEV - Lila
      'E': '#00BCD4',  // Consumer ZEV Calculated - Cyan
      'F': '#F44336',  // Bezug von VNB - Rot
      'G': '#8BC34A',  // Rücklieferung - Hellgrün
      'H': '#E91E63',  // Bilanz-Einheit Bezug - Pink
      'I': '#CDDC39'   // Bilanz-Einheit Rücklieferung - Lime
    };
    return colors[type] || '#999';
  }

}
