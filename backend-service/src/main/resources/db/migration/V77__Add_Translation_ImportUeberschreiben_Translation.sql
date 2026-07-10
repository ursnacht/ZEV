-- Übersetzung für die Import-Option "Bestehende Keys überschreiben" (Übersetzungsverwaltung)
INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('TRANSLATION_IMPORT_UEBERSCHREIBEN', 'Bestehende Keys überschreiben', 'Overwrite existing keys')
ON CONFLICT (key) DO NOTHING;
