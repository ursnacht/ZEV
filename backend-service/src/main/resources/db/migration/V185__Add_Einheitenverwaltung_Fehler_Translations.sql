-- Einheitenverwaltung: uebersetzte Validierungs- und Loeschmeldungen (Specs/Einheitenverwaltung.md, Anhang A.6)
INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('EINHEIT_NAME_LAENGE', 'Der Name muss 2 bis 30 Zeichen lang sein', 'Name must be between 2 and 30 characters'),
('TYP_IST_ERFORDERLICH', 'Typ ist erforderlich', 'Type is required'),
('EINHEIT_MESSPUNKT_ZU_LANG', 'Der Messpunkt darf höchstens 50 Zeichen lang sein', 'Measuring point must not exceed 50 characters'),
('FEHLER_LOESCHEN_EINHEIT', 'Fehler beim Löschen der Einheit', 'Error deleting the unit'),
('EINHEIT_HAT_MESSWERTE', 'Einheit kann nicht gelöscht werden: Es sind Messwerte vorhanden', 'Unit cannot be deleted: measurements exist')
ON CONFLICT (key) DO NOTHING;
