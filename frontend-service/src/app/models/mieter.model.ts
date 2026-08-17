export interface Mieter {
  id?: number;
  name: string;
  strasse?: string;
  plz?: string;
  ort?: string;
  mietbeginn: string;  // ISO date format: YYYY-MM-DD
  mietende?: string;   // ISO date format: YYYY-MM-DD, optional
  /**
   * Zugeordnete Einheiten (Wohnung und/oder Ladestation(en)) - mindestens eine.
   * Ersetzt das fruehere Einzelfeld `einheitId`; siehe Specs/Ladestationen.md.
   */
  einheitIds: number[];
}
