/**
 * Diagrammfarben aus den Design-Tokens (`Specs/EChart.md`, FR-4).
 *
 * **Warum aus Tokens und nicht fest:** Die Neutraltöne kippen im Dark Mode — `--color-gray-700` ist
 * dort hell. Feste Werte ergäben je nach Modus ein unlesbares Diagramm.
 *
 * **Warum trotzdem Rückfallwerte:** Fehlt ein Token (anderer Kontext, Test in jsdom, künftige
 * Umbenennung), wäre das Diagramm ohne sie unsichtbar. Sie sind die einzigen erlaubten
 * Farb-Literale dieses Bereichs.
 *
 * <p>Gelesen wird bei **jedem** Zeichnen. Ein Wechsel des Modus bei offenem Diagramm wirkt deshalb
 * erst beim nächsten Zeichnen — bekannte Einschränkung, für alle Diagramme gleich.
 */

export interface ChartFarben {
  /** Achsenlinien */
  achse: string;
  /** Achsenbeschriftung und Hinweistexte */
  text: string;
  /** Hilfslinien im Diagramm */
  gitter: string;
  /** Erste Datenreihe */
  primaer: string;
  /** Zweite Datenreihe */
  sekundaer: string;
  /** Fläche unter der ersten Reihe, wo verwendet */
  flaeche: string;
}

/**
 * Liest die Diagrammfarben aus den Design-Tokens des Dokuments.
 *
 * @returns Farben mit Rückfallwerten, falls ein Token fehlt
 */
export function chartFarben(): ChartFarben {
  const stil = getComputedStyle(document.documentElement);
  const token = (name: string, fallback: string) => stil.getPropertyValue(name).trim() || fallback;
  return {
    achse: token('--color-gray-500', '#cccccc'),
    text: token('--color-gray-700', '#555555'),
    gitter: token('--color-gray-300', '#e0e0e0'),
    primaer: token('--color-primary', '#4CAF50'),
    sekundaer: token('--color-secondary', '#2196F3'),
    flaeche: token('--color-primary-light', '#81C784')
  };
}
