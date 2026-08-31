-- Korrektur zu V133: Luecken in der Bilanzmessung verzerren BEIDE Seiten - aber unterschiedlich
-- stark und in entgegengesetzte Richtung. V133 hatte nur die gemessenen Zeilen gekennzeichnet.
--
-- Im Bilanzmodus stammt auch der ZEV-Anteil der Konsumenten aus den Bilanzdaten
-- (S = max(0, Verbrauch - Bezug)). Ein uebersprungenes Intervall laesst die Konsumenten ohne zev
-- zurueck, ihr Verbrauch zaehlt aber weiter - er schlaegt damit VOLL als Netzbezug zu Buche statt
-- nur mit seinem tatsaechlichen Netzanteil. Der gerechnete Autarkiegrad faellt dadurch zu tief aus,
-- der gemessene (dem nur der Netzbezug der Luecken-Intervalle fehlt) zu hoch. Der wahre Wert liegt
-- zwischen beiden.
INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('KENNZAHL_LUECKE_MESSUNG_HINWEIS', 'Fuer einzelne Intervalle fehlt der Messwert des Netzbezugs. Deren Netzbezug fehlt in der Summe: Der Wert faellt zu guenstig aus. Der wahre Wert liegt zwischen dieser Zeile und ihrem gerechneten Gegenstueck.', 'The grid supply measurement is missing for some intervals, so their grid supply is missing from the total: this value is too favourable. The true value lies between this row and its calculated counterpart.'),
('KENNZAHL_LUECKE_VERTEILUNG_HINWEIS', 'Fuer einzelne Intervalle fehlt der Messwert des Netzbezugs. Im Bilanzmodus bleibt dadurch auch der ZEV-Anteil dieser Intervalle leer, ihr Verbrauch zaehlt aber voll als Netzbezug: Der Wert faellt zu unguenstig aus. Der wahre Wert liegt zwischen dieser Zeile und ihrem gemessenen Gegenstueck.', 'The grid supply measurement is missing for some intervals. In balance mode their ZEV share therefore stays empty while their consumption still counts fully as grid supply: this value is too unfavourable. The true value lies between this row and its measured counterpart.')
ON CONFLICT (key) DO NOTHING;

-- Der Hinweis zum gemessenen Autarkiegrad nannte die Batterie als Grund fuer die Differenz. Das
-- gilt nur im Modus Producer-Messung: Im Bilanzmodus steckt die Batterie-Entladung bereits im
-- gerechneten Wert, dort weist eine Differenz auf Luecken in der Bilanzmessung hin.
UPDATE zev.translation
   SET deutsch = 'Anteil des Verbrauchs, der nicht aus dem Netz kam - gerechnet aus dem gemessenen Netzbezug (1 - Bezug / Verbrauch). Im Modus Producer-Messung enthaelt er zusaetzlich die Batterie-Entladung. Im Bilanzmodus sollte er mit dem Autarkiegrad uebereinstimmen; weichen die beiden ab, fehlen Bilanz-Messwerte.',
       englisch = 'Share of consumption not drawn from the grid, based on the measured grid supply (1 - supply / consumption). In producer-measurement mode it additionally includes battery discharge. In balance mode it should match the self-sufficiency ratio; if the two differ, balance measurements are missing.'
 WHERE key = 'KENNZAHL_AUTARKIEGRAD_GEMESSEN_HINWEIS';

UPDATE zev.translation
   SET deutsch = 'Anteil des Verbrauchs, der aus dem Netz bezogen wurde - gerechnet aus dem gemessenen Netzbezug (Bezug / Verbrauch) statt aus dem ZEV-Anteil der Konsumenten. Im Bilanzmodus sollte er mit der Netzbezugsquote uebereinstimmen; weichen die beiden ab, fehlen Bilanz-Messwerte.',
       englisch = 'Share of consumption drawn from the grid, based on the measured grid supply (supply / consumption) instead of the consumers'' ZEV share. In balance mode it should match the grid supply ratio; if the two differ, balance measurements are missing.'
 WHERE key = 'KENNZAHL_NETZBEZUGSQUOTE_GEMESSEN_HINWEIS';
