-- Add translations for Drop Zone upload area
INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('DROP_CSV_HERE', 'CSV-Datei hierher ziehen', 'Drop CSV file here'),
('OR_CLICK_TO_SELECT', 'oder klicken zum Auswählen', 'or click to select')
ON CONFLICT (key) DO NOTHING;
