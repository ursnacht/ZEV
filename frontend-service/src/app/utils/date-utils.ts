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
  return date ? toIsoDate(date) : '';
}

/**
 * Lokales Datum als ISO-Datum `YYYY-MM-DD` — aus den Datumsteilen der **Ortszeit**.
 *
 * Nicht `toISOString()`: Das liefert das UTC-Datum, und kurz nach Mitternacht stünde dann der
 * Vortag im Feld. Die eine Stelle für diese Umrechnung — vorher stand sie in neun Dateien.
 */
export function toIsoDate(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

/**
 * Formats a date to Swiss date and time (dd.MM.yyyy HH:mm)
 *
 * Bewusst manuell gebildet und **nicht** über `toLocaleString()`: Das haengt an der
 * Laufzeit-Locale und liefert je nach Umgebung ein anderes Trennzeichen
 * (`Specs/generell.md`, Zahlenformatierung).
 *
 * @param date - Date object or ISO string
 * @param fallback - Value to return if date is null/undefined (default: '-')
 * @param mitSekunden - `true` haengt `:ss` an (Specs/Messpunkt-Empfang.md, FR-2.3)
 * @returns Formatted string (dd.MM.yyyy HH:mm bzw. dd.MM.yyyy HH:mm:ss) or fallback value
 *
 * @example
 * formatSwissDateTime('2026-08-27T15:50:00') // '27.08.2026 15:50'
 * formatSwissDateTime('2026-08-27T15:50:07', '-', true) // '27.08.2026 15:50:07'
 * formatSwissDateTime(null) // '-'
 */
export function formatSwissDateTime(date: Date | string | null | undefined,
                                    fallback: string = '-',
                                    mitSekunden: boolean = false): string {
  if (date === null || date === undefined || date === '') {
    return fallback;
  }
  const wert = date instanceof Date ? date : new Date(date);
  if (isNaN(wert.getTime())) {
    return fallback;
  }
  const zwei = (teil: number) => String(teil).padStart(2, '0');
  return `${zwei(wert.getDate())}.${zwei(wert.getMonth() + 1)}.${wert.getFullYear()} `
    + `${zwei(wert.getHours())}:${zwei(wert.getMinutes())}`
    + (mitSekunden ? `:${zwei(wert.getSeconds())}` : '');
}

/**
 * Blättert einen Zeitraum um seine eigene Länge vor oder zurück (Specs/Statistik.md, FR „Blättern"):
 *
 * - **von = bis:** um einen Tag.
 * - **Ganze Monate** (von = 1. eines Monats, bis = letzter Tag eines Monats): um ihre Anzahl Monate;
 *   das Monatsende wird angepasst (Februar → 28./29., April → 30.). Ein Monat, ein Quartal, ein Jahr.
 * - **sonst:** um die Anzahl Tage (01.–07. → 08.–14.).
 *
 * Rechnet mit lokalen Kalenderdaten, nicht mit `toISOString()` (UTC) — sonst verschöbe die
 * Sommerzeit den Tag.
 *
 * @param von ISO-Datum (yyyy-MM-dd)
 * @param bis ISO-Datum (yyyy-MM-dd), nicht vor `von`
 * @param richtung 1 = vor, -1 = zurück
 * @returns neuer Zeitraum; bei ungültiger Eingabe unverändert
 */
export function blaettereZeitraum(von: string, bis: string, richtung: 1 | -1): { von: string; bis: string } {
  const start = isoZuDatum(von);
  const ende = isoZuDatum(bis);
  if (!start || !ende || start > ende) {
    return { von, bis };
  }

  if (von === bis) {
    return { von: toIsoDate(plusTage(start, richtung)), bis: toIsoDate(plusTage(ende, richtung)) };
  }

  const ganzeMonate = start.getDate() === 1 && plusTage(ende, 1).getDate() === 1;
  if (ganzeMonate) {
    const monate = (ende.getFullYear() - start.getFullYear()) * 12 + (ende.getMonth() - start.getMonth()) + 1;
    const versatz = richtung * monate;
    const neuVon = new Date(start.getFullYear(), start.getMonth() + versatz, 1);
    // Tag 0 des Folgemonats = letzter Tag des Monats - passt das Monatsende automatisch an
    const neuBis = new Date(ende.getFullYear(), ende.getMonth() + versatz + 1, 0);
    return { von: toIsoDate(neuVon), bis: toIsoDate(neuBis) };
  }

  const tage = Math.round((ende.getTime() - start.getTime()) / 86_400_000) + 1;
  return { von: toIsoDate(plusTage(start, richtung * tage)), bis: toIsoDate(plusTage(ende, richtung * tage)) };
}

/**
 * Das **vorangehende** Quartal als ISO-Zeitraum — im Q1 das Q4 des Vorjahres.
 *
 * Vorbelegung der Zeitraum-Filter (Statistik, Rechnungen, Debitoren, Messwerte-Grafik,
 * Solarberechnung). Stand vorher in fünf Komponenten als gleicher Block.
 *
 * @param heute Bezugsdatum, für Tests; Vorgabe ist jetzt
 */
export function vorquartal(heute: Date = new Date()): { von: string; bis: string } {
  let jahr = heute.getFullYear();
  let nummer = Math.ceil((heute.getMonth() + 1) / 3) - 1;
  if (nummer < 1) {
    nummer = 4;
    jahr--;
  }
  return quartal(jahr, nummer);
}

/**
 * Ein Quartal als ISO-Zeitraum, z.B. `quartal(2026, 3)` → 01.07.–30.09.2026.
 *
 * Die eine Stelle für die Quartalsgrenzen — der Quartal-Selektor und `vorquartal` rechnen damit.
 *
 * @param nummer 1–4
 */
export function quartal(jahr: number, nummer: number): { von: string; bis: string } {
  const startMonat = (nummer - 1) * 3;
  // Tag 0 des Folgemonats = letzter Tag des Quartals
  return { von: toIsoDate(new Date(jahr, startMonat, 1)), bis: toIsoDate(new Date(jahr, startMonat + 3, 0)) };
}

/**
 * Letzter Tag des Monats eines ISO-Datums (`2024-02-10` → `2024-02-29`); `''` bei ungültigem Datum.
 *
 * Für „Bis" nach einer Änderung von „Von". Gelesen als **lokales** Datum, nicht über
 * `new Date(iso)` — das deutet ein reines Datum als UTC-Mitternacht.
 */
export function monatsende(iso: string): string {
  const datum = isoZuDatum(iso);
  return datum ? toIsoDate(new Date(datum.getFullYear(), datum.getMonth() + 1, 0)) : '';
}

/** ISO-Datum (`yyyy-MM-dd`) als lokales Datum; `null` bei ungültiger Eingabe. */
export function isoZuDatum(iso: string): Date | null {
  const teile = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso ?? '');
  return teile ? new Date(Number(teile[1]), Number(teile[2]) - 1, Number(teile[3])) : null;
}

/** Datum plus `tage` Kalendertage (negativ: zurück), unabhängig von der Sommerzeit. */
export function plusTage(datum: Date, tage: number): Date {
  return new Date(datum.getFullYear(), datum.getMonth(), datum.getDate() + tage);
}
