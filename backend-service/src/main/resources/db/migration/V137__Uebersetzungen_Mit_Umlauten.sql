-- Deutsche Uebersetzungstexte mit Umlauten (Specs/generell.md, Abschnitt "Mehrsprachigkeit").
--
-- Die Texte aus V133 bis V136 waren in Ersatzschreibung erfasst ("lueckenhaft", "faellt", "Zaehler").
-- Sie erscheinen unveraendert auf dem Bildschirm und in den PDF der Mieter - dort ist das schlicht
-- falsch geschrieben. V133 bis V136 sind angewendet und bleiben unberuehrt; korrigiert wird hier.
--
-- Jedes UPDATE prueft den alten Wert in der WHERE-Klausel: Wer den Text ueber die
-- Uebersetzungsverwaltung schon selbst angepasst hat, behaelt seine Fassung.

-- ===================== Statistik-Kennzahlen (V133/V134) =====================

UPDATE zev.translation SET deutsch = 'lückenhaft'
 WHERE key = 'KENNZAHL_LUECKENHAFT' AND deutsch = 'lueckenhaft';

UPDATE zev.translation
   SET deutsch = 'Für einzelne Intervalle fehlt der Messwert des Netzbezugs. Die Summe ist dadurch zu klein: Der gemessene Autarkiegrad fällt zu hoch, die gemessene Netzbezugsquote zu tief aus.'
 WHERE key = 'KENNZAHL_LUECKENHAFT_HINWEIS'
   AND deutsch = 'Fuer einzelne Intervalle fehlt der Messwert des Netzbezugs. Die Summe ist dadurch zu klein: Der gemessene Autarkiegrad faellt zu hoch, die gemessene Netzbezugsquote zu tief aus.';

UPDATE zev.translation
   SET deutsch = 'Für einzelne Intervalle fehlt der Messwert des Netzbezugs. Deren Netzbezug fehlt in der Summe: Der Wert fällt zu günstig aus. Der wahre Wert liegt zwischen dieser Zeile und ihrem gerechneten Gegenstück.'
 WHERE key = 'KENNZAHL_LUECKE_MESSUNG_HINWEIS'
   AND deutsch = 'Fuer einzelne Intervalle fehlt der Messwert des Netzbezugs. Deren Netzbezug fehlt in der Summe: Der Wert faellt zu guenstig aus. Der wahre Wert liegt zwischen dieser Zeile und ihrem gerechneten Gegenstueck.';

UPDATE zev.translation
   SET deutsch = 'Für einzelne Intervalle fehlt der Messwert des Netzbezugs. Im Bilanzmodus bleibt dadurch auch der ZEV-Anteil dieser Intervalle leer, ihr Verbrauch zählt aber voll als Netzbezug: Der Wert fällt zu ungünstig aus. Der wahre Wert liegt zwischen dieser Zeile und ihrem gemessenen Gegenstück.'
 WHERE key = 'KENNZAHL_LUECKE_VERTEILUNG_HINWEIS'
   AND deutsch = 'Fuer einzelne Intervalle fehlt der Messwert des Netzbezugs. Im Bilanzmodus bleibt dadurch auch der ZEV-Anteil dieser Intervalle leer, ihr Verbrauch zaehlt aber voll als Netzbezug: Der Wert faellt zu unguenstig aus. Der wahre Wert liegt zwischen dieser Zeile und ihrem gemessenen Gegenstueck.';

UPDATE zev.translation
   SET deutsch = 'Anteil des Verbrauchs, der aus der Produktion des ZEV gedeckt wurde (ZEV-Anteil der Konsumenten geteilt durch deren Verbrauch). Eine Batterie-Entladung zählt hier nicht mit - dafür den gemessenen Autarkiegrad vergleichen.'
 WHERE key = 'KENNZAHL_AUTARKIEGRAD_HINWEIS'
   AND deutsch = 'Anteil des Verbrauchs, der aus der Produktion des ZEV gedeckt wurde (ZEV-Anteil der Konsumenten geteilt durch deren Verbrauch). Eine Batterie-Entladung zaehlt hier nicht mit - dafuer den gemessenen Autarkiegrad vergleichen.';

