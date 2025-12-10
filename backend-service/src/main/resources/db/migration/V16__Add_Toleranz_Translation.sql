-- Toleranz Translation für Statistikseite
INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('TOLERANZ', 'Toleranz', 'Tolerance')
ON CONFLICT (key) DO NOTHING;
