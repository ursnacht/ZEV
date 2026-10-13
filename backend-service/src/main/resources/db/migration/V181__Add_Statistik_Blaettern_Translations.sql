-- Blättern des Zeitraums in der Statistik (Specs/Statistik.md, FR „Blättern")
INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('ZEITRAUM_ZURUECK', 'Vorheriger Zeitraum', 'Previous period'),
('ZEITRAUM_VOR', 'Nächster Zeitraum', 'Next period')
ON CONFLICT (key) DO NOTHING;
