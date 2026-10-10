-- Tarifbezeichnung von 30 auf 50 Zeichen verlaengern (Specs/RechnungenGenerieren.md).
-- Auf der Rechnung bricht eine lange Bezeichnung um (rechnung.jrxml), statt abgeschnitten zu werden.
ALTER TABLE zev.tarif ALTER COLUMN bezeichnung TYPE VARCHAR(50);
