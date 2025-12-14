-- Translations for Tariff Management feature
INSERT INTO zev.translation (key, deutsch, englisch) VALUES
-- Navigation and Page Titles
('TARIFE', 'Tarife', 'Tariffs'),
('TARIFE_VERWALTUNG', 'Tarifverwaltung', 'Tariff Management'),
('NEUER_TARIF_ERSTELLEN', 'Neuer Tarif', 'New Tariff'),
('TARIF_BEARBEITEN', 'Tarif bearbeiten', 'Edit Tariff'),

-- Form Labels
('TARIFTYP', 'Tariftyp', 'Tariff Type'),
('TARIFTYP_HINT', 'ZEV = Solarstrom, VNB = Netzstrom', 'ZEV = Solar power, VNB = Grid power'),
('TARIF_BEZEICHNUNG_PLACEHOLDER', 'z.B. vZEV PV Tarif 2024', 'e.g. ZEV PV Tariff 2024'),
('BEZEICHNUNG_IST_ERFORDERLICH', 'Bezeichnung ist erforderlich', 'Description is required'),
('PREIS_IST_ERFORDERLICH', 'Preis muss grösser als 0 sein', 'Price must be greater than 0'),
('PREIS_HINT', 'Preis in CHF pro kWh (z.B. 0.20000)', 'Price in CHF per kWh (e.g. 0.20000)'),
('GUELTIG_VON', 'Gültig von', 'Valid from'),
('GUELTIG_BIS', 'Gültig bis', 'Valid until'),
('GUELTIG_VON_MUSS_VOR_BIS_SEIN', 'Gültig von muss vor oder gleich Gültig bis sein', 'Valid from must be before or equal to Valid until'),

-- Messages
('KEINE_TARIFE_VORHANDEN', 'Keine Tarife vorhanden', 'No tariffs available'),
('TARIF_ERSTELLT', 'Tarif erfolgreich erstellt', 'Tariff created successfully'),
('TARIF_AKTUALISIERT', 'Tarif erfolgreich aktualisiert', 'Tariff updated successfully'),
('TARIF_GELOESCHT', 'Tarif erfolgreich gelöscht', 'Tariff deleted successfully'),
('FEHLER_LADEN_TARIFE', 'Fehler beim Laden der Tarife', 'Error loading tariffs'),
('FEHLER_ERSTELLEN_TARIF', 'Fehler beim Erstellen des Tarifs', 'Error creating tariff'),
('FEHLER_AKTUALISIEREN_TARIF', 'Fehler beim Aktualisieren des Tarifs', 'Error updating tariff'),
('FEHLER_LOESCHEN_TARIF', 'Fehler beim Löschen des Tarifs', 'Error deleting tariff')
ON CONFLICT (key) DO NOTHING;
