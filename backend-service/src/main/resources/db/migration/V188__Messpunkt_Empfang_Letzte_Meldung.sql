-- Messpunkt-Empfang: Payload der letzten Meldung festhalten und ansehen
-- (Specs/Messpunkt-Empfang.md, FR-1.9 und FR-2.9)
--
-- TEXT, im Ingest auf 10'000 Zeichen gekuerzt: Pi-Gateway und gPlug senden einige hundert Zeichen;
-- die Grenze schuetzt vor einem fehlkonfigurierten Geraet. Bestehende Zeilen bleiben NULL, bis
-- ihr Messpunkt das naechste Mal sendet.

ALTER TABLE zev.messpunkt_empfang ADD COLUMN letzte_meldung TEXT;

COMMENT ON COLUMN zev.messpunkt_empfang.letzte_meldung IS
    'Payload der letzten Meldung, wie empfangen; höchstens 10''000 Zeichen, gekürzt mit …[gekürzt]';

INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('MESSPUNKT_EMPFANG_LETZTE_MELDUNG', 'Letzte Meldung anzeigen', 'Show last message'),
('MESSPUNKT_EMPFANG_KEINE_MELDUNG', 'Keine Meldung gespeichert.', 'No message stored.'),
('SCHLIESSEN', 'Schliessen', 'Close')
ON CONFLICT (key) DO NOTHING;
