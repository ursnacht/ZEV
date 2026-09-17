-- Einspeisesteuerung: Ladezustand in Diagramm und Protokoll (Specs/Einspeisesteuerung.md, FR-5)
--
-- Eigener Key mit Praefix, wie STEUERUNG_BEZUG und STEUERUNG_RUECKLIEFERUNG (V150): Vor dem
-- Anlegen geprueft, dass weder 'SOC' noch 'LADEZUSTAND' als Key existiert - bei RUECKLIEFERUNG
-- war genau das schon einmal der Fall, und ON CONFLICT DO NOTHING haette den neuen Text
-- stillschweigend verworfen.

INSERT INTO zev.translation (key, deutsch, englisch) VALUES

('STEUERUNG_SOC',
 'Ladezustand',
 'State of charge')

ON CONFLICT (key) DO NOTHING;