UPDATE zev.translation
   SET deutsch = 'Anteil des Verbrauchs, der nicht aus der Produktion des ZEV gedeckt wurde (Gegenstück zum Autarkiegrad). Liegt eine Batterie vor, weicht der Wert vom gemessenen Netzbezug ab.'
 WHERE key = 'KENNZAHL_NETZBEZUGSQUOTE_HINWEIS'
   AND deutsch = 'Anteil des Verbrauchs, der nicht aus der Produktion des ZEV gedeckt wurde (Gegenstueck zum Autarkiegrad). Liegt eine Batterie vor, weicht der Wert vom gemessenen Netzbezug ab.';

UPDATE zev.translation
   SET deutsch = 'Anteil des Verbrauchs, der nicht aus dem Netz kam - gerechnet aus dem gemessenen Netzbezug (1 - Bezug / Verbrauch). Im Modus Producer-Messung enthält er zusätzlich die Batterie-Entladung. Im Bilanzmodus sollte er mit dem Autarkiegrad übereinstimmen; weichen die beiden ab, fehlen Bilanz-Messwerte.'
 WHERE key = 'KENNZAHL_AUTARKIEGRAD_GEMESSEN_HINWEIS'
   AND deutsch = 'Anteil des Verbrauchs, der nicht aus dem Netz kam - gerechnet aus dem gemessenen Netzbezug (1 - Bezug / Verbrauch). Im Modus Producer-Messung enthaelt er zusaetzlich die Batterie-Entladung. Im Bilanzmodus sollte er mit dem Autarkiegrad uebereinstimmen; weichen die beiden ab, fehlen Bilanz-Messwerte.';

UPDATE zev.translation
   SET deutsch = 'Anteil des Verbrauchs, der aus dem Netz bezogen wurde - gerechnet aus dem gemessenen Netzbezug (Bezug / Verbrauch) statt aus dem ZEV-Anteil der Konsumenten. Im Bilanzmodus sollte er mit der Netzbezugsquote übereinstimmen; weichen die beiden ab, fehlen Bilanz-Messwerte.'
 WHERE key = 'KENNZAHL_NETZBEZUGSQUOTE_GEMESSEN_HINWEIS'
   AND deutsch = 'Anteil des Verbrauchs, der aus dem Netz bezogen wurde - gerechnet aus dem gemessenen Netzbezug (Bezug / Verbrauch) statt aus dem ZEV-Anteil der Konsumenten. Im Bilanzmodus sollte er mit der Netzbezugsquote uebereinstimmen; weichen die beiden ab, fehlen Bilanz-Messwerte.';

-- ===================== Umlage pro Person (V136) =====================

UPDATE zev.translation
   SET deutsch = 'Zähler der Umlage pro Person: Miettage x Wohnungen x Personen. Vorgabe 1.'
 WHERE key = 'NK_MIETER_ANZAHL_PERSONEN_HINT'
   AND deutsch = 'Zaehler der Umlage pro Person: Miettage x Wohnungen x Personen. Vorgabe 1.';

-- ===================== Bestandstexte der Nebenkostenabrechnung =====================
--
-- Dieselbe Ersatzschreibung, aelter als die Regel. "Strasse" und "ausschliesslich" bleiben
-- unangetastet: kein Eszett ist Schweizer Schreibweise und damit richtig.

UPDATE zev.translation SET deutsch = 'Abrechnung gelöscht'
 WHERE key = 'NK_ABRECHNUNG_GELOESCHT' AND deutsch = 'Abrechnung geloescht';

UPDATE zev.translation
   SET deutsch = 'Abrechnung wirklich löschen? Positionen, erfasste Mengen und Akonto-Angaben werden mitgelöscht.'
 WHERE key = 'NK_CONFIRM_LOESCHEN'
   AND deutsch = 'Abrechnung wirklich loeschen? Positionen, erfasste Mengen und Akonto-Angaben werden mitgeloescht.';

UPDATE zev.translation
   SET deutsch = 'Die Abrechnung ist abgeschlossen und kann nicht mehr geändert werden'
 WHERE key = 'NK_FEHLER_ABGERECHNET'
   AND deutsch = 'Die Abrechnung ist abgeschlossen und kann nicht mehr geaendert werden';

UPDATE zev.translation
   SET deutsch = 'Es wurde kein Wert für "Abgerechnet" übergeben'
 WHERE key = 'NK_FEHLER_ABGERECHNET_FEHLT'
   AND deutsch = 'Es wurde kein Wert fuer "Abgerechnet" uebergeben';

UPDATE zev.translation SET deutsch = 'Die Abrechnung konnte nicht gelöscht werden'
 WHERE key = 'NK_FEHLER_LOESCHEN' AND deutsch = 'Die Abrechnung konnte nicht geloescht werden';
