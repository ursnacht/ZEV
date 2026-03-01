-- Schritt 1: Alle distinct Keycloak-UUIDs aus allen 6 Tabellen sammeln
INSERT INTO zev.organisation (keycloak_org_id, name, erstellt_am)
SELECT DISTINCT org_id, org_id::text, NOW()
FROM (
    SELECT org_id FROM zev.einheit
    UNION SELECT org_id FROM zev.messwerte
    UNION SELECT org_id FROM zev.tarif
    UNION SELECT org_id FROM zev.metriken
    UNION SELECT org_id FROM zev.mieter
    UNION SELECT org_id FROM zev.einstellungen
) alle_orgs
ON CONFLICT (keycloak_org_id) DO NOTHING;

-- Schritt 2: einheit
ALTER TABLE zev.einheit ADD COLUMN org_id_bigint BIGINT;
UPDATE zev.einheit SET org_id_bigint = (SELECT id FROM zev.organisation WHERE keycloak_org_id = zev.einheit.org_id);
ALTER TABLE zev.einheit ALTER COLUMN org_id_bigint SET NOT NULL;
DROP INDEX IF EXISTS zev.idx_einheit_org_id;
ALTER TABLE zev.einheit DROP COLUMN org_id;
ALTER TABLE zev.einheit RENAME COLUMN org_id_bigint TO org_id;
ALTER TABLE zev.einheit ADD CONSTRAINT fk_einheit_org FOREIGN KEY (org_id) REFERENCES zev.organisation(id);
CREATE INDEX idx_einheit_org_id ON zev.einheit (org_id);

-- Schritt 3: messwerte
ALTER TABLE zev.messwerte ADD COLUMN org_id_bigint BIGINT;
UPDATE zev.messwerte SET org_id_bigint = (SELECT id FROM zev.organisation WHERE keycloak_org_id = zev.messwerte.org_id);
ALTER TABLE zev.messwerte ALTER COLUMN org_id_bigint SET NOT NULL;
DROP INDEX IF EXISTS zev.idx_messwerte_org_id;
ALTER TABLE zev.messwerte DROP COLUMN org_id;
ALTER TABLE zev.messwerte RENAME COLUMN org_id_bigint TO org_id;
ALTER TABLE zev.messwerte ADD CONSTRAINT fk_messwerte_org FOREIGN KEY (org_id) REFERENCES zev.organisation(id);
CREATE INDEX idx_messwerte_org_id ON zev.messwerte (org_id);

-- Schritt 4: tarif
ALTER TABLE zev.tarif ADD COLUMN org_id_bigint BIGINT;
UPDATE zev.tarif SET org_id_bigint = (SELECT id FROM zev.organisation WHERE keycloak_org_id = zev.tarif.org_id);
ALTER TABLE zev.tarif ALTER COLUMN org_id_bigint SET NOT NULL;
DROP INDEX IF EXISTS zev.idx_tarif_org_id;
ALTER TABLE zev.tarif DROP COLUMN org_id;
ALTER TABLE zev.tarif RENAME COLUMN org_id_bigint TO org_id;
ALTER TABLE zev.tarif ADD CONSTRAINT fk_tarif_org FOREIGN KEY (org_id) REFERENCES zev.organisation(id);
CREATE INDEX idx_tarif_org_id ON zev.tarif (org_id);

-- Schritt 5: metriken (Unique-Constraint auf name+org_id muss neu erstellt werden)
ALTER TABLE zev.metriken ADD COLUMN org_id_bigint BIGINT;
UPDATE zev.metriken SET org_id_bigint = (SELECT id FROM zev.organisation WHERE keycloak_org_id = zev.metriken.org_id);
ALTER TABLE zev.metriken ALTER COLUMN org_id_bigint SET NOT NULL;
DROP INDEX IF EXISTS zev.idx_metriken_org_id;
ALTER TABLE zev.metriken DROP CONSTRAINT IF EXISTS metriken_name_org_id_key;
ALTER TABLE zev.metriken DROP COLUMN org_id;
ALTER TABLE zev.metriken RENAME COLUMN org_id_bigint TO org_id;
ALTER TABLE zev.metriken ADD CONSTRAINT fk_metriken_org FOREIGN KEY (org_id) REFERENCES zev.organisation(id);
ALTER TABLE zev.metriken ADD CONSTRAINT metriken_name_org_id_key UNIQUE (name, org_id);
CREATE INDEX idx_metriken_org_id ON zev.metriken (org_id);

-- Schritt 6: mieter
ALTER TABLE zev.mieter ADD COLUMN org_id_bigint BIGINT;
UPDATE zev.mieter SET org_id_bigint = (SELECT id FROM zev.organisation WHERE keycloak_org_id = zev.mieter.org_id);
ALTER TABLE zev.mieter ALTER COLUMN org_id_bigint SET NOT NULL;
DROP INDEX IF EXISTS zev.idx_mieter_org;
ALTER TABLE zev.mieter DROP COLUMN org_id;
ALTER TABLE zev.mieter RENAME COLUMN org_id_bigint TO org_id;
ALTER TABLE zev.mieter ADD CONSTRAINT fk_mieter_org FOREIGN KEY (org_id) REFERENCES zev.organisation(id);
CREATE INDEX idx_mieter_org ON zev.mieter (org_id);

-- Schritt 7: einstellungen (Unique-Constraint auf org_id muss neu erstellt werden)
ALTER TABLE zev.einstellungen ADD COLUMN org_id_bigint BIGINT;
UPDATE zev.einstellungen SET org_id_bigint = (SELECT id FROM zev.organisation WHERE keycloak_org_id = zev.einstellungen.org_id);
ALTER TABLE zev.einstellungen ALTER COLUMN org_id_bigint SET NOT NULL;
DROP INDEX IF EXISTS zev.idx_einstellungen_org_id;
ALTER TABLE zev.einstellungen DROP COLUMN org_id;
ALTER TABLE zev.einstellungen RENAME COLUMN org_id_bigint TO org_id;
ALTER TABLE zev.einstellungen ADD CONSTRAINT uq_einstellungen_org_id UNIQUE (org_id);
ALTER TABLE zev.einstellungen ADD CONSTRAINT fk_einstellungen_org FOREIGN KEY (org_id) REFERENCES zev.organisation(id);
CREATE INDEX idx_einstellungen_org_id ON zev.einstellungen (org_id);
