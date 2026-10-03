-- Ladeplanung: Die Schattenrechnung sichtbar machen (Specs/Ladeplanung.md, FR-1a und FR-7).
--
-- Das dritte Zustandsband zeigt, was die Merit-Order entschieden HAETTE. Erst der Vergleich mit
-- dem Band darueber - dem geltenden Entscheid der Regelkaskade - macht die Schattenrechnung
-- lesbar: Wo die beiden Baender auseinanderlaufen, haetten die Verfahren verschieden entschieden.

INSERT INTO zev.translation (key, deutsch, englisch) VALUES

('STEUERUNG_LADEPLAN_GESPERRT',
 'Ladeplan: Ladung gesperrt',
 'Charging plan: charging blocked'),

('STEUERUNG_VERFAHREN',
 'Verfahren',
 'Method'),

('STEUERUNG_VERFAHREN_REGEL',
 'Regelkaskade',
 'Rule cascade'),

('STEUERUNG_VERFAHREN_MERIT_ORDER',
 'Merit-Order',
 'Merit order'),

('STEUERUNG_LADEPLAN',
 'Ladeplan',
 'Charging plan'),

('STEUERUNG_RANG',
 'Rang',
 'Rank'),

('STEUERUNG_RANG_VON',
 'Rang {0} von {1} benötigten',
 'Rank {0} of {1} needed'),

('STEUERUNG_KAPAZITAET_FREI',
 'Freie Kapazität',
 'Free capacity'),

('STEUERUNG_PROGNOSE_UEBERSCHUSS',
 'Erwarteter Überschuss',
 'Expected surplus'),

('STEUERUNG_OHNE_PROGNOSE',
 'Keine Prognose — Regelkaskade entscheidet',
 'No forecast — rule cascade decides'),

('STEUERUNG_ABWEICHUNG',
 'Verfahren weichen ab',
 'Methods disagree')

ON CONFLICT (key) DO NOTHING;
