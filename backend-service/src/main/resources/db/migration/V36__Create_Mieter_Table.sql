CREATE SEQUENCE IF NOT EXISTS zev.mieter_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE zev.mieter (
    id BIGINT PRIMARY KEY DEFAULT nextval('zev.mieter_seq'),
    org_id UUID NOT NULL,
    name VARCHAR(150) NOT NULL,
    strasse VARCHAR(150),
    plz VARCHAR(20),
    ort VARCHAR(100),
    mietbeginn DATE NOT NULL,
    mietende DATE,
    einheit_id BIGINT NOT NULL,
    CONSTRAINT fk_mieter_einheit FOREIGN KEY (einheit_id) REFERENCES zev.einheit(id),
    CONSTRAINT mieter_datum_check CHECK (mietende IS NULL OR mietende > mietbeginn)
);

CREATE INDEX idx_mieter_einheit ON zev.mieter (einheit_id);
CREATE INDEX idx_mieter_org ON zev.mieter (org_id);
CREATE INDEX idx_mieter_zeitraum ON zev.mieter (einheit_id, mietbeginn, mietende);
