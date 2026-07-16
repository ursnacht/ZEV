-- Übersetzungen für die Bilanzmesspunkt-Typen (BEZUG/RUECKLIEFERUNG), die zwei neuen
-- Summen-Vergleiche in der Statistik und die Eindeutigkeits-Fehlermeldung
INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('TYP_BEZUG',                    'Bezug',                                                  'Grid supply'),
('TYP_RUECKLIEFERUNG',           'Rücklieferung',                                          'Feed-in'),
('VERGLEICH_BEZUG',              'Bezug ↔ Bilanz',                                         'Grid supply ↔ balance'),
('VERGLEICH_RUECKLIEFERUNG',     'Rücklieferung ↔ Bilanz',                                 'Feed-in ↔ balance'),
('EINHEIT_BILANZ_TYP_EXISTIERT', 'Es existiert bereits eine Einheit dieses Bilanz-Typs.',  'A unit of this balance type already exists.')
ON CONFLICT (key) DO NOTHING;
