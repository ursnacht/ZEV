-- Messwerte-Grafik: Checkbox und Reihe des Einspeisetarifs (Specs/EChart.md, FR-10)
INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('GRAFIK_EINSPEISETARIF', 'Einspeisetarif', 'Feed-in tariff'),
('GRAFIK_EINSPEISETARIF_CHF_KWH', 'Einspeisetarif (CHF/kWh)', 'Feed-in tariff (CHF/kWh)')
ON CONFLICT (key) DO NOTHING;
