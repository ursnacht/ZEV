-- Einspeisesteuerung: zeit_von von UTC auf Ortszeit (Specs/Einspeisesteuerung.md, FR-3)
--
-- WARUM DIE KEHRTWENDE: V145 speicherte UTC mit der Begruendung, lokale Zeit sei an der
-- Zeitumstellung nicht eindeutig. Das stimmt - nur machen es die beiden anderen Zeitreihen des
-- Systems trotzdem anders: zev.zaehler_rohdaten.zeit und zev.messwerte.zeit sind Ortszeit. Damit
-- galten im selben System drei Konventionen nebeneinander, und genau daran ist die erste Fassung
-- der Steuerung gescheitert: Ein Entscheid trug den Preis von 11:45 Ortszeit neben der Produktion
-- von 09:30-09:45 - beide Zahlen sahen plausibel aus.
--
-- Eine Konvention weniger wiegt schwerer als die Eindeutigkeit an einem Tag im Jahr. Die
-- Aussenschnittstelle (DTO, Frontend, Tagesansicht) war ohnehin schon Ortszeit; jetzt ist es auch
-- die Speicherung.
--
-- WAS DAS KOSTET - ausdruecklich, damit es nicht der naechste stille Fehler wird:
-- An der Umstellung auf Winterzeit (naechstes Mal 25.10.2026) gibt es die Stunde 02:00-03:00
-- zweimal. Beide Durchgaenge tragen denselben zeit_von, der Upsert auf (org_id, zeit_von)
-- ueberschreibt daher die vier Entscheide des ersten. Der Tag hat 96 statt 100 Entscheide.
-- Fachlich folgenlos: Es ist Nacht, es gibt keinen Ueberschuss, und die Steuerung schaltet nichts.
-- zev.messwerte und zev.zaehler_rohdaten verlieren an dieser Stelle heute schon dieselben vier
-- Intervalle (nachgeprueft am 26.10.2025: 96 statt 100 Messwerte je Einheit).
--
-- Im Fruehling fehlt die Stunde 02:00-03:00; dort entsteht schlicht eine Luecke - unproblematisch.

-- Bestehende Werte umrechnen: naiver UTC-Zeitstempel -> als UTC lesen -> als Ortszeit schreiben.
UPDATE zev.steuerentscheid
SET zeit_von = (zeit_von AT TIME ZONE 'UTC') AT TIME ZONE 'Europe/Zurich';

COMMENT ON COLUMN zev.steuerentscheid.zeit_von IS
    'Beginn des ausgewerteten Intervalls in Ortszeit (Europe/Zurich) - wie zev.messwerte.zeit und zev.zaehler_rohdaten.zeit. An der Umstellung auf Winterzeit nicht eindeutig: die vier Intervalle der doppelten Stunde 02:00-03:00 ueberschreiben einander (Specs/Einspeisesteuerung.md, Edge Cases)';

COMMENT ON CONSTRAINT uq_steuerentscheid_org_zeit ON zev.steuerentscheid IS
    'Je Mandant ein Entscheid pro Intervall; Grundlage des Upserts. In Ortszeit - siehe Spaltenkommentar zu zeit_von zur doppelten Stunde der Winterzeit-Umstellung';
