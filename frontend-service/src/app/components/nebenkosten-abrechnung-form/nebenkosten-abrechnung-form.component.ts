import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CdkDrag, CdkDragDrop, CdkDropList, moveItemInArray } from '@angular/cdk/drag-drop';
import { NebenkostenService } from '../../services/nebenkosten.service';
import { TranslationService } from '../../services/translation.service';
import {
  NkAbrechnung,
  NkAbrechnungDetail,
  NkAkonto,
  NkPerson,
  NkBerechnung,
  NkMieterAbrechnung,
  NkPosition,
  NkPositionsart,
  NkUmlageInfo,
  NkZeile,
  NkZusatz,
  NK_ARTEN_MIT_EINGABE_JE_MIETER,
  NK_ARTEN_OHNE_EINHEIT,
  NK_MENGENEINHEITEN,
  NK_POSITIONSARTEN,
  leerePosition
} from '../../models/nebenkosten.model';
import { Mengeneinheit } from '../../models/tarif.model';
import { NkMieterTage, PERSONEN_VORGABE, berechneVorschau } from '../../utils/nebenkosten-berechnung';
import { formatSwissNumber } from '../../utils/number-utils';
import { TranslatePipe } from '../../pipes/translate.pipe';
import { IconComponent } from '../icon/icon.component';

/**
 * Bearbeitungsmaske einer Nebenkostenabrechnung
 * (Specs/Nebenkosten/Abrechnung.md, FR-7).
 *
 * Drei Bereiche untereinander: Angaben zur Abrechnung, allgemeine Positionen und je Mieter ein
 * Block. Die Beträge werden bei jeder Änderung neu gerechnet — **als Vorschau**. Nach dem
 * Speichern zeigt die Maske die Werte des Servers, nicht die eigenen (Entscheid 2 des
 * Umsetzungsplans): Weicht die Vorschau ab, fällt das im selben Moment auf.
 *
 * Die Mieterblöcke entstehen erst, wenn die Abrechnung **gespeichert** ist — die Miettage kommen
 * vom Server, der als einziger die Mietverhältnisse kennt.
 */
@Component({
  selector: 'app-nebenkosten-abrechnung-form',
  standalone: true,
  imports: [CommonModule, FormsModule, CdkDropList, CdkDrag, TranslatePipe, IconComponent],
  templateUrl: './nebenkosten-abrechnung-form.component.html',
  styleUrls: ['./nebenkosten-abrechnung-form.component.css']
})
export class NebenkostenAbrechnungFormComponent implements OnInit {
  /** `null` legt eine neue Abrechnung an. */
  @Input() abrechnungId: number | null = null;

  @Output() saved = new EventEmitter<void>();
  @Output() closed = new EventEmitter<void>();

  readonly Positionsart = NkPositionsart;
  readonly positionsarten = NK_POSITIONSARTEN;
  readonly mengeneinheiten = NK_MENGENEINHEITEN;

  kopf: NkAbrechnung = {
    bezeichnung: '',
    datumVon: '',
    datumBis: '',
    anzahlWohnungen: null,
    anzahlPersonen: null,
    abgerechnet: false
  };

  positionen: NkPosition[] = [];
  zusaetze: NkZusatz[] = [];
  akonto: NkAkonto[] = [];
  personen: NkPerson[] = [];

  /** Vom Server gelieferte Miettage — Grundlage der clientseitigen Vorschau. */
  mieterTage: NkMieterTage[] = [];

  berechnung: NkBerechnung | null = null;
  anzahlWohnungenVorschlag: number | null = null;
  anzahlPersonenVorschlag: number | null = null;

