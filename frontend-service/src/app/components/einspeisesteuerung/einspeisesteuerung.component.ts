import { AfterViewInit, Component, ElementRef, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { EinspeisesteuerungService } from '../../services/einspeisesteuerung.service';
import {
  STEUERREGELN,
  STEUERREGEL_KEYS,
  Prognosepunkt,
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
 * Höhe **eines** Zustandsbandes als Anteil der höchsten dargestellten Menge.
 *
 * <p><b>Warum relativ und nicht in kWh:</b> Zuerst standen hier feste Werte (Band von −0.1 bis
 * −1.0 kWh), ausgelegt für eine Achse bis etwa 25 kWh. Die Achse skaliert aber mit den Daten: An
 * einem Tag mit höchstens 1 kWh je Viertelstunde war ein Band mehr als doppelt so hoch wie der
 * ganze Datenbereich und drückte die Kurven in das obere Drittel. Ein Band soll immer denselben
 * *Anteil* der Höhe einnehmen, nicht dieselbe *Menge*.
 */
const BAND_HOEHE_ANTEIL = 0.04;

/** Abstand über, zwischen und unter den Bändern — ebenfalls als Anteil der höchsten Menge. */
const BAND_ABSTAND_ANTEIL = 0.015;

/**
 * Ersatz für die Bezugsgrösse, wenn alle Mengen 0 sind (Tag ohne Messwerte).
 *
 * <p>Ohne ihn wäre jede Bandhöhe 0 und beide Bänder unsichtbar — und zwar genau an den Tagen, an
 * denen die Bänder die einzige Aussage des Diagramms sind.
 */
const MENGE_ERSATZ = 1;

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

  /** Entscheide des Tages, **aufsteigend** — die Form, die das Diagramm braucht. */
  entscheide: Steuerentscheid[] = [];

  /**
   * Dieselben Entscheide, **neuste zuerst** — die Form für die Protokolltabelle.
   *
   * <p><b>Zwei Listen, nicht eine gedrehte:</b> Das Diagramm zeichnet eine Zeitachse und
   * `bloecke()` fasst aufeinanderfolgende Intervalle zusammen; beides setzt aufsteigende
   * Reihenfolge voraus. Würde `entscheide` selbst umgedreht, liefen die Kurven rückwärts und die
   * Zustandsbänder zerfielen in Einzelrechtecke — ohne dass ein Test es merkte.
   *
   * <p>Einmal beim Laden berechnet statt als Getter: Ein Getter liefe bei jedem
   * Change-Detection-Zyklus und gäbe jedes Mal ein neues Array zurück.
   */
  entscheideNeusteZuerst: Steuerentscheid[] = [];

  /**
   * Produktionsprognose des angezeigten Tages (`Specs/Ladeplanung.md`, FR-3).
   *
   * <p>Eigene Liste neben den Entscheiden, weil sie die **Zukunft** beschreibt: Entscheide gibt es
   * nur fuer abgeschlossene Intervalle, die Prognose auch fuer den Resttag. Genau der ist der
   * interessante Teil.
   *
   * <p>Leer, solange kein Standort erfasst oder noch nichts abgerufen ist — dann zeigt das
   * Diagramm die Kurve schlicht nicht.
   */
  prognose: Prognosepunkt[] = [];

  /** Prognose nach Intervallbeginn, zum Zuordnen in der Tabelle. */
  private prognoseJeZeit = new Map<string, Prognosepunkt>();

  loading = false;

  /** Schwellwert der Rückrechnung; vorbelegt mit dem des ersten Entscheids. */
  simulationSchwellwert: number | null = null;

  /**
   * Zu erprobender Mindest-Preisabstand; vorbelegt mit dem des ersten Entscheids.
   *
   * <p>Die **zweite** Groesse, an der sich beim Kalibrieren drehen laesst. Sie entscheidet, ob ein
   * Tal die Sperre ueberhaupt wert ist — ein zu kleiner Abstand laesst die Steuerung fuer einen
   * halben Rappen Sonnenstunden verstreichen, ein zu grosser schaltet `WARTEN_AUF_TAL` faktisch ab.
   */
  simulationAbstand: number | null = null;
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

  /**
   * Mindest-Preisabstand, mit dem der angezeigte Tag nachgerechnet ist.
   *
   * <p>Muss neben dem Schwellwert mitgefuehrt werden: Sonst laedt ein Tageswechsel den Tag zwar
   * weiter nachgerechnet, aber mit dem Abstand des **Mandanten** statt dem erprobten — und die
   * Tagesansicht widersprache den Kennzahlen darunter, ohne dass man es sähe.
   */
  simuliertMitAbstand: number | null = null;

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
      ? this.steuerungService.getEntscheideSimuliert(this.datum, this.simuliertMitSchwellwert,
          null, this.simuliertMitAbstand)
      : this.steuerungService.getEntscheide(this.datum);

    // Die Prognose kommt aus einer EIGENEN Abfrage und wird bewusst nicht mit den Entscheiden
    // verschraenkt: Sie liegt auch fuer Tage vor, an denen es keine Entscheide gibt, und sie
    // reicht ueber das letzte ausgewertete Intervall hinaus.
    this.ladePrognose();

    quelle.subscribe({
      next: (daten) => {
        this.entscheide = daten;
        this.entscheideNeusteZuerst = this.neusteZuerst(daten);
        this.loading = false;
        if (this.simulationSchwellwert == null && daten.length > 0) {
          this.simulationSchwellwert = daten[0].schwellwert;
        }
        if (this.simulationAbstand == null && daten.length > 0) {
          this.simulationAbstand = daten[0].mindestAbstand;
        }
        void this.zeichne();
      },
      error: (error) => {
        this.loading = false;
        this.entscheide = [];
        this.entscheideNeusteZuerst = [];
        this.showMessage(error.error || 'STEUERUNG_FEHLER_LADEN', 'error');
      }
    });
  }

  /**
   * Prognose des angezeigten Tages holen.
   *
   * <p><b>Ein Fehler bleibt stumm.</b> Die Prognose ist eine Zugabe; die Seite funktioniert ohne
   * sie vollstaendig. Eine Fehlermeldung wuerde den Blick auf das Protokoll verstellen, um das es
   * hier geht — im Log steht der Fehler trotzdem.
   */
  private ladePrognose(): void {
    this.steuerungService.getPrognose(this.datum).subscribe({
      next: (punkte) => {
        this.prognose = punkte;
        this.prognoseJeZeit = new Map(punkte.map(p => [p.zeit, p]));
        void this.zeichne();
      },
      error: (error) => {
        this.prognose = [];
        this.prognoseJeZeit = new Map();
        console.warn('Prognose konnte nicht geladen werden', error);
      }
    });
  }

  /**
   * Die Prognose als **lückenloses** Zeitraster für das Diagramm.
   *
   * <p><b>Warum nicht einfach die Punkte abbilden.</b> `connectNulls: false` greift nur bei einem
   * ausdrücklichen `null` im Datensatz. Fehlt dagegen eine ganze Zeile — und genau so kommen
   * Lücken aus der Datenbank —, sieht ECharts zwei benachbarte Punkte und zieht eine Gerade
   * darüber. Das Fehlen einer Vorhersage sähe dann aus wie eine Vorhersage.
   *
   * <p>Deshalb wird das 15-Minuten-Raster zwischen erstem und letztem bekannten Punkt aufgespannt
   * und jedes fehlende Intervall ausdrücklich mit `null` belegt.
   */
  private prognoseReihe(): (number | null)[][] {
    if (this.prognose.length === 0) {
      return [];
    }
    const werte = new Map(this.prognose.map(
      p => [new Date(p.zeit).getTime(), p.erwarteteErzeugung]));
    const zeiten = [...werte.keys()].sort((a, b) => a - b);

    const reihe: (number | null)[][] = [];
    for (let t = zeiten[0]; t <= zeiten[zeiten.length - 1]; t += INTERVALL_MS) {
      reihe.push([t, werte.get(t) ?? null]);
    }
    return reihe;
  }

  /** Prognosepunkt eines Entscheids — für die Tabellenspalten. */
  prognoseFuer(entscheid: Steuerentscheid): Prognosepunkt | undefined {
    return this.prognoseJeZeit.get(entscheid.zeit);
  }

  /** Einstrahlung in W/m², ohne Nachkommastellen; leer, wenn keine Prognose vorliegt. */
  einstrahlung(entscheid: Steuerentscheid): string {
    const punkt = this.prognoseFuer(entscheid);
    return punkt == null ? '' : formatSwissNumber(punkt.gti, 0);
  }

  /** Erwartete Erzeugung in kWh; leer, solange kein Faktor gelernt ist. */
  erwarteteErzeugung(entscheid: Steuerentscheid): string {
    const punkt = this.prognoseFuer(entscheid);
    return punkt?.erwarteteErzeugung == null ? '' : this.menge(punkt.erwarteteErzeugung);
  }

  /**
   * Zurück zur Aufzeichnung — zeigt wieder die gespeicherten Entscheide.
   *
   * <p>Die Kennzahlen der Rückrechnung bleiben stehen: Sie sind das Ergebnis, an dem der Benutzer
   * gerade arbeitet, und beziehen sich auf die ganze Historie, nicht auf den angezeigten Tag.
   */
  zeigeAufzeichnung(): void {
    this.simuliertMitSchwellwert = null;
    this.simuliertMitAbstand = null;
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
   * Zurück auf den heutigen Tag.
   *
   * <p>Ohne diese Schaltfläche führte der Weg zurück nur über wiederholtes Blättern oder über das
   * Datumsfeld — bei einem Vergleich über zwei Wochen hinweg ein Dutzend Klicks.
   *
   * <p>Die nachgerechnete Ansicht bleibt erhalten: Ein Sprung im Datum ist ein Tageswechsel wie
   * jeder andere (FR-6a), kein Wechsel der Betriebsart.
   */
  heute(): void {
    this.datum = this.heuteIso();
    this.ladeTag();
  }

  /** `true`, wenn der angezeigte Tag der heutige ist — dann ist „Heute" wirkungslos. */
  get zeigtHeute(): boolean {
    return this.datum === this.heuteIso();
  }

  /**
   * Die angezeigten Daten neu holen.
   *
   * <p>Der Job schreibt alle 15 Minuten einen weiteren Entscheid. Eine offene Tagesansicht merkt
   * davon nichts — sie lädt nur beim Öffnen und beim Tageswechsel. Ohne diese Schaltfläche bliebe
   * nur ein Neuladen der Seite.
   *
   * <p>Die Betriebsart bleibt: `ladeTag()` rechnet nach, wenn ein Schwellwert erprobt wird, und
   * liest sonst die Aufzeichnung. Ein „Aktualisieren" soll dieselbe Ansicht erneuern, nicht eine
   * andere zeigen.
   */
  aktualisieren(): void {
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
    const mindestAbstand = this.simulationAbstand;
    this.steuerungService.simuliere({ von, bis, schwellwert, mindestAbstand }).subscribe({
      next: (ergebnis) => {
        this.simulation = ergebnis;
        this.simulationLaeuft = false;
        // Den angezeigten Tag mit demselben Schwellwert nachrechnen: Die Kennzahlen sagen, WIE OFT
        // gesperrt worden waere - erst der Tagesverlauf zeigt, WANN. Ohne das blieben Diagramm und
        // Tabelle auf der Aufzeichnung stehen und widersprachen der Auswertung darunter.
        this.simuliertMitSchwellwert = schwellwert;
        this.simuliertMitAbstand = mindestAbstand;
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

  /**
   * `true`, sobald für **ein** Intervall des Tages Speichermengen vorliegen.
   *
   * <p>Entscheidet, ob die Produktionskurve verrechnet gezeichnet und entsprechend benannt wird.
   * Ein Tag ohne Speicherdaten sieht damit aus wie zuvor — und trägt auch dieselbe Beschriftung.
   */
  get hatSpeicherdaten(): boolean {
    return this.entscheide.some(e => e.speicherLadung != null || e.speicherEntladung != null);
  }

  /**
   * Erzeugung einschliesslich dessen, was in den Speicher ging —
   * `max(0, produktion + Ladung − Entladung)`.
   *
   * <p><b>Warum das nötig ist:</b> Der Hybrid-Wechselrichter gibt wechselstromseitig nur ab, was
   * das Haus braucht. Was aus der Erzeugung direkt in die Batterie fliesst, läuft über keinen
   * Erzeugungszähler und fehlt in `produktion` — bei voller Sonne stand dort weniger als der
   * Verbrauch. Umgekehrt erscheint eine Entladung dort als Erzeugung, obwohl sie keine ist:
   * Nachts misst der Zähler 0.1–0.3 kWh je Viertelstunde, ohne dass die Sonne scheint.
   *
   * <p><b>Warum die Schranke bei 0:</b> Die Entladung ist nur auf **0.1 kWh** genau (der
   * Wechselrichter führt seine Energiezähler in Zehnteln, siehe
   * `Specs/Solinteg_Modbus_Register.md`), die Produktion dagegen auf drei Stellen. Liegt die
   * wirkliche Entladung bei 0.17 kWh, meldet der Zähler mal 0.1 und mal 0.2 — die Differenz
   * schwankt um bis zu ±0.08, und nachts, wo sie null sein müsste, kippt sie ins Negative. Genau
   * das zeigte der Tagesverlauf. Eine negative Erzeugung gibt es nicht; die Schranke ist eine
   * physikalische Aussage, keine Kosmetik.
   *
   * <p>Die Rohwerte bleiben in der Tabelle sichtbar — wer nachrechnen will, findet dort
   * Produktion, Ladung und Entladung einzeln.
   *
   * <p><b>Nur für die Darstellung.</b> Überschuss, Regel und die gespeicherten Zustände bleiben auf
   * den gemessenen Werten.
   *
   * <p><b>Grenze:</b> Lädt die Batterie aus dem **Netz** statt aus der Erzeugung, zählt die Ladung
   * hier fälschlich zur Produktion. Der Fall tritt bei negativen Preisen auf.
   */
  produktionVerrechnet(e: Steuerentscheid): number {
    return Math.max(0, e.produktion + (e.speicherLadung ?? 0) - (e.speicherEntladung ?? 0));
  }

  /**
   * Kopie der Entscheide, absteigend nach Zeit.
   *
   * <p><b>Sortiert statt gedreht:</b> Ein `reverse()` gäbe nur dann das Richtige, wenn der Server
   * aufsteigend liefert. Das tut er heute (`ORDER BY zeitVon`), aber die Tabelle hinge damit an
   * einer Zusage, die sie selbst nicht prüfen kann.
   */
  private neusteZuerst(daten: Steuerentscheid[]): Steuerentscheid[] {
    return [...daten].sort(
      (a, b) => new Date(b.zeit).getTime() - new Date(a.zeit).getTime());
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
        // Kleiner als der ECharts-Standard (14 px): Der Tooltip nennt DREIZEHN Groessen - Zeit,
        // zwei Preise, gemessene und verrechnete Produktion, Ladung, Entladung, Verbrauch,
        // Ueberschuss, Regel, Ladezustand und die beiden Zustaende. Bei 14 px war er hoeher als
        // das Diagramm, und ECharts schneidet oben ab statt zu scrollen: Zeit und Preise, also
        // gerade die Fuehrungsgroessen, waren nicht mehr zu sehen.
        textStyle: { fontSize: 11 },
        padding: [6, 10],
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
          // `markArea` spannt die Skala - anders als eine Datenserie - NICHT auf: Ohne ein
          // gesetztes `min` endete die Achse bei 0 und die Baender waeren unsichtbar.
          min: this.achseMinMitBaendern(),
          nameTextStyle: { color: farben.text },
          axisLine: { lineStyle: { color: farben.achse } },
          // Negative Werte gehoeren zu den Baendern, nicht zu einer Menge - als Achsenbeschriftung
          // waeren sie irrefuehrend. `String(w)` stand hier zuerst und taugt nicht: Bei einer
          // kleinteiligen Achse liefert die Gleitkommarechnung Beschriftungen wie
          // "0.30000000000000004".
          axisLabel: {
            color: farben.text,
            formatter: (w: number) =>
              (w < 0 ? '' : formatSwissNumber(w, this.mengenNachkommastellen()))
          },
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
          // Der Name wechselt mit der Datenlage: Ohne Speicherdaten zeigt die Kurve die gemessene
          // Produktion und heisst so. Liegen sie vor, ist sie verrechnet - und muss das auch sagen,
          // sonst stuende in der Legende "Produktion" ueber einer Zahl, die in keiner Tabelle steht.
          name: this.translationService.translate(
            this.hatSpeicherdaten ? 'STEUERUNG_PRODUKTION_VERRECHNET' : 'PRODUKTION'),
          type: 'line',
          areaStyle: { opacity: 0.2 },
          showSymbol: false,
          yAxisIndex: 1,
          // GELB fuer die Solarproduktion - die uebliche Zuordnung fuer Sonnenenergie. Vorher
          // gruen; der Ton kommt aus einem eigenen Chart-Token und nicht aus `--color-warning`,
          // das im Dark Mode selbst gelb ist und dort das Zustandsband faerbt.
          itemStyle: { color: farben.solar },
          lineStyle: { color: farben.solar },
          data: this.entscheide.map((e, i) => [zeiten[i], this.produktionVerrechnet(e)])
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
        {
          // Die PROGNOSE, gestrichelt und auf derselben kWh-Achse wie die gemessene Produktion:
          // Nur so laesst sich am Abend ablesen, wie gut die Vorhersage war. Die Einstrahlung
          // selbst (W/m2) steht in der Tabelle - eine vierte y-Achse haette hier keinen Platz,
          // der rechte Rand musste fuer die dritte schon von 60 auf 115 wachsen.
          name: this.translationService.translate('LADEPLANUNG_PROGNOSE'),
          type: 'line',
          lineStyle: { color: farben.flaeche, width: 2, type: 'dashed' },
          itemStyle: { color: farben.flaeche },
          showSymbol: false,
          yAxisIndex: 1,
          // Luecken NICHT verbinden: Fehlt die Prognose fuer ein Intervall, soll die Linie
          // aussetzen statt eine Gerade darueber zu ziehen, die es so nie gab.
          connectNulls: false,
          data: this.prognoseReihe()
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
    const [oben, unten] = this.bandKanten(ebene);

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
   * Höchste dargestellte Menge — die Bezugsgrösse für alles unterhalb der Nulllinie.
   *
   * <p>Die Mengen-Achse skaliert mit den Daten. Damit ein Zustandsband immer denselben Anteil der
   * Höhe einnimmt, müssen seine Kanten aus derselben Grösse abgeleitet sein, an der sich auch die
   * Achse ausrichtet.
   */
  private hoechsteMenge(): number {
    let hoechste = 0;
    for (const e of this.entscheide) {
      // Die VERRECHNETE Produktion, weil genau sie gezeichnet wird - sonst liefe die Kurve an
      // Tagen mit viel Ladung ueber den oberen Rand der Achse hinaus.
      hoechste = Math.max(hoechste, this.produktionVerrechnet(e), e.verbrauch ?? 0);
    }
    // Die Prognose gehoert in die Bezugsgroesse: Liegt sie ueber der gemessenen Produktion - und
    // genau das ist am Morgen der Normalfall -, liefe die Kurve sonst oben aus dem Bild.
    for (const punkt of this.prognose) {
      hoechste = Math.max(hoechste, punkt.erwarteteErzeugung ?? 0);
    }
    return hoechste > 0 ? hoechste : MENGE_ERSATZ;
  }

  /**
   * Ober- und Unterkante eines Zustandsbandes auf der Mengen-Achse.
   *
   * <p>Ebene 1 liegt direkt unter der Nulllinie, Ebene 2 darunter — jede auf ihrer festen Position,
   * unabhängig davon, ob die andere gesetzt ist. So bedeutet dieselbe Höhe immer dieselbe Grösse.
   */
  private bandKanten(ebene: number): [number, number] {
    const bezug = this.hoechsteMenge();
    const hoehe = bezug * BAND_HOEHE_ANTEIL;
    const abstand = bezug * BAND_ABSTAND_ANTEIL;
    const oben = -(abstand + (ebene - 1) * (hoehe + abstand));
    return [oben, oben - hoehe];
  }

  /** Untere Grenze der Mengen-Achse: knapp unter dem tiefsten Band. */
  private achseMinMitBaendern(): number {
    return this.bandKanten(2)[1] - this.hoechsteMenge() * BAND_ABSTAND_ANTEIL;
  }

  /**
   * Nachkommastellen der Mengen-Achse, abgeleitet aus der Grössenordnung.
   *
   * <p>Eine Achse bis 25 kWh braucht keine, eine bis 1 kWh zwei — sonst trügen mehrere Striche
   * dieselbe Beschriftung.
   */
  private mengenNachkommastellen(): number {
    const bezug = this.hoechsteMenge();
    if (bezug >= 10) {
      return 0;
    }
    if (bezug >= 1) {
      return 1;
    }
    return bezug >= 0.1 ? 2 : 3;
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
      // Beide Werte, nicht nur der verrechnete: Die Differenz ist genau das, was der
      // Erzeugungszaehler nicht sieht - und der Grund, warum die Kurve hoeher laeuft als die Spalte.
      + (e.speicherLadung == null && e.speicherEntladung == null ? ''
        : `${t('STEUERUNG_PRODUKTION_VERRECHNET')}: `
          + `${this.menge(this.produktionVerrechnet(e))} kWh<br>`
        + `${t('STEUERUNG_LADUNG')}: ${this.menge(e.speicherLadung)} kWh<br>`
        + `${t('STEUERUNG_ENTLADUNG')}: ${this.menge(e.speicherEntladung)} kWh<br>`)
      + (this.prognoseFuer(e) == null ? ''
        : `${t('LADEPLANUNG_EINSTRAHLUNG')}: ${this.einstrahlung(e)} W/m²<br>`
        + (this.erwarteteErzeugung(e) === '' ? ''
          : `${t('LADEPLANUNG_PROGNOSE')}: ${this.erwarteteErzeugung(e)} kWh<br>`))
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
