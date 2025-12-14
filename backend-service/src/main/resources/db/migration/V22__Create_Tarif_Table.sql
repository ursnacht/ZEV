-- Create tarif table for storing tariffs with validity periods
CREATE SEQUENCE IF NOT EXISTS zev.tarif_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE zev.tarif (
    id BIGINT PRIMARY KEY DEFAULT nextval('zev.tarif_seq'),
    bezeichnung VARCHAR(30) NOT NULL,
    tariftyp VARCHAR(10) NOT NULL CHECK (tariftyp IN ('ZEV', 'VNB')),
    preis NUMERIC(10, 5) NOT NULL,
    gueltig_von DATE NOT NULL,
    gueltig_bis DATE NOT NULL,
    CONSTRAINT tarif_gueltig_check CHECK (gueltig_von <= gueltig_bis)
);

CREATE INDEX idx_tarif_typ_gueltig ON zev.tarif (tariftyp, gueltig_von, gueltig_bis);

COMMENT ON TABLE zev.tarif IS 'Tarife mit Gültigkeitszeitraum für Rechnungsgenerierung';
COMMENT ON COLUMN zev.tarif.tariftyp IS 'ZEV = Eigenverbrauch, VNB = Netzbezug';
COMMENT ON COLUMN zev.tarif.preis IS 'Preis in CHF/kWh mit 5 Nachkommastellen';