  /**
   * Hat der Benutzer die Anzahl Personen selbst gesetzt?
   *
   * <p>Solange nicht, **folgt** sie der Anzahl Wohnungen — das ist die Vorgabe „Default = Anzahl
   * Wohnungen" (`Specs/Nebenkosten/Abrechnung.md`, FR-2). Ohne dieses Nachziehen bliebe sie auf
   * dem Vorschlag des Servers (Zahl der nebenkostenrelevanten Einheiten) stehen, und wer die
   * Anzahl Wohnungen ändert, bekäme zwei verschiedene Nenner: Eine Umlage pro Person verteilte
   * dann anders als eine Umlage pro Wohnung, obwohl noch keine Personenzahl erfasst ist.
   *
   * <p>Ab dem ersten Speichern ist die Zahl erfasst und folgt nicht mehr — sonst verschöbe eine
   * Korrektur der Wohnungszahl stillschweigend die Personenumlage einer bestehenden Abrechnung.
   */
  private personenFolgtWohnungen = true;

  laedt = false;
  message = '';
  messageType: 'success' | 'error' = 'success';

  /**
   * Wurde in dieser Maske schon einmal auf Speichern gedrückt?
   *
   * <p>Feldfehler erscheinen erst danach. Eine frisch geöffnete Maske für eine neue Abrechnung ist
   * zwangsläufig unvollständig; sie mit roten Meldungen zu empfangen, tadelt den Benutzer für
   * etwas, das er noch gar nicht getan hat.
   */
  speichernVersucht = false;

  /**
   * Merker für den weggeklickten Hinweis „Mieterblöcke erscheinen nach dem Speichern".
   *
   * Als einziger der drei Hinweise ist er eine **einmalige Erklärung** und bleibt deshalb
   * dauerhaft verborgen (je Browser). Die beiden anderen beschreiben den Zustand einer
   * bestimmten Abrechnung oder eines bestimmten Mieters; sie dauerhaft auszublenden hiesse,
   * beim nächsten betroffenen Datensatz die Begründung zu verschweigen.
   */
  private static readonly HINWEIS_STORAGE_KEY = 'zev.nebenkosten.hinweisErstSpeichern';

  hinweisErstSpeichernSichtbar = true;
  hinweisAbgerechnetSichtbar = true;
  private hinweisOhneWohnungAusgeblendet = new Set<number>();

  /**
   * Aufgeklappte Mieterblöcke. Anfangs leer — **alle geschlossen**: Bei dreissig Mietern wäre die
   * Maske sonst mehrere Bildschirmseiten lang, und die Angaben zur Abrechnung verschwänden aus
   * dem Blick. Der Zustand gilt nur für die geöffnete Maske und wird nicht gespeichert.
   */
  private offeneMieter = new Set<number>();

  constructor(
    private nebenkostenService: NebenkostenService,
    private translationService: TranslationService
  ) { }

  ngOnInit(): void {
    this.hinweisErstSpeichernSichtbar = !this.leseHinweisAusgeblendet();
    if (this.abrechnungId) {
      this.ladeDetail(this.abrechnungId);
    } else {
      this.ladeVorlage();
    }
  }

  /** Blendet den Erklärhinweis dauerhaft aus (je Browser). */
  dismissHinweisErstSpeichern(): void {
    this.hinweisErstSpeichernSichtbar = false;
    try {
      localStorage.setItem(NebenkostenAbrechnungFormComponent.HINWEIS_STORAGE_KEY, 'true');
    } catch {
      // localStorage nicht verfuegbar - der Hinweis erscheint dann beim naechsten Aufruf erneut
    }
  }

  /** Blendet den Sperrhinweis für die geöffnete Maske aus; beim nächsten Öffnen ist er zurück. */
  dismissHinweisAbgerechnet(): void {
    this.hinweisAbgerechnetSichtbar = false;
  }

  istMieterOffen(mieterId: number): boolean {
    return this.offeneMieter.has(mieterId);
  }

  toggleMieter(mieterId: number): void {
    if (this.offeneMieter.has(mieterId)) {
      this.offeneMieter.delete(mieterId);
    } else {
      this.offeneMieter.add(mieterId);
    }
  }

  hinweisOhneWohnungSichtbar(mieterId: number): boolean {
    return !this.hinweisOhneWohnungAusgeblendet.has(mieterId);
  }

