export enum TarifTyp {
  ZEV = 'ZEV',
  VNB = 'VNB'
}

export interface Tarif {
  id?: number;
  bezeichnung: string;
  tariftyp: TarifTyp;
  preis: number;
  gueltigVon: string;  // ISO date format: YYYY-MM-DD
  gueltigBis: string;  // ISO date format: YYYY-MM-DD
}

export interface ValidationResult {
  valid: boolean;
  message: string;
  errors: string[];
}
