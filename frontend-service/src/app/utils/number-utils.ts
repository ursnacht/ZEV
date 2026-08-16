/**
 * Locale-unabhaengige Zahlenformatierung im Schweizer Format (siehe `Specs/generell.md`):
 * Punkt als Dezimal-, Hochkomma (`'`) als Tausendertrennzeichen — `1'234.567`.
 *
 * Bewusst **nicht** ueber Angulars `number`-Pipe oder `toLocaleString()`: beide haengen an der
 * Laufzeit-Locale und liefern je nach Umgebung ein Komma (`en-US`) oder ein typografisches
 * Apostroph U+2019 (`de-CH`) statt des hier gewuenschten ASCII-Hochkommas.
 */

/**
 * Formatiert eine Zahl im Schweizer Format.
 *
 * @param value Zu formatierende Zahl
 * @param decimals Anzahl Nachkommastellen (Default 3)
 * @returns Formatierter Wert, z.B. `1'234.567`
 */
export function formatSwissNumber(value: number, decimals = 3): string {
  const fixed = Math.abs(value).toFixed(decimals);
  const [intPart, fracPart] = fixed.split('.');
  const grouped = intPart.replace(/\B(?=(\d{3})+(?!\d))/g, '\'');
  const sign = value < 0 ? '-' : '';
  return fracPart ? `${sign}${grouped}.${fracPart}` : `${sign}${grouped}`;
}

/**
 * Wie {@link formatSwissNumber}, faengt aber Fehlwerte ab.
 *
 * @param value Zu formatierende Zahl oder null/undefined
 * @param decimals Anzahl Nachkommastellen (Default 3)
 * @param fallback Rueckgabe bei null/undefined (Default `–`, Gedankenstrich)
 * @returns Formatierter Wert oder Fallback
 */
export function formatSwissNumberOrFallback(
  value: number | null | undefined,
  decimals = 3,
  fallback = '–'
): string {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return fallback;
  }
  return formatSwissNumber(value, decimals);
}
