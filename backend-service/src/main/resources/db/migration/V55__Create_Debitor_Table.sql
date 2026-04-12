CREATE SEQUENCE IF NOT EXISTS zev.debitor_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE zev.debitor (
    id          BIGINT         PRIMARY KEY DEFAULT nextval('zev.debitor_seq'),
    mieter_id   BIGINT         NOT NULL,
    betrag      NUMERIC(10, 2) NOT NULL,
    datum_von   DATE           NOT NULL,
    datum_bis   DATE           NOT NULL,
    zahldatum   DATE,
    org_id      BIGINT         NOT NULL,
    CONSTRAINT fk_debitor_mieter FOREIGN KEY (mieter_id) REFERENCES zev.mieter(id) ON DELETE CASCADE,
    CONSTRAINT fk_debitor_org    FOREIGN KEY (org_id)    REFERENCES zev.organisation(id),
    CONSTRAINT debitor_betrag_check CHECK (betrag > 0),
    CONSTRAINT debitor_datum_check  CHECK (datum_von <= datum_bis),
    CONSTRAINT uq_debitor_mieter_von_org UNIQUE (mieter_id, datum_von, org_id)
);

COMMENT ON TABLE  zev.debitor               IS 'Debitorenkontrolle: persistierte Rechnungseinträge mit Zahlungsstatus';
COMMENT ON COLUMN zev.debitor.id            IS 'Primärschlüssel';
COMMENT ON COLUMN zev.debitor.mieter_id     IS 'FK auf Mieter; ON DELETE CASCADE';
COMMENT ON COLUMN zev.debitor.betrag        IS 'Rechnungsbetrag in CHF';
COMMENT ON COLUMN zev.debitor.datum_von     IS 'Abrechnungszeitraum Beginn';
COMMENT ON COLUMN zev.debitor.datum_bis     IS 'Abrechnungszeitraum Ende';
COMMENT ON COLUMN zev.debitor.zahldatum     IS 'Datum der Zahlung (NULL = offen)';
COMMENT ON COLUMN zev.debitor.org_id        IS 'Mandanten-ID (Multi-Tenancy)';

CREATE INDEX idx_debitor_org_datum ON zev.debitor (org_id, datum_von);
CREATE INDEX idx_debitor_mieter    ON zev.debitor (mieter_id);
