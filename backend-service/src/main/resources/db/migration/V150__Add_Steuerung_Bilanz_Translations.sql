-- Einspeisesteuerung: Spaltentitel der Bilanzkomponenten (Specs/Einspeisesteuerung.md, FR-5)
--
-- MIT PRAEFIX, anders als PRODUKTION und VERBRAUCH (V146): Der Key RUECKLIEFERUNG ist bereits
-- vergeben und traegt "Rücklieferung: Produktion (Total) - C" - ein Label der Statistik samt
-- Spaltenbuchstabe. Wegen ON CONFLICT (key) DO NOTHING waere ein zweiter Eintrag stillschweigend
-- verworfen worden, und die Tabelle der Einspeisesteuerung truege diesen Text. Geprueft wurde das
-- VOR dem Schreiben dieser Migration - bei V146 war genau die umgekehrte Annahme schon einmal
-- falsch.

INSERT INTO zev.translation (key, deutsch, englisch) VALUES

('STEUERUNG_BEZUG',
 'Bezug',
 'Grid supply'),

('STEUERUNG_RUECKLIEFERUNG',
 'Rücklieferung',
 'Feed-in to grid'),

('STEUERUNG_BILANZ_DIFFERENZ',
 'Batterie (aus Bilanz)',
 'Battery (from balance)'),

('STEUERUNG_BILANZ_HINWEIS',
 'Produktion + Bezug − Verbrauch − Rücklieferung. Positiv bedeutet Ladung, negativ Entladung. Die Batterie hat keinen eigenen Zähler; enthalten sind deshalb auch Verbraucher, die nicht als Einheit erfasst sind.',
 'Production + grid supply − consumption − feed-in. Positive means charging, negative discharging. The battery has no meter of its own, so unmetered loads are included as well.')

ON CONFLICT (key) DO NOTHING;
