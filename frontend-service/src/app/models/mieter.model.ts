export interface Mieter {
  id?: number;
  name: string;
  strasse?: string;
  plz?: string;
  ort?: string;
  mietbeginn: string;  // ISO date format: YYYY-MM-DD
  mietende?: string;   // ISO date format: YYYY-MM-DD, optional
  einheitId: number;
  /** Ladepunkt-Kennung; Zuordnungsgrundlage für den späteren Import, mandantenweit eindeutig. */
  ladepunkt?: string;
}
