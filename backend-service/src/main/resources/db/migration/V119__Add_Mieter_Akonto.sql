-- Akonto-Stammdatum am Mieter: Vorbelegung des Monatsbetrags in der Nebenkostenabrechnung
-- (Specs/Nebenkosten/Abrechnung.md, FR-4).
--
-- Bewusst nullable: Bestandsmieter haben keinen Wert. Das Feld bleibt in der Abrechnung dann
-- leer und wird von Hand gefuellt - eine Migration bestehender Daten ist nicht noetig.

ALTER TABLE zev.mieter ADD COLUMN akonto_pro_monat NUMERIC(10, 2);

ALTER TABLE zev.mieter ADD CONSTRAINT ck_mieter_akonto
    CHECK (akonto_pro_monat IS NULL OR akonto_pro_monat >= 0);

COMMENT ON COLUMN zev.mieter.akonto_pro_monat IS
    'Monatliche Akonto-Zahlung fuer Nebenkosten; Vorschlag fuer die Abrechnung, dort ueberschreibbar';
