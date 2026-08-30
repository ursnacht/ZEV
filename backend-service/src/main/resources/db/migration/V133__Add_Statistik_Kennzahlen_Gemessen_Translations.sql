-- Gemessene Gegenstuecke zu Autarkiegrad und Netzbezugsquote (Spec Statistik-Kennzahlen.md FR-1.7).
-- Die bestehenden Kennzahlen rechnen aus dem ZEV-Anteil der Consumer, die neuen aus dem gemessenen
-- Netzbezug der BEZUG-Bilanz-Einheit. Die Differenz ist der Verbrauchsanteil, der weder direkt aus
-- der PV noch aus dem Netz kam - typischerweise die Batterie-Entladung.
INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('KENNZAHL_AUTARKIEGRAD_GEMESSEN', 'Autarkiegrad (gemessen)', 'Self-sufficiency ratio (measured)'),
('KENNZAHL_NETZBEZUGSQUOTE_GEMESSEN', 'Netzbezugsquote (gemessen)', 'Grid supply ratio (measured)'),
('KENNZAHL_AUTARKIEGRAD_GEMESSEN_HINWEIS', 'Anteil des Verbrauchs, der nicht aus dem Netz kam - gerechnet aus dem gemessenen Netzbezug (1 - Bezug / Verbrauch). Enthaelt im Gegensatz zum Autarkiegrad auch die Entladung einer Batterie.', 'Share of consumption not drawn from the grid, based on the measured grid supply (1 - supply / consumption). Unlike the self-sufficiency ratio it also includes battery discharge.'),
('KENNZAHL_NETZBEZUGSQUOTE_GEMESSEN_HINWEIS', 'Anteil des Verbrauchs, der aus dem Netz bezogen wurde - gerechnet aus dem gemessenen Netzbezug (Bezug / Verbrauch) statt aus dem ZEV-Anteil der Konsumenten.', 'Share of consumption drawn from the grid, based on the measured grid supply (supply / consumption) instead of the consumers'' ZEV share.'),
('KENNZAHL_LUECKENHAFT', 'lueckenhaft', 'incomplete'),
('KENNZAHL_LUECKENHAFT_HINWEIS', 'Fuer einzelne Intervalle fehlt der Messwert des Netzbezugs. Die Summe ist dadurch zu klein: Der gemessene Autarkiegrad faellt zu hoch, die gemessene Netzbezugsquote zu tief aus.', 'The grid supply measurement is missing for some intervals. The total is therefore too low: the measured self-sufficiency ratio is too high and the measured grid supply ratio too low.')
ON CONFLICT (key) DO NOTHING;

-- Der bestehende Autarkiegrad rechnet aus dem ZEV-Anteil der Konsumenten. Solange er allein stand,
-- genuegte "Autarkiegrad"; neben dem gemessenen Wert braucht er die Angabe seiner Grundlage.
UPDATE zev.translation
   SET deutsch = 'Anteil des Verbrauchs, der aus der Produktion des ZEV gedeckt wurde (ZEV-Anteil der Konsumenten geteilt durch deren Verbrauch). Eine Batterie-Entladung zaehlt hier nicht mit - dafuer den gemessenen Autarkiegrad vergleichen.',
       englisch = 'Share of consumption covered by the ZEV''s own production (consumers'' ZEV share divided by their consumption). Battery discharge is not included here - compare with the measured self-sufficiency ratio.'
 WHERE key = 'KENNZAHL_AUTARKIEGRAD_HINWEIS'
   AND deutsch = 'Anteil des Verbrauchs, der intern aus PV/Batterie gedeckt wurde (nicht aus dem Netz).';

UPDATE zev.translation
   SET deutsch = 'Anteil des Verbrauchs, der nicht aus der Produktion des ZEV gedeckt wurde (Gegenstueck zum Autarkiegrad). Liegt eine Batterie vor, weicht der Wert vom gemessenen Netzbezug ab.',
       englisch = 'Share of consumption not covered by the ZEV''s own production (complement of the self-sufficiency ratio). With a battery present this differs from the measured grid supply.'
 WHERE key = 'KENNZAHL_NETZBEZUGSQUOTE_HINWEIS'
   AND deutsch = 'Anteil des Verbrauchs, der aus dem Netz bezogen wurde (Gegenstück zum Autarkiegrad).';