  /** Je Mieter einzeln: Wer einen Hinweis wegklickt, meint diesen einen Block. */
  dismissHinweisOhneWohnung(mieterId: number): void {
    this.hinweisOhneWohnungAusgeblendet.add(mieterId);
  }

  private leseHinweisAusgeblendet(): boolean {
    try {
      return localStorage.getItem(NebenkostenAbrechnungFormComponent.HINWEIS_STORAGE_KEY) === 'true';
    } catch {
      return false;
    }
  }

  /** Gesperrt, sobald die Abrechnung abgeschlossen ist; nur das Flag selbst bleibt bedienbar. */
  get gesperrt(): boolean {
    return this.kopf.abgerechnet;
  }

  /** Mieterblöcke gibt es erst nach dem ersten Speichern — vorher fehlen die Miettage. */
  get hatMieterbloecke(): boolean {
    return this.mieterTage.length > 0;
  }

  /**
   * Holt die Vorlage einer neuen Abrechnung — im Wesentlichen den Vorschlag für die Anzahl
   * Wohnungen.
   *
   * <p><b>Bewusst ohne {@link #uebernehme}:</b> Die Maske ist bedienbar, während die Vorlage noch
   * unterwegs ist. Ein spät eintreffender Stand überschrieb die ersten Eingaben — der Benutzer
   * tippte eine Bezeichnung, und sie war wieder weg. Die Vorlage trägt ohnehin nichts als leere
   * Listen und den Vorschlag; genau der wird hier übernommen, und die Anzahl Wohnungen nur, wenn
   * noch keine dasteht.
   *
   * <p>Sichtbar wurde das an zwei Fällen der E2E-Suite: Beide scheiterten beim ersten Speichern
   * einer neuen Abrechnung mit „Eingaben prüfen", weil die Bezeichnung zwischen Tippen und Klick
   * verschwunden war.
   */
  ladeVorlage(): void {
    this.laedt = true;
    this.nebenkostenService.getVorlage().subscribe({
      next: (detail) => {
        this.anzahlWohnungenVorschlag = detail.anzahlWohnungenVorschlag;
        this.anzahlPersonenVorschlag = detail.anzahlPersonenVorschlag;
        if (this.kopf.anzahlWohnungen === null) {
          this.kopf.anzahlWohnungen = detail.anzahlWohnungenVorschlag;
        }
        if (this.kopf.anzahlPersonen === null) {
          this.kopf.anzahlPersonen = detail.anzahlPersonenVorschlag;
        }
        this.laedt = false;
      },
      error: () => {
        this.showMessage('NK_FEHLER_LADEN', 'error');
        this.laedt = false;
      }
    });
  }

  /**
   * Lädt den Stand des Servers und zeigt ihn an.
   *
   * @param id            ID der Abrechnung
   * @param erfolgsmeldung Meldung, die **nach** erfolgreichem Laden erscheint. Bewusst hier und
   *                       nicht beim Aufrufer: Vor dem Ergebnis gezeigt, würde ihr
   *                       Fünf-Sekunden-Timer eine danach eintreffende Fehlermeldung mitnehmen.
   */
  ladeDetail(id: number, erfolgsmeldung?: string): void {
    this.laedt = true;
    this.nebenkostenService.getAbrechnungDetail(id).subscribe({
      next: (detail) => {
        this.uebernehme(detail);
        this.laedt = false;
        if (erfolgsmeldung) {
          this.showMessage(erfolgsmeldung, 'success');
        }
      },
      error: () => {
        this.showMessage('NK_FEHLER_LADEN', 'error');
        this.laedt = false;
      }
    });
  }

  // ==================== Positionen ====================

  onPositionHinzufuegen(): void {
    this.positionen.push(leerePosition(NkPositionsart.UMLAGE));
    this.rechne();
  }

  onPositionEntfernen(index: number): void {
    this.positionen.splice(index, 1);
    this.rechne();
  }

