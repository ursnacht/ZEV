-- Texte fuer "Abrechnung kopieren" (Specs/Nebenkosten/Abrechnung.md, FR-8).
INSERT INTO zev.translation (key, deutsch, englisch) VALUES

('NK_KOPIEREN',
 'Kopieren',
 'Copy'),

-- Zusatz an der Bezeichnung der Kopie. Steht bewusst als eigener Schluessel und nicht im Backend:
-- Es ist ein Anzeigetext und gehoert damit zu den Uebersetzungen.
('NK_KOPIE_SUFFIX',
 '(Kopie)',
 '(copy)'),

('NK_ABRECHNUNG_KOPIERT',
 'Abrechnung kopiert - Zeitraum und Bezeichnung anpassen',
 'Billing copied - adjust period and description'),

('NK_FEHLER_KOPIEREN',
 'Die Abrechnung konnte nicht kopiert werden',
 'The billing could not be copied'),

('NK_HINWEIS_MIETER_AUSSERHALB',
 'Ändert sich der Zeitraum, verschwinden beim Speichern die Angaben aller Mieter, die nicht mehr hineinfallen: Akonto, Personen, Mengen und Zusatzpositionen.',
 'If the period changes, saving removes the data of every tenant no longer within it: prepayments, persons, quantities and additional items.')

ON CONFLICT (key) DO NOTHING;
