-- Die Schaltflaeche am Ende der Abrechnungsmaske heisst neu "Speichern und zurueck"
-- (Specs/Nebenkosten/Abrechnung.md, FR-7).
--
-- Sie speichert seit dem 27.08.2026, bevor sie zur Liste zurueckfuehrt. "Zurueck zur Uebersicht"
-- verschwieg das: Wer den Text liest, erwartet Navigation und kein Schreiben - und bleibt
-- ueberraschend stehen, wenn das Speichern scheitert.
--
-- Der Schluessel behaelt seinen Namen. Ihn umzubenennen hiesse, ihn in Vorlage, Tests und E2E
-- mitzuziehen, ohne dass sich etwas am Verhalten aendert.
--
-- Nachgezogen: Der Text war im Betrieb ueber den Uebersetzungs-Editor schon geaendert. Ohne diese
-- Migration bekaeme eine frisch aufgesetzte Datenbank weiterhin den alten aus V122.

UPDATE zev.translation
   SET deutsch  = 'Speichern und zurück',
       englisch = 'Save and back to overview'
 WHERE key = 'NK_ZURUECK_UEBERSICHT';
