-- Übersetzungen für den CSV-Export der Messdaten (Statistik → Summen pro Einheit)
INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('DOWNLOAD_CSV', 'Download CSV', 'Download CSV'),
('EXPORT_SPALTE_DATUM_ZEIT', 'Datum+Zeit', 'Date+Time'),
('EXPORT_SPALTE_ENERGIEBEZUG_TOTAL', 'Energiebezug Total kWh', 'Total energy consumption kWh'),
('EXPORT_SPALTE_ANTEIL_ZEV', 'Anteil Bezug aus ZEV kWh', 'Share from ZEV kWh'),
('EXPORT_CSV_FEHLER', 'CSV-Export fehlgeschlagen', 'CSV export failed')
ON CONFLICT (key) DO NOTHING;
