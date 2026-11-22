import { Component, OnDestroy, OnInit, ChangeDetectorRef, ChangeDetectionStrategy, inject } from '@angular/core';
import Keycloak from 'keycloak-js';
import { WithMessage } from '../../utils/with-message';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MesswerteService, MesswertData } from '../../services/messwerte.service';
import { Einheit, EinheitTyp, MesspunktGruppe } from '../../models/einheit.model';
import { MesspunktGruppePipe } from '../../pipes/messpunkt-gruppe.pipe';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { PreiszeitreiheService } from '../../services/preiszeitreihe.service';
import { PreiszeitreihePunkt } from '../../models/preiszeitreihe.model';
import { FeatureFlagDirective } from '../../directives/feature-flag.directive';
import { TranslatePipe } from '../../pipes/translate.pipe';
import { TranslationService } from '../../services/translation.service';
import { QuarterSelectorComponent } from '../quarter-selector/quarter-selector.component';
import { IconComponent } from '../icon/icon.component';
import { EinheitSelectorComponent } from '../einheit-selector/einheit-selector.component';
import { formatSwissNumber } from '../../utils/number-utils';
import { blaettereZeitraum, formatSwissDateTime, monatsende, toIsoDate, vorquartal } from '../../utils/date-utils';
import { ChartFarben, chartFarben } from '../../utils/chart-farben';
import { ladeECharts } from '../../utils/echarts-loader';
import { formatPreis, preisPunkte } from '../../utils/chart-preis';
import { FeatureFlagService } from '../../services/feature-flag.service';

/** Länge eines Messintervalls: `messwerte.zeit` trägt den Beginn einer Viertelstunde. */
const INTERVALL_MS = 15 * 60 * 1000;

/** Kennung der Preisreihe im Diagramm - der Tooltip erkennt sie daran und formatiert als Preis. */
const EINSPEISETARIF_ID = 'einspeisetarif';

/** Deckkraft der Total-Fläche im Diagramm einer Einheit (Specs/Grafiken.md, FR-2): blass, sie liegt unten. */
const FLAECHE_TOTAL = 0.25;
/** Deckkraft der ZEV-Fläche darüber: dicht genug, um das Total zu überdecken. */
const FLAECHE_ZEV = 0.6;
/** Deckkraft des gedeckten Verbrauchs im Vergleich (FR-9.3): Mehrere Flächen liegen übereinander und müssen durchscheinen. */
const FLAECHE_GEDECKT = 0.35;

interface ChartData {
  /** Eindeutig je Diagramm: die Einheit-ID oder `messpunkt-<n>`. Teil der DOM-ID des Behälters. */
  schluessel: string;
  /** Eine Einheit — oder alle Einheiten eines Messpunkts (Specs/Grafiken.md, FR-8). */
  einheiten: Einheit[];
  /** Nur beim Diagramm eines Messpunkts gesetzt. */
  messpunkt?: string;
  /** Vergleichsdiagramm: alle gewählten Einheiten in einem (Specs/Grafiken.md, FR-9). */
  vergleich?: boolean;
  /** ECharts-Instanz; `null`, solange nicht gezeichnet. */
  instanz: import('echarts/core').ECharts | null;
}

@Component({
  selector: 'app-messwerte-chart',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe, MesspunktGruppePipe, QuarterSelectorComponent, IconComponent,
    EinheitSelectorComponent, FeatureFlagDirective],
  templateUrl: './messwerte-chart.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrls: ['./messwerte-chart.component.css']
})
export class MesswerteChartComponent extends WithMessage implements OnInit, OnDestroy {
  dateFrom: string = '';
  dateTo: string = '';
  selectedEinheiten: Einheit[] = [];
  /** Gewählte Messpunkte mit mehreren Einheiten — je einer ein zusätzliches Diagramm (FR-8). */
  selectedGruppen: MesspunktGruppe[] = [];
  loading = false;
  /**
   * Was zuletzt verlangt wurde: `einzeln` = ein Diagramm je Einheit und Messpunkt („Anzeigen"),
   * `vergleich` = alle gewählten Einheiten in einem („Vergleich", FR-9). Blättern und „Heute"
   * laden in der Ansicht neu, die gerade steht — ausser im Vergleich sind weniger als zwei
   * Einheiten übrig, dann fällt sie auf `einzeln` zurück.
   */
  modus: 'einzeln' | 'vergleich' = 'einzeln';

