/**
 * Modelle für die generische Datenbank-Ansicht (nur zev_admin, read-only).
 */

export type SortRichtung = 'ASC' | 'DESC';

export interface DatenbankAbfrageRequest {
  tabelle: string;
  where?: string;
  page: number;
  size: number;
  sortSpalte?: string;
  sortRichtung?: SortRichtung;
}

export interface DatenbankAbfrageResponse {
  spalten: string[];
  zeilen: (string | null)[][];
  seite: number;
  groesse: number;
  hatMehr: boolean;
}
