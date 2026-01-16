-- Add all missing translations used in the frontend
INSERT INTO zev.translation (key, deutsch, englisch) VALUES
    -- Einheit Form
    ('EINHEIT_BEARBEITEN', 'Einheit bearbeiten', 'Edit unit'),
    ('NEUE_EINHEIT_ERSTELLEN', 'Neue Einheit erstellen', 'Create new unit'),
    ('NAME', 'Name', 'Name'),
    ('EINHEIT_NAME', 'Einheit Name', 'Unit name'),
    ('NAME_IST_ERFORDERLICH', 'Name ist erforderlich', 'Name is required'),
    ('AKTUALISIEREN', 'Aktualisieren', 'Update'),
    ('ERSTELLEN', 'Erstellen', 'Create'),
    ('ABBRECHEN', 'Abbrechen', 'Cancel'),

    -- Navigation
    ('ZEV_MANAGEMENT', 'ZEV Management', 'ZEV Management'),
    ('MESSWERTE_UPLOAD', 'Messwerte Upload', 'Measurements Upload'),
    ('EINHEITEN_VERWALTUNG', 'Einheiten Verwaltung', 'Unit Management'),
    ('SOLAR_DISTRIBUTION', 'Solar Verteilung', 'Solar Distribution'),
    ('TRANSLATION_EDITOR', 'Übersetzungen', 'Translations'),
    ('LOGOUT', 'Abmelden', 'Logout'),

    -- Messwerte Chart
    ('MESSWERTE_GRAFIK', 'Messwerte Grafik', 'Measurements Chart'),
    ('EINHEITEN', 'Einheiten', 'Units'),
    ('VON_DATUM', 'Von Datum', 'From date'),
    ('BIS_DATUM', 'Bis Datum', 'To date'),
    ('LADE_DATEN', 'Lade Daten', 'Loading data'),
    ('ANZEIGEN', 'Anzeigen', 'Show'),

    -- Messwerte Upload
    ('ZEV_MESSWERTE_UPLOAD', 'ZEV Messwerte Upload', 'ZEV Measurements Upload'),
    ('CSV_DATEI', 'CSV Datei', 'CSV file'),
    ('DATUM', 'Datum', 'Date'),
    ('UPLOADING', 'Wird hochgeladen...', 'Uploading...'),
    ('UPLOAD', 'Hochladen', 'Upload'),

    -- Einheit List
    ('KEINE_EINHEITEN_VORHANDEN', 'Keine Einheiten vorhanden', 'No units available'),

    -- Solar Calculation
    ('SOLAR_DISTRIBUTION_BERECHNUNG', 'Solar Verteilung Berechnung', 'Solar Distribution Calculation'),
    ('BERECHNUNG_LAEUFT', 'Berechnung läuft...', 'Calculating...'),
    ('BERECHNEN', 'Berechnen', 'Calculate'),
    ('ZUSAMMENFASSUNG', 'Zusammenfassung', 'Summary'),
    ('ZEITBEREICH', 'Zeitbereich', 'Time range'),
    ('VERARBEITETE_ZEITSTEMPEL', 'Verarbeitete Zeitstempel', 'Processed timestamps'),
    ('VERARBEITETE_EINTRAEGE', 'Verarbeitete Einträge', 'Processed entries'),
    ('GESAMT_SOLAR_PRODUKTION', 'Gesamt Solar Produktion', 'Total solar production'),
    ('GESAMT_VERTEILT', 'Gesamt verteilt', 'Total distributed'),

    -- Translation Editor
    ('ADD_NEW_TRANSLATION', 'Neue Übersetzung hinzufügen', 'Add new translation'),
    ('KEY', 'Schlüssel', 'Key'),
    ('KEY_NAME', 'Schlüsselname', 'Key name'),
    ('DEUTSCH', 'Deutsch', 'German'),
    ('DEUTSCHER_TEXT', 'Deutscher Text', 'German text'),
    ('ENGLISCH', 'Englisch', 'English'),
    ('ENGLISH_TEXT', 'Englischer Text', 'English text'),
    ('ADD', 'Hinzufügen', 'Add'),

    -- Design System Showcase
    ('ZEV_DESIGN_SYSTEM_SHOWCASE', 'ZEV Design System Showcase', 'ZEV Design System Showcase'),
    ('DESIGN_SYSTEM_INTRO', 'Übersicht über die Design System Komponenten', 'Overview of design system components'),
    ('COLORS', 'Farben', 'Colors')
ON CONFLICT (key) DO NOTHING;
