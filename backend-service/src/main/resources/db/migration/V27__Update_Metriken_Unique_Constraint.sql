-- Migration: Update unique constraint on metriken table to include org_id
-- This allows each organization to have their own metrics with the same name

-- Drop the old unique constraint on name only
ALTER TABLE metriken DROP CONSTRAINT IF EXISTS metriken_name_key;

-- Add new unique constraint on name + org_id
ALTER TABLE metriken ADD CONSTRAINT metriken_name_org_id_key UNIQUE (name, org_id);
