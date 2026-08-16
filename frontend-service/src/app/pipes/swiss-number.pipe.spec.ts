import { SwissNumberPipe } from './swiss-number.pipe';

describe('SwissNumberPipe', () => {
  let pipe: SwissNumberPipe;

  beforeEach(() => {
    pipe = new SwissNumberPipe();
  });

  it('should create an instance', () => {
    expect(pipe).toBeTruthy();
  });

  describe('transform', () => {
    it('should format with three decimals by default', () => {
      expect(pipe.transform(1234.5)).toBe('1\'234.500');
    });

    it('should honour the decimals parameter', () => {
      expect(pipe.transform(1234.5, 2)).toBe('1\'234.50');
      expect(pipe.transform(0.195, 5)).toBe('0.19500');
    });

    it('should use an apostrophe as thousands separator and a dot as decimal separator', () => {
      expect(pipe.transform(1234567.891, 3)).toBe('1\'234\'567.891');
    });

    it('should format negative numbers with a leading sign', () => {
      expect(pipe.transform(-1234.5, 2)).toBe('-1\'234.50');
    });

    it('should return the default fallback for null', () => {
      expect(pipe.transform(null)).toBe('–');
    });

    it('should return the default fallback for undefined', () => {
      expect(pipe.transform(undefined)).toBe('–');
    });

    it('should return a custom fallback when provided', () => {
      expect(pipe.transform(null, 2, 'N/A')).toBe('N/A');
      expect(pipe.transform(undefined, 2, '')).toBe('');
    });

    it('should format zero instead of returning the fallback', () => {
      expect(pipe.transform(0, 2)).toBe('0.00');
    });
  });
});
