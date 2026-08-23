import { Mengeneinheit } from './tarif.model';

/**
 * Rechenart einer allgemeinen Position (Specs/Nebenkosten/Abrechnung.md, FR-2).
 * Spiegelt `ch.nacht.entity.NkPositionsart`.
 */
export enum NkPositionsart {
  /** Gesamtkosten zeitanteilig verteilt; Leerstand bleibt unverteilt. */
  UMLAGE = 'UMLAGE',
  /** Je Mieter gemessene Menge mal Preis je Einheit. */
  VERBRAUCH = 'VERBRAUCH',
  /** Prozent auf die Summe aller Zeilen mit kleinerer Reihenfolge. */
  ZUSCHLAG = 'ZUSCHLAG',
  /**
   * Totalbetrag nach einem je Mieter erfassten Prozentsatz verteilt (Heizkosten).
   *
   * Nicht zu verwechseln mit ZUSCHLAG: Dort steht der Prozentsatz an der Position und rechnet auf
   * die Zeilen davor; hier trägt jeder Mieter seinen eigenen, bezogen auf den Totalbetrag.
   */
  ANTEIL = 'ANTEIL'
}

/** Kopfdaten einer Nebenkostenabrechnung. */
export interface NkAbrechnung {
  id?: number;
  bezeichnung: string;
  datumVon: string;  // ISO date format: YYYY-MM-DD
  datumBis: string;  // ISO date format: YYYY-MM-DD
  /** Bildet mit den Tagen des Zeitraums den Nenner der Umlage. */
  anzahlWohnungen: number | null;
  abgerechnet: boolean;
  erstelltAm?: string;
}

/** Je Mieter erfasste Menge zu einer VERBRAUCH-Position. */
export interface NkVerbrauch {
  mieterId: number;
  menge: number | null;
}

/**
 * Allgemeine Position. Welche Felder gefüllt sein müssen, hängt von `art` ab — die nicht
 * zutreffenden blendet die Maske aus, statt sie bloss zu sperren.
 */
export interface NkPosition {
  id?: number;
  art: NkPositionsart;
  bezeichnung: string;
  /**
   * Fachlich tragend, nicht bloss Anzeige: Ein Zuschlag rechnet auf die Summe aller Zeilen mit
   * kleinerer Reihenfolge. Beim Speichern vergibt das Backend sie aus der Listenposition neu.
   */
  reihenfolge?: number;
  einheit?: Mengeneinheit | null;
  totalbetrag?: number | null;
  gesamtmenge?: number | null;
  betragProEinheit?: number | null;
  prozentsatz?: number | null;
  /** Nur bei VERBRAUCH: die je Mieter erfassten Mengen — eingebettet, nicht über eine ID verknüpft. */
  verbraeuche: NkVerbrauch[];
}

/** Frei erfasste Position eines einzelnen Mieters. */
export interface NkZusatz {
  id?: number;
  mieterId: number;
  reihenfolge?: number;
  bezeichnung: string;
  einheit: Mengeneinheit | null;
  menge: number | null;
  betragProEinheit: number | null;
}

/** Akonto-Angaben eines Mieters; alle drei Werte sind überschreibbare Vorschläge. */
export interface NkAkonto {
  id?: number;
  mieterId: number;
  anzahlMonate: number | null;
  betragProMonat: number | null;
  korrektur: number | null;
}

/**
 * Eine berechnete Zeile im Block eines Mieters.
 *
 * `zusatzId` unterscheidet die Herkunft: Ist sie gesetzt, ist die Zeile frei bearbeitbar; sonst
 * stammt sie aus einer allgemeinen Position. Deren `art` ist bei einer Zusatzzeile `VERBRAUCH`,
 * weil sie genau so rechnet.
 *
 * **`null` und nicht `undefined`:** Die Felder sind mit `| null` typisiert, weil das Backend
 * nicht gesetzte Werte als `null` mitschickt. Wer hier auf `undefined` prüft, prüft am echten
 * Wert vorbei — `null !== undefined` ist `true`. Deshalb durchgehend `== null` / `!= null`.
 */
export interface NkZeile {
  positionId?: number | null;
  zusatzId?: number | null;
  art: NkPositionsart;
  reihenfolge: number;
  bezeichnung: string;
  einheit?: Mengeneinheit | null;
  menge?: number | null;
  betragProEinheit?: number | null;
  prozentsatz?: number | null;
  betrag: number;
}

