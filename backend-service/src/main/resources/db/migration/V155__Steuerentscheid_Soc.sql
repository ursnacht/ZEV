-- Einspeisesteuerung: Ladezustand im Entscheid festhalten
-- (Specs/Einspeisesteuerung.md, FR-3; angekuendigt in Specs/Gerätezustand.md §8)
--
-- WARUM IM ENTSCHEID und nicht nur in zev.geraetezustand: Der Entscheid soll erklaeren, WARUM
-- gesperrt wurde. "Ladung gesperrt bei 95 % Ladestand" ist eine andere Aussage als "bei 40 %" -
-- die erste war wirkungslos, die zweite hat Kapazitaet freigehalten. Ohne die Spalte liesse sich
-- das im Nachhinein nur ueber einen Join auf einen Zeitstempel rekonstruieren, und die Ansicht
-- muesste zwei Zeitreihen zusammenfuehren.
--
-- NULLABLE: Entscheide aus der Zeit vor dieser Migration haben den Wert nicht, und ein Mandant
-- ohne Speicher-Einheit hat ihn nie. Eine 0 waere dort eine Luege - sie saehe aus wie eine leere
-- Batterie. Dieselbe Ueberlegung wie bei bezug/ruecklieferung (V149).
--
-- GEFUELLT wird mit dem letzten Wert VOR dem Intervallende: Der Entscheid beschreibt das
-- abgeschlossene Intervall, also zaehlt der Zustand an dessen Ende.

ALTER TABLE zev.steuerentscheid ADD COLUMN soc NUMERIC(5, 1);

COMMENT ON COLUMN zev.steuerentscheid.soc IS
    'Ladezustand des Speichers in Prozent am Ende des Intervalls, aus zev.geraetezustand. Leer, wenn kein Speicher erfasst ist oder kein Wert vorlag';
