-- Die Ladepunkt-Kennung am Mieter entfaellt wieder (Specs/Ladestromtarif.md, Abschnitt Zielbild).
--
-- Grund: Ein Attribut kann genau einen Wert halten. Ein Nutzer kann aber mehrere Ladestationen
-- verwenden, und ein Ladestations-Nutzer ist nicht zwingend Mieter einer Wohnung. Die Kennung
-- gehoert deshalb an eine eigene Einheit vom Typ LADESTATION (Feld `messpunkt`, existiert bereits)
-- statt als Feld an den Mieter.
--
-- Die Spalte wurde erst mit V100 eingefuehrt und ist nicht produktiv im Einsatz. Bis das Zielbild
-- umgesetzt ist, dient `tarifposition.quell_referenz` als Herkunftsangabe je Position.
DROP INDEX IF EXISTS zev.uk_mieter_ladepunkt;
ALTER TABLE zev.mieter DROP COLUMN IF EXISTS ladepunkt;
