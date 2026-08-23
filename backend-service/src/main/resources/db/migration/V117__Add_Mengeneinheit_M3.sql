-- Mengeneinheit um M3 (Kubikmeter) erweitern - benoetigt fuer die Nebenkostenabrechnung
-- (Wasser, Abwasser). Siehe Specs/Nebenkosten/Abrechnung.md, FR-5.
--
-- Der CHECK-Constraint auf zev.tarif zaehlt die erlaubten Werte auf; ohne diese Anpassung
-- schlaegt jeder Tarif mit der neuen Einheit beim Speichern fehl.

ALTER TABLE zev.tarif DROP CONSTRAINT IF EXISTS ck_tarif_mengeneinheit;

ALTER TABLE zev.tarif ADD CONSTRAINT ck_tarif_mengeneinheit
    CHECK (mengeneinheit IS NULL
           OR mengeneinheit IN ('KWH', 'MONAT', 'STUECK', 'M3'));

COMMENT ON COLUMN zev.tarif.mengeneinheit IS
    'Mengeneinheit eines ZUSATZ-Tarifs: KWH, MONAT, STUECK oder M3; bei anderen Typen NULL';

-- Uebersetzung der neuen Einheit. Die uebrigen Schluessel (KWH, MONATE, STUECK) bestehen bereits;
-- mengeneinheitKey() im Frontend liefert den Enum-Namen als Schluessel.
INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('M3', 'm³', 'm³')
ON CONFLICT (key) DO NOTHING;
