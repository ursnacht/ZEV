-- Zusammenstellung der Positionen: Mengen, Kosten und Gesamtsumme
-- (Specs/Nebenkosten/Abrechnung.md, FR-10).
INSERT INTO zev.translation (key, deutsch, englisch) VALUES

-- Spaltentitel der ersten Spalte: Die Uebersicht fuehrt jetzt jede Positionsart, nicht nur
-- Umlagen - "Umlage" waere als Titel falsch.
('NK_POSITION',
 'Position',
 'Item'),

('NK_SUMME_KOSTEN',
 'Kosten',
 'Costs'),

-- Sammelzeile aller Zusatzpositionen. Steht als Anzeigetext hier und nicht im Backend.
('NK_ZUSATZPOSITIONEN',
 'Zusatzpositionen',
 'Additional items'),

('NK_SUMME_KOSTEN_ABWEICHUNG',
 'Diese Summe weicht vom Kostentotal aller Mieter ab. Beide zählen dieselben Zeilenbeträge - stimmen sie nicht überein, fehlt der Übersicht eine Quelle.',
 'This total differs from the total costs of all tenants. Both count the same line amounts; if they disagree, the overview is missing a source.')

ON CONFLICT (key) DO NOTHING;
