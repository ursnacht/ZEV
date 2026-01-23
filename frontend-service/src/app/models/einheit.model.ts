export enum EinheitTyp {
  PRODUCER = 'PRODUCER',
  CONSUMER = 'CONSUMER'
}

export interface Einheit {
  id?: number;
  name: string;
  typ: EinheitTyp;
  messpunkt?: string;
}
