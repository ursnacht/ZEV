-- Übersetzungen für den Ladestromtarif (Specs/Ladestromtarif.md).
-- Enthält generische Labels (TARIF, QUARTAL, JAHR, ...), die bisher fehlten - sie sind bewusst
-- neutral gehalten, weil die Tabelle `tarifposition` generisch ist und später auch andere
-- Anwendungsfälle als Ladestrom aufnimmt.
INSERT INTO zev.translation (key, deutsch, englisch) VALUES
-- Navigation und Seite
('TARIFPOSITIONEN', 'Tarifpositionen', 'Tariff positions'),
('TARIFPOSITIONEN_VERWALTUNG', 'Tarifpositionen', 'Tariff positions'),
('NEUE_TARIFPOSITION_ERSTELLEN', 'Neue Position erfassen', 'Add position'),
('TARIFPOSITION_BEARBEITEN', 'Position bearbeiten', 'Edit position'),
('KEINE_TARIFPOSITIONEN', 'Für diesen Mieter sind keine Positionen erfasst.', 'No positions recorded for this tenant.'),
('MIETER_WAEHLEN', 'Mieter wählen…', 'Select tenant…'),
('TARIF_WAEHLEN', 'Tarif wählen…', 'Select tariff…'),

-- Generische Labels
('TARIF', 'Tarif', 'Tariff'),
('QUARTAL', 'Quartal', 'Quarter'),
('JAHR', 'Jahr', 'Year'),
('BEMERKUNG', 'Bemerkung', 'Note'),
('HERKUNFT', 'Herkunft', 'Origin'),

-- Erfassungsart
('ERFASSUNGSART_MANUELL', 'manuell', 'manual'),
('ERFASSUNGSART_IMPORT', 'importiert', 'imported'),

-- Hinweise
('TARIFPOSITION_MEHRFACHVERRECHNUNG_HINWEIS',
 'Erfasste Positionen erscheinen bei jeder Rechnungserstellung erneut - es gibt keinen Status "bereits verrechnet". Rechnungen für einen Zeitraum deshalb nur einmal erstellen.',
 'Recorded positions appear on every invoice run - there is no "already billed" status. Create invoices for a period only once.'),
('TARIFPOSITION_MENGE_HINT', 'Menge in kWh; auf der Rechnung wird auf ganze kWh gerundet.', 'Quantity in kWh; rounded to whole kWh on the invoice.'),
('KEIN_LADESTROM_TARIF_HINT', 'Es ist kein Ladestrom-Tarif erfasst. Bitte zuerst in der Tarifverwaltung anlegen.', 'No charging tariff exists. Please create one in the tariff management first.'),
('QUELL_REFERENZ', 'Quell-Referenz', 'Source reference'),
('QUELL_REFERENZ_HINT', 'Herkunft der Menge, z.B. die Ladepunkt-Kennung. Wird aus dem Mieter vorbelegt.', 'Origin of the quantity, e.g. the charging point identifier. Prefilled from the tenant.'),
('LADEPUNKT', 'Ladepunkt', 'Charging point'),
('LADEPUNKT_HINT', 'Kennung des Ladepunkts; Grundlage für die spätere automatische Zuordnung. Muss eindeutig sein.', 'Charging point identifier; basis for the later automatic assignment. Must be unique.'),

-- Meldungen
('CONFIRM_DELETE_TARIFPOSITION', 'Soll diese Position wirklich gelöscht werden?', 'Do you really want to delete this position?'),
('TARIFPOSITION_ERSTELLT', 'Position erfasst', 'Position created'),
('TARIFPOSITION_AKTUALISIERT', 'Position aktualisiert', 'Position updated'),
('TARIFPOSITION_GELOESCHT', 'Position gelöscht', 'Position deleted'),
('FEHLER_LADEN_TARIFPOSITIONEN', 'Fehler beim Laden der Positionen', 'Error loading positions'),
('FEHLER_ERSTELLEN_TARIFPOSITION', 'Fehler beim Erfassen der Position', 'Error creating position'),
('FEHLER_AKTUALISIEREN_TARIFPOSITION', 'Fehler beim Aktualisieren der Position', 'Error updating position'),
('FEHLER_LOESCHEN_TARIFPOSITION', 'Fehler beim Löschen der Position', 'Error deleting position'),
('FEHLER_LADEN_MIETER', 'Fehler beim Laden der Mieter', 'Error loading tenants'),

-- Tariftyp
('TARIFTYP_LADESTROM', 'Ladestrom', 'Charging current')
ON CONFLICT (key) DO NOTHING;
