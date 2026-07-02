/** Effektive Feature-Flags des aktuellen Mandanten: technischer Key → aktiv/inaktiv. */
export type FeatureFlagMap = Record<string, boolean>;

/** Quelle des effektiven Werts eines Flags. */
export enum FeatureFlagQuelle {
  DEFAULT = 'DEFAULT',
  OVERRIDE = 'OVERRIDE'
}

/** Admin-Sicht eines Feature-Flags (Verwaltung unter /einstellungen). */
export interface FeatureFlagAdmin {
  key: string;
  beschreibungKey: string;
  defaultWert: boolean;
  effektiv: boolean;
  quelle: FeatureFlagQuelle;
}
