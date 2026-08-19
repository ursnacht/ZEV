-- Übersetzungen für die Grundgebühr als erfassbare Tarifposition (Specs/Ladestromtarif.md).
-- `MONATE` und `KWH` existieren bereits und fehlen hier deshalb.
INSERT INTO zev.translation (key, deutsch, englisch) VALUES
-- Mengen-Hinweis, wenn der gewählte Tarif eine Grundgebühr ist (Menge = Anzahl Monate)
('TARIFPOSITION_MENGE_HINT_MONATE',
 'Anzahl Monate; wird zusätzlich zur automatisch berechneten Grundgebühr verrechnet.',
 'Number of months; billed in addition to the automatically calculated basic fee.'),

-- Ersetzt KEIN_LADESTROM_TARIF_HINT: erfassbar sind inzwischen mehrere Tariftypen
('KEIN_ERFASSBARER_TARIF_HINT',
 'Es ist kein Tarif vorhanden, für den Positionen erfasst werden können (Ladestrom oder Grundgebühr). Bitte zuerst in der Tarifverwaltung anlegen.',
 'No tariff exists for which positions can be recorded (charging current or basic fee). Please create one in the tariff management first.')
ON CONFLICT (key) DO NOTHING;

-- Der Kommentar aus V100 sagte "aktuell durchgehend kWh" - das stimmt seit der Grundgebuehr
-- nicht mehr. V100 ist ausgefuehrt und darf nicht geaendert werden, daher hier nachgezogen.
COMMENT ON COLUMN zev.tarifposition.menge IS
    'Erfasste Menge; die Mengeneinheit ergibt sich aus dem Tariftyp (LADESTROM: kWh, GRUNDGEBUEHR: Monate)';
