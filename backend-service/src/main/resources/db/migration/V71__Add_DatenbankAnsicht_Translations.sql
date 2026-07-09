-- Übersetzungen für die Datenbank-Ansicht (Einstellungen, nur zev_admin)
INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('DATENBANK_ANSICHT',          'Datenbank-Ansicht',                                  'Database View'),
('DATENBANK_TABELLE',          'Tabelle',                                            'Table'),
('DATENBANK_TABELLE_WAEHLEN',  '– Tabelle wählen –',                                 '– Select table –'),
('DATENBANK_FILTER',           'Filter (SQL-WHERE-Klausel, optional)',               'Filter (SQL WHERE clause, optional)'),
('DATENBANK_ANZEIGEN',         'Anzeigen',                                           'Show'),
('DATENBANK_SEITE',            'Seite',                                              'Page'),
('DATENBANK_VORHERIGE',        'Zurück',                                             'Previous'),
('DATENBANK_NAECHSTE',         'Weiter',                                             'Next'),
('DATENBANK_KEINE_DATEN',      'Keine Daten',                                        'No data'),
('DATENBANK_FEHLER',           'Fehler bei der Datenbank-Abfrage',                   'Database query error'),
('DATENBANK_ABFRAGE_FEHLER',   'Die Abfrage konnte nicht ausgeführt werden (ungültige WHERE-Klausel?)', 'The query could not be executed (invalid WHERE clause?)'),
('DATENBANK_WHERE_UNGUELTIG',  'Unzulässige Filter-Eingabe',                         'Invalid filter input'),
('DATENBANK_WHERE_ZU_LANG',    'Filter-Eingabe zu lang',                             'Filter input too long'),
('DATENBANK_TABELLE_UNGUELTIG','Ungültige oder unbekannte Tabelle',                  'Invalid or unknown table')
ON CONFLICT (key) DO NOTHING;
