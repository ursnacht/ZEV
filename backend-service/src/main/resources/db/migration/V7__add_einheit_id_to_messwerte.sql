-- Add einheit_id column to messwerte table
ALTER TABLE zev.messwerte ADD COLUMN einheit_id BIGINT;

-- Add foreign key constraint
ALTER TABLE zev.messwerte
ADD CONSTRAINT fk_messwerte_einheit
FOREIGN KEY (einheit_id)
REFERENCES zev.einheit(id);

-- Create index on einheit_id for better query performance
CREATE INDEX idx_messwerte_einheit_id ON zev.messwerte(einheit_id);