  /**
   * Wechselt die Art einer Zeile und räumt die Felder der alten Art ab.
   *
   * Ohne das Abräumen bliebe ein Totalbetrag an einer Zuschlagszeile hängen: unsichtbar, weil die
   * Maske ihn ausblendet, aber im Rumpf des Requests — und dort läuft er in den CHECK-Constraint.
   */
  onArtChange(position: NkPosition): void {
    position.totalbetrag = null;
    position.gesamtmenge = null;
    position.betragProEinheit = null;
    position.prozentsatz = null;
    position.verbraeuche = [];
    position.einheit = NK_ARTEN_OHNE_EINHEIT.includes(position.art) ? null : Mengeneinheit.M3;
    this.rechne();
  }

  onDrop(event: CdkDragDrop<NkPosition[]>): void {
    moveItemInArray(this.positionen, event.previousIndex, event.currentIndex);
    // Die Reihenfolge bestimmt die Bemessungsgrundlage der Zuschlaege - deshalb sofort neu rechnen.
    this.rechne();
  }

  /**
   * Stabile Identität einer erfassbaren Zeile (Position wie Zusatzzeile).
   *
   * `track zeile` - also Identität - warf NG0956: `uebernehme()` ersetzt die Listen nach dem
   * Laden und nach dem Speichern durch frische Objekte vom Server, womit Angular jede Zeile
   * samt Eingabefeldern verwirft und neu aufbaut (Fokus und Cursor gehen verloren). Gespeicherte
   * Zeilen tragen ihre ID; noch nicht gespeicherte behelfen sich mit dem Index - sie hängen am
   * Ende der Liste und erhalten ihre ID beim Speichern.
   */
  trackZeile(index: number, zeile: { id?: number }): string {
    return zeile.id != null ? `id-${zeile.id}` : `neu-${index}`;
  }

  /** Die Bemessungsgrundlage einer Zuschlagszeile: alles, was in der Liste darüber steht. */
  bemessungsgrundlage(index: number): string {
    const davor = this.positionen.slice(0, index).map(p => p.bezeichnung).filter(b => !!b);
    return davor.length > 0 ? davor.join(', ') : this.translationService.translate('NK_KEINE_ZEILEN_DAVOR');
  }

  // ==================== Verbrauchsmengen und Zusatzzeilen ====================

  /** Erfasste Menge eines Mieters zu einer Verbrauchsposition; legt die Zeile bei Bedarf an. */
  mengeFuer(position: NkPosition, mieterId: number): number | null {
    return position.verbraeuche.find(v => v.mieterId === mieterId)?.menge ?? null;
  }

  /**
   * Übernimmt eine eingetippte Menge.
   *
   * Nimmt Zahl **und** Zeichenkette entgegen: Über `ngModelChange` kommt bei einem Zahlenfeld eine
   * Zahl oder `null`, bei einem leeren Feld je nach Browser auch ein leerer String.
   */
  onMengeChange(position: NkPosition, mieterId: number, wert: string | number | null): void {
    const menge = wert === '' || wert === null || wert === undefined ? null : Number(wert);
    const vorhanden = position.verbraeuche.find(v => v.mieterId === mieterId);
    if (vorhanden) {
      vorhanden.menge = menge;
    } else {
      position.verbraeuche.push({ mieterId, menge });
    }
    this.rechne();
  }

  zusaetzeFuer(mieterId: number): NkZusatz[] {
    return this.zusaetze.filter(z => z.mieterId === mieterId);
  }

  onZusatzHinzufuegen(mieterId: number): void {
    this.zusaetze.push({
      mieterId,
      // Ans Ende der Kaskade: eine neue Zeile soll bestehende Zuschlaege nicht ruecklaufend
      // veraendern, solange der Benutzer sie nicht bewusst nach vorne schiebt.
      reihenfolge: this.positionen.length + this.zusaetzeFuer(mieterId).length + 1,
      bezeichnung: '',
      einheit: Mengeneinheit.STUECK,
      menge: null,
      betragProEinheit: null
    });
    this.rechne();
  }

  onZusatzEntfernen(zusatz: NkZusatz): void {
    const index = this.zusaetze.indexOf(zusatz);
    if (index >= 0) {
      this.zusaetze.splice(index, 1);
      this.rechne();
    }
  }

