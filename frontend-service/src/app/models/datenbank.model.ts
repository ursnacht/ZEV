/**
 * Modelle für die generische Datenbank-Ansicht (nur zev_admin, read-only).
 */

export interface DatenbankAbfrageRequest {
  tabelle: string;
  where?: string;
  page: number;
  size: number;
}

export interface DatenbankAbfrageResponse {
  spalten: string[];
  zeilen: (string | null)[][];
  seite: number;
  groesse: number;
  hatMehr: boolean;
}