  /**
   * Darf der Benutzer den Einspeisetarif sehen? Die Preiszeitreihe verlangt `tarife:manage`
   * (Specs/Berechtigungen.md) - ohne die Permission gibt es die Checkbox nicht, sonst liefe jeder
   * Klick in ein 403. Das Feature-Flag `PREISZEITREIHE` prüft das Template (`*appFeature`) und
   * zusätzlich die Wache vor dem Preisabruf.
   *
   * Über `hasRealmRole` wie in den übrigen Komponenten (z.B. Einheiten-Liste) und wie im Backend,
   * das nur die Realm-Rollen auswertet.
   */
  readonly darfEinspeisetarif = inject(Keycloak).hasRealmRole('tarife:manage');
  /** Checkbox „Einspeisetarif": blendet den dynamischen Einspeisepreis in jedes Diagramm ein (FR-10). */
  mitEinspeisetarif = false;
  /** Die Preise des zuletzt geladenen Zeitraums; leer, wenn nicht verlangt oder keine vorhanden. */
  private preise: PreiszeitreihePunkt[] = [];

  charts: ChartData[] = [];
  /** Solange die Bibliothek nachgeladen wird, zeigt jedes Panel einen Hinweis. */
  bibliothekLaedt = false;
  /** Nachladen gescheitert - einmal gemeldet, nicht je Einheit. */
  bibliothekFehlt = false;

  /**
   * ECharts wird **dynamisch** nachgeladen (`utils/echarts-loader.ts`): `/chart` ist eine eager
   * Route, ein statischer Import landete im Initial-Bundle und jede Seite der Anwendung lüde die
   * Bibliothek mit (Specs/Grafiken.md, NFR-1).
   */
  private echarts?: typeof import('echarts/core');
  /**
   * Der Lader als Feld, damit ein Test das Scheitern des Nachladens vorgeben kann. Das Modul
   * selbst lässt sich im Test nicht ersetzen, und ein echter Fehlschlag bräuchte einen fehlenden
   * Chunk.
   */
  private lade: typeof ladeECharts = ladeECharts;
  /** Je Diagramm ein Beobachter - ersetzt die frühere manuelle Canvas-Berechnung. */
  private observers = new Map<string, ResizeObserver>();

  constructor(
    private messwerteService: MesswerteService,
    private preiszeitreiheService: PreiszeitreiheService,
    private featureFlagService: FeatureFlagService,
    private translationService: TranslationService,
    private cdr: ChangeDetectorRef
  ) { super(); }

  ngOnInit(): void {
    this.setDefaultDates();
  }

  ngOnDestroy(): void {
    this.gebeDiagrammeFrei();
  }

  /**
   * Gibt alle Instanzen und Beobachter frei.
   *
   * Nötig an zwei Stellen: beim Verlassen der Seite und vor jedem neuen „Anzeigen". Ohne die
   * Freigabe wächst der Speicher mit jedem Klick, denn die Seite erlaubt beliebig viele Diagramme.
   */
  private gebeDiagrammeFrei(): void {
    this.observers.forEach(observer => observer.disconnect());
    this.observers.clear();
    this.charts.forEach(chartData => chartData.instanz?.dispose());
  }

  onSelectionChange(einheiten: Einheit[]): void {
    this.selectedEinheiten = einheiten;
  }

  onGruppenChange(gruppen: MesspunktGruppe[]): void {
    this.selectedGruppen = gruppen;
  }

  /** Mindestens eine Einheit oder ein Messpunkt gewählt — sonst gibt es nichts zu zeichnen. */
  get hatAuswahl(): boolean {
    return this.selectedEinheiten.length > 0 || this.selectedGruppen.length > 0;
  }

  /**
   * Ein Vergleich braucht mindestens zwei Einheiten - mit einer gäbe es nichts zu vergleichen.
   * Gezählt wird, was geladen würde: Ein Messpunkt mit zwei Einheiten genügt.
   */
  get kannVergleichen(): boolean {
    return this.zuLadendeEinheiten().length >= 2;
  }

  /**
   * Die Einheiten, deren Messwerte geladen werden: die gewählten und die der gewählten Messpunkte,
   * **jede einmal**. Wer Bezug, Rücklieferung und ihren Messpunkt wählt, löst zwei Abfragen aus,
   * nicht vier.
   */
  private zuLadendeEinheiten(): Einheit[] {
    const jeId = new Map<number, Einheit>();
    [...this.selectedEinheiten, ...this.selectedGruppen.flatMap(g => g.einheiten)]
      .forEach(e => jeId.set(e.id!, e));
    return [...jeId.values()];
  }

  /**
   * Belegt den Zeitraum mit dem vorangehenden Quartal vor
   * (im Q1 wird Q4 des Vorjahres gesetzt).
   */
  private setDefaultDates(): void {
    const { von, bis } = vorquartal();
    this.dateFrom = von;
    this.dateTo = bis;
  }

  onDateFromChange(): void {
    if (this.dateFrom) {
      this.dateTo = monatsende(this.dateFrom);
    }
  }

