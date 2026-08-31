-- Texte fuer die Umlage pro Person (Specs/Nebenkosten/Abrechnung.md, FR-2).
-- NK_ART_UMLAGE bleibt bewusst "Umlage" - die bestehende Art wird nicht umbenannt.
INSERT INTO zev.translation (key, deutsch, englisch) VALUES

('NK_ART_UMLAGE_PERSON',
 'Umlage pro Person',
 'Allocation per person'),

('NK_ANZAHL_PERSONEN',
 'Anzahl Personen',
 'Number of persons'),

('NK_ANZAHL_PERSONEN_HINT',
 'Bildet mit den Tagen des Zeitraums den Nenner der Umlage pro Person. Vorgeschlagen wird die Anzahl Wohnungen; wird bei den Mietern nichts anderes erfasst, rechnet eine Umlage pro Person dann genau wie eine Umlage pro Wohnung.',
 'Together with the days of the period this forms the denominator of the per-person allocation. The number of apartments is proposed; unless something else is recorded for the tenants, a per-person allocation then works exactly like a per-apartment one.'),

('NK_MIETER_ANZAHL_PERSONEN',
 'Personen je Wohnung',
 'Persons per apartment'),

('NK_MIETER_ANZAHL_PERSONEN_HINT',
 'Zaehler der Umlage pro Person: Miettage x Wohnungen x Personen. Vorgabe 1.',
 'Numerator of the per-person allocation: rental days x apartments x persons. Default 1.'),

('NK_FEHLER_ANZAHL_PERSONEN',
 'Anzahl Personen muss mindestens 1 sein',
 'Number of persons must be at least 1')

ON CONFLICT (key) DO NOTHING;
