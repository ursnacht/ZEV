export enum EinheitTyp {
  PRODUCER = 'PRODUCER',
  CONSUMER = 'CONSUMER',
  BEZUG = 'BEZUG',
  RUECKLIEFERUNG = 'RUECKLIEFERUNG',
  /** Ladestation; `messpunkt` traegt die RFID (Specs/Ladestationen.md). */
  LADESTATION = 'LADESTATION'
}

export interface Einheit {
  id?: number;
  name: string;
  typ: EinheitTyp;
  messpunkt?: string;
  /**
   * Zählt die Einheit als Wohnung in der Nebenkostenabrechnung? Nur bei `CONSUMER` ausgewertet.
   *
   * Abzuwählen bei Verbrauchern, die keine Wohnung sind — Allgemeinstrom, Eigenverbrauch der
   * PV-Anlage. Sie zählten sonst in den Nenner der Umlage (Specs/Nebenkosten/Abrechnung.md, FR-2).
   */
  nebenkostenRelevant?: boolean;
}
