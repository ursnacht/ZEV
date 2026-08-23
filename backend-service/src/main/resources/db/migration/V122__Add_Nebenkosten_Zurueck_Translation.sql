-- Schaltflaeche "Zurueck zur Uebersicht" in der Bearbeitungsmaske der Nebenkostenabrechnung
-- (Specs/Nebenkosten/Abrechnung.md, FR-7).
--
-- Eigene Migration und keine Ergaenzung von V120/V121: Beide sind bereits ausgefuehrt, ihre
-- Checksumme darf sich nicht mehr aendern.

INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('NK_ZURUECK_UEBERSICHT',
 'Zurück zur Übersicht',
 'Back to overview')
ON CONFLICT (key) DO NOTHING;
