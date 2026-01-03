-- Add missing i18n keys used in frontend components

INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('FEHLER_BEIM_LADEN_DER_EINHEITEN', 'Fehler beim Laden der Einheiten', 'Error loading units'),
('START_DATUM_MUSS_VOR_END_DATUM_LIEGEN', 'Startdatum muss vor Enddatum liegen', 'Start date must be before end date'),
('DATENPUNKTE_FUER', 'Datenpunkte für', 'Data points for'),
('EINHEITEN_GELADEN', 'Einheiten geladen', 'units loaded'),
('FEHLER_BEIM_LADEN_DER_DATEN', 'Fehler beim Laden der Daten', 'Error loading data'),
('ZEIT', 'Zeit', 'Time');
