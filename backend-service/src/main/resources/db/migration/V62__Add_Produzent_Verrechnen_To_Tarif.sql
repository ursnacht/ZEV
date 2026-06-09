-- Add flag controlling whether a GRUNDGEBUEHR tariff is also charged to producers.
-- Defaults to false: existing tariffs are NOT charged to producers unless explicitly enabled.
ALTER TABLE zev.tarif
    ADD COLUMN produzent_verrechnen BOOLEAN NOT NULL DEFAULT FALSE;
