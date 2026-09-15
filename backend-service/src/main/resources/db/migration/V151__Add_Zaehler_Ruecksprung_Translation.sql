-- Systemmeldung: Zaehlerstand gesunken, Delta verworfen (Specs/MQTT-Integration.md)
--
-- Ein Zaehlerstand kann nicht sinken. Sinkt er doch (Zaehlertausch ohne Seriennummernwechsel,
-- Ueberlauf, fehlerhafte Uebertragung), wird das Delta auf 0 gesetzt - und die Energie dieses
-- Intervalls geht verloren. Bei einem Producer sinkt damit die ausgewiesene Produktion, ohne dass
-- der Anlage etwas fehlt.
--
-- Bisher stand das nur in einer Logzeile. Im Betrieb war es damit unsichtbar: Eine zu tief
-- erscheinende Produktion liess sich nicht davon unterscheiden, dass die Anlage wirklich weniger
-- lieferte. Der verworfene Betrag steht deshalb IN der Meldung.

INSERT INTO zev.translation (key, deutsch, englisch) VALUES

('MQTT_ZAEHLER_RUECKSPRUNG',
 'Zählerstand gesunken – das Delta wurde auf 0 gesetzt und die Energie dieses Intervalls geht verloren. Bei einem Produzenten sinkt dadurch die ausgewiesene Produktion (Einheit, Register, Intervall, verworfene Menge):',
 'Meter reading decreased – the delta was set to 0 and the energy of this interval is lost. For a producer this lowers the reported production (unit, register, interval, discarded amount):')

ON CONFLICT (key) DO NOTHING;
