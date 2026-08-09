-- Bilanzmodell: Intervalle ohne Bilanzdaten werden übersprungen (statt harter Abbruch);
-- Meldung als WARN mit Anzahl und Zeitraum (Specs/Bilanzmodell.md, FR-2.5).
INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('BILANZMODELL_INTERVALLE_UEBERSPRUNGEN', 'Intervalle ohne Bilanzdaten (Bezug) wurden bei der Verteilung übersprungen – für diese Intervalle wurde kein ZEV-Anteil verteilt. Nach Nachlieferung der Bilanzdaten die Verteilung erneut ausführen.', 'Intervals without balance data (grid draw) were skipped during distribution – no ZEV share was allocated for them. Re-run the distribution once the balance data has been supplied.')
ON CONFLICT (key) DO NOTHING;

-- BILANZMODELL_KEINE_BILANZDATEN (aus V85) gilt jetzt nur noch für den Konfigurationsfehler
-- "keine BEZUG-Einheit vorhanden" – Intervall-Lücken werden übersprungen (s. o.).
-- Text entsprechend präzisieren (V85 selbst bleibt unverändert).
UPDATE zev.translation
   SET deutsch  = 'Keine BEZUG-Einheit (Bilanzmesspunkt) vorhanden – im Bilanzmodell ist keine Verteilung möglich, der Lauf wurde abgebrochen.',
       englisch = 'No BEZUG unit (balance metering point) configured – distribution is not possible in balance mode, the run was aborted.'
 WHERE key = 'BILANZMODELL_KEINE_BILANZDATEN';
