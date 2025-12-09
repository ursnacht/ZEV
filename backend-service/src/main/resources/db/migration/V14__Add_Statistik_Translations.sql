-- Statistikseite Translations
-- Verwendet ON CONFLICT um bestehende Keys zu ignorieren

INSERT INTO zev.translation (key, deutsch, englisch) VALUES
-- Seitentitel und Navigation
('STATISTIK', 'Statistik', 'Statistics'),
('STATISTIK_UEBERSICHT', 'Statistik-Uebersicht', 'Statistics Overview'),

-- Filter
('ZEITRAUM_WAEHLEN', 'Zeitraum waehlen', 'Select Period'),
('VORHERIGER_MONAT', 'Vorheriger Monat', 'Previous Month'),
('MONATE_GELADEN', 'Monate geladen', 'months loaded'),

-- Uebersicht
('MESSWERTE_VORHANDEN_BIS', 'Messwerte vorhanden bis', 'Measurements available until'),
('DATENSTATUS', 'Datenstatus', 'Data Status'),
('DATEN_VOLLSTAENDIG', 'Daten vollstaendig', 'Data complete'),
('DATEN_UNVOLLSTAENDIG', 'Daten unvollstaendig', 'Data incomplete'),
('FEHLENDE_EINHEITEN', 'Fehlende Einheiten', 'Missing units'),
('FEHLENDE_TAGE', 'Fehlende Tage', 'Missing days'),
('DETAILS_ANZEIGEN', 'Details anzeigen', 'Show details'),

-- Summen
('BESCHREIBUNG', 'Beschreibung', 'Description'),
('VISUALISIERUNG', 'Visualisierung', 'Visualization'),
('WERT', 'Wert', 'Value'),
('PRODUKTION_TOTAL', 'Produktion (Total)', 'Production (Total)'),
('VERBRAUCH_TOTAL', 'Verbrauch (Total)', 'Consumption (Total)'),
('ZEV_PRODUCER', 'ZEV Producer', 'ZEV Producer'),
('ZEV_CONSUMER', 'ZEV Consumer', 'ZEV Consumer'),
('ZEV_CONSUMER_BERECHNET', 'ZEV Consumer (berechnet)', 'ZEV Consumer (calculated)'),

-- Vergleiche
('SUMMEN_VERGLEICH', 'Summen-Vergleich', 'Sum Comparison'),
('DIFFERENZ', 'Differenz', 'Difference'),
('GLEICH', 'Gleich', 'Equal'),
('UNGLEICH', 'Ungleich', 'Not equal'),
('TAGE_MIT_ABWEICHUNGEN', 'Tage mit Abweichungen', 'Days with deviations'),
('ABWEICHUNG', 'Abweichung', 'Deviation'),

-- Status
('STATUS_VOLLSTAENDIG', 'Alle Daten vorhanden', 'All data available'),
('STATUS_TEILWEISE', 'Teilweise Daten fehlen', 'Some data missing'),
('STATUS_FEHLEND', 'Keine Daten vorhanden', 'No data available'),

-- Monate
('JANUAR', 'Januar', 'January'),
('FEBRUAR', 'Februar', 'February'),
('MAERZ', 'Maerz', 'March'),
('APRIL', 'April', 'April'),
('MAI', 'Mai', 'May'),
('JUNI', 'Juni', 'June'),
('JULI', 'Juli', 'July'),
('AUGUST', 'August', 'August'),
('SEPTEMBER', 'September', 'September'),
('OKTOBER', 'Oktober', 'October'),
('NOVEMBER', 'November', 'November'),
('DEZEMBER', 'Dezember', 'December'),

-- PDF Export
('PDF_EXPORTIEREN', 'Als PDF exportieren', 'Export as PDF'),
('FEHLER_BEIM_EXPORT', 'Fehler beim Export', 'Export error'),
('STATISTIK_REPORT', 'Statistik-Report', 'Statistics Report'),
('ZEITRAUM', 'Zeitraum', 'Period'),
('GENERIERT_AM', 'Generiert am', 'Generated on'),
('SEITE', 'Seite', 'Page'),
('BITTE_ZUERST_STATISTIK_LADEN', 'Bitte zuerst Statistik laden', 'Please load statistics first'),

-- Leerer Zustand
('WAEHLEN_SIE_EINEN_ZEITRAUM', 'Waehlen Sie einen Zeitraum und klicken Sie auf Anzeigen', 'Select a period and click Show')
ON CONFLICT (key) DO NOTHING;
