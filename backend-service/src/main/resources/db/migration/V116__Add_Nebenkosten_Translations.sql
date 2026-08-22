-- Uebersetzungen fuer das Grundgeruest der Nebenkostenabrechnung (Specs/Nebenkosten/).
-- Nur Menue, Seitentitel und Platzhalter-Hinweis: Die Fachlichkeit folgt mit
-- NK-Tarifpositionen und NK-Abrechnung.

INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('NEBENKOSTEN',
 'Nebenkosten',
 'Ancillary costs'),

('NK_TARIFPOSITIONEN',
 'Tarifpositionen',
 'Tariff items'),

('NK_ABRECHNUNG',
 'Abrechnung',
 'Billing'),

('NK_NOCH_NICHT_VERFUEGBAR',
 'Dieser Bereich ist noch im Aufbau und aktuell ohne Funktion.',
 'This section is still under construction and has no function yet.'),

('FEATURE_FLAG_NEBENKOSTENABRECHNUNG',
 'Nebenkostenabrechnung aktivieren',
 'Enable ancillary cost billing')
ON CONFLICT (key) DO NOTHING;
