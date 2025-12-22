-- Mandantenfähigkeit: org_id Spalte zu allen relevanten Tabellen hinzufügen

-- Spalten hinzufügen (nullable für Migration)
ALTER TABLE zev.einheit ADD COLUMN IF NOT EXISTS org_id UUID;
ALTER TABLE zev.messwerte ADD COLUMN IF NOT EXISTS org_id UUID;
ALTER TABLE zev.tarif ADD COLUMN IF NOT EXISTS org_id UUID;
ALTER TABLE zev.metriken ADD COLUMN IF NOT EXISTS org_id UUID;

-- Bestehende Daten zur Default-Organisation migrieren
UPDATE zev.einheit SET org_id = 'c2c9ba74-de18-4491-9489-8185629edd93' WHERE org_id IS NULL;
UPDATE zev.messwerte SET org_id = 'c2c9ba74-de18-4491-9489-8185629edd93' WHERE org_id IS NULL;
UPDATE zev.tarif SET org_id = 'c2c9ba74-de18-4491-9489-8185629edd93' WHERE org_id IS NULL;
UPDATE zev.metriken SET org_id = 'c2c9ba74-de18-4491-9489-8185629edd93' WHERE org_id IS NULL;

-- NOT NULL Constraint setzen
ALTER TABLE zev.einheit ALTER COLUMN org_id SET NOT NULL;
ALTER TABLE zev.messwerte ALTER COLUMN org_id SET NOT NULL;
ALTER TABLE zev.tarif ALTER COLUMN org_id SET NOT NULL;
ALTER TABLE zev.metriken ALTER COLUMN org_id SET NOT NULL;

-- Indizes für Performance erstellen
CREATE INDEX IF NOT EXISTS idx_einheit_org_id ON zev.einheit(org_id);
CREATE INDEX IF NOT EXISTS idx_messwerte_org_id ON zev.messwerte(org_id);
CREATE INDEX IF NOT EXISTS idx_tarif_org_id ON zev.tarif(org_id);
CREATE INDEX IF NOT EXISTS idx_metriken_org_id ON zev.metriken(org_id);
