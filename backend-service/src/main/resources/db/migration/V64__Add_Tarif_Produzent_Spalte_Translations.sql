INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('TARIF_PRODUZENT_VERRECHNEN_SPALTE', 'Produzenten', 'Producers'),
('JA',                                'Ja',          'Yes'),
('NEIN',                              'Nein',        'No')
ON CONFLICT (key) DO NOTHING;