  onQuarterSelected(event: {von: string, bis: string}): void {
    this.dateFrom = event.von;
    this.dateTo = event.bis;
  }

  /**
   * Setzt den Zeitraum auf heute–heute und zeigt die Grafik sofort an — wie in der Statistik
   * (Specs/Grafiken.md, FR-1a). Auch ein zweiter Klick lädt: Für den laufenden Tag kommen laufend
   * Messwerte dazu.
   *
   * <p>Über {@link toIsoDate} statt {@code toISOString()}: Letzteres liefert das UTC-Datum, und
   * kurz nach Mitternacht stünde dann der Vortag im Feld.
   */
  onHeute(): void {
    const heute = toIsoDate(new Date());
    this.dateFrom = heute;
    this.dateTo = heute;
    this.ladeUndZeichne();
  }

  /**
   * Blättert den Zeitraum um seine eigene Länge und lädt sofort — dieselbe Regel wie in der
   * Statistik (`blaettereZeitraum`, Specs/Statistik.md, FR-4).
   */
  onBlaettern(richtung: 1 | -1): void {
    if (!this.kannBlaettern) {
      return;
    }
    const neu = blaettereZeitraum(this.dateFrom, this.dateTo, richtung);
    this.dateFrom = neu.von;
    this.dateTo = neu.bis;
    this.ladeUndZeichne();
  }

  /**
   * Blättern nur mit gültigem Zeitraum, einer Auswahl und nicht während des Ladens.
   * Ohne Auswahl liefe das sofortige Laden nur in die Fehlermeldung.
   */
  get kannBlaettern(): boolean {
    return !this.loading && this.hatAuswahl
      && !!this.dateFrom && !!this.dateTo && this.dateFrom <= this.dateTo;
  }

  /** „Anzeigen": ein Diagramm je gewählter Einheit und je gewähltem Messpunkt. */
  onSubmit(): void {
    this.modus = 'einzeln';
    this.ladeUndZeichne();
  }

  /**
   * „Vergleich": alle gewählten Einheiten in **einem** Diagramm; die einzelnen Diagramme
   * verschwinden (Specs/Grafiken.md, FR-9).
   */
  onVergleich(): void {
    if (!this.kannVergleichen) {
      return;
    }
    this.modus = 'vergleich';
    this.ladeUndZeichne();
  }

  /**
   * Checkbox „Einspeisetarif" (Specs/Grafiken.md, FR-10). Stehen schon Diagramme, werden sie neu
   * geladen und gezeichnet - mit bzw. ohne Preisreihe. Ohne Diagramme merkt sich die Maske nur
   * die Wahl; sie gilt beim nächsten Laden.
   */
  onEinspeisetarifToggle(): void {
    this.mitEinspeisetarif = !this.mitEinspeisetarif;
    if (this.charts.length > 0) {
      this.ladeUndZeichne();
    }
  }

