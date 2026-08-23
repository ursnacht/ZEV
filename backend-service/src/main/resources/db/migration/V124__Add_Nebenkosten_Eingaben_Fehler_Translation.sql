-- Meldung beim Speicherversuch mit unvollstaendigen Angaben
-- (Specs/Nebenkosten/Abrechnung.md, FR-7).
--
-- Die Feldfehler erscheinen erst nach dem ersten Klick auf Speichern; diese Meldung sagt oben,
-- dass deshalb NICHT gespeichert wurde - sonst bliebe der Klick ohne erkennbare Wirkung.

INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('NK_FEHLER_EINGABEN',
 'Die Abrechnung wurde nicht gespeichert: Bitte die markierten Felder prüfen.',
 'The billing was not saved: please check the highlighted fields.')
ON CONFLICT (key) DO NOTHING;
