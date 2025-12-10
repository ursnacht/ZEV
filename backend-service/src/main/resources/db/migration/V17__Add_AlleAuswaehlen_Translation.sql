-- Toleranz Translation für Statistikseite
INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('ALLE_AUSWAEHLEN', 'Alle auswaehlen', 'Select all')
ON CONFLICT (key) DO NOTHING;
