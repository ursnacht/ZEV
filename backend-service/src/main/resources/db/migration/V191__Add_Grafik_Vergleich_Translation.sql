-- Messwerte-Grafik: Schaltfläche und Panel-Titel des Vergleichsdiagramms (Specs/EChart.md, FR-9)
INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('GRAFIK_VERGLEICH', 'Vergleich', 'Comparison')
ON CONFLICT (key) DO NOTHING;
