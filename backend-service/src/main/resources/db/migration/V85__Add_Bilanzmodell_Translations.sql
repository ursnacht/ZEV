-- Übersetzungen für das Bilanzmodell (Verteilmodus je Mandant)
INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('VERTEILMODUS', 'Verteilmodus', 'Distribution mode'),
('VERTEILMODUS_PRODUCER_MESSUNG', 'Producer-Messung', 'Producer measurement'),
('VERTEILMODUS_BILANZ', 'Bilanzmessung', 'Balance measurement'),
('BILANZMODELL_KEINE_BILANZDATEN', 'Keine Bilanzdaten (Bezug) für das Intervall vorhanden – Verteilung abgebrochen.', 'No balance data (grid draw) available for the interval – distribution aborted.'),
('STATISTIK_MODUS_BILANZ_HINWEIS', 'Im Bilanzmodus werden die Bezug/Rücklieferung-Vergleiche aus der Bilanz abgeleitet und sind daher tautologisch (Kontrollfunktion entfällt).', 'In balance mode the draw/feed-in comparisons are derived from the balance and are therefore tautological (control function no longer applies).'),
('STATISTIK_RUECKLIEFERUNG_GEMESSEN', 'Rücklieferung (gemessen)', 'Feed-in (measured)')
ON CONFLICT (key) DO NOTHING;
