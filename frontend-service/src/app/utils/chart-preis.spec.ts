import { PreiszeitreihePunkt } from '../models/preiszeitreihe.model';
import { PREIS_NACHKOMMASTELLEN, formatPreis, preisPunkte } from './chart-preis';

describe('Chart Preis Utils', () => {

  describe('formatPreis', () => {
    it('should format a price with five decimals', () => {
      expect(formatPreis(0.1234567)).toBe('0.12346');
      expect(PREIS_NACHKOMMASTELLEN).toBe(5);
    });

    it('should pad short prices to five decimals', () => {
      expect(formatPreis(0.08)).toBe('0.08000');
      expect(formatPreis(0)).toBe('0.00000');
    });

    it('should keep the sign of a negative price', () => {
      // Dynamische Einspeisepreise koennen negativ sein.
      expect(formatPreis(-0.5)).toBe('-0.50000');
    });

    it('should use the Swiss thousands separator', () => {
      expect(formatPreis(1234.5)).toBe("1'234.50000");
    });
  });

  describe('preisPunkte', () => {
    const preise: PreiszeitreihePunkt[] = [
      { zeit: '2026-01-15T10:00:00', preis: 0.08125 },
      { zeit: '2026-01-15T10:15:00', preis: -0.01 }
    ];

    it('should convert every point to [timestamp, price] in input order', () => {
      expect(preisPunkte(preise)).toEqual([
        [new Date('2026-01-15T10:00:00').getTime(), 0.08125],
        [new Date('2026-01-15T10:15:00').getTime(), -0.01]
      ]);
    });

    it('should read the time as local time', () => {
      // Der Server liefert Ortszeit ohne Zone - als UTC gelesen laege die Kurve um die
      // Zonenverschiebung daneben.
      const [zeitpunkt] = preisPunkte(preise)[0];

      expect(new Date(zeitpunkt).getHours()).toBe(10);
    });

    it('should return an empty list for no prices', () => {
      expect(preisPunkte([])).toEqual([]);
    });
  });
});
