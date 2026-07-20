-- Übersetzungen für den Bilanz-Messwerte-Upload (Erkennung, Fehler, UI)
INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('UPLOAD_TYP_BILANZ',                'Bilanz',                                               'Balance'),
('ALS_BILANZ_MARKIEREN',             'Als Bilanz markieren',                                 'Mark as balance'),
('BILANZ_MARKIERUNG_ZURUECKSETZEN',  'Bilanz-Markierung zurücksetzen',                       'Reset balance marking'),
('BILANZ_UPLOAD_ERFOLG',             'Bilanz-Datei erfolgreich importiert.',                 'Balance file imported successfully.'),
('BILANZ_EINHEIT_FEHLT',             'Bilanz-Einheit (Bezug/Rücklieferung) fehlt im Mandanten.', 'Balance unit (grid supply/feed-in) is missing for this tenant.'),
('BILANZ_CSV_UNGUELTIG',             'Ungültige Bilanz-CSV-Datei.',                          'Invalid balance CSV file.')
ON CONFLICT (key) DO NOTHING;
