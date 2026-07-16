export enum EinheitTyp {
  PRODUCER = 'PRODUCER',
  CONSUMER = 'CONSUMER',
  BEZUG = 'BEZUG',
  RUECKLIEFERUNG = 'RUECKLIEFERUNG'
}

export interface Einheit {
  id?: number;
  name: string;
  typ: EinheitTyp;
  messpunkt?: string;
}
