-- Einspeisesteuerung: Bezug und Ruecklieferung im Entscheid festhalten
-- (Specs/Einspeisesteuerung.md, FR-3 und FR-5)
--
-- WARUM: Der Entscheid trug bisher nur Produktion und Verbrauch. Damit laesst sich nicht pruefen,
-- ob die Zahlen stimmen - und genau diese Frage kam auf ("Verbrauch und Produktion scheinen nicht
-- korrekt summiert, moeglicherweise im Zusammenhang mit der Batterieladung").
--
-- Mit allen vier Bilanzkomponenten geht die Energiebilanz des Knotens auf:
--
--     Produktion + Bezug  -  Verbrauch - Ruecklieferung  =  ungeklaerte Differenz
--
-- Diese Differenz ist der NETTO-BATTERIEFLUSS (positiv = laedt, negativ = entlaedt), sofern alle
-- Verbraucher als CONSUMER erfasst sind. Damit wird die Batterie erstmals sichtbar, OBWOHL sie
-- keinen eigenen Zaehler hat - es gibt bis heute keine Einheit vom Typ SPEICHER.
--
-- Die Differenz wird bewusst NICHT gespeichert: Sie ist aus den vier Spalten jederzeit ableitbar,
-- und ein gespeicherter Ableitungswert kann von seiner Grundlage abweichen.
--
-- NULLABLE, anders als produktion/verbrauch: Entscheide aus der Zeit vor dieser Migration haben
-- die Werte nicht. Eine 0 waere dort eine Luege - sie saehe aus wie "kein Bezug gemessen".

ALTER TABLE zev.steuerentscheid ADD COLUMN bezug          NUMERIC(12, 3);
ALTER TABLE zev.steuerentscheid ADD COLUMN ruecklieferung NUMERIC(12, 3);

COMMENT ON COLUMN zev.steuerentscheid.bezug IS
    'Summe der BEZUG-Einheiten im Intervall in kWh (positiv). Leer bei Entscheiden vor V149';
COMMENT ON COLUMN zev.steuerentscheid.ruecklieferung IS
    'Summe der RUECKLIEFERUNG-Einheiten im Intervall in kWh, als Betrag (total steht dort negativ). Leer bei Entscheiden vor V149';
