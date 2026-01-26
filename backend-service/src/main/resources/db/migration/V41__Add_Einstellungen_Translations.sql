-- Translations for settings page
INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('EINSTELLUNGEN', 'Einstellungen', 'Settings'),
('RECHNUNGSEINSTELLUNGEN', 'Rechnungseinstellungen', 'Invoice Settings'),
('ZAHLUNGSFRIST', 'Zahlungsfrist', 'Payment Terms'),
('IBAN', 'IBAN', 'IBAN'),
('RECHNUNGSSTELLER', 'Rechnungssteller', 'Invoice Issuer'),
('STRASSE', 'Strasse', 'Street'),
('PLZ', 'PLZ', 'Postal Code'),
('ORT', 'Ort', 'City'),
('EINSTELLUNGEN_GESPEICHERT', 'Einstellungen erfolgreich gespeichert', 'Settings saved successfully'),
('EINSTELLUNGEN_FEHLER', 'Fehler beim Speichern der Einstellungen', 'Error saving settings'),
('IBAN_UNGUELTIG', 'Bitte geben Sie eine gültige Schweizer IBAN ein', 'Please enter a valid Swiss IBAN'),
('PFLICHTFELD', 'Dieses Feld ist erforderlich', 'This field is required'),
('LADEN', 'Laden', 'Loading')
ON CONFLICT (key) DO NOTHING;