  // ==================== Akonto ====================

  akontoFuer(mieterId: number): NkAkonto {
    let vorhanden = this.akonto.find(a => a.mieterId === mieterId);
    if (!vorhanden) {
      // Vorschlag des Servers uebernehmen, damit die Werte beim Speichern nicht verlorengehen.
      const block = this.berechnung?.mieter.find(m => m.mieterId === mieterId);
      vorhanden = {
        mieterId,
        anzahlMonate: block?.akontoAnzahlMonate ?? null,
        betragProMonat: block?.akontoBetragProMonat ?? null,
        korrektur: block?.akontoKorrektur ?? 0
      };
      this.akonto.push(vorhanden);
    }
    return vorhanden;
  }

  // ==================== Speichern ====================

  onSpeichern(): void {
    this.speichereUnd();
  }

  /**
   * Speichert und führt danach optional etwas aus.
   *
   * @param danach läuft **nur** nach erfolgreichem Speichern. Scheitert die Prüfung oder der
   *               Server, bleibt die Maske stehen und zeigt den Grund — ein Weitergehen würde
   *               genau die Eingaben verwerfen, die gerade gesichert werden sollten.
   */
  private speichereUnd(danach?: () => void): void {
    this.speichernVersucht = true;
    if (!this.istGueltig()) {
      // Die Feldfehler stehen jetzt an den Feldern; die Meldung oben sagt, dass gar nicht
      // gespeichert wurde - sonst bliebe der Klick ohne erkennbare Wirkung.
      this.showMessage('NK_FEHLER_EINGABEN', 'error');
      return;
    }

    if (this.abrechnungId) {
      this.speichereDetail(this.abrechnungId, danach);
    } else {
      // Erst anlegen, dann den vollstaendigen Stand schreiben: Positionen brauchen eine ID.
      this.nebenkostenService.createAbrechnung(this.kopf).subscribe({
        next: (erstellt) => {
          this.abrechnungId = erstellt.id ?? null;
          if (this.abrechnungId) {
            this.speichereDetail(this.abrechnungId, danach);
          }
        },
        error: (error) => this.showMessage(error.error || 'NK_FEHLER_SPEICHERN', 'error')
      });
    }
  }

  /**
   * Verwirft die nicht gespeicherten Änderungen und zeigt den Stand des Servers.
   *
   * <p>Die Maske bleibt dabei **offen**. Vorher tat „Abbrechen" dasselbe wie „Zurück zur
   * Übersicht" — zwei Schaltflächen mit demselben Verhalten, von denen eine ein Versprechen gab,
   * das sie nicht hielt: Verworfen wurde nichts, die Maske wurde bloss verlassen.
   *
   * <p>Bei einer **noch nicht gespeicherten** Abrechnung gibt es keinen Stand, auf den man
   * zurückfallen könnte. Dort schliesst „Abbrechen" die Maske wie bisher (Entscheid).
   */
  onAbbrechen(): void {
    if (!this.abrechnungId) {
      this.closed.emit();
      return;
    }

    // Feldfehler eines gescheiterten Speicherversuchs gehören zu Eingaben, die es nicht mehr gibt.
    this.speichernVersucht = false;
    this.ladeDetail(this.abrechnungId, 'NK_AENDERUNGEN_VERWORFEN');
  }

  /**
   * Speichert und geht zur Liste zurück.
   *
   * <p>„Ich bin fertig" heisst: sichern und raus. Scheitert das Speichern — ungültige Eingaben
   * oder ein Fehler des Servers —, bleibt die Maske stehen und zeigt den Grund. Ein Verlassen
   * würde genau die Eingaben verwerfen, die gerade gesichert werden sollten.
   *
   * <p>Bei einer <b>abgeschlossenen</b> Abrechnung gibt es nichts zu speichern: Die Felder sind
   * gesperrt, und der Server wiese das Schreiben ab. Dort führt der Weg direkt zurück.
   *
   * <p>Abgegrenzt von {@link onAbbrechen}: Dieses verwirft und bleibt, dieses hier sichert und
   * geht.
   */
  onZurueckZurUebersicht(): void {
    if (this.gesperrt) {
      this.closed.emit();
      return;
    }
    this.speichereUnd(() => this.closed.emit());
  }

