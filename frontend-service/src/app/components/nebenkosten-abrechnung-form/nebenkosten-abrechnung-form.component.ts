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
import { NkMieterTage, berechneVorschau } from '../../utils/nebenkosten-berechnung';
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
    abgerechnet: false
  };

  positionen: NkPosition[] = [];
  zusaetze: NkZusatz[] = [];
  akonto: NkAkonto[] = [];

  /** Vom Server gelieferte Miettage — Grundlage der clientseitigen Vorschau. */
  mieterTage: NkMieterTage[] = [];

  berechnung: NkBerechnung | null = null;
  anzahlWohnungenVorschlag: number | null = null;

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

  ladeVorlage(): void {
    this.laedt = true;
    this.nebenkostenService.getVorlage().subscribe({
      next: (detail) => {
        this.uebernehme(detail);
        this.kopf.anzahlWohnungen = detail.anzahlWohnungenVorschlag;
        this.laedt = false;
      },
      error: () => {
        this.showMessage('NK_FEHLER_LADEN', 'error');
        this.laedt = false;
      }
    });
  }

  ladeDetail(id: number): void {
    this.laedt = true;
    this.nebenkostenService.getAbrechnungDetail(id).subscribe({
      next: (detail) => {
        this.uebernehme(detail);
        this.laedt = false;
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
    this.speichernVersucht = true;
    if (!this.istGueltig()) {
      // Die Feldfehler stehen jetzt an den Feldern; die Meldung oben sagt, dass gar nicht
      // gespeichert wurde - sonst bliebe der Klick ohne erkennbare Wirkung.
      this.showMessage('NK_FEHLER_EINGABEN', 'error');
      return;
    }

    if (this.abrechnungId) {
      this.speichereDetail(this.abrechnungId);
    } else {
      // Erst anlegen, dann den vollstaendigen Stand schreiben: Positionen brauchen eine ID.
      this.nebenkostenService.createAbrechnung(this.kopf).subscribe({
        next: (erstellt) => {
          this.abrechnungId = erstellt.id ?? null;
          if (this.abrechnungId) {
            this.speichereDetail(this.abrechnungId);
          }
        },
        error: (error) => this.showMessage(error.error || 'NK_FEHLER_SPEICHERN', 'error')
      });
    }
  }

  onAbbrechen(): void {
    this.closed.emit();
  }

  /**
   * Weg zurück zur Liste, ohne dabei etwas zu verwerfen.
   *
   * <p>Führt heute zum selben Ergebnis wie {@link onAbbrechen} — die Maske hält keinen Zustand,
   * der beim Verlassen verlorenginge, weil Gespeichertes gespeichert ist. Getrennt trotzdem,
   * weil die beiden Schaltflächen verschiedene Absichten benennen: „Abbrechen" heisst „ich
   * verwerfe, was ich angefangen habe", „Zurück zur Übersicht" heisst „ich bin fertig".
   */
  onZurueckZurUebersicht(): void {
    this.closed.emit();
  }

  istGueltig(): boolean {
    return !!(
      this.kopf.bezeichnung.trim() &&
      this.kopf.datumVon &&
      this.kopf.datumBis &&
      this.kopf.datumVon <= this.kopf.datumBis &&
      this.kopf.anzahlWohnungen !== null &&
      this.kopf.anzahlWohnungen >= 1
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
   * <p>Verglichen wird auf drei Nachkommastellen genau — so fein lässt sich ein Prozentsatz
   * erfassen. Ein Gleitkomma-Vergleich auf exakte Gleichheit meldete sonst bei 33.333 × 3 eine
   * Abweichung, die keine ist.
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

  private speichereDetail(id: number): void {
    const detail: NkAbrechnungDetail = {
      abrechnung: this.kopf,
      positionen: this.positionen,
      zusaetze: this.zusaetze,
      akonto: this.akonto,
      anzahlWohnungenVorschlag: this.anzahlWohnungenVorschlag
    };

    this.nebenkostenService.updateAbrechnung(id, detail).subscribe({
      next: (gespeichert) => {
        // Bewusst die Antwort des Servers uebernehmen statt die eigene Vorschau stehenzulassen:
        // Das Backend ist massgebend, eine Abweichung wird so sofort sichtbar.
        this.uebernehme(gespeichert);
        this.showMessage('NK_ABRECHNUNG_GESPEICHERT', 'success');
        this.saved.emit();
      },
      error: (error) => this.showMessage(error.error || 'NK_FEHLER_SPEICHERN', 'error')
    });
  }

  private uebernehme(detail: NkAbrechnungDetail): void {
    this.kopf = { ...detail.abrechnung };
    this.positionen = detail.positionen ?? [];
    this.zusaetze = detail.zusaetze ?? [];
    this.akonto = detail.akonto ?? [];
    this.anzahlWohnungenVorschlag = detail.anzahlWohnungenVorschlag;
    this.berechnung = detail.berechnung ?? null;
    this.mieterTage = (detail.berechnung?.mieter ?? []).map((m: NkMieterAbrechnung) => ({
      mieterId: m.mieterId,
      name: m.name,
      tage: m.tage,
      ohneWohnung: m.ohneWohnung
    }));

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

  /** Vorschau neu rechnen — bei jeder Änderung in der Maske. */
  rechne(): void {
    if (!this.hatMieterbloecke || this.kopf.anzahlWohnungen === null) {
      return;
    }
    const tageImZeitraum = this.tageImZeitraum();
    const nenner = this.kopf.anzahlWohnungen * tageImZeitraum;
    this.berechnung = berechneVorschau(
      nenner, this.mieterTage, this.positionen, this.zusaetze, this.akonto);
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
        this.message = '';
      }, 5000);
    }
  }
}
