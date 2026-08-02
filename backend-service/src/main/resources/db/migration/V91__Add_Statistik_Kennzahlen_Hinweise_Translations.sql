-- Erklärende Hinweise (Tooltips/title) für die Statistik-Kennzahlen (Spec Statistik-Kennzahlen.md)
INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('KENNZAHL_AUTARKIEGRAD_HINWEIS', 'Anteil des Verbrauchs, der intern aus PV/Batterie gedeckt wurde (nicht aus dem Netz).', 'Share of consumption covered internally by PV/battery (not from the grid).'),
('KENNZAHL_EIGENVERBRAUCHSQUOTE_HINWEIS', 'Anteil der Produktion, der intern genutzt statt ins Netz eingespeist wurde.', 'Share of production used internally instead of fed into the grid.'),
('KENNZAHL_NETZBEZUGSQUOTE_HINWEIS', 'Anteil des Verbrauchs, der aus dem Netz bezogen wurde (Gegenstück zum Autarkiegrad).', 'Share of consumption drawn from the grid (complement of the self-sufficiency ratio).'),
('KENNZAHL_EINSPEISEQUOTE_HINWEIS', 'Anteil der Produktion, der ins Netz eingespeist wurde (Gegenstück zur Eigenverbrauchsquote).', 'Share of production fed into the grid (complement of the self-consumption ratio).'),
('KENNZAHL_ZEV_EIGENVERBRAUCH_HINWEIS', 'Intern im ZEV gedeckter Verbrauch in kWh.', 'Consumption covered internally within the ZEV, in kWh.'),
('KENNZAHL_BATTERIE_NETTO_HINWEIS', 'Berechneter Netto-Speicherfluss über den Zeitraum: positiv = Netto-Ladung, negativ = Netto-Entladung.', 'Calculated net storage flow over the period: positive = net charge, negative = net discharge.'),
('KENNZAHL_BATTERIE_GELADEN_HINWEIS', 'Berechnete geladene Energie (Summe der positiven Netto-Intervalle).', 'Calculated charged energy (sum of positive net intervals).'),
('KENNZAHL_BATTERIE_ENTLADEN_HINWEIS', 'Berechnete entladene Energie (Summe der negativen Netto-Intervalle).', 'Calculated discharged energy (sum of negative net intervals).'),
('KENNZAHL_BATTERIE_WIRKUNGSGRAD_HINWEIS', 'Round-Trip-Wirkungsgrad: entladene geteilt durch geladene Energie.', 'Round-trip efficiency: discharged divided by charged energy.')
ON CONFLICT (key) DO NOTHING;
