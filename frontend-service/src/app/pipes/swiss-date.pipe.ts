import { Pipe, PipeTransform } from '@angular/core';
import { formatSwissDate } from '../utils/date-utils';

/**
 * Angular pipe to format dates to Swiss format (dd.MM.yyyy)
 *
 * @example
 * {{ '2024-01-15' | swissDate }}           // '15.01.2024'
 * {{ someDate | swissDate }}               // '15.01.2024'
 * {{ null | swissDate }}                   // '-'
 * {{ null | swissDate:'' }}                // ''
 */
@Pipe({
  name: 'swissDate',
  standalone: true,
  pure: true
})
export class SwissDatePipe implements PipeTransform {
  /**
   * Transforms a date to Swiss format (dd.MM.yyyy)
   *
   * @param value - Date object, ISO string, or null/undefined
   * @param fallback - Value to return if date is null/undefined (default: '-')
   * @returns Formatted date string in Swiss format
   */
  transform(value: Date | string | null | undefined, fallback: string = '-'): string {
    return formatSwissDate(value, fallback);
  }
}
