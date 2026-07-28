-- Abschnittstitel "Rechnungskonfiguration" in der Einstellungen-Seite (analog RECHNUNGSSTELLER)
INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('RECHNUNGSKONFIGURATION', 'Rechnungskonfiguration', 'Invoice Configuration')
ON CONFLICT (key) DO NOTHING;
