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
  /**
   * Dritte Datenreihe — kräftiges Rot, deutlich abgesetzt von Grün und Blau.
   *
   * Für Diagramme mit **drei** Reihen: `primaer` und `flaeche` stammen aus derselben Farbfamilie
   * (Grün und Hellgrün) und sind nebeneinander kaum zu unterscheiden. Wer eine dritte Reihe
   * braucht, nimmt diese statt `flaeche`.
   */
  akzent: string;
  /**
   * **Solarproduktion** — Gelb, die übliche Zuordnung für Sonnenenergie.
   *
   * Bewusst **nicht** `--color-warning`: Das ist eine Statusfarbe und im Dark Mode selbst gelb
   * (`#ffd43b`) — dort trug das Zustandsband bereits genau diesen Ton. Produktion und Band hätten
   * sich im dunklen Thema nicht mehr unterschieden.
   */
  solar: string;
  /**
   * Erstes **Zustandsband** — Orange, ausserhalb der Farbfamilien der Datenreihen.
   *
   * Zustandsbänder zeigen keine Messgrösse, sondern einen Sollzustand. Sie dürfen deshalb **nicht**
   * die Farben der Kurven tragen: In der Einspeisesteuerung waren Band und Kurve zuerst beide grün
   * bzw. beide blau — in der Legende standen „Produktion" und „Batterieladung" ununterscheidbar
   * nebeneinander.
   */
  bandEins: string;
  /** Zweites Zustandsband — neutrales Grau, klar abgesetzt von {@link bandEins}. */
  bandZwei: string;
  /**
   * Sechste Reihe — Violett, ausserhalb der Grün-/Blau-/Rot-Familien.
   *
   * Die Einspeisesteuerung zeigt Preis, Produktion, Verbrauch, zwei Zustandsbänder **und** den
   * Ladezustand. Nach fünf Reihen war die Palette erschöpft; eine sechste in einem vorhandenen
   * Ton hiesse, dass zwei Kurven in der Legende gleich aussehen — der Fehler, der bei Band und
   * Kurve schon einmal auftrat.
   */
  soc: string;
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
    flaeche: token('--color-primary-light', '#81C784'),
    akzent: token('--color-danger', '#f44336'),
    solar: token('--color-chart-yellow', '#EAB308'),
    // Eigener Chart-Token statt `--color-warning`: Jenes wechselt mit dem Thema den Farbton
    // (hell orange, dunkel gelb) und kollidierte im Dark Mode mit der gelben Produktionslinie.
    bandEins: token('--color-chart-orange', '#F97316'),
    bandZwei: token('--color-gray-600', '#666666'),
    soc: token('--color-chart-purple', '#7E57C2')
  };
}
