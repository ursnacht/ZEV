-- Bilanzmodell: Intervalle ohne Bilanzdaten werden übersprungen (statt harter Abbruch);
-- Meldung als WARN mit Anzahl und Zeitraum (Specs/Bilanzmodell.md, FR-2.5).
INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('BILANZMODELL_INTERVALLE_UEBERSPRUNGEN', 'Intervalle ohne Bilanzdaten (Bezug) wurden bei der Verteilung übersprungen – für diese Intervalle wurde kein ZEV-Anteil verteilt. Nach Nachlieferung der Bilanzdaten die Verteilung erneut ausführen.', 'Intervals without balance data (grid draw) were skipped during distribution – no ZEV share was allocated for them. Re-run the distribution once the balance data has been supplied.')
ON CONFLICT (key) DO NOTHING;
