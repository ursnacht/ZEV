-- Übersetzungen für Import/Export in der Übersetzungsverwaltung
INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('TRANSLATION_EXPORT',              'Exportieren',                                          'Export'),
('TRANSLATION_IMPORT',              'Importieren',                                          'Import'),
('TRANSLATION_IMPORT_ERFOLG',       '{count} Übersetzungen importiert',                     '{count} translations imported'),
('TRANSLATION_IMPORT_FEHLER',       'Import fehlgeschlagen',                                'Import failed'),
('TRANSLATION_IMPORT_FORMAT_FEHLER','Ungültiges Dateiformat (JSON erwartet)',               'Invalid file format (JSON expected)'),
('TRANSLATION_IMPORT_LEER',         'Die Datei enthält keine Übersetzungen',                'The file contains no translations'),
('TRANSLATION_IMPORT_KEY_FEHLT',    'Import fehlgeschlagen: Ein Eintrag hat keinen Key',    'Import failed: an entry has no key'),
('TRANSLATION_IMPORT_KEY_ZU_LANG',  'Import fehlgeschlagen: Ein Key ist zu lang (max. 200 Zeichen)', 'Import failed: a key is too long (max. 200 characters)')
ON CONFLICT (key) DO NOTHING;