  /**
   * Anzahl Wohnungen geändert: Die Anzahl Personen zieht nach, solange sie nicht erfasst ist.
   */
  onAnzahlWohnungenChange(): void {
    if (this.personenFolgtWohnungen) {
      this.kopf.anzahlPersonen = this.kopf.anzahlWohnungen;
    }
    this.rechne();
  }

  /** Anzahl Personen von Hand gesetzt: Ab jetzt folgt sie der Anzahl Wohnungen nicht mehr. */
  onAnzahlPersonenChange(): void {
    this.personenFolgtWohnungen = false;
    this.rechne();
  }

  istGueltig(): boolean {
    return !!(
      this.kopf.bezeichnung.trim() &&
      this.kopf.datumVon &&
      this.kopf.datumBis &&
      this.kopf.datumVon <= this.kopf.datumBis &&
      this.kopf.anzahlWohnungen !== null &&
      this.kopf.anzahlWohnungen >= 1 &&
      this.kopf.anzahlPersonen !== null &&
      this.kopf.anzahlPersonen >= 1
    );
  }

  istZeitraumGueltig(): boolean {
    return !this.kopf.datumVon || !this.kopf.datumBis || this.kopf.datumVon <= this.kopf.datumBis;
  }

  // ==================== Anzeige ====================

  betrag(wert: number | null | undefined): string {
    return formatSwissNumber(wert ?? 0, 2);
  }

  menge(wert: number | null | undefined): string {
    return wert === null || wert === undefined ? '' : formatSwissNumber(wert, 3);
  }

  /**
   * Kontrollzahlen der Umlageposition, aus der eine Zeile stammt.
   *
   * Gesucht wird über die Datenbank-ID der Zeile; eine noch nicht gespeicherte Position hat keine
   * und wird über die negierte Reihenfolge gefunden — derselbe Schlüssel, den die Vorschau vergibt.
   */
  umlageInfoFuer(zeile: NkZeile): NkUmlageInfo | undefined {
    const schluessel = zeile.positionId ?? -zeile.reihenfolge;
    return this.berechnung?.umlagen.find(u => u.positionId === schluessel);
  }

  /**
   * Nimmt diese Zeile eine Eingabe je Mieter entgegen?
   *
   * <p>Ja bei `VERBRAUCH` (Menge) und `ANTEIL` (Prozentsatz) — beide stammen aus einer allgemeinen
   * Position, deren Wert erst der Mieterblock füllt. Zusatzzeilen haben ihre eigenen Felder,
   * Umlage und Zuschlag sind gerechnet.
   */
  istEingebbareZeile(zeile: NkZeile): boolean {
    return !this.gesperrt
      && !this.istZusatzZeile(zeile)
      && NK_ARTEN_MIT_EINGABE_JE_MIETER.includes(zeile.art);
  }

  /**
   * Ergibt die Summe der Anteile 100%?
   *
   * <p>Die Toleranz fängt **Gleitkomma-Rauschen** ab (Summen wie 99.9999999), nicht eine echte
   * Lücke. Hier stand, sie verhindere die Meldung bei 33.333 × 3 = 99.999 — das trifft nicht zu:
   * 0.001 liegt über der Toleranz und wird gemeldet. Das ist auch richtig so, denn der
   * Prozentsatz je Mieter wird mit drei Nachkommastellen erfasst
   * ({@code nk_verbrauch.menge}, `NUMERIC(12,3)`), genau 100 % ist also erreichbar:
   * 33.334 + 33.333 + 33.333.
   */
  summeProzentStimmt(info: NkUmlageInfo): boolean {
    return Math.abs(info.summeProzent - 100) < 0.0005;
  }

