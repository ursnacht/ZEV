-- Übersetzungen für die Statistik-Kennzahlen (Spec Statistik-Kennzahlen.md)
INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('STATISTIK_KENNZAHLEN', 'Kennzahlen', 'Key figures'),
('KENNZAHL_AUTARKIEGRAD', 'Autarkiegrad', 'Self-sufficiency ratio'),
('KENNZAHL_EIGENVERBRAUCHSQUOTE', 'Eigenverbrauchsquote', 'Self-consumption ratio'),
('KENNZAHL_NETZBEZUGSQUOTE', 'Netzbezugsquote', 'Grid consumption ratio'),
('KENNZAHL_EINSPEISEQUOTE', 'Einspeisequote', 'Feed-in ratio'),
('KENNZAHL_ZEV_EIGENVERBRAUCH', 'ZEV-Eigenverbrauch', 'ZEV self-consumption'),
('KENNZAHL_BATTERIE_NETTO', 'Netto-Speicherfluss', 'Net storage flow'),
('KENNZAHL_BATTERIE_GELADEN', 'Batterie geladen', 'Battery charged'),
('KENNZAHL_BATTERIE_ENTLADEN', 'Batterie entladen', 'Battery discharged'),
('KENNZAHL_BATTERIE_WIRKUNGSGRAD', 'Round-Trip-Wirkungsgrad', 'Round-trip efficiency'),
('KENNZAHL_BERECHNET', 'berechnet', 'calculated'),
('KENNZAHL_BERECHNET_HINWEIS', 'Berechneter Schätzwert aus der Energiebilanz (enthält Messfehler, Wandlungs- und Leitungsverluste).', 'Calculated estimate from the energy balance (includes measurement errors, conversion and line losses).')
ON CONFLICT (key) DO NOTHING;
