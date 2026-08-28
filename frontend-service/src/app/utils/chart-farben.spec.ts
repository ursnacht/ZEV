import { chartFarben } from './chart-farben';

/**
 * Tests für `chart-farben.ts`.
 *
 * Der Baustein hat genau eine Aufgabe und zwei Fälle: Token vorhanden → Token, Token fehlt →
 * Rückfallwert. Der zweite Fall ist der wichtigere: Ohne ihn wäre ein Diagramm unsichtbar, sobald
 * ein Token umbenannt wird oder der Stil noch nicht geladen ist.
 */
describe('chartFarben', () => {

  afterEach(() => {
    // Gesetzte Eigenschaften wieder entfernen, sonst faerbt ein Test den naechsten.
    ['--color-gray-500', '--color-gray-700', '--color-gray-300',
      '--color-primary', '--color-secondary', '--color-primary-light']
      .forEach(name => document.documentElement.style.removeProperty(name));
  });

  it('should deliver a colour for every purpose', () => {
    const farben = chartFarben();

    expect(farben.achse).toBeTruthy();
    expect(farben.text).toBeTruthy();
    expect(farben.gitter).toBeTruthy();
    expect(farben.primaer).toBeTruthy();
    expect(farben.sekundaer).toBeTruthy();
    expect(farben.flaeche).toBeTruthy();
  });

  it('should fall back when a token is missing', () => {
    // In jsdom sind die Design-Tokens nicht geladen - genau der Fall, den die Rueckfallwerte
    // abdecken. Ohne sie waere das Diagramm hier unsichtbar.
    const farben = chartFarben();

    expect(farben.primaer).toBe('#4CAF50');
    expect(farben.sekundaer).toBe('#2196F3');
  });

  it('should prefer the token over the fallback', () => {
    document.documentElement.style.setProperty('--color-primary', 'rgb(1, 2, 3)');
    document.documentElement.style.setProperty('--color-secondary', 'rgb(4, 5, 6)');

    const farben = chartFarben();

    expect(farben.primaer).toBe('rgb(1, 2, 3)');
    expect(farben.sekundaer).toBe('rgb(4, 5, 6)');
  });

  it('should read the tokens on every call', () => {
    const vorher = chartFarben().primaer;
    document.documentElement.style.setProperty('--color-primary', 'rgb(9, 9, 9)');

    const nachher = chartFarben().primaer;

    // Kein Zwischenspeicher: Der Dark-Mode-Wechsel wirkt beim naechsten Zeichnen, und genau dafuer
    // muss jeder Aufruf neu lesen.
    expect(vorher).not.toBe(nachher);
    expect(nachher).toBe('rgb(9, 9, 9)');
  });

  it('should use different colours for the two series', () => {
    const farben = chartFarben();

    // Zwei Reihen im selben Diagramm muessen unterscheidbar bleiben.
    expect(farben.primaer).not.toBe(farben.sekundaer);
  });
});
