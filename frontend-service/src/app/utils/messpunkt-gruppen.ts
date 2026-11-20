/**
 * Utility functions for grouping Einheiten by their Messpunkt
 */

import { Einheit, MesspunktGruppe } from '../models/einheit.model';

/**
 * Fasst Einheiten mit **demselben Messpunkt** zu Gruppen zusammen (Specs/EChart.md, FR-8).
 *
 * Eine Gruppe entsteht erst ab zwei Einheiten; Einheiten ohne Messpunkt gehören zu keiner. Verglichen
 * wird der Messpunkt ohne Leerraum am Rand, sonst aber genau — `BKW` und `bkw` sind zwei Messpunkte,
 * wie in der Einheitenverwaltung auch.
 *
 * Die Einheiten behalten ihre Reihenfolge aus der Eingabe, die Gruppen sind nach Messpunkt sortiert.
 *
 * @param einheiten - die Einheiten eines Mandanten, in der Reihenfolge der Anzeige
 * @returns je Messpunkt mit mindestens zwei Einheiten eine Gruppe; leer, wenn es keinen gibt
 *
 * @example
 * messpunktGruppen([bezug, ruecklieferung, wohnung])
 * // [{ messpunkt: 'Bilanz', einheiten: [bezug, ruecklieferung] }]
 */
export function messpunktGruppen(einheiten: Einheit[]): MesspunktGruppe[] {
  const jeMesspunkt = new Map<string, Einheit[]>();
  for (const einheit of einheiten) {
    const messpunkt = einheit.messpunkt?.trim();
    if (!messpunkt) {
      continue;
    }
    const gruppe = jeMesspunkt.get(messpunkt);
    if (gruppe) {
      gruppe.push(einheit);
    } else {
      jeMesspunkt.set(messpunkt, [einheit]);
    }
  }

  return [...jeMesspunkt.entries()]
    .filter(([, gruppe]) => gruppe.length > 1)
    .map(([messpunkt, gruppe]) => ({ messpunkt, einheiten: gruppe }))
    .sort((a, b) => a.messpunkt.localeCompare(b.messpunkt));
}
