-- Negative Einspeisepreise sind zulaessig (Specs/Preiszeitreihe.md, FR-2).
--
-- V129 legte CHECK (preis >= 0) an - das war eine falsche Annahme. Dynamische Einspeisepreise
-- koennen 0 und auch negativ sein: Bei Ueberangebot (viel Sonne, wenig Last) kostet das Einspeisen
-- Geld, statt Ertrag zu bringen. Ein Wert, den die Quelle liefert, ist damit gueltig und darf nicht
-- an einem Constraint scheitern - sonst faellt der Abruf genau in jenen Stunden aus, die fuer die
-- Steuerung am interessantesten sind.
--
-- Eigene Migration statt Aenderung von V129: V129 ist ausgefuehrt, eine Aenderung wuerde die
-- Checksum-Pruefung von Flyway beim naechsten Start brechen.

ALTER TABLE zev.preiszeitreihe DROP CONSTRAINT IF EXISTS ck_preiszeitreihe_preis;

COMMENT ON COLUMN zev.preiszeitreihe.preis IS
    'Einspeisepreis in CHF/kWh mit 5 Nachkommastellen. Darf 0 oder negativ sein (Ueberangebot).';
