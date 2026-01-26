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
 * Invoice configuration stored per tenant.
 */
export interface RechnungKonfiguration {
  zahlungsfrist: string;
  iban: string;
  steller: Steller;
}

/**
 * Tenant-specific settings.
 */
export interface Einstellungen {
  id?: number;
  rechnung: RechnungKonfiguration;
}
