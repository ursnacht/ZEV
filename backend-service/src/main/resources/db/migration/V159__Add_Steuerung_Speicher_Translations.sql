-- Einspeisesteuerung: Speichermengen in Tabelle und Diagramm (Specs/Einspeisesteuerung.md, FR-5b)
--
-- Keys mit Praefix STEUERUNG_, wie STEUERUNG_BEZUG und STEUERUNG_RUECKLIEFERUNG (V150): Geprueft,
-- dass weder LADUNG noch ENTLADUNG als eigener Key existiert. Vorhanden sind nur
-- KENNZAHL_BATTERIE_GELADEN/-ENTLADEN (Statistik, andere Bedeutung: Monatssumme) und
-- STEUERUNG_BATTERIELADUNG, das den ZUSTAND benennt, nicht die Menge.

INSERT INTO zev.translation (key, deutsch, englisch) VALUES

('STEUERUNG_LADUNG',
 'Ladung',
 'Charged'),

('STEUERUNG_ENTLADUNG',
 'Entladung',
 'Discharged'),

('STEUERUNG_PRODUKTION_VERRECHNET',
 'Produktion (mit Speicher)',
 'Production (incl. storage)'),

('STEUERUNG_PRODUKTION_VERRECHNET_HINWEIS',
 'Gemessene Produktion zuzüglich Ladung, abzüglich Entladung des Speichers. Der Hybrid-Wechselrichter gibt wechselstromseitig nur ab, was das Haus braucht; was in die Batterie geht, erscheint am Erzeugungszähler nicht. Die Verrechnung betrifft nur die Darstellung - Überschuss und Entscheid bleiben auf den gemessenen Werten.',
 'Measured production plus storage charging, less discharging. The hybrid inverter only delivers what the building needs on the AC side, so energy going into the battery never reaches the generation meter. This affects the display only - surplus and decision stay on the measured values.')

ON CONFLICT (key) DO NOTHING;
