import { Pipe, PipeTransform } from '@angular/core';
import { formatSwissNumberOrFallback } from '../utils/number-utils';

/**
 * Angular pipe to format numbers in Swiss format (`1'234.567`).
 *
 * Ersetzt Angulars `number`-Pipe, die an der Laufzeit-Locale haengt und im Default (`en-US`)
 * ein Komma als Tausendertrennzeichen liefert — siehe `Specs/generell.md`.
 *
 * @example
 * {{ 1234.5 | swissNumber }}          // '1'234.500'
 * {{ 1234.5 | swissNumber:2 }}        // '1'234.50'
 * {{ null | swissNumber:2 }}          // '–'
 * {{ null | swissNumber:2:'' }}       // ''
 */
@Pipe({
  name: 'swissNumber',
  standalone: true,
  pure: true
})
export class SwissNumberPipe implements PipeTransform {
  /**
   * Transforms a number to Swiss format.
   *
   * @param value - Number or null/undefined
   * @param decimals - Number of decimal places (default: 3)
   * @param fallback - Value to return if the number is null/undefined (default: '–')
   * @returns Formatted number string in Swiss format
   */
  transform(value: number | null | undefined, decimals = 3, fallback = '–'): string {
    return formatSwissNumberOrFallback(value, decimals, fallback);
  }
}
