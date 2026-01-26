INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('SUCHEN', 'Suchen...', 'Search...')
ON CONFLICT (key) DO NOTHING;
