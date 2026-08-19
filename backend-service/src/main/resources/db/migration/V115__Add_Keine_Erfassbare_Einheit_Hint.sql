-- Der Hinweis bei leerer Einheiten-Auswahl lautete unabhängig von der Checkbox „Es ist keine
-- Einheit vom Typ Ladestation erfasst" - bei abgewählter Checkbox sind aber auch Wohnungen
-- gemeint (Specs/Tarifpositionen.md, Edge Cases).
INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('KEINE_ERFASSBARE_EINHEIT_HINT',
 'Es ist keine Einheit vom Typ Ladestation oder Konsument erfasst. Bitte zuerst in der Einheiten-Verwaltung anlegen.',
 'No unit of type charging station or consumer exists. Please create one in the unit management first.')
ON CONFLICT (key) DO NOTHING;
