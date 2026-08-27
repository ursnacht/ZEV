-- Meldung nach dem Verwerfen nicht gespeicherter Aenderungen in der Abrechnungsmaske
-- (Specs/Nebenkosten/Abrechnung.md, FR-7).
--
-- "Abbrechen" laedt die Abrechnung neu und bleibt in der Maske. Ohne diese Meldung waere der Klick
-- auf einer unveraenderten Maske ohne erkennbare Wirkung - der Benutzer wuesste nicht, ob die
-- Schaltflaeche etwas getan hat.

INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('NK_AENDERUNGEN_VERWORFEN',
 'Die nicht gespeicherten Änderungen wurden verworfen.',
 'The unsaved changes have been discarded.')
ON CONFLICT (key) DO NOTHING;
