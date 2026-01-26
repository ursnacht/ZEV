-- Make address fields required for tenants
-- First update any NULL values to empty string (shouldn't exist but just in case)
UPDATE zev.mieter SET strasse = '' WHERE strasse IS NULL;
UPDATE zev.mieter SET plz = '' WHERE plz IS NULL;
UPDATE zev.mieter SET ort = '' WHERE ort IS NULL;

-- Then add NOT NULL constraints
ALTER TABLE zev.mieter ALTER COLUMN strasse SET NOT NULL;
ALTER TABLE zev.mieter ALTER COLUMN plz SET NOT NULL;
ALTER TABLE zev.mieter ALTER COLUMN ort SET NOT NULL;

-- Add translations for validation error messages
INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('STRASSE_IST_ERFORDERLICH', 'Strasse ist erforderlich', 'Street is required'),
('PLZ_IST_ERFORDERLICH', 'PLZ ist erforderlich', 'Postal code is required'),
('ORT_IST_ERFORDERLICH', 'Ort ist erforderlich', 'City is required')
ON CONFLICT (key) DO NOTHING;
