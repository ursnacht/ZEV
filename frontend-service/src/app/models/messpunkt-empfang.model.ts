/** Quelle einer MQTT-Meldung (Specs/Messpunkt-Empfang.md, FR-1.2). */
export type MesspunktEmpfangQuelle = 'PI_GATEWAY' | 'GPLUG';

/** Ergebnis der letzten Meldung (Specs/Messpunkt-Empfang.md, FR-1.3). */
export type MesspunktEmpfangErgebnis = 'GESPEICHERT' | 'UNBEKANNTER_MESSPUNKT' | 'UNGUELTIG';

/**
 * Eine Zeile der Ansicht Messpunkt-Empfang.
 *
 * Bei `keinEmpfang` (Einheit, deren Messpunkt noch nie gesendet hat) sind alle Empfangsfelder
 * `null`, auch `id`. Das Backend schickt `null`, nicht `undefined` — deshalb `== null` prüfen.
 */
export interface MesspunktEmpfang {
  id: number | null;
  messpunkt: string;
  einheiten: string[];
  quelle: MesspunktEmpfangQuelle | null;
  /** Ortszeit, ISO ohne Zone */
  ersterEmpfang: string | null;
  /** Ortszeit, ISO ohne Zone */
  letzterEmpfang: string | null;
  /** Vom Backend über absolute Zeitpunkte gerechnet, nie negativ (FR-2.5) */
  sekundenSeitEmpfang: number | null;
  anzahlMeldungen: number | null;
  zaehlerstandBezug: number | null;
  zaehlerstandEinspeisung: number | null;
  ergebnis: MesspunktEmpfangErgebnis | null;
  grund: string | null;
  keinEmpfang: boolean;
}

/** Payload der letzten Meldung (FR-2.9); `null`, wenn keiner festgehalten ist. */
export interface MesspunktEmpfangMeldung {
  payload: string | null;
}
