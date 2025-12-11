-- Einheit Summen Translations fuer Statistikseite
INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('SUMMEN_PRO_EINHEIT', 'Summen pro Einheit', 'Sums per Unit'),
('EINHEIT', 'Einheit', 'Unit'),
('TYP', 'Typ', 'Type'),
('TOTAL', 'Total', 'Total'),
('ZEV', 'ZEV', 'ZEV'),
('ZEV_BERECHNET', 'ZEV berechnet', 'ZEV calculated'),
('PRODUZENT', 'Produzent', 'Producer'),
('KONSUMENT', 'Konsument', 'Consumer')
ON CONFLICT (key) DO NOTHING;
