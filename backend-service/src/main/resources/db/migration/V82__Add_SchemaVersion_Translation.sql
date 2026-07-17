-- Übersetzung für die Anzeige der Datenbank-Schema-Version auf der Startseite
INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('DB_SCHEMA_VERSION', 'Datenbank-Version', 'Database version')
ON CONFLICT (key) DO NOTHING;