  /** Lädt die Messwerte der Auswahl und zeichnet sie in der Ansicht von {@link modus}. */
  private ladeUndZeichne(): void {
    if (!this.dateFrom || !this.dateTo || !this.hatAuswahl) {
      this.showMessage(this.translationService.translate('BITTE_ALLE_FELDER_AUSFUELLEN'), 'error');
      return;
    }

    if (this.dateFrom > this.dateTo) {
      this.showMessage(this.translationService.translate('START_DATUM_MUSS_VOR_END_DATUM_LIEGEN'), 'error');
      return;
    }

    this.loading = true;
    this.gebeDiagrammeFrei();
    this.charts = [];

    // Auswahl festhalten: Sie kann sich aendern, waehrend die Antwort unterwegs ist.
    const einheiten = this.zuLadendeEinheiten();
    const einzelne = [...this.selectedEinheiten];
    const gruppen = [...this.selectedGruppen];
    // Ein Vergleich mit weniger als zwei Einheiten ist keiner: Wurde die Auswahl seit dem Klick
    // auf "Vergleich" verkleinert und dann geblaettert, geht es zurueck zu den einzelnen
    // Diagrammen - sonst stuende dort "Vergleich: 1 Einheiten" mit einer einzigen Einheit.
    if (this.modus === 'vergleich' && einheiten.length < 2) {
      this.modus = 'einzeln';
    }
    const modus = this.modus;
    const requests = einheiten.map(e =>
      this.messwerteService.getMesswerteByEinheit(e.id!, this.dateFrom, this.dateTo)
    );

    // Der Einspeisetarif ist Beiwerk: Scheitert sein Abruf, erscheinen die Messwerte trotzdem -
    // ohne Preisreihe und mit der Meldung, warum sie fehlt.
    // Auch das Flag gehoert zur Wache: Wird es abgeschaltet, waehrend das Haekchen sitzt,
    // verschwindet die Checkbox - das Haekchen liesse sich nicht mehr entfernen und jedes Laden
    // liefe in ein 403.
    const mitPreis = this.mitEinspeisetarif && this.darfEinspeisetarif
      && this.featureFlagService.isEnabled('PREISZEITREIHE');
    let preisFehler: string | null = null;
    const preise$ = mitPreis
      ? this.preiszeitreiheService.getPunkte(this.dateFrom, this.dateTo).pipe(
        catchError(error => {
          // Das Backend antwortet mit einem Key ({ error }, z.B. FEATURE_FLAG_DEAKTIVIERT) oder
          // im Klartext (z.B. bei einer Spanne ueber 366 Tage). Ein Key wird uebersetzt, Klartext
          // kommt aus translate() unveraendert zurueck.
          preisFehler = this.translationService.translate(error?.error?.error
            || (typeof error?.error === 'string' && error.error)
            || 'PREISE_ABRUF_FEHLGESCHLAGEN');
          return of<PreiszeitreihePunkt[]>([]);
        }))
      : of<PreiszeitreihePunkt[]>([]);

    forkJoin({ messwerte: forkJoin(requests), preise: preise$ }).subscribe({
      next: ({ messwerte: results, preise }) => {
        this.preise = preise ?? [];
        let totalDataPoints = 0;
        const datenJeEinheit = new Map<number, MesswertData[]>();

        results.forEach((data, index) => {
          totalDataPoints += data.length;
          datenJeEinheit.set(einheiten[index].id!, data);
        });

        // Vergleich: EIN Diagramm mit allen geladenen Einheiten. Sonst erst je Einheit ein
        // Diagramm, danach je Messpunkt eines - dieselbe Reihenfolge wie in der Auswahl.
        this.charts = modus === 'vergleich'
          ? [{ schluessel: 'vergleich', einheiten, vergleich: true, instanz: null }]
          : [
            ...einzelne.map(e => ({ schluessel: `${e.id}`, einheiten: [e], instanz: null })),
            ...gruppen.map((g, index) => ({
              schluessel: `messpunkt-${index}`, einheiten: g.einheiten, messpunkt: g.messpunkt, instanz: null
            }))
          ];

        this.cdr.detectChanges();
        void this.createChartsSequentially(datenJeEinheit);
        // Gezaehlt werden die geladenen Einheiten, nicht die Diagramme: Ein Messpunkt-Diagramm
        // laedt keine eigenen Daten.
        if (preisFehler) {
          this.showMessage(preisFehler, 'error');
        } else if (mitPreis && this.preise.length === 0) {
          // Verlangt, aber nichts da: sagen statt ein Diagramm ohne die erwartete Linie zu zeigen.
          this.showMessage(this.translationService.translate('KEINE_PREISE_VORHANDEN'), 'warning');
        } else {
          this.showMessage(`${totalDataPoints} ${this.translationService.translate('DATENPUNKTE_FUER')} ${einheiten.length} ${this.translationService.translate('EINHEITEN_GELADEN')}`, 'success');
        }
        this.loading = false;
      },
      error: (error) => {
        this.showMessage(`${this.translationService.translate('FEHLER_BEIM_LADEN_DER_DATEN')}: ${error.message}`, 'error');
        this.loading = false;
      }
    });
  }

  /**
   * Lädt die Bibliothek **einmal** und zeichnet die Diagramme danach nacheinander.
   *
   * <p>Die Staffelung (100 ms, dann 50 ms je weiteres) stammt aus der Zeit vor ECharts und **bleibt**,
   * begruendet durch eine Messung mit „Alle auswaehlen" (16 Einheiten, 113'568 Datenpunkte):
   * Mit ECharts sind alle Diagramme nach 1'817 ms bemalt, davon sind 850 ms diese Staffelung — die
   * Zeichenzeit selbst liegt bei rund 60 ms je Diagramm. Alle 16 unmittelbar hintereinander zu
   * zeichnen hiesse, den Haupt-Thread rund eine Sekunde am Stueck zu blockieren. Gestaffelt
   * beantwortet die Maske einen Klick in 64 ms (vorher: 739 ms bei 9'078 ms Gesamtzeit).
   * Die Kette kostet also weniger als das, was sie staffelt.
   *
   * <p>Der Fehlschlag des Nachladens wird **einmal** gemeldet, nicht je Einheit: Bei „Alle
   * auswählen" stünden sonst zehn identische Meldungen.
   */
  private async createChartsSequentially(datenJeEinheit: Map<number, MesswertData[]>): Promise<void> {
    if (!await this.ladeBibliothek()) {
      return;
    }

    let index = 0;
    const createNext = () => {
      if (index >= this.charts.length) return;
      this.createChart(this.charts[index], datenJeEinheit);
      index++;
      if (index < this.charts.length) setTimeout(createNext, 50);
    };
    setTimeout(createNext, 100);
  }

