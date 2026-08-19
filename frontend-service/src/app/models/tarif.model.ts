export enum TarifTyp {
  ZEV = 'ZEV',
  VNB = 'VNB',
  GRUNDGEBUEHR = 'GRUNDGEBUEHR',
  /** Ladestrom: Menge kommt nicht aus Messwerten, sondern aus erfassten Tarifpositionen. */
  LADESTROM = 'LADESTROM'
}

/**
 * Tariftypen, deren Menge manuell als Tarifposition erfasst wird.
 * Bewusst eine Liste: ein weiterer Anwendungsfall erweitert nur sie.
 *
 * GRUNDGEBUEHR steht hier **zusätzlich** zu seiner automatischen Berechnung auf der Rechnung:
 * Eine Ladestation kann eine eigene Grundgebühr tragen. Beide Zeilen erscheinen nebeneinander.
 */
export const MANUELL_ERFASSTE_TARIFTYPEN: TarifTyp[] = [TarifTyp.LADESTROM, TarifTyp.GRUNDGEBUEHR];

/**
 * Übersetzungs-Key der Mengeneinheit eines Tariftyps: Grundgebühr zählt Monate, alles
 * andere kWh. Spiegelt `TarifTyp.mengeneinheit()` im Backend.
 */
export function mengeneinheitKey(typ: TarifTyp | undefined): string {
  return typ === TarifTyp.GRUNDGEBUEHR ? 'MONATE' : 'KWH';
}

export interface Tarif {
  id?: number;
  bezeichnung: string;
  tariftyp: TarifTyp;
  preis: number;
  gueltigVon: string;  // ISO date format: YYYY-MM-DD
  gueltigBis: string;  // ISO date format: YYYY-MM-DD
  produzentVerrechnen?: boolean;  // Only relevant for GRUNDGEBUEHR: also charge producers
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