  /**
   * Zusatzzeilen sind bearbeitbar, allgemeine nicht — erkennbar an der Herkunft der Zeile.
   *
   * **`!= null` und nicht `!== undefined`:** Das Backend schickt nicht gesetzte Felder als
   * `null` mit (Jackson mit Standard-Inclusion). Ein Vergleich auf `undefined` haette jede
   * allgemeine Position als Zusatzzeile eingestuft und damit die Mengenfelder aller Mieter
   * gesperrt — bis die erste clientseitige Neuberechnung die Zeilen ohne das Feld neu aufbaute.
   */
  istZusatzZeile(zeile: NkZeile): boolean {
    return zeile.zusatzId != null;
  }

  /**
   * Die Position, aus der eine berechnete Zeile stammt.
   *
   * Zuerst über die ID, weil sie eindeutig ist. Der Rückfall auf die Reihenfolge greift bei einer
   * noch nicht gespeicherten Position: In der Vorschau steht dort die Listenposition, nicht die
   * Datenbank-ID.
   */
  positionZuZeile(zeile: NkZeile): NkPosition | undefined {
    const ueberId = this.positionen.find(p => p.id != null && p.id === zeile.positionId);
    return ueberId ?? this.positionen[zeile.reihenfolge - 1];
  }

  dismissMessage(): void {
    this.message = '';
  }

  private speichereDetail(id: number, danach?: () => void): void {
    const detail: NkAbrechnungDetail = {
      abrechnung: this.kopf,
      positionen: this.positionen,
      zusaetze: this.zusaetze,
      akonto: this.akonto,
      personen: this.personen,
      anzahlWohnungenVorschlag: this.anzahlWohnungenVorschlag,
      anzahlPersonenVorschlag: this.anzahlPersonenVorschlag
    };

    this.nebenkostenService.updateAbrechnung(id, detail).subscribe({
      next: (gespeichert) => {
        // Bewusst die Antwort des Servers uebernehmen statt die eigene Vorschau stehenzulassen:
        // Das Backend ist massgebend, eine Abweichung wird so sofort sichtbar.
        this.uebernehme(gespeichert);
        this.showMessage('NK_ABRECHNUNG_GESPEICHERT', 'success');
        this.saved.emit();
        danach?.();
      },
      error: (error) => this.showMessage(error.error || 'NK_FEHLER_SPEICHERN', 'error')
    });
  }

  private uebernehme(detail: NkAbrechnungDetail): void {
    this.kopf = {
      ...detail.abrechnung,
      // Die Vorlage einer NEUEN Abrechnung ist ein leeres Objekt vom Server: Bezeichnung und
      // Daten kommen als `null`, nicht als leerer String. Ohne diese Normalisierung wirft der
      // erste Klick auf Speichern in `istGueltig()` bei `bezeichnung.trim()` - der Benutzer
      // saehe weder Feldfehler noch Meldung, die Schaltflaeche bliebe scheinbar wirkungslos.
      bezeichnung: detail.abrechnung.bezeichnung ?? '',
      datumVon: detail.abrechnung.datumVon ?? '',
      datumBis: detail.abrechnung.datumBis ?? ''
    };
    this.positionen = detail.positionen ?? [];
    this.zusaetze = detail.zusaetze ?? [];
    this.akonto = detail.akonto ?? [];
    this.personen = detail.personen ?? [];
    this.anzahlWohnungenVorschlag = detail.anzahlWohnungenVorschlag;
    this.anzahlPersonenVorschlag = detail.anzahlPersonenVorschlag;
    // Vorschlag greift auch beim Laden einer Abrechnung, die noch vor dieser Erweiterung
    // gespeichert wurde und deshalb keinen Wert mitbringt.
    if (this.kopf.anzahlPersonen == null) {
      this.kopf.anzahlPersonen = detail.anzahlPersonenVorschlag ?? this.kopf.anzahlWohnungen;
    } else {
      // Eine gespeicherte Abrechnung trägt ihre eigene Zahl - die folgt der Wohnungszahl nicht.
      this.personenFolgtWohnungen = false;
    }
    this.berechnung = detail.berechnung ?? null;
    this.mieterTage = (detail.berechnung?.mieter ?? []).map((m: NkMieterAbrechnung) => ({
      mieterId: m.mieterId,
      name: m.name,
      tage: m.tage,
      ohneWohnung: m.ohneWohnung
    }));

    // Personenzahl je Mieter: Der Server speichert nur Abweichungen von der Vorgabe, die Maske
    // braucht aber fuer jeden Block ein Feld - sonst haette ngModel nichts zum Binden.
    for (const block of detail.berechnung?.mieter ?? []) {
      if (!this.personen.some(x => x.mieterId === block.mieterId)) {
        this.personen.push({ mieterId: block.mieterId, anzahlPersonen: block.anzahlPersonen });
      }
    }

    // Akonto-Vorschlaege des Servers uebernehmen, damit ein unveraendert gespeicherter Block
    // dieselben Werte behaelt statt auf 0 zu fallen.
    for (const block of detail.berechnung?.mieter ?? []) {
      if (!this.akonto.some(a => a.mieterId === block.mieterId)) {
        this.akonto.push({
          mieterId: block.mieterId,
          anzahlMonate: block.akontoAnzahlMonate,
          betragProMonat: block.akontoBetragProMonat,
          korrektur: block.akontoKorrektur
        });
      }
    }
  }

