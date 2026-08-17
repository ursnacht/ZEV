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
}
