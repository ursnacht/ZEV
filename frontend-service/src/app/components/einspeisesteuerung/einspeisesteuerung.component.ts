import { AfterViewInit, Component, ElementRef, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { EinspeisesteuerungService } from '../../services/einspeisesteuerung.service';
import {
  STEUERREGELN,
  STEUERREGEL_KEYS,
  Simulation,
  Steuerentscheid,
  Steuerregel
} from '../../models/einspeisesteuerung.model';
import { TranslatePipe } from '../../pipes/translate.pipe';
import { TranslationService } from '../../services/translation.service';
import { IconComponent } from '../icon/icon.component';
import { WithMessage } from '../../utils/with-message';
import { chartFarben } from '../../utils/chart-farben';
import { ladeECharts } from '../../utils/echarts-loader';
import { formatSwissDateTime } from '../../utils/date-utils';
import { formatSwissNumber } from '../../utils/number-utils';

type EChartsCore = Awaited<ReturnType<typeof ladeECharts>>;

/** Länge eines Messintervalls in Millisekunden — dasselbe 15-Minuten-Raster wie im Backend. */
const INTERVALL_MS = 15 * 60 * 1000;

/**
 * Untere Grenze der Mengen-Achse, damit die beiden Zustandsbänder unterhalb der Nulllinie Platz
 * haben. Nötig, weil `markArea` die Skala — anders als eine Datenserie — **nicht** aufspannt:
 * Ohne diesen Wert endete die Achse bei 0 und die Bänder wären unsichtbar.
 */
const ACHSE_MIN_MIT_BAENDERN = -2.4;

/**
 * Die Einspeisesteuerung im Trockenlauf (Specs/Einspeisesteuerung.md, FR-5).
 *
 * <p><b>Sie schaltet nichts.</b> Die Seite zeigt, was die Steuerung tun *würde*: Preisverlauf,
 * Produktion und Verbrauch, darunter zwei Zustandsbänder, und als Protokoll die Tabelle mit den
 * Eingangsgrössen jedes Intervalls. Bei einer Steuerung ist das *Warum* wichtiger als das *Was* —
 * ohne die Eingangsgrössen stünde man vor dem Diagramm und rätselte, weshalb um 09:15 umgeschaltet
 * wurde.
 */
@Component({
  selector: 'app-einspeisesteuerung',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe, IconComponent],
  templateUrl: './einspeisesteuerung.component.html',
  styleUrls: ['./einspeisesteuerung.component.css']
})
export class EinspeisesteuerungComponent extends WithMessage
  implements OnInit, AfterViewInit, OnDestroy {

  private readonly steuerungService = inject(EinspeisesteuerungService);
  protected readonly translationService = inject(TranslationService);

  @ViewChild('diagramm') private diagrammRef?: ElementRef<HTMLDivElement>;

  /** Gewählter Tag in Ortszeit, ISO `yyyy-MM-dd`. */
  datum = this.heuteIso();

  entscheide: Steuerentscheid[] = [];
  loading = false;

  /** Schwellwert der Rückrechnung; vorbelegt mit dem des ersten Entscheids. */
  simulationSchwellwert: number | null = null;
  simulation: Simulation | null = null;
  simulationLaeuft = false;

  /**
   * Schwellwert, mit dem der **angezeigte Tag** nachgerechnet ist — `null` zeigt die Aufzeichnung.
   *
   * Bewusst der Wert und nicht nur ein Schalter: Er steht im Hinweis über dem Diagramm, damit
   * niemand eine Hypothese für das Protokoll hält. Und er bleibt beim Blättern erhalten, sonst
   * müsste man für jeden Tag neu rechnen.
   */
  simuliertMitSchwellwert: number | null = null;

  readonly regeln = STEUERREGELN;

  private echarts: EChartsCore = null;
  private instanz?: { setOption: (o: unknown, n?: boolean) => void; resize: () => void; dispose: () => void };
  private resizeObserver?: ResizeObserver;
  private bibliothekFehlt = false;

  ngOnInit(): void {
    this.ladeTag();
  }

  /**
   * Zeichnet, sobald der Behaelter im DOM steht.
   *
   * <p>Nötig, weil die Antwort des Servers schneller da sein kann als das erste Rendern: `zeichne()`
   * aus `ladeTag()` fände den Behälter dann nicht und bräche still ab.
   */
  ngAfterViewInit(): void {
    void this.zeichne();
  }

  ngOnDestroy(): void {
    this.resizeObserver?.disconnect();
    this.instanz?.dispose();
  }

  /**
   * Entscheide des gewählten Tages laden und zeichnen.
   *
   * <p>Ist ein Schwellwert nachgerechnet (`simuliertMitSchwellwert`), wird der Tag **gerechnet**
   * statt gelesen — auch beim Blättern. Sonst fiele die Ansicht bei jedem Tageswechsel
   * unbemerkt auf die Aufzeichnung zurück, und man vergliche Äpfel mit Birnen.
   */
  ladeTag(): void {
    this.loading = true;
    const quelle = this.simuliertMitSchwellwert != null
      ? this.steuerungService.getEntscheideSimuliert(this.datum, this.simuliertMitSchwellwert)
      : this.steuerungService.getEntscheide(this.datum);

    quelle.subscribe({
      next: (daten) => {
        this.entscheide = daten;
        this.loading = false;
        if (this.simulationSchwellwert == null && daten.length > 0) {
          this.simulationSchwellwert = daten[0].schwellwert;
        }
        void this.zeichne();
      },
      error: (error) => {
        this.loading = false;
        this.entscheide = [];
        this.showMessage(error.error || 'STEUERUNG_FEHLER_LADEN', 'error');
      }
    });
  }

  /**
   * Zurück zur Aufzeichnung — zeigt wieder die gespeicherten Entscheide.
   *
   * <p>Die Kennzahlen der Rückrechnung bleiben stehen: Sie sind das Ergebnis, an dem der Benutzer
   * gerade arbeitet, und beziehen sich auf die ganze Historie, nicht auf den angezeigten Tag.
   */
  zeigeAufzeichnung(): void {
    this.simuliertMitSchwellwert = null;
    this.ladeTag();
  }

  /** Einen Tag zurück. */
  vorherigerTag(): void {
    this.datum = this.verschiebe(-1);
    this.ladeTag();
  }

  /** Einen Tag vor. */
  naechsterTag(): void {
    this.datum = this.verschiebe(1);
    this.ladeTag();
  }

  /**
   * Rechnet die Regel über die vorhandene Historie mit dem eingegebenen Schwellwert nach.
   *
   * <p>Verändert **nichts** an den gespeicherten Entscheiden — das ist der Zweck: am Schwellwert
   * drehen und sehen, was gewesen wäre.
   */
  rechneNach(): void {
    if (this.simulationSchwellwert == null) {
      return;
    }
    this.simulationLaeuft = true;
    // Rueckwaerts bis ein Jahr vor dem gewaehlten Tag - die Historie ist ohnehin kuerzer, und die
    // Grenze des Servers liegt bei 366 Tagen.
    const bis = this.datum;
    const von = this.verschiebe(-365);
    const schwellwert = this.simulationSchwellwert;
    this.steuerungService.simuliere({ von, bis, schwellwert }).subscribe({
      next: (ergebnis) => {
        this.simulation = ergebnis;
        this.simulationLaeuft = false;
        // Den angezeigten Tag mit demselben Schwellwert nachrechnen: Die Kennzahlen sagen, WIE OFT
        // gesperrt worden waere - erst der Tagesverlauf zeigt, WANN. Ohne das blieben Diagramm und
        // Tabelle auf der Aufzeichnung stehen und widersprachen der Auswertung darunter.
        this.simuliertMitSchwellwert = schwellwert;
        this.ladeTag();
      },
      error: (error) => {
        this.simulationLaeuft = false;
        this.simulation = null;
        this.showMessage(error.error || 'STEUERUNG_FEHLER_SIMULATION', 'error');
      }
    });
  }

  /** Übersetzungs-Key einer Regel — die Tabelle zeigt Klartext, nicht den Schlüssel. */
  regelKey(regel: Steuerregel): string {
    return STEUERREGEL_KEYS[regel];
  }

  /** Auslösungen einer Regel in der Rückrechnung; 0, wenn sie nie griff. */
  anzahlJeRegel(regel: Steuerregel): number {
    return this.simulation?.jeRegel?.[regel] ?? 0;
  }

  /** Betrag im Schweizer Format; leer bei `null` — eine 0.00 sähe aus wie ein gemessener Wert. */
  preis(wert: number | null): string {
    return wert == null ? '' : formatSwissNumber(wert, 5);
  }

  /** Ladezustand in Prozent, eine Nachkommastelle; leer bei `null` (kein Speicher erfasst). */
  prozent(wert: number | null): string {
    return wert == null ? '' : formatSwissNumber(wert, 1);
  }

  /** Energiemenge im Schweizer Format. */
  menge(wert: number | null): string {
    return wert == null ? '' : formatSwissNumber(wert, 3);
  }

  /**
   * Netto-Batteriefluss aus der Energiebilanz — `null`, wenn die Bilanzwerte fehlen.
   *
   * <p>`produktion + bezug − verbrauch − ruecklieferung`. Positiv heisst laden, negativ entladen.
   *
   * <p><b>Abgeleitet, nicht gespeichert:</b> Der Wert ergibt sich jederzeit aus den vier Spalten;
   * eine eigene Spalte könnte von ihrer Grundlage abweichen.
   *
   * <p><b>Was er mit enthält:</b> Die Batterie hat keinen Zähler (kein Einheiten-Typ `SPEICHER`).
   * In der Differenz stecken deshalb auch Verbraucher, die nicht als Einheit erfasst sind. Eine
   * dauerhaft grosse Differenz bei stillstehender Batterie ist genau dieser Fall — und damit der
   * Hinweis, dass Einheiten fehlen.
   */
  bilanzDifferenz(e: Steuerentscheid): number | null {
    if (e.bezug == null || e.ruecklieferung == null) {
      return null;
    }
    return e.produktion + e.bezug - e.verbrauch - e.ruecklieferung;
  }

  /** Uhrzeit `HH:mm` des Intervalls. */
  uhrzeit(entscheid: Steuerentscheid): string {
    return formatSwissDateTime(new Date(entscheid.zeit)).slice(-5);
  }

  // ==================== Diagramm ====================

  /**
   * Zeichnet Preis, Produktion, Verbrauch und die beiden Zustandsbänder.
   *
   * <p>Die Bänder sind der Kern der Ansicht: Auf einen Blick liest man „ab 09:15 Ladung gesperrt,
   * ab 11:30 frei".
   */
  private async zeichne(): Promise<void> {
    if (this.entscheide.length === 0) {
      this.instanz?.dispose();
      this.instanz = undefined;
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
    const geladen = await ladeECharts();
    if (!geladen) {
      this.bibliothekFehlt = true;
      this.showMessage(this.translationService.translate('DIAGRAMM_NICHT_LADBAR'), 'error');
      return false;
    }
    this.echarts = geladen;
    return true;
  }

  private optionen(): Record<string, unknown> {
    const farben = chartFarben();
    const zeiten = this.entscheide.map(e => new Date(e.zeit).getTime());

    return {
      animation: false,
      // Rechts mehr Platz als links: Dort liegen ZWEI Achsen nebeneinander (kWh und Ladezustand).
      grid: { left: 60, right: 115, top: 20, bottom: 110 },
      tooltip: {
        trigger: 'axis',
        formatter: (params: { dataIndex: number }[]) => this.tooltip(params[0]?.dataIndex ?? 0)
      },
      legend: { bottom: 60, textStyle: { color: farben.text } },
      xAxis: {
        type: 'time',
        axisLine: { lineStyle: { color: farben.achse } },
        axisLabel: { color: farben.text }
      },
      yAxis: [
        {
          type: 'value',
          name: this.translationService.translate('PREIS_CHF_KWH'),
          nameTextStyle: { color: farben.text },
          axisLine: { lineStyle: { color: farben.achse } },
          axisLabel: { color: farben.text, formatter: (w: number) => formatSwissNumber(w, 3) },
          splitLine: { lineStyle: { color: farben.gitter } }
        },
        {
          type: 'value',
          name: 'kWh',
          min: ACHSE_MIN_MIT_BAENDERN,
          nameTextStyle: { color: farben.text },
          axisLine: { lineStyle: { color: farben.achse } },
          // Negative Werte gehoeren zu den Baendern, nicht zu einer Menge - als Achsenbeschriftung
          // waeren sie irrefuehrend.
          axisLabel: { color: farben.text, formatter: (w: number) => (w < 0 ? '' : String(w)) },
          splitLine: { show: false }
        },
        {
          // Ladezustand auf EIGENER Achse, nach aussen versetzt. Ohne sie muesste er sich eine
          // Skala mit den Mengen teilen: 0-100 % gegen 0-25 kWh - die Mengenkurven waeren an den
          // unteren Rand gedrueckt und nicht mehr lesbar.
          type: 'value',
          name: '%',
          min: 0,
          max: 100,
          position: 'right',
          offset: 55,
          nameTextStyle: { color: farben.soc },
          axisLine: { show: true, lineStyle: { color: farben.soc } },
          axisLabel: { color: farben.soc },
          splitLine: { show: false }
        }
      ],
      dataZoom: [{ type: 'inside' }, { type: 'slider', bottom: 20 }],
      series: [
        {
          name: this.translationService.translate('PREIS_CHF_KWH'),
          type: 'line',
          step: 'end',
          showSymbol: false,
          yAxisIndex: 0,
          // Rot und damit klar abgesetzt: Preis laeuft auf einer EIGENEN Achse und ist die
          // Fuehrungsgroesse der Steuerung. Gruen und Hellgruen (die erste Zuordnung) waren
          // nebeneinander nicht zu unterscheiden.
          itemStyle: { color: farben.akzent },
          lineStyle: { color: farben.akzent, width: 2 },
          data: this.entscheide.map((e, i) => [zeiten[i], e.preis])
        },
        {
          name: this.translationService.translate('PRODUKTION'),
          type: 'line',
          areaStyle: { opacity: 0.2 },
          showSymbol: false,
          yAxisIndex: 1,
          // Gruen fuer die Solarproduktion - die naheliegende Zuordnung.
          itemStyle: { color: farben.primaer },
          lineStyle: { color: farben.primaer },
          data: this.entscheide.map((e, i) => [zeiten[i], e.produktion])
        },
        {
          name: this.translationService.translate('VERBRAUCH'),
          type: 'line',
          showSymbol: false,
          yAxisIndex: 1,
          // Blau gegen das Gruen der Produktion - die beiden Mengen liegen auf derselben Achse
          // und muessen sich unterscheiden lassen.
          itemStyle: { color: farben.sekundaer },
          lineStyle: { color: farben.sekundaer },
          data: this.entscheide.map((e, i) => [zeiten[i], e.verbrauch])
        },
        {
          name: this.translationService.translate('STEUERUNG_SOC'),
          type: 'line',
          // Gestrichelt und ohne Flaeche: Der Ladezustand ist eine Zustandsgroesse, keine Menge -
          // die Linienart sagt das, noch bevor jemand die Legende liest.
          lineStyle: { color: farben.soc, width: 2, type: 'dashed' },
          itemStyle: { color: farben.soc },
          showSymbol: false,
          yAxisIndex: 2,
          // Luecken NICHT verbinden: Fehlt der Wert fuer ein Intervall, soll die Linie aussetzen
          // statt eine Gerade ueber die Luecke zu ziehen, die es so nie gab.
          connectNulls: false,
          data: this.entscheide.map((e, i) => [zeiten[i], e.soc])
        },
        this.band('STEUERUNG_BATTERIELADUNG', e => e.batterieladung === 'GESPERRT', 1),
        this.band('STEUERUNG_EINSPEISUNG', e => e.einspeisung === 'GESPERRT', 2)
      ]
    };
  }

  /**
   * Ein Zustandsband als **`markArea`** — exakte Von-bis-Rechtecke.
   *
   * <p><b>Warum keine Balken mehr:</b> Auf einer Zeitachse zentriert ECharts einen Balken auf
   * seinen Datenpunkt (das Band lag also 7½ Minuten zu früh) und ordnet mehrere Balkenserien
   * **nebeneinander** an statt übereinander. Beide Bänder beschrieben dasselbe Intervall, wurden
   * aber eine Viertelstunde auseinander gezeichnet. `markArea` nimmt Anfang und Ende direkt und
   * kennt dieses Problem nicht.
   *
   * <p>Zusammenhängende gesperrte Intervalle werden zu **einem** Rechteck verschmolzen: Bei 96
   * Einzelrechtecken pro Tag zeigten sich sonst Fugen an den Stossstellen.
   *
   * <p>Die Bänder liegen unterhalb der Nulllinie auf festen Ebenen — unabhängig davon, ob das
   * jeweils andere Band gesetzt ist. So bedeutet dieselbe Höhe immer dieselbe Grösse.
   */
  private band(nameKey: string, gesperrt: (e: Steuerentscheid) => boolean,
               ebene: number): Record<string, unknown> {
    const farben = chartFarben();
    // Eigene Farbfamilie, NICHT die der Kurven: Band und Kurve waren zuerst beide gruen bzw. beide
    // blau - in der Legende standen "Produktion" und "Batterieladung" ununterscheidbar nebeneinander.
    const farbe = ebene === 1 ? farben.bandEins : farben.bandZwei;
    const [oben, unten] = ebene === 1 ? [-0.1, -1.0] : [-1.3, -2.2];

    return {
      name: this.translationService.translate(nameKey),
      // `bar` statt `line`, obwohl die Serie keine Daten hat: Die Legende zeichnet dann ein
      // gefuelltes Rechteck statt einer Linie mit Kreis - ein Band ist eine Flaeche, keine Kurve.
      type: 'bar',
      yAxisIndex: 1,
      // Die Serie selbst zeichnet nichts - sie traegt nur die Flaechen und den Legendeneintrag.
      data: [],
      silent: true,
      itemStyle: { color: farbe },
      markArea: {
        silent: true,
        // Gedeckt: Die Baender zeigen einen Zustand, keine Messgroesse, und sollen die Kurven
        // nicht ueberstimmen. Unterschieden werden sie ueber ihre Ebene und die Legende.
        itemStyle: { color: farbe, opacity: 0.45 },
        data: this.bloecke(gesperrt).map(([von, bis]) => [
          { xAxis: von, yAxis: oben },
          { xAxis: bis, yAxis: unten }
        ])
      }
    };
  }

  /**
   * Fasst aufeinanderfolgende gesperrte Intervalle zu Blöcken `[von, bis]` zusammen.
   *
   * <p>`bis` ist das **Ende** des letzten Intervalls im Block, also dessen Beginn plus eine
   * Viertelstunde: Ein Entscheid um 17:45 gilt bis 18:00. Ohne diesen Zuschlag endete das Band
   * am Beginn seines letzten Intervalls und wäre um eine Viertelstunde zu kurz.
   *
   * <p>Eine Lücke in den Entscheiden (fehlendes Intervall) trennt zwei Blöcke — das Band wird
   * dort unterbrochen, statt über die Lücke hinwegzulaufen.
   */
  private bloecke(gesperrt: (e: Steuerentscheid) => boolean): [number, number][] {
    const bloecke: [number, number][] = [];
    let start: number | null = null;
    let letztesEnde = 0;

    for (const e of this.entscheide) {
      const von = new Date(e.zeit).getTime();
      const bis = von + INTERVALL_MS;
      if (gesperrt(e)) {
        // Neuer Block, sobald der Vorgaenger nicht unmittelbar anschliesst.
        if (start === null || von !== letztesEnde) {
          if (start !== null) {
            bloecke.push([start, letztesEnde]);
          }
          start = von;
        }
        letztesEnde = bis;
      } else if (start !== null) {
        bloecke.push([start, letztesEnde]);
        start = null;
      }
    }
    if (start !== null) {
      bloecke.push([start, letztesEnde]);
    }
    return bloecke;
  }

  /** Tooltip mit allen Eingangsgrössen — das *Warum* des Entscheids. */
  private tooltip(index: number): string {
    const e = this.entscheide[index];
    if (!e) {
      return '';
    }
    const t = (key: string) => this.translationService.translate(key);
    return `${formatSwissDateTime(new Date(e.zeit))}<br>`
      + `${t('PREIS_CHF_KWH')}: ${this.preis(e.preis) || '–'}<br>`
      + `${t('STEUERUNG_TIEFSTPREIS_REST')}: ${this.preis(e.preisTiefRest) || '–'}<br>`
      + `${t('PRODUKTION')}: ${this.menge(e.produktion)} kWh<br>`
      + `${t('VERBRAUCH')}: ${this.menge(e.verbrauch)} kWh<br>`
      + `${t('STEUERUNG_UEBERSCHUSS')}: ${this.menge(e.ueberschuss)} kWh<br>`
      + `<b>${t(this.regelKey(e.regel))}</b><br>`
      + (e.soc == null ? '' : `${t('STEUERUNG_SOC')}: ${formatSwissNumber(e.soc, 1)} %<br>`)
      + `${t('STEUERUNG_BATTERIELADUNG')}: ${t('STEUERUNG_' + e.batterieladung)}<br>`
      + `${t('STEUERUNG_EINSPEISUNG')}: ${t('STEUERUNG_' + e.einspeisung)}`;
  }

  // ==================== Datum ====================

  private heuteIso(): string {
    return this.alsIso(new Date());
  }

  private verschiebe(tage: number): string {
    const datum = new Date(this.datum + 'T12:00:00');
    datum.setDate(datum.getDate() + tage);
    return this.alsIso(datum);
  }

  private alsIso(datum: Date): string {
    const zwei = (wert: number) => String(wert).padStart(2, '0');
    return `${datum.getFullYear()}-${zwei(datum.getMonth() + 1)}-${zwei(datum.getDate())}`;
  }
}
