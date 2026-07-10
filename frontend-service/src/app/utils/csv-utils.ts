/**
 * Minimale, RFC-4180-orientierte CSV-Hilfsfunktionen (Komma als Trenner).
 *
 * Felder, die ein Komma, ein Anführungszeichen oder einen Zeilenumbruch enthalten,
 * werden in doppelte Anführungszeichen gesetzt; eingebettete Anführungszeichen werden
 * verdoppelt (`"` → `""`). Dadurch sind Export und Import verlustfrei (Round-Trip).
 */

/** Serialisiert Zeilen (Array von Feld-Arrays) als CSV-Text mit CRLF-Zeilenenden. */
export function toCsv(rows: string[][]): string {
  return rows.map(row => row.map(escapeField).join(',')).join('\r\n');
}

function escapeField(value: string | null | undefined): string {
  const v = value ?? '';
  if (/[",\r\n]/.test(v)) {
    return '"' + v.replace(/"/g, '""') + '"';
  }
  return v;
}

/**
 * Parst CSV-Text in Zeilen (Array von Feld-Arrays). Unterstützt gequotete Felder mit
 * eingebetteten Kommas, Zeilenumbrüchen und verdoppelten Anführungszeichen. Ein optionales
 * BOM am Dateianfang wird entfernt. Vollständig leere Zeilen werden übersprungen.
 */
export function parseCsv(text: string): string[][] {
  // BOM entfernen (z.B. von Excel-Exporten)
  if (text.charCodeAt(0) === 0xFEFF) {
    text = text.slice(1);
  }

  const rows: string[][] = [];
  let row: string[] = [];
  let field = '';
  let inQuotes = false;
  let i = 0;

  while (i < text.length) {
    const c = text[i];

    if (inQuotes) {
      if (c === '"') {
        if (text[i + 1] === '"') { field += '"'; i += 2; continue; }
        inQuotes = false; i++; continue;
      }
      field += c; i++; continue;
    }

    if (c === '"') { inQuotes = true; i++; continue; }
    if (c === ',') { row.push(field); field = ''; i++; continue; }
    if (c === '\r') { i++; continue; }               // CR ignorieren (CRLF/CR)
    if (c === '\n') { row.push(field); rows.push(row); row = []; field = ''; i++; continue; }

    field += c; i++;
  }

  // Letztes Feld / letzte Zeile
  row.push(field);
  rows.push(row);

  // Vollständig leere Zeilen (nur ein leeres Feld) entfernen
  return rows.filter(r => !(r.length === 1 && r[0] === ''));
}
