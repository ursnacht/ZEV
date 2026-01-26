-- Create settings table for tenant-specific configuration
CREATE SEQUENCE IF NOT EXISTS zev.einstellungen_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE zev.einstellungen (
    id BIGINT PRIMARY KEY DEFAULT nextval('zev.einstellungen_seq'),
    org_id UUID NOT NULL UNIQUE,
    konfiguration JSONB NOT NULL
);

CREATE INDEX idx_einstellungen_org_id ON zev.einstellungen (org_id);

COMMENT ON TABLE zev.einstellungen IS 'Mandantenspezifische Einstellungen';
COMMENT ON COLUMN zev.einstellungen.konfiguration IS 'JSON-Struktur mit Rechnungskonfiguration';
