-- Übersetzungen für die berechneten Werte in der Monats-Statistik (vor dem Summen-Vergleich)
INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('BEZUG_VON_VNB',   'Bezug von VNB',   'Grid supply (from DSO)'),
('RUECKLIEFERUNG',  'Rücklieferung',   'Feed-in')
ON CONFLICT (key) DO NOTHING;
