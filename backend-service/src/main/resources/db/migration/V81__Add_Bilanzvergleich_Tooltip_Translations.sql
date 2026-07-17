-- Tooltips für die beiden Bilanz-Vergleiche in der Statistik (Erläuterung der Berechnung)
INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('TOOLTIP_VERGLEICH_BEZUG',
 'Vergleicht den berechneten Bezug von VNB (Verbrauch Total − ZEV Konsument (B, gemessen)) mit der gemessenen Summe der Bilanz-Einheit vom Typ Bezug.',
 'Compares the calculated grid supply (total consumption − ZEV consumer (B, measured)) with the measured total of the balance unit of type grid supply.'),
('TOOLTIP_VERGLEICH_RUECKLIEFERUNG',
 'Vergleicht die berechnete Rücklieferung (Produktion Total − ZEV Produzent (A)) mit der gemessenen Summe der Bilanz-Einheit vom Typ Rücklieferung.',
 'Compares the calculated feed-in (total production − ZEV producer (A)) with the measured total of the balance unit of type feed-in.')
ON CONFLICT (key) DO NOTHING;