  /**
   * Gibt es mindestens eine Position, die nach Personen verteilt?
   *
   * <p>Nur dann zeigt die Maske je Mieter das Personenfeld. Ein Feld ohne Wirkung anzuzeigen laedt
   * zum Ausfuellen ein und weckt die Erwartung, dass sich etwas aendert.
   */
  get hatPersonenumlage(): boolean {
    return this.positionen.some(p => p.art === NkPositionsart.UMLAGE_PERSON);
  }

  /**
   * Personenzahl-Eintrag eines Mieters; wird bei Bedarf mit der Vorgabe angelegt.
   *
   * <p>Gleiche Bauart wie {@code akontoFuer}: Die Maske braucht ein Objekt zum Binden, auch wenn
   * der Server nichts gespeichert hat.
   */
  personFuer(mieterId: number): NkPerson {
    let eintrag = this.personen.find(x => x.mieterId === mieterId);
    if (!eintrag) {
      eintrag = { mieterId, anzahlPersonen: PERSONEN_VORGABE };
      this.personen.push(eintrag);
    }
    return eintrag;
  }

  /** Vorschau neu rechnen — bei jeder Änderung in der Maske. */
  rechne(): void {
    if (!this.hatMieterbloecke || this.kopf.anzahlWohnungen === null) {
      return;
    }
    const tageImZeitraum = this.tageImZeitraum();
    const nenner = this.kopf.anzahlWohnungen * tageImZeitraum;
    const nennerPerson = (this.kopf.anzahlPersonen ?? 0) * tageImZeitraum;
    this.berechnung = berechneVorschau(
      nenner, this.mieterTage, this.positionen, this.zusaetze, this.akonto,
      nennerPerson, this.personen);
  }

  private tageImZeitraum(): number {
    if (!this.kopf.datumVon || !this.kopf.datumBis) {
      return 0;
    }
    const von = new Date(this.kopf.datumVon).getTime();
    const bis = new Date(this.kopf.datumBis).getTime();
    return Math.round((bis - von) / 86_400_000) + 1;
  }

  private showMessage(message: string, type: 'success' | 'error'): void {
    this.message = message;
    this.messageType = type;
    if (type === 'success') {
      setTimeout(() => {
        // Nur die eigene Meldung abräumen: Steht inzwischen eine andere - typischerweise ein
        // Fehler, der nach der Erfolgsmeldung eintraf -, würde ein bedingungsloses Leeren sie
        // nach fünf Sekunden verschwinden lassen. Fehler bleiben stehen, bis sie weggeklickt
        // werden (Projektkonvention).
        if (this.message === message) {
          this.message = '';
        }
      }, 5000);
    }
  }
}
