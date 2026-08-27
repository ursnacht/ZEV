-- Texte der Preiszeitreihe (Specs/Preiszeitreihe.md).
--
-- Enthaelt auch die Kategorie und die beiden Meldungs-Keys der Systemmeldungen: Der Abruf laeuft
-- unbeobachtet um 02:00, und /systemmeldungen zeigt den Text ueber diese Keys an.
--
-- VON_DATUM, BIS_DATUM, ANZEIGEN und LADE_DATEN existieren bereits (V34) und werden wiederverwendet.

INSERT INTO zev.translation (key, deutsch, englisch) VALUES
-- Feature-Flag (Verwaltung unter /einstellungen)
('FEATURE_FLAG_PREISZEITREIHE',
 'Preiszeitreihe der dynamischen Einspeisepreise',
 'Time series of dynamic feed-in prices'),

-- Panel und Steuerung
('EINSPEISEPREISE', 'Einspeisepreise', 'Feed-in prices'),
('PREISE_HERUNTERLADEN', 'Herunterladen', 'Download'),
('PREIS_CHF_KWH', 'Preis (CHF/kWh)', 'Price (CHF/kWh)'),
('SPANNE_TAG', 'Tag', 'Day'),
('SPANNE_WOCHE', 'Woche', 'Week'),
('SPANNE_MONAT', 'Monat', 'Month'),
('ZURUECK', 'Zurück', 'Back'),
('VOR', 'Vor', 'Forward'),

-- Rueckmeldungen
('PREISE_HERUNTERGELADEN',
 'Preise aktualisiert: {0} neu, {1} geändert (Stand der Quelle: {2})',
 'Prices updated: {0} new, {1} changed (source as of {2})'),
('PREISE_ABRUF_FEHLGESCHLAGEN',
 'Die Einspeisepreise konnten nicht abgerufen werden.',
 'The feed-in prices could not be retrieved.'),
('KEINE_PREISE_VORHANDEN',
 'Für den gewählten Zeitraum sind keine Preise vorhanden.',
 'No prices available for the selected period.'),
('PREISE_ZEITRAUM_VERTAUSCHT',
 'Datum von muss vor oder gleich Datum bis liegen.',
 'Date from must be before or equal to date to.'),
('DIAGRAMM_LAEDT', 'Diagramm wird geladen …', 'Loading chart …'),
('DIAGRAMM_NICHT_LADBAR',
 'Das Diagramm konnte nicht geladen werden.',
 'The chart could not be loaded.'),

-- Systemmeldungen des Abrufs
('SYSTEMMELDUNG_KATEGORIE_PREISZEITREIHE', 'Preiszeitreihe', 'Price time series'),
('PREISZEITREIHE_ABRUF_FEHLER',
 'Abruf der Einspeisepreise fehlgeschlagen',
 'Retrieval of feed-in prices failed'),
('PREISZEITREIHE_WERTE_UEBERSPRUNGEN',
 'Einzelne Preisintervalle der Quelle wurden übersprungen',
 'Individual price intervals from the source were skipped')
ON CONFLICT (key) DO NOTHING;
