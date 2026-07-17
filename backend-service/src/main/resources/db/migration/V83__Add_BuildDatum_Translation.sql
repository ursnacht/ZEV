-- Übersetzung für die Anzeige des Build-Datums auf der Startseite
INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('BUILD_DATUM', 'Build', 'Build')
ON CONFLICT (key) DO NOTHING;
