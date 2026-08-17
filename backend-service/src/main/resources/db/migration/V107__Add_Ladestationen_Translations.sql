-- Übersetzungen für die Ladestations-Einheiten (Specs/Ladestationen.md).
-- `EINHEITEN` und `FEHLER_LADEN_EINHEITEN` existieren bereits (V34/V38) und fehlen hier deshalb.
INSERT INTO zev.translation (key, deutsch, englisch) VALUES
-- Einheiten-Typ
('TYP_LADESTATION', 'Ladestation', 'Charging station'),
('MESSPUNKT_HINT_LADESTATION',
 'RFID, mit der der Ladevorgang gestartet wird. Je Mandant eindeutig; beim Mieterwechsel eine neue Einheit mit neuer RFID erfassen.',
 'RFID used to start the charging session. Unique per organisation; on a tenant change create a new unit with a new RFID.'),

-- Mieter: mehrere Einheiten
('EINHEITEN_HINT',
 'Mindestens eine Einheit ist erforderlich. Ein Mieter kann Wohnung und Ladestation(en) haben - alles erscheint auf einer Rechnung.',
 'At least one unit is required. A tenant may have an apartment and charging station(s) - everything appears on one invoice.'),

-- Tarifpositionen: Auswahl der Einheit
('EINHEIT_WAEHLEN', 'Einheit wählen…', 'Select unit…'),
('KEINE_LADESTATION_HINT',
 'Es ist keine Einheit vom Typ Ladestation erfasst. Bitte zuerst in der Einheiten-Verwaltung anlegen.',
 'No unit of type charging station exists. Please create one in the unit management first.'),
('TARIFPOSITION_EINHEIT_OHNE_MIETER_HINT',
 'Dieser Einheit ist kein Mieter zugeordnet - erfasste Positionen erscheinen auf keiner Rechnung.',
 'No tenant is assigned to this unit - recorded positions will not appear on any invoice.'),

-- Fehlermeldungen
('EINHEIT_MESSPUNKT_EXISTIERT',
 'Diese RFID ist bereits einer anderen Ladestation zugeordnet',
 'This RFID already belongs to another charging station')
ON CONFLICT (key) DO NOTHING;
