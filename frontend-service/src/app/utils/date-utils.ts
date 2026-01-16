/**
 * Utility functions for date formatting
 */

/**
 * Formats a date to Swiss format (dd.MM.yyyy)
 *
 * @param date - Date object, ISO string (yyyy-MM-dd), or null/undefined
 * @param fallback - Value to return if date is null/undefined (default: '-')
 * @returns Formatted date string in Swiss format (dd.MM.yyyy) or fallback value
 *
 * @example
 * formatSwissDate('2024-01-15') // '15.01.2024'
 * formatSwissDate(new Date(2024, 0, 15)) // '15.01.2024'
 * formatSwissDate(null) // '-'
 * formatSwissDate(undefined, '') // ''
 */
export function formatSwissDate(date: Date | string | null | undefined, fallback: string = '-'): string {
  if (date === null || date === undefined || date === '') {
    return fallback;
  }

  let dateObj: Date;

  if (typeof date === 'string') {
    // Handle ISO date string (yyyy-MM-dd or yyyy-MM-ddTHH:mm:ss)
    dateObj = new Date(date);
  } else {
    dateObj = date;
  }

  // Check for invalid date
  if (isNaN(dateObj.getTime())) {
    return fallback;
  }

  const day = String(dateObj.getDate()).padStart(2, '0');
  const month = String(dateObj.getMonth() + 1).padStart(2, '0');
  const year = dateObj.getFullYear();

  return `${day}.${month}.${year}`;
}

/**
 * Parses a Swiss date string (dd.MM.yyyy) to a Date object
 *
 * @param dateString - Date string in Swiss format (dd.MM.yyyy)
 * @returns Date object or null if parsing fails
 *
 * @example
 * parseSwissDate('15.01.2024') // Date(2024, 0, 15)
 * parseSwissDate('invalid') // null
 */
export function parseSwissDate(dateString: string): Date | null {
  if (!dateString) {
    return null;
  }

  const parts = dateString.split('.');
  if (parts.length !== 3) {
    return null;
  }

  const day = parseInt(parts[0], 10);
  const month = parseInt(parts[1], 10) - 1; // Month is 0-indexed
  const year = parseInt(parts[2], 10);

  if (isNaN(day) || isNaN(month) || isNaN(year)) {
    return null;
  }

  const date = new Date(year, month, day);

  // Validate the date is correct (handles invalid dates like 31.02.2024)
  if (date.getDate() !== day || date.getMonth() !== month || date.getFullYear() !== year) {
    return null;
  }

  return date;
}

/**
 * Converts a Swiss date string (dd.MM.yyyy) to ISO format (yyyy-MM-dd)
 *
 * @param swissDate - Date string in Swiss format (dd.MM.yyyy)
 * @returns ISO date string (yyyy-MM-dd) or empty string if parsing fails
 *
 * @example
 * swissToIsoDate('15.01.2024') // '2024-01-15'
 */
export function swissToIsoDate(swissDate: string): string {
  const date = parseSwissDate(swissDate);
  if (!date) {
    return '';
  }

  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');

  return `${year}-${month}-${day}`;
}
