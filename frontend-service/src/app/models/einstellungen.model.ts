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
 * Invoice configuration stored per tenant.
 */
export interface RechnungKonfiguration {
  zahlungsfrist: string;
  iban: string;
  steller: Steller;
  verteilmodus?: Verteilmodus;
}

/**
 * Tenant-specific settings.
 */
export interface Einstellungen {
  id?: number;
  rechnung: RechnungKonfiguration;
}
