-- Nachtrag zu V111: Platzhalter der Mengeneinheit-Auswahl im Tarif-Formular.
-- Eigene Migration, weil V111 zu diesem Zeitpunkt bereits ausgefuehrt war (Specs/generell.md:
-- angewendete Migrationen werden nie nachtraeglich geaendert).
-- Namensmuster wie die uebrigen Auswahl-Platzhalter: TARIF_WAEHLEN, EINHEIT_WAEHLEN, ...
INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('MENGENEINHEIT_WAEHLEN', 'Mengeneinheit wählen…', 'Select unit…')
ON CONFLICT (key) DO NOTHING;
