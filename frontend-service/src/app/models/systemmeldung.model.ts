/**
 * Schweregrad einer Systemmeldung.
 */
export type MeldungLevel = 'INFO' | 'WARN' | 'ERROR';

/**
 * Filter über den Erledigt-Status.
 */
export type ErledigtFilter = 'ALLE' | 'OFFENE' | 'ERLEDIGTE';

/**
 * Eine persistente Systemmeldung (Betriebsmeldung).
 */
export interface Systemmeldung {
  id: number;
  level: MeldungLevel;
  kategorie: string;      // Übersetzungs-Key
  meldungKey: string;     // Übersetzungs-Key
  parameter?: string | null;
  erstmalsAufgetreten: string; // ISO
  zuletztAufgetreten: string;  // ISO
  erledigt: boolean;
  erledigtAm?: string | null;
  erledigtAutomatisch: boolean;
  zaehler: number;
}

/**
 * Serverseitig paginierte Seite (analog Datenbank-Ansicht: `hatMehr` statt Gesamt-Count).
 */
export interface SystemmeldungSeite {
  items: Systemmeldung[];
  hatMehr: boolean;
  page: number;
}
