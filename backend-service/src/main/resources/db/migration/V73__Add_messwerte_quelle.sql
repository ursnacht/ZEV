-- Herkunft eines Messwerts: CSV-Upload, MQTT-Ingest oder API. Rückwärtskompatibel (Default CSV).
ALTER TABLE zev.messwerte ADD COLUMN IF NOT EXISTS quelle VARCHAR(20) NOT NULL DEFAULT 'CSV';

COMMENT ON COLUMN zev.messwerte.quelle IS 'Herkunft des Messwerts: CSV | MQTT | API (Default CSV)';
