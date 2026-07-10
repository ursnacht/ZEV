import { toCsv, parseCsv } from './csv-utils';

describe('csv-utils', () => {
  describe('toCsv', () => {
    it('should join simple fields with commas and CRLF rows', () => {
      expect(toCsv([['a', 'b'], ['c', 'd']])).toBe('a,b\r\nc,d');
    });

    it('should quote fields containing a comma', () => {
      expect(toCsv([['x, y', 'z']])).toBe('"x, y",z');
    });

    it('should quote and double embedded quotes', () => {
      expect(toCsv([['say "hi"', 'ok']])).toBe('"say ""hi""",ok');
    });

    it('should quote fields containing newlines', () => {
      expect(toCsv([['line1\nline2', 'b']])).toBe('"line1\nline2",b');
    });

    it('should treat null/undefined as empty', () => {
      expect(toCsv([[null as unknown as string, undefined as unknown as string]])).toBe(',');
    });
  });

  describe('parseCsv', () => {
    it('should parse simple rows', () => {
      expect(parseCsv('a,b\r\nc,d')).toEqual([['a', 'b'], ['c', 'd']]);
    });

    it('should parse quoted fields with commas', () => {
      expect(parseCsv('"x, y",z')).toEqual([['x, y', 'z']]);
    });

    it('should parse doubled quotes as a single quote', () => {
      expect(parseCsv('"say ""hi""",ok')).toEqual([['say "hi"', 'ok']]);
    });

    it('should parse quoted fields spanning multiple lines', () => {
      expect(parseCsv('"line1\nline2",b')).toEqual([['line1\nline2', 'b']]);
    });

    it('should handle LF-only line endings', () => {
      expect(parseCsv('a,b\nc,d')).toEqual([['a', 'b'], ['c', 'd']]);
    });

    it('should skip empty lines and trailing newline', () => {
      expect(parseCsv('a,b\r\n\r\nc,d\r\n')).toEqual([['a', 'b'], ['c', 'd']]);
    });

    it('should strip a leading BOM', () => {
      expect(parseCsv('﻿a,b')).toEqual([['a', 'b']]);
    });
  });

  describe('round-trip', () => {
    it('should preserve tricky translation values', () => {
      const rows = [
        ['key', 'deutsch', 'englisch'],
        ['DATENBANK_TABELLE_WAEHLEN', '– Tabelle wählen –', '– Select table –'],
        ['SOME_KEY', 'Wert mit, Komma', 'value with "quotes"'],
        ['MULTILINE', 'Zeile1\nZeile2', 'ok']
      ];
      expect(parseCsv(toCsv(rows))).toEqual(rows);
    });
  });
});
