import { formatSwissNumber, formatSwissNumberOrFallback } from './number-utils';

describe('number-utils', () => {

  describe('formatSwissNumber', () => {
    it('should format with three decimals by default', () => {
      expect(formatSwissNumber(1234.5)).toBe('1\'234.500');
      expect(formatSwissNumber(0)).toBe('0.000');
    });

    it('should use a dot as decimal separator', () => {
      expect(formatSwissNumber(12.345)).toBe('12.345');
    });

    it('should use an ASCII apostrophe as thousands separator', () => {
      expect(formatSwissNumber(1000)).toBe('1\'000.000');
      expect(formatSwissNumber(1234567.891)).toBe('1\'234\'567.891');
    });

    it('should not group numbers below 1000', () => {
      expect(formatSwissNumber(999.999)).toBe('999.999');
    });

    it('should group every three digits in large numbers', () => {
      expect(formatSwissNumber(1234567890, 0)).toBe('1\'234\'567\'890');
    });

    it('should honour the decimals parameter', () => {
      expect(formatSwissNumber(1234.5, 2)).toBe('1\'234.50');
      expect(formatSwissNumber(0.195, 5)).toBe('0.19500');
    });

    it('should omit the decimal part when decimals is 0', () => {
      expect(formatSwissNumber(1234.4, 0)).toBe('1\'234');
    });

    it('should round to the requested number of decimals', () => {
      expect(formatSwissNumber(1.2345, 2)).toBe('1.23');
      expect(formatSwissNumber(1.2355, 2)).toBe('1.24');
    });

    it('should keep the sign in front of the grouped value', () => {
      expect(formatSwissNumber(-1234.5, 2)).toBe('-1\'234.50');
      expect(formatSwissNumber(-0.5, 1)).toBe('-0.5');
    });

    it('should format zero without a sign', () => {
      expect(formatSwissNumber(0, 2)).toBe('0.00');
    });
  });

  describe('formatSwissNumberOrFallback', () => {
    it('should format a number like formatSwissNumber', () => {
      expect(formatSwissNumberOrFallback(1234.5)).toBe(formatSwissNumber(1234.5));
      expect(formatSwissNumberOrFallback(1234.5, 2)).toBe('1\'234.50');
    });

    it('should return the default fallback for null', () => {
      expect(formatSwissNumberOrFallback(null)).toBe('–');
    });

    it('should return the default fallback for undefined', () => {
      expect(formatSwissNumberOrFallback(undefined)).toBe('–');
    });

    it('should return the default fallback for NaN', () => {
      expect(formatSwissNumberOrFallback(Number.NaN)).toBe('–');
    });

    it('should return a custom fallback when provided', () => {
      expect(formatSwissNumberOrFallback(null, 2, 'N/A')).toBe('N/A');
      expect(formatSwissNumberOrFallback(undefined, 2, '')).toBe('');
    });

    it('should format zero instead of returning the fallback', () => {
      expect(formatSwissNumberOrFallback(0, 2)).toBe('0.00');
    });
  });
});
