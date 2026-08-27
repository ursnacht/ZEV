/**
 * Herkunft einer Forderung (Specs/Nebenkosten/RechnungenGenerieren.md, FR-5).
 * Spiegelt `ch.nacht.entity.Debitorherkunft`.
 */
export type DebitorHerkunft = 'ZEV' | 'NK';

/** Auswahl des Herkunft-Filters; `ALLE` ist kein Wert der Entity, nur ein Filterzustand. */
export type DebitorHerkunftFilter = DebitorHerkunft | 'ALLE';

export interface Debitor {
  id?: number;
  mieterId: number;
  mieterName?: string;   // vom Backend per JOIN geliefert
  einheitName?: string;  // vom Backend per JOIN geliefert
  betrag: number;
  datumVon: string;      // ISO: YYYY-MM-DD
  datumBis: string;      // ISO: YYYY-MM-DD
  zahldatum?: string;    // ISO: YYYY-MM-DD, optional
  /** Fehlt sie im Request, setzt der Server `ZEV` - der Bestand ist so entstanden. */
  herkunft?: DebitorHerkunft;
}
