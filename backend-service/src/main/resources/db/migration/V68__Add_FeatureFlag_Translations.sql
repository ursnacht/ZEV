INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('FEATURE_FLAGS',                 'Feature-Flags',                                     'Feature Flags'),
('FEATURE_FLAG',                  'Funktion',                                          'Feature'),
('FEATURE_FLAG_DEFAULT',          'Standard',                                          'Default'),
('FEATURE_FLAG_STATUS',           'Status',                                            'Status'),
('FEATURE_FLAG_QUELLE',           'Quelle',                                            'Source'),
('FEATURE_FLAG_AKTION',           'Aktion',                                            'Action'),
('FEATURE_FLAG_AKTIV',            'Aktiv',                                             'Active'),
('FEATURE_FLAG_INAKTIV',          'Inaktiv',                                           'Inactive'),
('FEATURE_FLAG_QUELLE_DEFAULT',   'Standard',                                          'Default'),
('FEATURE_FLAG_QUELLE_OVERRIDE',  'Überschrieben',                                     'Overridden'),
('FEATURE_FLAG_ZURUECKSETZEN',    'Zurücksetzen',                                      'Reset'),
('FEATURE_FLAG_ZURUECKGESETZT',   'Feature-Flag zurückgesetzt',                        'Feature flag reset'),
('FEATURE_FLAG_GESPEICHERT',      'Feature-Flag gespeichert',                          'Feature flag saved'),
('FEATURE_FLAG_FEHLER',           'Fehler beim Speichern des Feature-Flags',           'Error saving feature flag'),
('FEATURE_FLAG_KEINE',            'Keine Feature-Flags definiert',                     'No feature flags defined'),
('FEATURE_FLAG_DEAKTIVIERT',      'Diese Funktion ist für Ihren Mandanten nicht aktiviert.', 'This feature is not enabled for your organisation.'),
('FEATURE_FLAG_MESSWERTE_UPLOAD', 'Messdatenupload (CSV-Upload) aktivieren',           'Enable measurement data upload (CSV)')
ON CONFLICT (key) DO NOTHING;
