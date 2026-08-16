-- Der CHECK-Constraint auf zev.tarif.tariftyp zaehlt die erlaubten Werte explizit auf
-- (zuletzt in V50 gesetzt) und muss deshalb bei jedem neuen TarifTyp mitgezogen werden.
-- Ohne diese Migration scheitert das Anlegen eines LADESTROM-Tarifs (Specs/Ladestromtarif.md).
-- Die Spaltenlaenge VARCHAR(20) reicht fuer 'LADESTROM' - keine Typaenderung noetig.
ALTER TABLE zev.tarif DROP CONSTRAINT IF EXISTS tarif_tariftyp_check;
ALTER TABLE zev.tarif ADD CONSTRAINT tarif_tariftyp_check
    CHECK (tariftyp IN ('ZEV', 'VNB', 'GRUNDGEBUEHR', 'LADESTROM'));

COMMENT ON COLUMN zev.tarif.tariftyp IS 'ZEV = Eigenverbrauch, VNB = Netzbezug, GRUNDGEBUEHR = Festpreis pro Zaehler/Monat, LADESTROM = manuell erfasste Ladestrom-Positionen';
