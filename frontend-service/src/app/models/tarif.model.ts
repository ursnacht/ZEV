export enum TarifTyp {
  ZEV = 'ZEV',
  VNB = 'VNB',
  GRUNDGEBUEHR = 'GRUNDGEBUEHR',
  /** Ladestrom: Menge kommt nicht aus Messwerten, sondern aus erfassten Tarifpositionen. */
  LADESTROM = 'LADESTROM',
  /** Frei konfigurierbare Zusatzleistung mit eigener Mengeneinheit (Sauna, Waschküche, …). */
  ZUSATZ = 'ZUSATZ'
}

/** Mengeneinheit eines ZUSATZ-Tarifs. Bei allen anderen Typen folgt sie aus dem Typ. */
export enum Mengeneinheit {
  KWH = 'KWH',
  MONAT = 'MONAT',
  STUECK = 'STUECK',
  /** Kubikmeter — für die Nebenkostenabrechnung (Wasser, Abwasser). */
  M3 = 'M3',
  /**
   * Betrag in Franken — für Umlagen, deren verteilte Grösse selbst ein Betrag ist
   * (Grünabfuhr, Versicherungsprämie). Nicht an einem ZUSATZ-Tarif wählbar.
   */
  CHF = 'CHF'
}

/** Tariftypen mit frei wählbarer Mengeneinheit am Tarif. */
export const TARIFTYPEN_MIT_MENGENEINHEIT: TarifTyp[] = [TarifTyp.ZUSATZ];

/**
 * Übersetzungs-Key der Bezugsgrösse des **Preises** („CHF pro …"), im **Singular**:
 * `5.00 / Monat`, nicht `5.00 / Monate`.
 *
 * Liefert `''`, solange ein Tarif mit freier Einheit noch keine gewählt hat — dann zeigt die
 * Oberfläche nur „CHF", statt fälschlich kWh zu behaupten.
 */
export function preisEinheitKey(typ: TarifTyp | undefined, einheit?: Mengeneinheit): string {
  if (typ && TARIFTYPEN_MIT_MENGENEINHEIT.includes(typ)) {
    return einheit ?? '';
  }
  return typ === TarifTyp.GRUNDGEBUEHR ? 'MONAT' : 'KWH';
}

/**
 * Tariftypen, deren Menge manuell als Tarifposition erfasst wird.
 * Bewusst eine Liste: ein weiterer Anwendungsfall erweitert nur sie.
 *
 * GRUNDGEBUEHR gehört bewusst **nicht** dazu: Je Zeitraum ist nur ein Grundgebühr-Tarif gültig,
 * und jeder gültige wird automatisch auf jede Konsumenten-Rechnung geschrieben. Eine Grundgebühr
 * für Ladestationen wird stattdessen über einen Tarif mit Mengeneinheit „Monat" abgebildet
 * (Specs/Tarifpositionen.md).
 */
export const MANUELL_ERFASSTE_TARIFTYPEN: TarifTyp[] = [TarifTyp.LADESTROM, TarifTyp.ZUSATZ];

/**
 * Tariftypen, die an einer Einheit des gegebenen Typs erfassbar sind.
 *
 * An einer Wohnung ist ausschliesslich ZUSATZ zulässig — Ladestrom gehört fachlich an eine
 * Ladestation. Spiegelt `TarifpositionService.pruefeTariftypZuEinheit` im Backend; verbindlich
 * ist dort, hier geht es nur um die Auswahl in der Maske.
 */
export function erfassbareTariftypenFuer(einheitTyp: string | undefined): TarifTyp[] {
  return einheitTyp === 'LADESTATION' ? MANUELL_ERFASSTE_TARIFTYPEN : [TarifTyp.ZUSATZ];
}

/**
 * Übersetzungs-Key der Mengeneinheit eines Tarifs.
 *
 * Bei ZUSATZ steht sie am Tarif, sonst folgt sie aus dem Typ (Grundgebühr zählt Monate, alles
 * andere kWh). Spiegelt `Tarif.effektiveMengeneinheit()` im Backend.
 */
export function mengeneinheitKey(typ: TarifTyp | undefined, einheit?: Mengeneinheit | string): string {
  if (typ && TARIFTYPEN_MIT_MENGENEINHEIT.includes(typ) && einheit) {
    // Bewusst eine vollstaendige Zuordnung statt eines else-Zweigs auf 'KWH': Ein unbekannter
    // Wert wurde sonst stillschweigend als Kilowattstunden beschriftet — die Zahl stimmt, die
    // Einheit daneben nicht. Ein neuer Enum-Wert muss hier eingetragen werden.
    return MENGENEINHEIT_KEYS[einheit as Mengeneinheit] ?? String(einheit);
  }
  return typ === TarifTyp.GRUNDGEBUEHR ? 'MONATE' : 'KWH';
}

/** Übersetzungs-Key je Mengeneinheit. `MONAT` heisst in der Anzeige „Monate". */
const MENGENEINHEIT_KEYS: Record<Mengeneinheit, string> = {
  [Mengeneinheit.KWH]: 'KWH',
  [Mengeneinheit.MONAT]: 'MONATE',
  [Mengeneinheit.STUECK]: 'STUECK',
  [Mengeneinheit.M3]: 'M3',
  [Mengeneinheit.CHF]: 'CHF'
};

export interface Tarif {
  id?: number;
  bezeichnung: string;
  tariftyp: TarifTyp;
  preis: number;
  gueltigVon: string;  // ISO date format: YYYY-MM-DD
  gueltigBis: string;  // ISO date format: YYYY-MM-DD
  produzentVerrechnen?: boolean;  // Only relevant for GRUNDGEBUEHR: also charge producers
  mengeneinheit?: Mengeneinheit;  // Pflicht bei ZUSATZ, sonst leer
}

/** A single tariff coverage gap (language-neutral; the frontend translates it). */
export interface TarifLuecke {
  tarifTyp: string;   // 'ZEV' | 'VNB' → translation key TARIF_LUECKE_<typ>
  datum: string;      // first uncovered date, Swiss format dd.MM.yyyy
  weitere: boolean;   // whether further gaps exist
}

/** Coverage gaps for a single period (quarter or year). */
export interface TarifLueckePeriode {
  periode: string;            // language-neutral label, e.g. 'Q1/2024' or '2024'
  luecken: TarifLuecke[];
}

export interface ValidationResult {
  valid: boolean;
  luecken: TarifLueckePeriode[];
}
