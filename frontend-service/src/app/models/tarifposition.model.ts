import { TarifTyp } from './tarif.model';

/** Herkunft einer Tarifposition. */
export enum Erfassungsart {
  MANUELL = 'MANUELL',
  IMPORT = 'IMPORT'
}

/**
 * Manuell erfasste Menge zu einem Tarif, je Einheit und Quartal.
 *
 * Bewusst generisch: Die fachliche Bedeutung steckt im referenzierten Tarif — erster
 * Anwendungsfall ist Ladestrom.
 */
export interface Tarifposition {
  id?: number;
  einheitId: number;
  einheitName?: string;        // nur lesend vom Backend geliefert
  einheitMesspunkt?: string;   // nur lesend; RFID, belegt die Quell-Referenz vor
  tarifId: number;
  tarifBezeichnung?: string;  // nur lesend
  /** nur lesend; Tariftyp der Position */
  tarifTyp?: TarifTyp;
  /** nur lesend; Mengeneinheit der Zeile (KWH / MONAT / STUECK), vom Backend aufgeloest */
  tarifMengeneinheit?: string;
  tarifPreis?: number;        // nur lesend
  jahr: number;
  quartal: number;            // 1-4
  menge: number;
  erfassungsart?: Erfassungsart;
  quellReferenz?: string;
  bemerkung?: string;
}
