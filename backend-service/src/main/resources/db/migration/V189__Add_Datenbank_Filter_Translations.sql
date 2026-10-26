-- Datenbank-Ansicht: Meldung des Filter-Parsers (Specs/Datenbank-Ansicht.md, NFR-2)
INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('DATENBANK_WHERE_SPALTE_UNBEKANNT', 'Der Filter enthält eine Spalte, die diese Tabelle nicht hat, oder eine Funktion.', 'The filter contains a column this table does not have, or a function.')
ON CONFLICT (key) DO NOTHING;
