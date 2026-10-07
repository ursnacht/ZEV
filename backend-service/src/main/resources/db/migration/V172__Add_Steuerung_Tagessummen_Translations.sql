-- Einspeisesteuerung: Summen der Kurven zwischen Diagramm und Protokoll (Specs/Einspeisesteuerung.md, FR-5).

INSERT INTO zev.translation (key, deutsch, englisch) VALUES

('STEUERUNG_TAGESSUMMEN',
 'Tagessummen',
 'Daily totals'),

('STEUERUNG_BIS_ZEIT',
 'bis {0}',
 'until {0}'),

('STEUERUNG_GANZER_TAG',
 'ganzer Tag',
 'whole day')

ON CONFLICT (key) DO NOTHING;