  /** Lädt `echarts` beim ersten Zeichnen nach. `false`, wenn das misslingt. */
  private async ladeBibliothek(): Promise<boolean> {
    if (this.echarts) {
      return true;
    }
    if (this.bibliothekFehlt) {
      return false;
    }
    this.bibliothekLaedt = true;
    this.cdr.detectChanges();
    const geladen = await this.lade();
    this.bibliothekLaedt = false;
    if (!geladen) {
      this.bibliothekFehlt = true;
      this.showMessage(this.translationService.translate('DIAGRAMM_NICHT_LADBAR'), 'error');
      this.cdr.detectChanges();
      return false;
    }
    this.echarts = geladen;
    this.cdr.detectChanges();
    return true;
  }

  /**
   * Zeichnet ein Diagramm in seinen Behälter — für eine Einheit oder für einen Messpunkt.
   *
   * <p>Die Optionen sind mit Absicht denen der Preiszeitreihe nachgebaut — zwei Diagramme, die
   * gleich aussehen und gleich gebaut sind, sind zusammen billiger zu pflegen als zwei eigene Welten.
   */
  private createChart(chartData: ChartData, datenJeEinheit: Map<number, MesswertData[]>): void {
    const behaelter = document.getElementById(`chart-${chartData.schluessel}`);
    if (!behaelter || !this.echarts) {
      return;
    }

    const instanz = this.echarts.init(behaelter);
    chartData.instanz = instanz;

    // Ersetzt die frühere manuelle Canvas-Berechnung samt `responsive: false` (und dem Kommentar
    // "WICHTIG! Funktioniert mit true nicht."): ECharts misst selbst, sobald wir es anstossen.
    const observer = new ResizeObserver(() => instanz.resize());
    observer.observe(behaelter);
    this.observers.set(chartData.schluessel, observer);

    const daten = (einheit: Einheit) => datenJeEinheit.get(einheit.id!) ?? [];
    // Messpunkt und Vergleich zeichnen eine Reihe je Einheit; nur der Vergleich spiegelt die
    // Produzenten und ergaenzt den gedeckten Verbrauch als Flaeche (FR-9.3).
    const optionen = chartData.messpunkt || chartData.vergleich
      ? this.optionenMesspunkt(
        chartData.einheiten.map(einheit => ({ einheit, daten: daten(einheit) })), this.preise,
        !!chartData.vergleich)
      : this.optionen(daten(chartData.einheiten[0]), this.preise);
    instanz.setOption(optionen, true);
  }

  /**
   * Diagramm-Optionen für eine Einheit.
   *
   * <p><b>Stufenlinie</b> (`step: 'end'`): Ein Messwert gilt für die **ganze** Viertelstunde. Die
   * frühere geglättete Linie (`tension: 0.1`) behauptete einen stetigen Verlauf zwischen den
   * Intervallen, den es nicht gibt.
   *
   * <p><b>Zwei Flächen übereinander</b> (FR-2): das Total und darüber, in der zweiten Farbe, der
   * ZEV-Anteil — der Teil, den die eigene Produktion deckt. Beide stehen im gespeicherten
   * Vorzeichen, der ZEV-Anteil liegt also **innerhalb** des Totals; was vom Total sichtbar bleibt,
   * kommt aus dem Netz. Die ZEV-Fläche ist dichter, damit sie das Total darunter überdeckt.
   */
  private optionen(data: MesswertData[], preise: PreiszeitreihePunkt[] = []): Record<string, unknown> {
    const farben = chartFarben();
    const kwh = this.translationService.translate('KWH');

    return this.diagrammOptionen([
      this.reihe(this.legende('TOTAL', this.summe(data, 'total'), kwh), this.zeitreihe(data, 'total'),
        farben.primaer, FLAECHE_TOTAL),
      this.reihe(this.legende('ZEV', this.summe(data, 'zev'), kwh), this.zeitreihe(data, 'zev'),
        farben.sekundaer, FLAECHE_ZEV)
    ], farben, kwh, preise);
  }

