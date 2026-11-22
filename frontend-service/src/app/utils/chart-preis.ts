/**
 * Utility functions for showing prices of the Preiszeitreihe in charts
 */

import { PreiszeitreihePunkt } from '../models/preiszeitreihe.model';
import { formatSwissNumber } from './number-utils';

/**
 * Nachkommastellen eines Preises in CHF/kWh.
 *
 * Fünf, weil die dynamischen Einspeisepreise in Rappenbruchteilen schwanken: Mit drei Stellen
 * sähen benachbarte Viertelstunden gleich aus.
 */
export const PREIS_NACHKOMMASTELLEN = 5;

/**
 * Formatiert einen Preis in CHF/kWh im Schweizer Format mit fünf Nachkommastellen.
 *
 * Gemeinsam für alle Diagramme, die den Einspeisepreis zeigen (Preiszeitreihe,
 * Einspeisesteuerung, Messwerte-Grafik) — damit derselbe Preis überall gleich aussieht.
 *
 * @param wert - Preis in CHF/kWh
 * @returns z.B. `0.12346`
 *
 * @example
 * formatPreis(0.1234567)   // '0.12346'
 * formatPreis(-0.5)        // '-0.50000'
 */
export function formatPreis(wert: number): string {
  return formatSwissNumber(wert, PREIS_NACHKOMMASTELLEN);
}

/**
 * Wandelt die Punkte der Preiszeitreihe in Datenpunkte einer ECharts-Zeitreihe um.
 *
 * @param preise - Viertelstundenwerte; `zeit` ist der Intervallbeginn in Ortszeit
 * @returns `[Zeitpunkt in ms, Preis]` je Punkt, in der Reihenfolge der Eingabe
 *
 * @example
 * preisPunkte([{ zeit: '2026-01-15T10:00:00', preis: 0.08 }])
 * // [[1768467600000, 0.08]]
 */
export function preisPunkte(preise: PreiszeitreihePunkt[]): number[][] {
  return preise.map(punkt => [new Date(punkt.zeit).getTime(), punkt.preis]);
}