/** Der berechnete Block eines Mieters. Positiver Saldo = Nachzahlung, negativer = Guthaben. */
export interface NkMieterAbrechnung {
  mieterId: number;
  name: string;
  /** Miettage im Zeitraum, bereits mit der Zahl der Wohnungen multipliziert. */
  tage: number;
  /** Kein zugeordnetes Wohnobjekt — die Maske weist darauf hin, statt den Mieter zu verschweigen. */
  ohneWohnung: boolean;
  zeilen: NkZeile[];
  kostentotal: number;
  akontoAnzahlMonate: number;
  akontoBetragProMonat: number;
  akontoKorrektur: number;
  akontoTotal: number;
  saldo: number;
}

/**
 * Kontrollzahlen einer Umlageposition. Leerstandsanteil und Rundungsdifferenz werden bewusst
 * getrennt geführt: Der eine ist fachlich begründet, die andere sind wenige Rappen aus dem Runden.
 */
export interface NkUmlageInfo {
  positionId: number;
  bezeichnung: string;
  /** UMLAGE oder ANTEIL — bestimmt, welche Kontrollzahlen etwas aussagen. */
  art: NkPositionsart;
  totalbetrag: number;
  summeVerteilt: number;
  nichtVerteilt: number;
  rundungsdifferenz: number;
  /** Nur bei ANTEIL: Summe der je Mieter erfassten Prozentsätze; sollte 100 ergeben. */
  summeProzent: number;
}

/** Ergebnis der Berechnung. */
export interface NkBerechnung {
  nenner: number;
  summeTage: number;
  mieter: NkMieterAbrechnung[];
  umlagen: NkUmlageInfo[];
}

/**
 * Eine Abrechnung samt allem, was zu ihr gehört — Antwort von `GET /{id}` und Rumpf von
 * `PUT /{id}` (ein Aufruf statt fünf).
 *
 * `berechnung` ist nur Ausgabe: Das Backend rechnet und ist massgebend. Nach dem Speichern zeigt
 * die Maske dessen Werte, nicht die eigene Vorschau.
 */
export interface NkAbrechnungDetail {
  abrechnung: NkAbrechnung;
  positionen: NkPosition[];
  zusaetze: NkZusatz[];
  akonto: NkAkonto[];
  berechnung?: NkBerechnung;
  /** Vorschlag für die Anzahl Wohnungen; `null`, wenn es keine CONSUMER-Einheiten gibt. */
  anzahlWohnungenVorschlag: number | null;
}

/** Positionsarten in der Reihenfolge, in der sie in der Auswahl erscheinen. */
export const NK_POSITIONSARTEN: NkPositionsart[] = [
  NkPositionsart.UMLAGE,
  NkPositionsart.VERBRAUCH,
  NkPositionsart.ANTEIL,
  NkPositionsart.ZUSCHLAG
];

/**
 * Mengeneinheiten, die in der Nebenkostenabrechnung zur Auswahl stehen.
 *
 * `CHF` steht bewusst hier und nicht im Tarifformular: Bei einer Umlage ist die verteilte Grösse
 * oft selbst ein Betrag (Grünabfuhr), bei einem Tarif wäre „CHF pro Fr." dagegen sinnlos.
 */
export const NK_MENGENEINHEITEN: Mengeneinheit[] = [
  Mengeneinheit.M3,
  Mengeneinheit.CHF,
  Mengeneinheit.KWH,
  Mengeneinheit.STUECK,
  Mengeneinheit.MONAT
];

/** Positionsarten, deren Betrag sich aus einem je Mieter erfassten Wert ergibt. */
export const NK_ARTEN_MIT_EINGABE_JE_MIETER: NkPositionsart[] = [
  NkPositionsart.VERBRAUCH,
  NkPositionsart.ANTEIL
];

/** Positionsarten ohne eigene Mengeneinheit — dort ist die Einheit Prozent oder gar keine. */
export const NK_ARTEN_OHNE_EINHEIT: NkPositionsart[] = [
  NkPositionsart.ZUSCHLAG,
  NkPositionsart.ANTEIL
];

/** Eine leere Position der gegebenen Art — alle nicht zutreffenden Felder bleiben leer. */
export function leerePosition(art: NkPositionsart): NkPosition {
  return {
    art,
    bezeichnung: '',
    einheit: NK_ARTEN_OHNE_EINHEIT.includes(art) ? null : Mengeneinheit.M3,
    totalbetrag: null,
    gesamtmenge: null,
    betragProEinheit: null,
    prozentsatz: null,
    verbraeuche: []
  };
}
