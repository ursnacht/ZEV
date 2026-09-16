export enum EinheitTyp {
  PRODUCER = 'PRODUCER',
  CONSUMER = 'CONSUMER',
  BEZUG = 'BEZUG',
  RUECKLIEFERUNG = 'RUECKLIEFERUNG',
  /** Ladestation; `messpunkt` traegt die RFID (Specs/Ladestationen.md). */
  LADESTATION = 'LADESTATION',
  /**
   * Batteriespeicher am Hybrid-Wechselrichter (Specs/Batteriespeicher.md).
   *
   * Die beiden Register tragen hier **Ladung** und **Entladung**; `total` ist entsprechend
   * positiv beim Laden und negativ beim Entladen. Nimmt nicht an der Solarverteilung teil.
   */
  SPEICHER = 'SPEICHER'
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
