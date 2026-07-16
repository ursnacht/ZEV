export interface TagMitAbweichung {
  datum: string;
  abweichungstyp: string;
  differenz: number;
}

export interface EinheitSummen {
  einheitId: number;
  einheitName: string;
  einheitTyp: 'PRODUCER' | 'CONSUMER' | 'BEZUG' | 'RUECKLIEFERUNG';
  summeTotal: number;
  summeZev: number;
  summeZevCalculated: number;
}

export interface MonatsStatistik {
  jahr: number;
  monat: number;
  von: string;
  bis: string;
  datenVollstaendig: boolean;
  fehlendeEinheiten: string[];
  fehlendeTage: string[];

  // Summen
  summeProducerTotal: number;
  summeConsumerTotal: number;
  summeProducerZev: number;
  summeConsumerZev: number;
  summeConsumerZevCalculated: number;

  // Berechnete Werte (vor dem Summen-Vergleich)
  bezugVonVnb: number;
  ruecklieferung: number;

  // Bilanzmesspunkte (Netzanschluss)
  bilanzBezug: number;
  bilanzRuecklieferung: number;

  // Vergleiche
  summenCDGleich: boolean;
  differenzCD: number;
  summenCEGleich: boolean;
  differenzCE: number;
  summenDEGleich: boolean;
  differenzDE: number;

  // Vergleiche gegen die Bilanzmesspunkte
  bezugBilanzGleich: boolean;
  bezugBilanzDifferenz: number;
  ruecklieferungBilanzGleich: boolean;
  ruecklieferungBilanzDifferenz: number;

  // Tage mit Abweichungen
  tageAbweichungen: TagMitAbweichung[];

  // Summen pro Einheit
  einheitSummen: EinheitSummen[];
}

export interface Statistik {
  messwerteBisDate: string | null;
  datenVollstaendig: boolean;
  fehlendeEinheiten: string[];
  fehlendeTage: string[];
  monate: MonatsStatistik[];
  toleranz: number;
}
