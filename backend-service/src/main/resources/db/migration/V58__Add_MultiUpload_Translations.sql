INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('DATEIEN_AUSWAEHLEN',      'Dateien auswählen',                                    'Select files'),
('MEHRERE_DATEIEN_DROPPEN', 'Eine oder mehrere CSV-Dateien hier ablegen',            'Drop one or more CSV files here'),
('DATEI_BEREITS_IN_LISTE',  'Datei ist bereits in der Liste',                       'File is already in the list'),
('ALLE_IMPORTIEREN',        'Alle importieren',                                     'Import all'),
('IMPORTIERE',              'Importiere...',                                        'Importing...'),
('DATEIEN_IMPORT_ERGEBNIS', '{success} von {total} Dateien erfolgreich importiert', '{success} of {total} files imported successfully'),
('KEINE_DATEIEN',           'Keine Dateien ausgewählt',                             'No files selected'),
('DATEI_ENTFERNEN',         'Datei entfernen',                                      'Remove file'),
('DATEINAME',               'Dateiname',                                            'Filename'),
('STATUS_WARTEND',          'Wartend',                                              'Pending'),
('STATUS_ERKENNE',          'Erkenne...',                                           'Detecting...'),
('STATUS_IMPORTIERT',       'Importiert',                                           'Imported'),
('STATUS_FEHLER',           'Fehler',                                               'Error')
ON CONFLICT (key) DO NOTHING;
