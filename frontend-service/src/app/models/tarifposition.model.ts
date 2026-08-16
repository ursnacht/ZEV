/** Herkunft einer Tarifposition. */
export enum Erfassungsart {
  MANUELL = 'MANUELL',
  IMPORT = 'IMPORT'
}

/**
 * Manuell erfasste Menge zu einem Tarif, je Mieter und Quartal.
 *
 * Bewusst generisch: Die fachliche Bedeutung steckt im referenzierten Tarif — erster
 * Anwendungsfall ist Ladestrom.
 */
export interface Tarifposition {
  id?: number;
  mieterId: number;
  mieterName?: string;        // nur lesend vom Backend geliefert
  tarifId: number;
  tarifBezeichnung?: string;  // nur lesend
  tarifPreis?: number;        // nur lesend
  jahr: number;
  quartal: number;            // 1-4
  menge: number;
  erfassungsart?: Erfassungsart;
  quellReferenz?: string;
  bemerkung?: string;
}
