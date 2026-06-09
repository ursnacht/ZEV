INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('TARIF_PRODUZENT_VERRECHNEN',      'Auch Produzenten verrechnen',                                       'Also charge producers'),
('TARIF_PRODUZENT_VERRECHNEN_HINT', 'Wenn aktiviert, wird diese Grundgebühr auch Produzenten verrechnet', 'If enabled, this basic fee is also charged to producers')
ON CONFLICT (key) DO NOTHING;
