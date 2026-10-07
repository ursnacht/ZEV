-- Statistik: Panel über den gesamten gewählten Zeitraum (Specs/Statistik.md).

INSERT INTO zev.translation (key, deutsch, englisch) VALUES

('STATISTIK_GESAMTER_ZEITRAUM',
 'Gesamter Zeitraum',
 'Entire period')

ON CONFLICT (key) DO NOTHING;