  /**
   * Diagramm-Optionen für mehrere Einheiten in einem Diagramm — einen Messpunkt (Specs/Grafiken.md,
   * FR-8) oder den Vergleich aller gewählten Einheiten (FR-9): **eine Reihe je Einheit**, jeweils
   * ihr `total` — im gespeicherten Vorzeichen, ausser bei Produzenten im Vergleich.
   *
   * <p>Im Vergleich (`vergleich`) werden die Reihen der **Produzenten** gespiegelt, damit die
   * Produktion über der Nulllinie neben dem Verbrauch steht (FR-9.3). Dazu bekommt jeder
   * **Konsument** mit ZEV-Anteil eine Fläche in seiner Farbe: den Teil seines Verbrauchs, den die
   * eigene Produktion deckt. Die Flächen stehen **nach** allen Linien, damit die Reihenfolge der
   * Einheiten und ihre Farben bleiben.
   *
   * <p>Bei Bezug und Rücklieferung ergibt das gespeicherte Vorzeichen von selbst das gewünschte
   * Bild: Der Bezug ist positiv, die Rücklieferung negativ gespeichert — die eine Linie liegt über,
   * die andere unter der Nulllinie. Im Messpunkt-Diagramm wird deshalb **nichts** gespiegelt.
   *
   * <p>Im Messpunkt-Diagramm gibt es keine ZEV-Reihe: Für Bezug und Rücklieferung ist `zev` 0.
   *
   * <p>Die Reihen tragen der Reihe nach die Reihenpalette aus `chartFarben().reihen`. Für eine
   * siebte und weitere nimmt ECharts seine eigene Palette — unterscheidbar, statt eine Farbe
   * zweimal zu vergeben.
   */
  private optionenMesspunkt(reihen: { einheit: Einheit; daten: MesswertData[] }[],
                            preise: PreiszeitreihePunkt[] = [],
                            vergleich = false): Record<string, unknown> {
    const farben = chartFarben();
    const reihenfarben = farben.reihen;
    const kwh = this.translationService.translate('KWH');

    const linien = reihen.map(({ einheit, daten }, index) => {
      // Im Vergleich (FR-9) steht die Produktion ueber der Nulllinie, neben Verbrauch und Bezug:
      // Gespeichert ist sie negativ, und unter der Nulllinie liesse sie sich mit den uebrigen
      // Kurven nicht vergleichen. Reihe UND Summe drehen, sonst widerspraeche die Legende dem Bild.
      const spiegeln = vergleich && einheit.typ === EinheitTyp.PRODUCER;
      const zeitreihe = this.zeitreihe(daten, 'total');
      const summe = this.summe(daten, 'total');
      return this.reihe(
        this.legendeText(einheit.name, spiegeln ? this.gespiegelt(summe) : summe, kwh),
        spiegeln ? zeitreihe.map(([zeit, wert]) => [zeit, this.gespiegelt(wert)]) : zeitreihe,
        reihenfarben[index]);
    });
    return this.diagrammOptionen(
      vergleich ? [...linien, ...this.gedeckteFlaechen(reihen, reihenfarben, kwh)] : linien,
      farben, kwh, preise);
  }

  /**
   * Der gedeckte Verbrauch im Vergleich (Specs/Grafiken.md, FR-9.3): je Konsument eine Fläche mit
   * seinem ZEV-Anteil, in der Farbe seiner Linie. Die Linie zeigt den ganzen Verbrauch — gefüllt
   * ist, was die eigene Produktion deckt.
   *
   * Nur Konsumenten: Der ZEV-Anteil des Produzenten ist dieselbe Energie noch einmal. Und nur mit
   * einem Anteil im Zeitraum — eine leere Fläche je Einheit füllte bloss die Legende.
   */
  private gedeckteFlaechen(reihen: { einheit: Einheit; daten: MesswertData[] }[], reihenfarben: string[],
                           kwh: string): Record<string, unknown>[] {
    const zev = this.translationService.translate('ZEV');
    return reihen
      .map(({ einheit, daten }, index) => ({ einheit, daten, farbe: reihenfarben[index] }))
      .filter(({ einheit, daten }) => einheit.typ === EinheitTyp.CONSUMER && daten.some(d => (d.zev ?? 0) !== 0))
      // Rand duenner als die Linie der Einheit: Die Flaeche traegt die Aussage, nicht ihr Rand.
      .map(({ einheit, daten, farbe }) => this.reihe(
        this.legendeText(`${einheit.name} – ${zev}`, this.summe(daten, 'zev'), kwh),
        this.zeitreihe(daten, 'zev'), farbe, FLAECHE_GEDECKT, 1));
  }

  /**
   * Dreht das Vorzeichen; eine 0 bleibt 0 statt `-0`. Angezeigt sähen beide gleich aus, `-0`
   * unterscheidet sich aber in `Object.is`/`toEqual` von `0`.
   */
  private gespiegelt(wert: number): number {
    return wert === 0 ? 0 : -wert;
  }

