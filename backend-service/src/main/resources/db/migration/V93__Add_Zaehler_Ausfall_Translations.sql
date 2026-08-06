-- Monitoring von Zähler-Ausfällen in der MQTT-Integration (Specs/MQTT-Integration.md):
-- Kategorie + zwei Meldungs-Keys (INFO = einzelnes Intervall, WARN = mehrere Intervalle).
INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('SYSTEMMELDUNG_KATEGORIE_MQTT', 'MQTT-Zähler', 'MQTT meters'),
('MQTT_ZAEHLER_LUECKE', 'Ein Aggregationsintervall ohne Zählerdaten – der Verbrauch geht nicht verloren, er wird dem Folgeintervall zugeschlagen.', 'One aggregation interval without meter data – no consumption is lost, it is added to the following interval.'),
('MQTT_ZAEHLER_AUSFALL', 'Zähler-Ausfall über mehrere Aggregationsintervalle – die 15-Minuten-Auflösung geht verloren; der gesamte Lückenverbrauch erscheint als Spitze in einem Intervall und verzerrt die Solarverteilung.', 'Meter outage spanning several aggregation intervals – the 15-minute resolution is lost; the entire gap consumption appears as a spike in one interval and distorts the solar distribution.')
ON CONFLICT (key) DO NOTHING;
