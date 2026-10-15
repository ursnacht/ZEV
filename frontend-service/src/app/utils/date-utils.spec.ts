import { blaettereZeitraum, formatSwissDate, formatSwissDateTime, parseSwissDate, swissToIsoDate, toIsoDate } from './date-utils';

describe('Date Utils', () => {
  describe('formatSwissDate', () => {
    it('should format ISO date string to Swiss format', () => {
      expect(formatSwissDate('2024-01-15')).toBe('15.01.2024');
      expect(formatSwissDate('2024-12-31')).toBe('31.12.2024');
      expect(formatSwissDate('2024-06-05')).toBe('05.06.2024');
    });

    it('should format Date object to Swiss format', () => {
      expect(formatSwissDate(new Date(2024, 0, 15))).toBe('15.01.2024');
      expect(formatSwissDate(new Date(2024, 11, 31))).toBe('31.12.2024');
    });

    it('should handle ISO datetime strings', () => {
      expect(formatSwissDate('2024-01-15T10:30:00')).toBe('15.01.2024');
      expect(formatSwissDate('2024-01-15T00:00:00.000Z')).toBe('15.01.2024');
    });

    it('should return fallback for null', () => {
      expect(formatSwissDate(null)).toBe('-');
      expect(formatSwissDate(null, 'N/A')).toBe('N/A');
    });

    it('should return fallback for undefined', () => {
      expect(formatSwissDate(undefined)).toBe('-');
      expect(formatSwissDate(undefined, '')).toBe('');
    });

    it('should return fallback for empty string', () => {
      expect(formatSwissDate('')).toBe('-');
    });

    it('should return fallback for invalid date string', () => {
      expect(formatSwissDate('invalid')).toBe('-');
      expect(formatSwissDate('not-a-date')).toBe('-');
    });

    it('should use custom fallback value', () => {
      expect(formatSwissDate(null, 'Kein Datum')).toBe('Kein Datum');
      expect(formatSwissDate(undefined, '')).toBe('');
    });
  });

  describe('parseSwissDate', () => {
    it('should parse Swiss date string to Date object', () => {
      const result = parseSwissDate('15.01.2024');
      expect(result).not.toBeNull();
      expect(result!.getDate()).toBe(15);
      expect(result!.getMonth()).toBe(0);
      expect(result!.getFullYear()).toBe(2024);
    });

    it('should parse end of year date', () => {
      const result = parseSwissDate('31.12.2024');
      expect(result).not.toBeNull();
      expect(result!.getDate()).toBe(31);
      expect(result!.getMonth()).toBe(11);
      expect(result!.getFullYear()).toBe(2024);
    });

    it('should return null for empty string', () => {
      expect(parseSwissDate('')).toBeNull();
    });

    it('should return null for invalid format', () => {
      expect(parseSwissDate('2024-01-15')).toBeNull();
      expect(parseSwissDate('15/01/2024')).toBeNull();
      expect(parseSwissDate('15-01-2024')).toBeNull();
    });

    it('should return null for invalid date values', () => {
      expect(parseSwissDate('32.01.2024')).toBeNull();
      expect(parseSwissDate('15.13.2024')).toBeNull();
      expect(parseSwissDate('31.02.2024')).toBeNull();
    });

    it('should return null for non-numeric parts', () => {
      expect(parseSwissDate('ab.01.2024')).toBeNull();
      expect(parseSwissDate('15.ab.2024')).toBeNull();
    });
  });

  describe('toIsoDate', () => {
    it('should format a local date as YYYY-MM-DD with leading zeros', () => {
      expect(toIsoDate(new Date(2026, 0, 5))).toBe('2026-01-05');
    });

    /**
     * Der Grund fuer die Funktion: kurz nach Mitternacht Ortszeit ist es in UTC noch der Vortag.
     * `toISOString()` lieferte dann das falsche Datum, `toIsoDate` das lokale.
     */
    it('should use the local date, not the UTC date, shortly after midnight', () => {
      const kurzNachMitternacht = new Date(2026, 9, 15, 0, 30);
      expect(toIsoDate(kurzNachMitternacht)).toBe('2026-10-15');
    });
  });

  describe('swissToIsoDate', () => {
    it('should convert Swiss date to ISO format', () => {
      expect(swissToIsoDate('15.01.2024')).toBe('2024-01-15');
      expect(swissToIsoDate('31.12.2024')).toBe('2024-12-31');
      expect(swissToIsoDate('05.06.2024')).toBe('2024-06-05');
    });

    it('should return empty string for invalid input', () => {
      expect(swissToIsoDate('')).toBe('');
      expect(swissToIsoDate('invalid')).toBe('');
      expect(swissToIsoDate('2024-01-15')).toBe('');
    });
  });

  describe('formatSwissDateTime', () => {
    it('should format an ISO string', () => {
      expect(formatSwissDateTime('2026-08-27T15:50:00')).toBe('27.08.2026 15:50');
    });

    it('should format a Date object', () => {
      expect(formatSwissDateTime(new Date(2026, 0, 5, 7, 9))).toBe('05.01.2026 07:09');
    });

    it('should pad day, month, hour and minute', () => {
      // Fuehrende Nullen sind der haeufigste Fehler beim Selberbauen - deshalb ein eigener Fall.
      expect(formatSwissDateTime(new Date(2026, 8, 1, 0, 0))).toBe('01.09.2026 00:00');
    });

    it('should return the fallback for null and undefined', () => {
      expect(formatSwissDateTime(null)).toBe('-');
      expect(formatSwissDateTime(undefined)).toBe('-');
      expect(formatSwissDateTime('')).toBe('-');
      expect(formatSwissDateTime(null, '–')).toBe('–');
    });

    it('should return the fallback for an unreadable value', () => {
      expect(formatSwissDateTime('kein Datum')).toBe('-');
    });
  });

  describe('blaettereZeitraum', () => {
    it('should move a single day by one day', () => {
      expect(blaettereZeitraum('2026-10-13', '2026-10-13', 1)).toEqual({ von: '2026-10-14', bis: '2026-10-14' });
      expect(blaettereZeitraum('2026-10-13', '2026-10-13', -1)).toEqual({ von: '2026-10-12', bis: '2026-10-12' });
    });

    it('should move a single day across month and year', () => {
      expect(blaettereZeitraum('2026-12-31', '2026-12-31', 1)).toEqual({ von: '2027-01-01', bis: '2027-01-01' });
    });

    it('should move a whole month and adjust the month end', () => {
      expect(blaettereZeitraum('2026-01-01', '2026-01-31', 1)).toEqual({ von: '2026-02-01', bis: '2026-02-28' });
      expect(blaettereZeitraum('2026-02-01', '2026-02-28', 1)).toEqual({ von: '2026-03-01', bis: '2026-03-31' });
      expect(blaettereZeitraum('2026-05-01', '2026-05-31', -1)).toEqual({ von: '2026-04-01', bis: '2026-04-30' });
    });

    it('should respect leap years', () => {
      expect(blaettereZeitraum('2028-01-01', '2028-01-31', 1)).toEqual({ von: '2028-02-01', bis: '2028-02-29' });
    });

    it('should move a quarter by three months', () => {
      expect(blaettereZeitraum('2026-07-01', '2026-09-30', 1)).toEqual({ von: '2026-10-01', bis: '2026-12-31' });
      expect(blaettereZeitraum('2026-01-01', '2026-03-31', -1)).toEqual({ von: '2025-10-01', bis: '2025-12-31' });
    });

    it('should move a year by twelve months', () => {
      expect(blaettereZeitraum('2026-01-01', '2026-12-31', 1)).toEqual({ von: '2027-01-01', bis: '2027-12-31' });
    });

    it('should move other ranges by their number of days', () => {
      expect(blaettereZeitraum('2026-10-01', '2026-10-07', 1)).toEqual({ von: '2026-10-08', bis: '2026-10-14' });
      expect(blaettereZeitraum('2026-10-08', '2026-10-14', -1)).toEqual({ von: '2026-10-01', bis: '2026-10-07' });
    });

    it('should not treat the 1st to a mid-month day as a whole month', () => {
      // 01.-15. = 15 Tage
      expect(blaettereZeitraum('2026-10-01', '2026-10-15', 1)).toEqual({ von: '2026-10-16', bis: '2026-10-30' });
    });

    it('should count days correctly across the daylight saving change', () => {
      // 25.10.2026: Umstellung auf Winterzeit - 7 Tage bleiben 7 Tage
      expect(blaettereZeitraum('2026-10-22', '2026-10-28', 1)).toEqual({ von: '2026-10-29', bis: '2026-11-04' });
    });

    it('should leave an invalid range unchanged', () => {
      expect(blaettereZeitraum('2026-10-14', '2026-10-01', 1)).toEqual({ von: '2026-10-14', bis: '2026-10-01' });
      expect(blaettereZeitraum('', '2026-10-01', 1)).toEqual({ von: '', bis: '2026-10-01' });
    });
  });
});