  /**
   * `total` oder `zev` als Zeitreihe `[Zeitpunkt, Wert]`, im gespeicherten Vorzeichen. `zev` ist
   * der Teil des Totals, den die eigene Produktion deckt.
   */
  private zeitreihe(data: MesswertData[], feld: 'total' | 'zev'): number[][] {
    return data.map(d => [new Date(d.zeit).getTime(), d[feld] ?? 0]);
  }

  private summe(data: MesswertData[], feld: 'total' | 'zev'): number {
    return data.reduce((summe, d) => summe + (d[feld] ?? 0), 0);
  }

  /** Was beide Diagrammarten teilen: Achsen, Tooltip, Legende, Zoom — nur die Reihen unterscheiden sich. */
  private diagrammOptionen(series: Record<string, unknown>[], farben: ChartFarben, kwh: string,
                           preise: PreiszeitreihePunkt[] = []): Record<string, unknown> {
    const zeigtPreis = preise.length > 0;
    const energieAchse = {
      ...this.wertAchse(kwh, farben),
      splitLine: { lineStyle: { color: farben.gitter } }
    };
    return {
      animation: false,
      // top 55: Die Legende steht oben ueber die ganze Breite, sobald sie blaettert (Vergleich mit
      // vielen Einheiten, schmale Anzeige). Mit 40 lag sie auf dem Achsentitel "kWh".
      // Mit Preisreihe braucht die zweite Achse rechts Platz fuer ihre Beschriftung.
      grid: { left: 70, right: zeigtPreis ? 70 : 20, top: 55, bottom: 70 },
      // `scroll`: Die Legende bleibt einzeilig und blaettert, wenn sie nicht passt. Beim
      // Messpunkt-Diagramm stehen die Namen der Einheiten darin; auf schmalen Anzeigen brach sie
      // sonst auf zwei Zeilen um und lag ueber dem Achsentitel und der Zeichenflaeche.
      legend: { type: 'scroll', top: 0, textStyle: { color: farben.text } },
      tooltip: {
        trigger: 'axis',
        formatter: (alle: { seriesName: string; seriesId?: string; value: number[] }[]) => {
          // Der Abschlusspunkt (siehe mitAbschluss) ist kein Messwert und hat keinen Tooltip.
          const params = alle.filter(p => !p.value[2]);
          if (params.length === 0) {
            return '';
          }
          const kopf = formatSwissDateTime(new Date(params[0].value[0]));
          // Der Einspeisetarif ist ein Preis: fuenf Stellen wie in der Preiszeitreihe und ohne
          // die Einheit kWh - sie steht bei ihm im Namen (CHF/kWh).
          const zeilen = params.map(p => p.seriesId === EINSPEISETARIF_ID
            ? `${p.seriesName}: ${formatPreis(p.value[1])}`
            : `${p.seriesName}: ${formatSwissNumber(p.value[1], 3)} ${kwh}`);
          return [kopf, ...zeilen].join('<br>');
        }
      },
      // Kein Achsentitel: "Zeit" stand mitten im Zoom-Schieber darunter, und dass die Achse die
      // Zeit zeigt, sagen die Uhrzeiten an ihr ohnehin (Specs/Grafiken.md, FR-2).
      xAxis: {
        type: 'time',
        axisLine: { lineStyle: { color: farben.achse } },
        // hideOverlap: Bei schmaler Anzeige liefen die Uhrzeiten sonst ineinander.
        axisLabel: { color: farben.text, hideOverlap: true }
      },
      // Ohne Preis EINE Achse wie bisher; mit Preis kommt rechts eine zweite fuer CHF/kWh dazu -
      // Energie und Preis haben keine gemeinsame Skala.
      yAxis: zeigtPreis ? [energieAchse, this.preisAchse(farben)] : energieAchse,
      dataZoom: [{ type: 'inside' }, { type: 'slider', bottom: 10 }],
      series: zeigtPreis ? [...series, this.preisReihe(preise, farben)] : series
    };
  }

  /** Was beide y-Achsen teilen: Titel, Farben und die Beschriftung im Schweizer Format. */
  private wertAchse(name: string, farben: ChartFarben): Record<string, unknown> {
    return {
      type: 'value',
      name,
      nameTextStyle: { color: farben.text },
      axisLine: { lineStyle: { color: farben.achse } },
      axisLabel: {
        color: farben.text,
        formatter: (wert: number) => formatSwissNumber(wert, 3)
      }
    };
  }

  /** Rechte Achse für den Einspeisetarif in CHF/kWh, ohne eigene Hilfslinien. */
  private preisAchse(farben: ChartFarben): Record<string, unknown> {
    return {
      ...this.wertAchse(this.translationService.translate('PREIS_CHF_KWH'), farben),
      position: 'right',
      // Die rechte Achsenlinie zeichnet ECharts nur auf Verlangen.
      axisLine: { show: true, lineStyle: { color: farben.achse } },
      // Zwei Gitter uebereinander waeren unlesbar - das Gitter gehoert der Energie.
      splitLine: { show: false }
    };
  }

