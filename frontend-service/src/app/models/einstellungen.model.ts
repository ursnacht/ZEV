/**
 * Invoice issuer (Rechnungssteller) information.
 */
export interface Steller {
  name: string;
  strasse: string;
  plz: string;
  ort: string;
}

/**
 * Verteilmodus je Mandant. Optional: fehlt bei Bestandsmandanten (→ Default PRODUCER_MESSUNG).
 */
export type Verteilmodus = 'PRODUCER_MESSUNG' | 'BILANZ';

/**
 * Konfiguration der Einspeisesteuerung je Mandant (Specs/Einspeisesteuerung.md, FR-7).
 *
 * Alle Felder optional: Bestandsmandanten haben den Block nicht, dann gelten die Vorgaben des
 * Servers (`0.05` / `0.31`). Beide Preisschwellen dürfen **negativ** sein — negative Preise sind
 * genau die Stunden, für die sich eine Steuerung lohnt.
 */
export interface SteuerKonfiguration {
  /** Schwellwert für „auf das Tal warten" in CHF/kWh. */
  schwellwert?: number | null;
  /** Wert einer gespeicherten kWh in CHF/kWh. */
  speicherwert?: number | null;
  /** Nutzbare Batteriekapazität in kWh; rein dokumentierend, keine Regel wertet sie aus. */
  batteriekapazitaet?: number | null;
  /** Mindest-Ladezustand in Prozent; darunter hebt `SOC_TIEF` eine Ladesperre auf. */
  socMinimum?: number | null;
  /** Prozentpunkte ueber dem Mindestwert, bis zu denen die Freigabe weiterlaeuft. */
  socHysterese?: number | null;
  /** Mindestabstand in CHF/kWh, um den das Tal unter dem aktuellen Preis liegen muss. */
  mindestAbstand?: number | null;
  /** Breitengrad der Anlage; zusammen mit den drei folgenden Grundlage des Prognose-Abrufs. */
  breitengrad?: number | null;
  laengengrad?: number | null;
  /** Ausrichtung in Open-Meteo-Konvention: 0 = Sued, -90 = Ost, 90 = West. */
  azimut?: number | null;
  /** Neigung: 0 = flach, 90 = senkrecht. */
  neigung?: number | null;
  /** Tage, ueber die der Umrechnungsfaktor von W/m2 auf kWh gelernt wird. */
  historieTage?: number | null;
}

/**
 * Invoice configuration stored per tenant.
 */
export interface RechnungKonfiguration {
  zahlungsfrist: string;
  iban: string;
  steller: Steller;
  verteilmodus?: Verteilmodus;
  /**
   * Einspeisesteuerung. Liegt im selben JSON wie die Rechnungsdaten — der Name des Typs passt
   * damit nicht mehr zu seinem Inhalt, die Spalte trägt längst mehr als die Rechnungskonfiguration.
   */
  steuerung?: SteuerKonfiguration;
}

/**
 * Tenant-specific settings.
 */
export interface Einstellungen {
  id?: number;
  rechnung: RechnungKonfiguration;
}
