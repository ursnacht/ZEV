-- Füge einheit_name Spalte zur metriken Tabelle hinzu
-- Ermöglicht die Speicherung von Metriken pro Einheit (z.B. letzter Upload-Zeitpunkt)

-- Spalte hinzufügen (nullable, da bestehende Metriken keinen Einheit-Bezug haben)
ALTER TABLE zev.metriken ADD COLUMN einheit_name VARCHAR(100);

-- Entferne den UNIQUE Constraint auf name, da name + einheit_name zusammen unique sein soll
ALTER TABLE zev.metriken DROP CONSTRAINT IF EXISTS metriken_name_key;

-- Neuer UNIQUE Constraint auf name + einheit_name Kombination
-- COALESCE wird verwendet, damit NULL-Werte korrekt behandelt werden
CREATE UNIQUE INDEX idx_metriken_name_einheit_unique
    ON zev.metriken(name, COALESCE(einheit_name, ''));

-- Index für schnellen Zugriff nach name + einheit_name
CREATE INDEX idx_metriken_name_einheit ON zev.metriken(name, einheit_name);
