-- BILANZMODELL_KEINE_BILANZDATEN (Text aus V85) galt ursprünglich auch für fehlende
-- Bilanzdaten einzelner Intervalle. Seit dem Nachtrag zu FR-2.5 werden Intervall-Lücken
-- übersprungen (eigene Meldung BILANZMODELL_INTERVALLE_UEBERSPRUNGEN, s. V95); der Key
-- steht nur noch für den Konfigurationsfehler "keine BEZUG-Einheit vorhanden".
-- Text entsprechend präzisieren (V85/V95 selbst bleiben unverändert).
UPDATE zev.translation
   SET deutsch  = 'Keine BEZUG-Einheit (Bilanzmesspunkt) vorhanden – im Bilanzmodell ist keine Verteilung möglich, der Lauf wurde abgebrochen.',
       englisch = 'No BEZUG unit (balance metering point) configured – distribution is not possible in balance mode, the run was aborted.'
 WHERE key = 'BILANZMODELL_KEINE_BILANZDATEN';
