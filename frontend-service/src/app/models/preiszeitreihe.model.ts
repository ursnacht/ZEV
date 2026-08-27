/**
 * Preiszeitreihe der dynamischen Einspeisepreise (Specs/Preiszeitreihe.md).
 */

/** Ein Viertelstundenwert. `zeit` ist Ortszeit (ISO ohne Zone), der Server rechnet UTC um. */
export interface PreiszeitreihePunkt {
  zeit: string;
  preis: number;
}

/** Ergebnis eines Abrufs bei der Quelle. */
export interface PreiszeitreiheDownload {
  abgerufen: number;
  neu: number;
  aktualisiert: number;
  /** Unvollständige Intervalle der Quelle - bewusst ausgewiesen statt als 0 gespeichert. */
  uebersprungen: number;
  /**
   * Publikationszeitpunkt der Quelle. **`null`**, wenn die Quelle keinen nennt — das Backend
   * schickt `null`, nicht `undefined` (Jackson). Prüfungen deshalb mit `== null` / `!= null`.
   */
  publikation: string | null;
}

/** Gewählte Darstellungsspanne; `FREI` = über Datum von/bis gesetzt. */
export type Spanne = 'TAG' | 'WOCHE' | 'MONAT' | 'FREI';

/**
 * Darstellungsart des Diagramms.
 *
 * `LINIE` ist eine **Stufen**linie (ein Preis gilt für die ganze Viertelstunde), `BALKEN` zeigt
 * je Intervall einen Balken. Beide sagen dasselbe aus - Balken betonen die einzelne
 * Viertelstunde, die Linie den Verlauf.
 */
export type Darstellung = 'LINIE' | 'BALKEN';
