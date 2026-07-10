-- Import/Export der Übersetzungen erfolgt neu als CSV (Komma-getrennt) statt JSON.
-- Fehlermeldung für ungültiges Dateiformat entsprechend anpassen.
UPDATE zev.translation
SET deutsch  = 'Ungültiges Dateiformat (CSV erwartet)',
    englisch = 'Invalid file format (CSV expected)'
WHERE key = 'TRANSLATION_IMPORT_FORMAT_FEHLER';
