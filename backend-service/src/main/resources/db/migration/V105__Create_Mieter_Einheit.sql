-- Zuordnung Mieter <-> Einheit als 1:n (Specs/Ladestationen.md FR-2).
-- Bisher trug der Mieter genau eine Einheit (`mieter.einheit_id`). Damit Wohnung und
-- Ladestation(en) auf einer Rechnung erscheinen koennen - und ein Nutzer ohne Wohnung ueberhaupt
-- abrechenbar wird - wandert die Beziehung in eine eigene Tabelle.
CREATE TABLE zev.mieter_einheit (
    org_id     BIGINT NOT NULL,
    mieter_id  BIGINT NOT NULL,
    einheit_id BIGINT NOT NULL,
    CONSTRAINT pk_mieter_einheit PRIMARY KEY (mieter_id, einheit_id),
    -- Mit dem Mieter verschwinden nur seine Zuordnungen, nicht die Einheiten.
    CONSTRAINT fk_mieter_einheit_mieter FOREIGN KEY (mieter_id)
        REFERENCES zev.mieter (id) ON DELETE CASCADE,
    -- RESTRICT: Eine Einheit mit Zuordnungen darf nicht verschwinden, sonst stuende ein Mieter
    -- ohne Einheit da. Der Service weist das Loeschen mit einer Meldung ab.
    CONSTRAINT fk_mieter_einheit_einheit FOREIGN KEY (einheit_id)
        REFERENCES zev.einheit (id) ON DELETE RESTRICT,
    CONSTRAINT fk_mieter_einheit_org FOREIGN KEY (org_id)
        REFERENCES zev.organisation (id)
);

COMMENT ON TABLE zev.mieter_einheit IS 'Zuordnung eines Mieters zu seinen Einheiten (Wohnung, Ladestationen). Der Mietzeitraum bleibt am Mieter und gilt fuer alle seine Einheiten.';
COMMENT ON COLUMN zev.mieter_einheit.org_id IS 'Mandant; serverseitig gesetzt';
COMMENT ON COLUMN zev.mieter_einheit.mieter_id IS 'Zugeordneter Mieter';
COMMENT ON COLUMN zev.mieter_einheit.einheit_id IS 'Zugeordnete Einheit (CONSUMER, PRODUCER oder LADESTATION)';

-- Deckt die Abfrage bei der Rechnungserzeugung ab (alle Einheiten eines Mieters).
CREATE INDEX idx_mieter_einheit_org_mieter ON zev.mieter_einheit (org_id, mieter_id);
-- Gegenrichtung: alle Mieter einer Einheit (Mietende-/Ueberschneidungspruefung, Loeschschutz).
CREATE INDEX idx_mieter_einheit_einheit ON zev.mieter_einheit (einheit_id);

-- Bestand uebernehmen: jeder Mieter behaelt seine bisherige Einheit.
INSERT INTO zev.mieter_einheit (org_id, mieter_id, einheit_id)
SELECT org_id, id, einheit_id FROM zev.mieter;

ALTER TABLE zev.mieter DROP COLUMN einheit_id;
