-- Audit-Systemmeldung (INFO) für den manuell gestarteten Solar-Verteillauf
-- (Specs/Systemmeldungen.md, FR-1.12): neue Kategorie + Meldungs-Key.
-- Die dynamischen Teile (Benutzer, Zeitraum, Algorithmus) stehen in `parameter` und werden
-- im Frontend an den übersetzten Text angehängt – daher nennt der Text die Reihenfolge.
INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('SYSTEMMELDUNG_KATEGORIE_VERTEILUNG', 'Solar-Verteilung', 'Solar distribution'),
('VERTEILUNG_MANUELL_GESTARTET', 'Die Solar-Verteilung wurde manuell gestartet (Benutzer, Zeitraum, Algorithmus):', 'The solar distribution was started manually (user, period, algorithm):')
ON CONFLICT (key) DO NOTHING;
