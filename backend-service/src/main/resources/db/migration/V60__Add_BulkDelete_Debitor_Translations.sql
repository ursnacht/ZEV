INSERT INTO zev.translation (key, deutsch, englisch) VALUES
    ('AUSWAHL_LOESCHEN', 'Auswahl löschen', 'Delete selection'),
    ('DEBITOREN_GELOESCHT', 'Debitoren gelöscht', 'Debtors deleted'),
    ('DEBITOREN_LOESCHEN_BESTAETIGUNG', 'Möchten Sie die ausgewählten Einträge wirklich löschen?', 'Do you really want to delete the selected entries?'),
    ('FEHLER_SAMMEL_LOESCHEN_DEBITOR', 'Fehler beim Löschen der Debitoren', 'Error deleting debtors')
ON CONFLICT (key) DO NOTHING;
