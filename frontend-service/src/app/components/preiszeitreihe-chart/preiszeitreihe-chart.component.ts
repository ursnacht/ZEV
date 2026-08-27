import { AfterViewInit, Component, ElementRef, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { PreiszeitreiheService } from '../../services/preiszeitreihe.service';
import { Darstellung, PreiszeitreihePunkt, Spanne } from '../../models/preiszeitreihe.model';
import { TranslationService } from '../../services/translation.service';
import { TranslatePipe } from '../../pipes/translate.pipe';
import { IconComponent } from '../icon/icon.component';
import { WithMessage } from '../../utils/with-message';
import { formatSwissNumber } from '../../utils/number-utils';

/**
 * Diagramm der dynamischen Einspeisepreise (Specs/Preiszeitreihe.md, FR-3).
 *
 * <p>Sitzt am Ende der Tarifseite und wird dort hinter dem Feature-Flag `PREISZEITREIHE`
 * eingebunden. Die Auswahl der Spanne (TAG/WOCHE/MONAT oder Datum von/bis) lädt neu; Zoomen und
 * Verschieben innerhalb der geladenen Spanne übernimmt ECharts ohne weiteren Server-Aufruf.
 */
@Component({
  selector: 'app-preiszeitreihe-chart',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe, IconComponent],
  templateUrl: './preiszeitreihe-chart.component.html',
  styleUrls: ['./preiszeitreihe-chart.component.css']
})
export class PreiszeitreiheChartComponent extends WithMessage
    implements OnInit, AfterViewInit, OnDestroy {

  @ViewChild('diagramm') diagrammRef?: ElementRef<HTMLDivElement>;

  spanne: Spanne = 'TAG';
  /** Linie oder Balken. Gilt für die Sitzung; die Reihe selbst ändert sich dadurch nicht. */
  darstellung: Darstellung = 'LINIE';
  von = '';
  bis = '';
  punkte: PreiszeitreihePunkt[] = [];
  laedt = false;
  laedtHerunter = false;
  /** Die Bibliothek liess sich nicht nachladen - das Diagramm bleibt aus, die Seite bedienbar. */
  bibliothekFehlt = false;

  /**
   * ECharts wird **dynamisch** nachgeladen, nicht am Dateikopf importiert: `/tarife` ist eine eager
   * Route, ein statischer Import landete im Initial-Bundle und jede Seite der Anwendung lüde die
   * Bibliothek mit (Specs/Preiszeitreihe.md, NFR-1).
   */
  private echarts?: typeof import('echarts/core');
  private instanz?: import('echarts/core').ECharts;
  private resizeObserver?: ResizeObserver;
  private ansichtBereit = false;

  constructor(private preiszeitreiheService: PreiszeitreiheService,
              private translationService: TranslationService) {
    super();
  }

  ngOnInit(): void {
    this.setzeSpanne('TAG');
  }

  ngAfterViewInit(): void {
    this.ansichtBereit = true;
    void this.zeichne();
  }

  ngOnDestroy(): void {
    this.resizeObserver?.disconnect();
    this.instanz?.dispose();
  }

  /** Setzt die Spanne auf Tag/Woche/Monat um den heutigen Tag und lädt neu. */
  setzeSpanne(spanne: Spanne): void {
    const heute = new Date();
    this.spanne = spanne;
    if (spanne === 'TAG') {
      this.von = this.alsIso(heute);
      this.bis = this.von;
    } else if (spanne === 'WOCHE') {
      const montag = new Date(heute);
      // getDay(): 0 = Sonntag. Die Woche beginnt am Montag, deshalb der Sonntag-Sonderfall.
      const versatz = (heute.getDay() + 6) % 7;
      montag.setDate(heute.getDate() - versatz);
      this.von = this.alsIso(montag);
      this.bis = this.alsIso(this.plusTage(montag, 6));
    } else if (spanne === 'MONAT') {
      this.von = this.alsIso(new Date(heute.getFullYear(), heute.getMonth(), 1));
      this.bis = this.alsIso(new Date(heute.getFullYear(), heute.getMonth() + 1, 0));
    }
    this.lade();
  }

  /**
   * Verschiebt die Spanne um genau ihre eigene Länge.
   *
   * @param richtung -1 = zurück, 1 = vor
   */
  blaettere(richtung: -1 | 1): void {
    const von = this.ausIso(this.von);
    const bis = this.ausIso(this.bis);
    if (!von || !bis) {
      return;
    }
    if (this.spanne === 'MONAT') {
      const ziel = new Date(von.getFullYear(), von.getMonth() + richtung, 1);
      this.von = this.alsIso(ziel);
      this.bis = this.alsIso(new Date(ziel.getFullYear(), ziel.getMonth() + 1, 0));
    } else {
      const tage = Math.round((bis.getTime() - von.getTime()) / 86400000) + 1;
      this.von = this.alsIso(this.plusTage(von, richtung * tage));
      this.bis = this.alsIso(this.plusTage(bis, richtung * tage));
    }
    this.lade();
  }

  /**
   * Wechselt zwischen Stufenlinie und Balken.
   *
   * Zeichnet nur neu - **kein** Server-Aufruf: Die Daten liegen schon vor, es ändert sich allein
   * ihre Darstellung.
   */
  setzeDarstellung(darstellung: Darstellung): void {
    if (this.darstellung === darstellung) {
      return;
    }
    this.darstellung = darstellung;
    void this.zeichne();
  }

  /** Eine Eingabe in den Datumsfeldern hebt die Spannen-Auswahl auf. */
  onDatumChange(): void {
    this.spanne = 'FREI';
    this.lade();
  }

  /** Lädt die Werte der aktuellen Spanne. */
  lade(): void {
    if (!this.von || !this.bis) {
      return;
    }
    if (this.von > this.bis) {
      this.showMessage(this.translationService.translate('PREISE_ZEITRAUM_VERTAUSCHT'), 'error');
      return;
    }
    this.laedt = true;
    this.preiszeitreiheService.getPunkte(this.von, this.bis).subscribe({
      next: (punkte) => {
        this.punkte = punkte;
        this.laedt = false;
        void this.zeichne();
      },
      error: (error) => {
        this.punkte = [];
        this.laedt = false;
        this.showMessage(error.error || this.translationService.translate('FEHLER_BEIM_LADEN_DER_DATEN'), 'error');
      }
    });
  }

  /** Holt die Preise jetzt bei der Quelle und lädt die Ansicht neu. */
  onHerunterladen(): void {
    this.laedtHerunter = true;
    this.preiszeitreiheService.download().subscribe({
      next: (ergebnis) => {
        this.laedtHerunter = false;
        const stand = ergebnis.publikation != null
          ? this.formatiereZeit(new Date(ergebnis.publikation))
          : '–';
        this.showMessage(
          this.translationService.translate('PREISE_HERUNTERGELADEN')
            .replace('{0}', String(ergebnis.neu))
            .replace('{1}', String(ergebnis.aktualisiert))
            .replace('{2}', stand),
          'success');
        this.lade();
      },
      error: (error) => {
        this.laedtHerunter = false;
        this.showMessage(
          error.error || this.translationService.translate('PREISE_ABRUF_FEHLGESCHLAGEN'), 'error');
      }
    });
  }

  /** Zeichnet oder aktualisiert das Diagramm; lädt die Bibliothek beim ersten Mal nach. */
  private async zeichne(): Promise<void> {
    if (!this.ansichtBereit || this.punkte.length === 0) {
      return;
    }
    if (!await this.ladeBibliothek()) {
      return;
    }
    const behaelter = this.diagrammRef?.nativeElement;
    if (!behaelter || !this.echarts) {
      return;
    }
    if (!this.instanz) {
      this.instanz = this.echarts.init(behaelter);
      this.resizeObserver = new ResizeObserver(() => this.instanz?.resize());
      this.resizeObserver.observe(behaelter);
    }
    this.instanz.setOption(this.optionen(), true);
  }

  /** Lädt `echarts` beim ersten Zeichnen nach. `false`, wenn das misslingt. */
  private async ladeBibliothek(): Promise<boolean> {
    if (this.echarts) {
      return true;
    }
    if (this.bibliothekFehlt) {
      return false;
    }
    try {
      const [core, charts, komponenten, renderer] = await Promise.all([
        import('echarts/core'),
        import('echarts/charts'),
        import('echarts/components'),
        import('echarts/renderers')
      ]);
      core.use([
        // Beide Diagrammtypen: Ohne BarChart zeichnet `type: 'bar'` stillschweigend NICHTS -
        // ECharts meldet einen nicht registrierten Typ nicht als Fehler, die Flaeche bleibt leer.
        charts.LineChart,
        charts.BarChart,
        komponenten.GridComponent,
        komponenten.TooltipComponent,
        komponenten.DataZoomComponent,
        renderer.CanvasRenderer
      ]);
      this.echarts = core;
      return true;
    } catch {
      this.bibliothekFehlt = true;
      this.showMessage(this.translationService.translate('DIAGRAMM_NICHT_LADBAR'), 'error');
      return false;
    }
  }

  /**
   * Diagramm-Optionen.
   *
   * Stufenlinie mit Absicht: Ein Preis gilt für die **ganze** Viertelstunde. Eine interpolierte
   * Linie behauptete einen stetigen Verlauf, den es nicht gibt.
   */
  private optionen(): Record<string, unknown> {
    const farben = this.farben();
    const daten = this.punkte.map(p => [new Date(p.zeit).getTime(), p.preis]);
    return {
      animation: false,
      grid: { left: 60, right: 20, top: 20, bottom: 70 },
      tooltip: {
        trigger: 'axis',
        formatter: (params: { value: [number, number] }[]) => {
          const [zeit, preis] = params[0].value;
          return `${this.formatiereZeit(new Date(zeit))}<br>`
            + `${this.translationService.translate('PREIS_CHF_KWH')}: `
            + `${formatSwissNumber(preis, 5)}`;
        }
      },
      xAxis: {
        type: 'time',
        axisLine: { lineStyle: { color: farben.achse } },
        axisLabel: { color: farben.text }
      },
      yAxis: {
        type: 'value',
        name: this.translationService.translate('PREIS_CHF_KWH'),
        nameTextStyle: { color: farben.text },
        axisLine: { lineStyle: { color: farben.achse } },
        axisLabel: {
          color: farben.text,
          formatter: (wert: number) => formatSwissNumber(wert, 3)
        },
        splitLine: { lineStyle: { color: farben.gitter } }
      },
      dataZoom: [{ type: 'inside' }, { type: 'slider', bottom: 10 }],
      series: [this.serie(daten, farben)]
    };
  }

  /**
   * Die Datenserie in der gewählten Darstellung.
   *
   * **Linie:** Stufenlinie (`step: 'end'`) - ein Preis gilt für die **ganze** Viertelstunde, eine
   * interpolierte Linie behauptete einen stetigen Verlauf, den es nicht gibt.
   *
   * **Balken:** je Intervall ein Balken, mit `barMaxWidth` begrenzt. Über einen Monat liegen bis zu
   * 2'976 Balken nebeneinander; ohne Begrenzung würde ECharts sie in einer schmalen Ansicht zu
   * einer Fläche verschmelzen.
   */
  private serie(daten: number[][],
                farben: { linie: string }): Record<string, unknown> {
    if (this.darstellung === 'BALKEN') {
      return {
        type: 'bar',
        barMaxWidth: 24,
        itemStyle: { color: farben.linie },
        data: daten
      };
    }
    // Reine Linie, **keine** Flaechenfuellung: Mit `areaStyle` sah die Darstellung wie ein
    // Flaechendiagramm aus, und die gefuellte Flaeche suggeriert eine Summe ueber die Zeit -
    // aufsummierte Preise sind aber sinnlos.
    return {
      type: 'line',
      step: 'end',
      showSymbol: false,
      connectNulls: false,
      lineStyle: { color: farben.linie, width: 2 },
      data: daten
    };
  }

  /**
   * Farben aus den Design-Tokens statt hart kodiert - sonst wäre das Diagramm im Dark Mode
   * unlesbar (Specs/DarkMode.md).
   */
  private farben(): { achse: string; text: string; gitter: string; linie: string } {
    const stil = getComputedStyle(document.documentElement);
    const token = (name: string, fallback: string) =>
      stil.getPropertyValue(name).trim() || fallback;
    // Die Neutraltoene kippen im Dark Mode (gray-600 ist dort hell) - genau deshalb kommen die
    // Farben aus den Tokens und nicht aus festen Werten.
    return {
      achse: token('--color-gray-500', '#cccccc'),
      text: token('--color-gray-700', '#555555'),
      gitter: token('--color-gray-300', '#e0e0e0'),
      linie: token('--color-primary', '#4CAF50')
    };
  }

  /** `dd.MM.yyyy HH:mm` - bewusst manuell, nicht über `toLocaleString` (Specs/generell.md). */
  private formatiereZeit(datum: Date): string {
    const zwei = (wert: number) => String(wert).padStart(2, '0');
    return `${zwei(datum.getDate())}.${zwei(datum.getMonth() + 1)}.${datum.getFullYear()} `
      + `${zwei(datum.getHours())}:${zwei(datum.getMinutes())}`;
  }

  private alsIso(datum: Date): string {
    const zwei = (wert: number) => String(wert).padStart(2, '0');
    return `${datum.getFullYear()}-${zwei(datum.getMonth() + 1)}-${zwei(datum.getDate())}`;
  }

  private ausIso(iso: string): Date | null {
    const teile = iso.split('-').map(Number);
    if (teile.length !== 3 || teile.some(isNaN)) {
      return null;
    }
    return new Date(teile[0], teile[1] - 1, teile[2]);
  }

  private plusTage(datum: Date, tage: number): Date {
    const ergebnis = new Date(datum);
    ergebnis.setDate(datum.getDate() + tage);
    return ergebnis;
  }
}
