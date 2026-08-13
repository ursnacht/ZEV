-- Erklärende Hinweise (Tooltips/title) für die visualisierten Werte der Statistik-Summentabelle
-- (Spec Statistik.md, analog zu den Kennzahlen-Hinweisen aus V91)
INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('PRODUKTION_TOTAL_HINWEIS', 'Gemessene Gesamtproduktion aller Produzenten im Monat.', 'Total measured production of all producers in the month.'),
('VERBRAUCH_TOTAL_HINWEIS', 'Gemessener Gesamtverbrauch aller Konsumenten im Monat.', 'Total measured consumption of all consumers in the month.'),
('ZEV_PRODUCER_HINWEIS', 'Anteil der Produktion, der im ZEV verbraucht wurde (Sicht der Produzenten-Messwerte).', 'Share of production consumed within the ZEV (from the producer measurements).'),
('ZEV_CONSUMER_HINWEIS', 'Im ZEV gedeckter Verbrauch gemäss den Messwerten der Konsumenten.', 'Consumption covered within the ZEV according to the consumer measurements.'),
('ZEV_CONSUMER_BERECHNET_HINWEIS', 'Der den Konsumenten durch die Verteilung rechnerisch zugeteilte ZEV-Anteil; bei MQTT-Daten identisch mit dem gemessenen Wert.', 'ZEV share allocated to the consumers by the distribution; identical to the measured value for MQTT data.'),
('STATISTIK_BILANZ_BEZUG_HINWEIS', 'Am Bilanzmesspunkt gemessener Bezug aus dem Netz.', 'Grid import measured at the balance metering point.'),
('STATISTIK_BILANZ_RUECKLIEFERUNG_HINWEIS', 'Am Bilanzmesspunkt gemessene Einspeisung ins Netz.', 'Grid feed-in measured at the balance metering point.')
ON CONFLICT (key) DO NOTHING;
