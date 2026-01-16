import { SwissDatePipe } from './swiss-date.pipe';

describe('SwissDatePipe', () => {
  let pipe: SwissDatePipe;

  beforeEach(() => {
    pipe = new SwissDatePipe();
  });

  it('should create an instance', () => {
    expect(pipe).toBeTruthy();
  });

  describe('transform', () => {
    it('should transform ISO date string to Swiss format', () => {
      expect(pipe.transform('2024-01-15')).toBe('15.01.2024');
      expect(pipe.transform('2024-12-31')).toBe('31.12.2024');
    });

    it('should transform Date object to Swiss format', () => {
      expect(pipe.transform(new Date(2024, 0, 15))).toBe('15.01.2024');
    });

    it('should return default fallback for null', () => {
      expect(pipe.transform(null)).toBe('-');
    });

    it('should return default fallback for undefined', () => {
      expect(pipe.transform(undefined)).toBe('-');
    });

    it('should return custom fallback when provided', () => {
      expect(pipe.transform(null, 'N/A')).toBe('N/A');
      expect(pipe.transform(undefined, '')).toBe('');
    });

    it('should return fallback for invalid date', () => {
      expect(pipe.transform('invalid')).toBe('-');
    });
  });
});
