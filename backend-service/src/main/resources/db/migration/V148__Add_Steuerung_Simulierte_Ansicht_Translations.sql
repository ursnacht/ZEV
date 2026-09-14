-- Einspeisesteuerung: Tagesansicht mit nachgerechnetem Schwellwert
-- (Specs/Einspeisesteuerung.md, FR-6a)
--
-- Die Rueckrechnung lieferte bisher nur Kennzahlen - WIE OFT gesperrt worden waere. Erst der
-- nachgerechnete Tagesverlauf zeigt, WANN. Diagramm und Tabelle sehen dabei genauso aus wie beim
-- Protokoll; ohne den Hinweis liesse sich beides nicht unterscheiden.

INSERT INTO zev.translation (key, deutsch, englisch) VALUES

('STEUERUNG_SIMULIERTE_ANSICHT',
 'Nachgerechnete Ansicht — keine Aufzeichnung. Es wird nichts gespeichert. Schwellwert:',
 'Recalculated view — not a recording. Nothing is stored. Threshold:'),

('STEUERUNG_AUFZEICHNUNG_ZEIGEN',
 'Aufzeichnung zeigen',
 'Show recording')

ON CONFLICT (key) DO NOTHING;
