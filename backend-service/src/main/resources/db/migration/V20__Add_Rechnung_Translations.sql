-- Translations for Invoice Generation feature
INSERT INTO zev.translation (key, deutsch, englisch) VALUES
-- Navigation and Page Titles
('RECHNUNGEN', 'Rechnungen', 'Invoices'),
('RECHNUNGEN_GENERIEREN', 'Rechnungen generieren', 'Generate Invoices'),
('GENERIERTE_RECHNUNGEN', 'Generierte Rechnungen', 'Generated Invoices'),

-- Form Labels
('KONSUMENTEN_WAEHLEN', 'Konsumenten auswählen', 'Select Consumers'),
('GENERIEREN', 'Generieren', 'Generate'),
('GENERIERE', 'Generiere', 'Generating'),
('DOWNLOAD', 'Download', 'Download'),
('AKTION', 'Aktion', 'Action'),
('BETRAG', 'Betrag', 'Amount'),

-- Einheit Form Extensions
('MIETERNAME', 'Mietername', 'Tenant Name'),
('MIETERNAME_PLACEHOLDER', 'Name des Mieters', 'Name of tenant'),
('MIETERNAME_HINT', 'Für Rechnungsadresse (optional)', 'For invoice address (optional)'),
('MESSPUNKT', 'Messpunkt', 'Metering Point'),
('MESSPUNKT_PLACEHOLDER', 'z.B. CH1012501234500000000011000006457', 'e.g. CH1012501234500000000011000006457'),
('MESSPUNKT_HINT', 'Messpunkt-ID für Rechnung (optional)', 'Metering point ID for invoice (optional)'),

-- Messages
('RECHNUNGEN_GENERIERT', 'Rechnungen generiert', 'invoices generated'),
('RECHNUNGEN_TEMPORAER_HINWEIS', 'Die Rechnungen sind nur temporär verfügbar. Bitte laden Sie die gewünschten Rechnungen jetzt herunter.', 'The invoices are only temporarily available. Please download the desired invoices now.'),
('FEHLER_BEIM_GENERIEREN', 'Fehler beim Generieren der Rechnungen', 'Error generating invoices'),
('KEINE_KONSUMENTEN_VORHANDEN', 'Keine Konsumenten vorhanden', 'No consumers available'),

-- PDF Invoice Labels
('STROMRECHNUNG', 'Stromrechnung', 'Electricity Invoice'),
('ZAHLUNGSFRIST', 'Zahlungsfrist', 'Payment Terms'),
('RECHNUNGSSTELLER', 'Rechnungssteller', 'Invoicer'),
('ENERGIEBEZUG', 'Energiebezug', 'Energy Consumption'),
('BEZEICHNUNG', 'Bezeichnung', 'Description'),
('MENGE', 'Menge', 'Quantity'),
('PREIS', 'Preis', 'Price'),
('EINHEIT_LABEL', 'Einheit', 'Unit'),
('RUNDUNG', 'Rundung', 'Rounding'),
('TOTAL_ZU_BEZAHLEN', 'Total zu bezahlender Betrag', 'Total Amount Due'),
('BESTEN_DANK', 'Besten Dank', 'Thank you'),
('RECHNUNG_ZUSAMMENSETZUNG', 'Die Rechnung setzt sich wie folgt zusammen:', 'The invoice is composed as follows:'),

-- QR Bill Labels
('EMPFANGSSCHEIN', 'Empfangsschein', 'Receipt'),
('ZAHLTEIL', 'Zahlteil', 'Payment Part'),
('KONTO_ZAHLBAR_AN', 'Konto / Zahlbar an', 'Account / Payable to'),
('ZAHLBAR_DURCH', 'Zahlbar durch', 'Payable by'),
('WAEHRUNG', 'Währung', 'Currency')
ON CONFLICT (key) DO NOTHING;
