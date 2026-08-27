-- Umschaltung der Darstellungsart im Diagramm der Preiszeitreihe (Specs/Preiszeitreihe.md, FR-3).
--
-- Eigene Migration statt Ergaenzung von V130: V130 ist bereits ausgefuehrt, eine Aenderung wuerde
-- die Checksum-Pruefung von Flyway beim naechsten Start brechen.

INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('DARSTELLUNG_LINIE', 'Linie', 'Line'),
('DARSTELLUNG_BALKEN', 'Balken', 'Bars')
ON CONFLICT (key) DO NOTHING;
