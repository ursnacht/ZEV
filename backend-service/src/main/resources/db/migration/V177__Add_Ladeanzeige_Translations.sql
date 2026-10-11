-- Texte der Ladeanzeige (Specs/Statistik.md, FR Ladeanzeige). LADE_DATEN existiert bereits (V34).
INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('STATISTIK_WIRD_BERECHNET', 'Statistik wird berechnet …', 'Calculating statistics …'),
('RECHNUNGEN_WERDEN_GENERIERT', 'Rechnungen werden generiert …', 'Generating invoices …')
ON CONFLICT (key) DO NOTHING;
