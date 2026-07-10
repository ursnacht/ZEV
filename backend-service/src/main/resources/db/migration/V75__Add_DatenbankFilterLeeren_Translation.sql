-- Übersetzung für den Löschen-Button (×) im Filter-Feld der Datenbank-Ansicht
INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('DATENBANK_FILTER_LEEREN', 'Filter leeren', 'Clear filter')
ON CONFLICT (key) DO NOTHING;
