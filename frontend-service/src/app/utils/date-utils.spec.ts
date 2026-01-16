import { formatSwissDate, parseSwissDate, swissToIsoDate } from './date-utils';

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
});
