-- Schritt 1: konfiguration-Spalte zur Organisation-Tabelle hinzufügen
ALTER TABLE zev.organisation ADD COLUMN konfiguration JSONB;

-- Schritt 2: Bestehende Einstellungs-Daten migrieren
UPDATE zev.organisation o
SET konfiguration = e.konfiguration
FROM zev.einstellungen e
WHERE e.org_id = o.id;

-- Schritt 3: einstellungen-Tabelle und Sequenz löschen
DROP TABLE zev.einstellungen;
DROP SEQUENCE IF EXISTS zev.einstellungen_seq;
