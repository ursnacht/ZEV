-- Translations for Tariff Validation Feature
INSERT INTO zev.translation (key, deutsch, englisch) VALUES
    ('QUARTALE_VALIDIEREN', 'Quartale validieren', 'Validate quarters'),
    ('JAHRE_VALIDIEREN', 'Jahre validieren', 'Validate years'),
    ('VALIDIERUNG_ERFOLGREICH', 'Validierung erfolgreich. Alle Zeiträume sind abgedeckt.', 'Validation successful. All periods are covered.'),
    ('VALIDIERUNG_FEHLER', 'Validierungsfehler', 'Validation error'),
    ('FEHLER_VALIDIERUNG', 'Fehler bei der Validierung', 'Validation failed')
ON CONFLICT (key) DO NOTHING;