  /**
   * Die Reihe des Einspeisetarifs (Specs/Grafiken.md, FR-10): Stufenlinie wie die Messwerte - ein
   * Preis gilt für die ganze Viertelstunde -, auf der rechten Achse.
   *
   * Gestrichelt und in neutralem Grau: Die Farben der Reihenpalette sind in einem Vergleich alle
   * vergeben, und der Preis ist keine Energie. Strich und Ton setzen ihn von jeder Messreihe ab.
   */
  private preisReihe(preise: PreiszeitreihePunkt[], farben: ChartFarben): Record<string, unknown> {
    return {
      id: EINSPEISETARIF_ID,
      name: this.translationService.translate('GRAFIK_EINSPEISETARIF_CHF_KWH'),
      type: 'line',
      step: 'end',
      showSymbol: false,
      connectNulls: false,
      sampling: 'lttb',
      yAxisIndex: 1,
      lineStyle: { color: farben.bandZwei, width: 1.5, type: 'dashed' },
      itemStyle: { color: farben.bandZwei },
      data: this.mitAbschluss(preisPunkte(preise))
    };
  }

  /**
   * Legendenname samt Summe, z.B. `Total (Σ 1'234.567 kWh)`.
   *
   * Der Name kommt aus dem `TranslationService` (die Keys `TOTAL` und `ZEV` sind vorhanden), die
   * Summe im Schweizer Format über `formatSwissNumber` — früher stand hier fester Text und
   * eine feste Rundung ohne Tausendertrennzeichen.
   */
  private legende(key: string, summe: number, kwh: string): string {
    return this.legendeText(this.translationService.translate(key), summe, kwh);
  }

  /** Wie {@link legende}, aber mit fertigem Namen — beim Messpunkt-Diagramm ist das der Name der Einheit. */
  private legendeText(name: string, summe: number, kwh: string): string {
    return `${name} (Σ ${formatSwissNumber(summe, 3)} ${kwh})`;
  }

  /**
   * Eine Datenreihe.
   *
   * `sampling: 'lttb'` ist dauerhaft aktiv: Ein Quartal sind bis zu 8'640 Punkte je Reihe, und bei
   * „Alle auswählen" liegen mehrere Diagramme gleichzeitig auf der Seite. Bei kleinen Mengen ist die
   * Ausdünnung unschädlich, bei grossen ist sie der Unterschied zwischen flüssig und zäh.
   *
   * Mit `flaeche` (Deckkraft) ist der Raum zwischen Linie und Nulllinie gefüllt; ohne Angabe bleibt
   * die Reihe eine reine Linie.
   */
  private reihe(name: string, daten: number[][], farbe?: string, flaeche?: number,
                linienbreite = 2): Record<string, unknown> {
    return {
      name,
      type: 'line',
      step: 'end',
      showSymbol: false,
      connectNulls: false,
      sampling: 'lttb',
      // Ohne Farbe (siebte und weitere Reihe eines Diagramms) waehlt ECharts aus seiner Palette.
      lineStyle: farbe ? { color: farbe, width: linienbreite } : { width: linienbreite },
      ...(farbe ? { itemStyle: { color: farbe } } : {}),
      ...(flaeche === undefined ? {} : { areaStyle: { opacity: flaeche } }),
      data: this.mitAbschluss(daten)
    };
  }

  /**
   * Hängt an eine Reihe den **Abschlusspunkt** an: das Ende des letzten Intervalls, mit dessen Wert.
   *
   * Eine Stufenlinie (`step: 'end'`) zeichnet jeden Wert als waagrechtes Stück bis zum **nächsten**
   * Punkt. Der letzte Punkt hat keinen nächsten: Seine Viertelstunde fehlte im Bild, und eine
   * Reihe mit einem einzigen Messwert zeichnete gar nichts — obwohl die Legende eine Summe
   * nannte. Belegt am 21.11.2026 an einer frisch angelegten Einheit mit genau einem Intervall.
   *
   * Der Punkt trägt als drittes Element eine `1`, damit der Tooltip ihn überspringt: Er ist kein
   * Messwert. In die Summe der Legende geht er nicht ein, sie wird aus den Messwerten gebildet.
   */
  private mitAbschluss(daten: number[][]): number[][] {
    if (daten.length === 0) {
      return daten;
    }
    const letzter = daten[daten.length - 1];
    return [...daten, [letzter[0] + INTERVALL_MS, letzter[1], 1]];
  }

}
