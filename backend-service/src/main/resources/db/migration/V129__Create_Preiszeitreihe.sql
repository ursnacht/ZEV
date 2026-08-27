-- Preiszeitreihe: dynamische Einspeisepreise (Specs/Preiszeitreihe.md)
--
-- Bewusst OHNE org_id: Die Preise der BKW sind fuer alle Mandanten identisch, eine Kopie je
-- Mandant waere redundant, und der taegliche Job hat keinen Mandantenkontext. Der Zugriff ist
-- ueber die Permission tarife:manage geschuetzt (FR-2, NFR-2).

CREATE SEQUENCE IF NOT EXISTS zev.preiszeitreihe_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE zev.preiszeitreihe (
    id              BIGINT PRIMARY KEY DEFAULT nextval('zev.preiszeitreihe_seq'),
    zeit_von        TIMESTAMP      NOT NULL,
    zeit_bis        TIMESTAMP      NOT NULL,
    preis           NUMERIC(10, 5) NOT NULL,
    publikation     TIMESTAMP,
    aktualisiert_am TIMESTAMP      NOT NULL DEFAULT now(),
    CONSTRAINT uq_preiszeitreihe_zeit_von UNIQUE (zeit_von),
    CONSTRAINT ck_preiszeitreihe_intervall CHECK (zeit_von < zeit_bis),
    CONSTRAINT ck_preiszeitreihe_preis CHECK (preis >= 0)
);

COMMENT ON TABLE zev.preiszeitreihe IS
    'Dynamische Einspeisepreise (BKW), 15-Min-Raster. Mandantenuebergreifend - siehe Specs/Preiszeitreihe.md FR-2';
COMMENT ON COLUMN zev.preiszeitreihe.zeit_von IS
    'Intervallbeginn in UTC (verbatim aus der Quelle; lokale Zeit waere an der Zeitumstellung nicht eindeutig)';
COMMENT ON COLUMN zev.preiszeitreihe.zeit_bis IS
    'Intervallende in UTC';
COMMENT ON COLUMN zev.preiszeitreihe.preis IS
    'Einspeisepreis in CHF/kWh mit 5 Nachkommastellen';
COMMENT ON COLUMN zev.preiszeitreihe.publikation IS
    'publication_timestamp der Quelle (UTC); leer, wenn die Quelle keinen liefert';
COMMENT ON COLUMN zev.preiszeitreihe.aktualisiert_am IS
    'Zeitpunkt des letzten Schreibens (Upsert)';
