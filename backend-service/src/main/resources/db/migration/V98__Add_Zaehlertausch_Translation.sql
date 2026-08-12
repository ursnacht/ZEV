-- Zählertausch-Erkennung (Specs/Zaehlertausch-Erkennung.md, FR-5): INFO-Systemmeldung je
-- erkanntem Wechsel. Die dynamischen Teile (Einheit, Seriennummer alt → neu, Intervall) stehen
-- in `parameter` und werden im Frontend an den übersetzten Text angehängt – daher nennt der
-- Text die Reihenfolge. Kategorie SYSTEMMELDUNG_KATEGORIE_MQTT existiert bereits (V93).
INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('MQTT_ZAEHLERTAUSCH', 'Zählerwechsel erkannt – für das Übergangsintervall wurde kein Messwert gebildet, ab dem Folgeintervall zählt der neue Zähler (Einheit, Seriennummer alt → neu, Intervall):', 'Meter replacement detected – no reading was recorded for the transition interval; the new meter counts from the following interval onwards (unit, serial number old → new, interval):')
ON CONFLICT (key) DO NOTHING;
