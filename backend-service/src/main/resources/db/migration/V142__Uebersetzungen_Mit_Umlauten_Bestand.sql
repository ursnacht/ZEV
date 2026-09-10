-- Restliche Bestandstexte mit Umlauten (Specs/generell.md, Abschnitt "Mehrsprachigkeit").
--
-- V137 hat die Texte dieser Regel-Einfuehrung korrigiert. Ein Abgleich ALLER Migrationen gegen die
-- Regel fand fuenf weitere: Sie stammen aus V14, V17 und V120 und sind laengst angewendet.
--
-- Die laufende Datenbank ist bereits sauber - dort wurden sie ueber die Uebersetzungsverwaltung
-- angepasst. Genau das ist der Grund fuer diese Migration: Eine FRISCH aufgesetzte Datenbank
-- bekaeme sonst weiterhin "Statistik-Uebersicht" und "Zeitraum waehlen". Dieselbe Luecke wie bei
-- V138 ("Umlage pro Wohnung") und V128 ("Speichern und zurueck").
--
-- Jedes UPDATE prueft den alten Wert in der WHERE-Klausel und greift auf der laufenden Datenbank
-- damit ins Leere - was hier genau richtig ist.

UPDATE zev.translation SET deutsch = 'Statistik-Übersicht'
 WHERE key = 'STATISTIK_UEBERSICHT' AND deutsch = 'Statistik-Uebersicht';

UPDATE zev.translation SET deutsch = 'Zeitraum wählen'
 WHERE key = 'ZEITRAUM_WAEHLEN' AND deutsch = 'Zeitraum waehlen';

UPDATE zev.translation SET deutsch = 'Wählen Sie einen Zeitraum und klicken Sie auf Anzeigen'
 WHERE key = 'WAEHLEN_SIE_EINEN_ZEITRAUM'
   AND deutsch = 'Waehlen Sie einen Zeitraum und klicken Sie auf Anzeigen';

UPDATE zev.translation SET deutsch = 'Alle auswählen'
 WHERE key = 'ALLE_AUSWAEHLEN' AND deutsch = 'Alle auswaehlen';

UPDATE zev.translation SET deutsch = 'Position hinzufügen'
 WHERE key = 'NK_POSITION_HINZUFUEGEN' AND deutsch = 'Position hinzufuegen';
