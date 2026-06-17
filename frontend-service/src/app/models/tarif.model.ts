export enum TarifTyp {
  ZEV = 'ZEV',
  VNB = 'VNB',
  GRUNDGEBUEHR = 'GRUNDGEBUEHR'
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
