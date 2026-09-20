-- Einspeisesteuerung: Mindest-Preisabstand (Specs/Einspeisesteuerung.md, FR-2/FR-6/FR-7)

INSERT INTO zev.translation (key, deutsch, englisch) VALUES

('STEUERUNG_MINDEST_ABSTAND',
 'Mindest-Preisabstand',
 'Minimum price gap'),

('STEUERUNG_MINDEST_ABSTAND_HINWEIS',
 'Um so viel muss das erwartete Preistal unter dem aktuellen Preis liegen, damit die Batterieladung überhaupt gesperrt wird. Ohne diesen Abstand wartet die Steuerung auch auf ein Tal, das nur einen halben Rappen tiefer liegt — und lässt dabei Sonnenstunden verstreichen, in denen der Speicher hätte laden können. Vorgabe 0.02 CHF/kWh; 0 schaltet die Prüfung ab.',
 'The expected price trough must lie at least this far below the current price before battery charging is blocked at all. Without this gap the control also waits for a trough half a centime lower — letting sunny hours pass in which the storage could have charged. Default 0.02 CHF/kWh; 0 disables the check.')

ON CONFLICT (key) DO NOTHING;
