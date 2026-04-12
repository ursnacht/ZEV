export interface Debitor {
  id?: number;
  mieterId: number;
  mieterName?: string;   // vom Backend per JOIN geliefert
  einheitName?: string;  // vom Backend per JOIN geliefert
  betrag: number;
  datumVon: string;      // ISO: YYYY-MM-DD
  datumBis: string;      // ISO: YYYY-MM-DD
  zahldatum?: string;    // ISO: YYYY-MM-DD, optional
}
