/**
 * Modelle der Einspeisesteuerung (Specs/Einspeisesteuerung.md).
 *
 * Die Steuerung **schaltet nichts** — die Werte hier sagen, was sie tun *würde*.
 */

/** Sollzustand einer Stellgrösse. */
export type Steuerzustand = 'FREI' | 'GESPERRT';

/**
 * Die Regel, die einen Entscheid bestimmt hat.
 *
 * Die Reihenfolge ist die Auswertungsreihenfolge im Backend; die erste zutreffende gewinnt.
 */
export type Steuerregel =
  | 'PREIS_NEGATIV'
  | 'SOC_TIEF'
  | 'EINSPEISEN_LOHNT'
  | 'WARTEN_AUF_TAL'
  | 'KEIN_UEBERSCHUSS'
  | 'LADEN';

/** Alle Regeln in Auswertungsreihenfolge — für Tabellen und Legenden. */
export const STEUERREGELN: Steuerregel[] = [
  'PREIS_NEGATIV',
  'SOC_TIEF',
  'EINSPEISEN_LOHNT',
  'WARTEN_AUF_TAL',
  'KEIN_UEBERSCHUSS',
  'LADEN'
];

/** Übersetzungs-Key je Regel. */
export const STEUERREGEL_KEYS: Record<Steuerregel, string> = {
  PREIS_NEGATIV: 'STEUERUNG_REGEL_PREIS_NEGATIV',
  SOC_TIEF: 'STEUERUNG_REGEL_SOC_TIEF',
  KEIN_UEBERSCHUSS: 'STEUERUNG_REGEL_KEIN_UEBERSCHUSS',
  EINSPEISEN_LOHNT: 'STEUERUNG_REGEL_EINSPEISEN_LOHNT',
  WARTEN_AUF_TAL: 'STEUERUNG_REGEL_WARTEN_AUF_TAL',
  LADEN: 'STEUERUNG_REGEL_LADEN'
};

/**
 * Ein Steuerentscheid eines 15-Minuten-Intervalls.
 *
 * **`zeit` ist Ortszeit** (Europe/Zurich) — so gespeichert, nicht umgerechnet (V147). Das Backend
 * schickt `null`, nicht `undefined`: immer mit `== null` prüfen.
 */
export interface Steuerentscheid {
  /** Intervallbeginn in Ortszeit, ISO ohne Zone. */
  zeit: string;
  /** Einspeisepreis in CHF/kWh; `null`, wenn keiner vorlag. Darf negativ sein. */
  preis: number | null;
  /** Tiefster erwarteter Preis im Rest des Tages; `null`, wenn keiner mehr folgt. */
  preisTiefRest: number | null;
  /** Produktion in kWh, als Betrag. */
  produktion: number;
  verbrauch: number;
  /**
   * Bezug am Bilanzmesspunkt in kWh; `null` bei Entscheiden vor V149.
   *
   * Geht in **keine** Regel ein — zusammen mit `ruecklieferung` macht der Wert die Energiebilanz
   * prüfbar: `produktion + bezug − verbrauch − ruecklieferung` ist der Netto-Batteriefluss.
   */
  bezug: number | null;
  /** Rücklieferung in kWh, als Betrag; `null` bei Entscheiden vor V149. */
  ruecklieferung: number | null;
  /**
   * Ladezustand des Speichers in Prozent am Intervallende; `null` ohne Speicher-Einheit.
   *
   * Geht in **keine** Regel ein — er erklärt den Entscheid: Eine Sperre bei 95 % war wirkungslos,
   * eine bei 40 % hat Kapazität freigehalten.
   */
  soc: number | null;
  /**
   * Gemessene **Ladung** des Speichers im Intervall in kWh; `null` ohne Speicher-Einheit.
   *
   * Gegenstück zur Bilanzdifferenz: Die ist ein Residuum und enthält alles nicht Gemessene, dies
   * hier ist der Zählerwert.
   */
  speicherLadung: number | null;
  /** Gemessene **Entladung** des Speichers im Intervall in kWh, als Betrag; `null` ohne Speicher. */
  speicherEntladung: number | null;
  ueberschuss: number;
  regel: Steuerregel;
  batterieladung: Steuerzustand;
  einspeisung: Steuerzustand;
  /** Die **beim Entscheid** geltenden Schwellen, nicht die heutigen. */
  schwellwert: number;
  speicherwert: number;
  /** Mindest-Ladezustand, der beim Entscheid galt; `null` bei Entscheiden vor V160. */
  socMinimum: number | null;
  /** Hysterese, die beim Entscheid galt; `null` bei Entscheiden vor V162. */
  socHysterese: number | null;
}

/** Anfrage der Rückrechnung. */
export interface SimulationAnfrage {
  von: string;
  bis: string;
  schwellwert: number;
  /** `null` → Wert des Mandanten. */
  speicherwert?: number | null;
}

/**
 * Ergebnis der Rückrechnung.
 *
 * **Bewusst ohne Ertrag in Franken:** Der bräuchte ein Batteriemodell und gemessene
 * Lade-/Entladedaten. Diese Grössen genügen, um den Schwellwert einzugrenzen.
 */
export interface Simulation {
  von: string;
  bis: string;
  schwellwert: number;
  speicherwert: number;
  tage: number;
  /** Ausgewertete Intervalle — **nur solche mit Überschuss**. */
  intervalle: number;
  jeRegel: Record<Steuerregel, number>;
  stundenLadungGesperrt: number;
  stundenEinspeisungGesperrt: number;
  /** Überschuss-kWh in Intervallen mit gesperrter Ladung. */
  energieVerschoben: number;
}
