-- Einspeisesteuerung: Die Preiskurve deckt den ganzen Tag ab (Specs/Einspeisesteuerung.md, FR-5).
--
-- Fuer Intervalle des Resttages gibt es einen Preis, aber noch keinen Entscheid. Der Tooltip sagt
-- das ausdruecklich - ohne den Hinweis wirkte die Kurve dort tot, und es bliebe offen, ob die
-- Steuerung ausgefallen ist oder das Intervall schlicht noch nicht an der Reihe war.

INSERT INTO zev.translation (key, deutsch, englisch) VALUES

('STEUERUNG_NOCH_KEIN_ENTSCHEID',
 'Noch nicht ausgewertet',
 'Not yet evaluated')

ON CONFLICT (key) DO NOTHING;
