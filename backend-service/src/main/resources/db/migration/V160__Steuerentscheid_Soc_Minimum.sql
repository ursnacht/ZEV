-- Einspeisesteuerung: Regel SOC_TIEF (Specs/Einspeisesteuerung.md, FR-2)
--
-- WARUM DDL NOETIG IST: ck_steuerentscheid_regel zaehlt die erlaubten Regeln auf. Ohne diese
-- Migration scheitert der erste Entscheid mit SOC_TIEF beim INSERT - und zwar im Job, also
-- nachts und ohne dass jemand zusieht. Vor dem Anlegen geprueft mit pg_get_constraintdef.

ALTER TABLE zev.steuerentscheid DROP CONSTRAINT ck_steuerentscheid_regel;

ALTER TABLE zev.steuerentscheid ADD CONSTRAINT ck_steuerentscheid_regel
    CHECK (regel IN ('PREIS_NEGATIV', 'SOC_TIEF', 'KEIN_UEBERSCHUSS', 'EINSPEISEN_LOHNT',
                     'WARTEN_AUF_TAL', 'LADEN'));

-- Die geltende Schwelle im Entscheid festhalten, wie schwellwert und speicherwert: Ein Entscheid
-- soll sich im Nachhinein erklaeren lassen. "Ladung freigegeben bei 18 %" ist nur dann eine
-- Aussage, wenn dabeisteht, ab welchem Wert freigegeben wurde.
--
-- NULLABLE: Entscheide vor dieser Migration haben den Wert nicht.
ALTER TABLE zev.steuerentscheid ADD COLUMN soc_minimum NUMERIC(5, 1);

COMMENT ON COLUMN zev.steuerentscheid.soc_minimum IS
    'Ladezustand in Prozent, unterhalb dessen eine Ladesperre aufgehoben wird (Regel SOC_TIEF). Leer bei Entscheiden vor V160';
