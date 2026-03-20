-- Extend tariftyp column to accommodate GRUNDGEBUEHR (12 characters)
ALTER TABLE zev.tarif ALTER COLUMN tariftyp TYPE VARCHAR(20);

-- Update check constraint to include GRUNDGEBUEHR
ALTER TABLE zev.tarif DROP CONSTRAINT IF EXISTS tarif_tariftyp_check;
ALTER TABLE zev.tarif ADD CONSTRAINT tarif_tariftyp_check CHECK (tariftyp IN ('ZEV', 'VNB', 'GRUNDGEBUEHR'));
