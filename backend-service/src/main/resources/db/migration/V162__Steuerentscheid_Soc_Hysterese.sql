-- Einspeisesteuerung: Hysterese der Regel SOC_TIEF (Specs/Einspeisesteuerung.md, FR-2a)
--
-- WARUM MITGEFUEHRT: Ohne diesen Wert laesst sich ein Entscheid nicht erklaeren. "Ladung
-- freigegeben bei 22 %" sieht falsch aus, solange nicht dabeisteht, dass die Freigabe bei 20 %
-- begann und erst ueber 25 % endet. Dieselbe Ueberlegung wie bei schwellwert, speicherwert und
-- soc_minimum.
--
-- NULLABLE: Entscheide vor dieser Migration haben den Wert nicht.

ALTER TABLE zev.steuerentscheid ADD COLUMN soc_hysterese NUMERIC(5, 1);

COMMENT ON COLUMN zev.steuerentscheid.soc_hysterese IS
    'Prozentpunkte, um die der Mindest-Ladezustand angehoben wird, solange SOC_TIEF bereits gilt (Hysterese). Leer bei Entscheiden vor V162';
