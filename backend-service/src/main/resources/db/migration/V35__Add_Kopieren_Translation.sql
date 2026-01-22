INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('KOPIEREN', 'Kopieren', 'Copy')
ON CONFLICT (key) DO NOTHING;
