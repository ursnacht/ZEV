export interface TagMitAbweichung {
  datum: string;
  abweichungstyp: string;
  differenz: number;
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

  // Vergleiche
  summenCDGleich: boolean;
  differenzCD: number;
  summenCEGleich: boolean;
  differenzCE: number;
  summenDEGleich: boolean;
  differenzDE: number;

  // Tage mit Abweichungen
  tageAbweichungen: TagMitAbweichung[];
}

export interface Statistik {
  messwerteBisDate: string | null;
  datenVollstaendig: boolean;
  fehlendeEinheiten: string[];
  fehlendeTage: string[];
  monate: MonatsStatistik[];
}
