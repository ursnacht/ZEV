-- Beschriftung der Positionsart UMLAGE: "Umlage pro Wohnung" statt "Umlage"
-- (Specs/Nebenkosten/Abrechnung.md, FR-2).
--
-- Seit es die Umlage pro Person gibt, sagt "Umlage" allein nicht mehr, wonach verteilt wird. Der
-- Text war im Betrieb ueber den Uebersetzungs-Editor schon geaendert; ohne diese Migration bekaeme
-- eine frisch aufgesetzte Datenbank weiterhin den alten aus V120 - und die Spaltenbreite der
-- Positionstabelle waere dort auf einen Text ausgelegt, den es nicht gibt. Gleiches Muster wie
-- V128 (Schaltflaeche "Speichern und zurueck").
--
-- Der Schluessel NK_ART_UMLAGE behaelt seinen Namen; die Enum-Konstante UMLAGE bleibt unberuehrt.

-- Deutsch: nur den urspruenglichen Text ersetzen. Wer schon selbst umbenannt hat, behaelt seine
-- Fassung - dann greift dieses UPDATE ins Leere, was hier genau richtig ist.
UPDATE zev.translation SET deutsch = 'Umlage pro Wohnung'
 WHERE key = 'NK_ART_UMLAGE' AND deutsch = 'Umlage';

-- Englisch mitgezogen: Neben "Allocation per person" verliert das blosse "Allocation" genau die
-- Unterscheidung, um die es hier geht.
UPDATE zev.translation SET englisch = 'Allocation per apartment'
 WHERE key = 'NK_ART_UMLAGE' AND englisch = 'Allocation';
