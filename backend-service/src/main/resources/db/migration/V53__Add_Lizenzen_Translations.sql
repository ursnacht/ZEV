INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('LIZENZEN',                 'Lizenzen',                                          'Licenses'),
('LIZENZEN_BACKEND',         'Backend-Libraries',                                 'Backend Libraries'),
('LIZENZEN_FRONTEND',        'Frontend-Libraries',                                'Frontend Libraries'),
('LIZENZ_NAME',              'Name',                                              'Name'),
('LIZENZ_VERSION',           'Version',                                           'Version'),
('LIZENZ_LIZENZ',            'Lizenz',                                            'License'),
('LIZENZ_HERSTELLER',        'Hersteller',                                        'Publisher'),
('LIZENZ_HASH',              'Hash',                                              'Hash'),
('LIZENZ_SUCHEN',            'Suchen nach Name oder Lizenz...',                   'Search by name or license...'),
('LIZENZ_UNBEKANNT',         'Unbekannt',                                         'Unknown'),
('LIZENZ_KEIN_HASH',         '–',                                                 '–'),
('LIZENZEN_LEER',            'Keine Libraries gefunden.',                         'No libraries found.'),
('LIZENZEN_FEHLER_BACKEND',  'Backend-Lizenzen konnten nicht geladen werden.',    'Failed to load backend licenses.'),
('LIZENZEN_FEHLER_FRONTEND', 'Frontend-Lizenzen konnten nicht geladen werden.',   'Failed to load frontend licenses.')
ON CONFLICT (key) DO NOTHING;
