-- Fehlende Übersetzungen für verschiedene Komponenten

INSERT INTO zev.translation (key, deutsch, englisch) VALUES
-- messwerte-upload
('NUR_CSV_DATEIEN', 'Bitte nur CSV-Dateien hochladen', 'Please upload CSV files only'),
('BITTE_ALLE_FELDER_AUSFUELLEN', 'Bitte alle Felder ausfüllen', 'Please fill in all fields'),

-- einheit-list
('EINHEIT_GELOESCHT', 'Einheit erfolgreich gelöscht', 'Unit successfully deleted'),
('EINHEIT_AKTUALISIERT', 'Einheit erfolgreich aktualisiert', 'Unit successfully updated'),
('EINHEIT_ERSTELLT', 'Einheit erfolgreich erstellt', 'Unit successfully created'),
('CONFIRM_DELETE_EINHEIT', 'Möchten Sie diese Einheit wirklich löschen?', 'Do you really want to delete this unit?'),

-- solar-calculation
('BITTE_BEIDE_DATEN_AUSFUELLEN', 'Bitte beide Daten ausfüllen', 'Please fill in both dates'),
('START_VOR_END_DATUM', 'Start-Datum muss vor End-Datum liegen', 'Start date must be before end date'),
('BERECHNUNG_ERFOLGREICH', 'Berechnung erfolgreich abgeschlossen!', 'Calculation completed successfully!'),

-- tarif-list
('CONFIRM_DELETE_TARIF', 'Möchten Sie diesen Tarif wirklich löschen?', 'Do you really want to delete this tariff?')

ON CONFLICT (key) DO NOTHING;
