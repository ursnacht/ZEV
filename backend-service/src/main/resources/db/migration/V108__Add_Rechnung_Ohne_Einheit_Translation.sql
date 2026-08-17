-- Hinweis in der Rechnungserzeugung, wenn fuer eine gewaehlte Einheit keine Rechnung entstand.
-- Bisher verschwanden solche Einheiten kommentarlos: eine Ladestation ohne Positionen im
-- Zeitraum, ein Produzent ohne Grundgebuehr-Tarif oder eine Einheit, deren Mietverhaeltnis den
-- Zeitraum nicht beruehrt.
INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('KEINE_RECHNUNG_FUER_EINHEITEN',
 'Keine Rechnung erzeugt für',
 'No invoice created for')
ON CONFLICT (key) DO NOTHING;
