-- Add zev_calculated column to store calculated solar distribution
ALTER TABLE zev.messwerte ADD COLUMN zev_calculated DOUBLE PRECISION;

-- Add index on zeit column for better query performance during calculations
CREATE INDEX idx_messwerte_zeit ON zev.messwerte(zeit);
