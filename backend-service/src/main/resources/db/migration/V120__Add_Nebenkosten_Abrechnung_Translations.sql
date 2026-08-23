-- Uebersetzungen der Nebenkostenabrechnung (Specs/Nebenkosten/Abrechnung.md, FR-7).
--
-- Die Fehlerschluessel (NK_FEHLER_*) wirft der Service; das Frontend zeigt sie ueber dieselbe
-- Uebersetzung an. Eine Ausnahme bleibt die Meldung zum zu klein erfassten Nenner: Sie nennt zwei
-- berechnete Zahlen und kommt deshalb als Klartext aus dem Backend.

INSERT INTO zev.translation (key, deutsch, englisch) VALUES

-- ===== Allgemein =====
('SPEICHERN',
 'Speichern',
 'Save'),

-- ===== Liste =====
('NK_NEUE_ABRECHNUNG',
 'Neue Abrechnung erstellen',
 'Create new billing'),

('NK_ABRECHNUNG_BEARBEITEN',
 'Abrechnung bearbeiten',
 'Edit billing'),

('NK_KEINE_ABRECHNUNGEN',
 'Keine Abrechnungen erfasst',
 'No billings recorded'),

('NK_DATUM_VON',
 'Datum von',
 'Date from'),

('NK_DATUM_BIS',
 'Datum bis',
 'Date to'),

('NK_ABGERECHNET',
 'Abgerechnet',
 'Settled'),

('NK_CONFIRM_LOESCHEN',
 'Abrechnung wirklich loeschen? Positionen, erfasste Mengen und Akonto-Angaben werden mitgeloescht.',
 'Really delete this billing? Items, recorded quantities and prepayments will be deleted with it.'),

('NK_CONFIRM_FREIGEBEN',
 'Abrechnung wieder zur Bearbeitung freigeben?',
 'Reopen this billing for editing?'),

('NK_ABRECHNUNG_GELOESCHT',
 'Abrechnung geloescht',
 'Billing deleted'),

('NK_ABRECHNUNG_GESPEICHERT',
 'Abrechnung gespeichert',
 'Billing saved'),

('NK_ABGERECHNET_GESETZT',
 'Abrechnung abgeschlossen',
 'Billing closed'),

('NK_ABGERECHNET_GELOEST',
 'Abrechnung wieder zur Bearbeitung freigegeben',
 'Billing reopened for editing'),

-- ===== Maske: Angaben zur Abrechnung =====
('NK_ANGABEN',
 'Angaben zur Abrechnung',
 'Billing details'),

('NK_BEZEICHNUNG_PLACEHOLDER',
 'z.B. Nebenkostenabrechnung 2026',
 'e.g. Ancillary costs 2026'),

('NK_ANZAHL_WOHNUNGEN',
 'Anzahl Wohnungen',
 'Number of apartments'),

('NK_ANZAHL_WOHNUNGEN_HINT',
 'Bildet mit den Tagen des Zeitraums den Nenner der Umlage. Steht eine Wohnung leer, bleibt ihr Anteil unverteilt und geht zu Lasten des Eigentuemers.',
 'Together with the days of the period this forms the denominator of the allocation. If an apartment is vacant, its share remains undistributed and is borne by the owner.'),

-- ===== Maske: Allgemeine Positionen =====
('NK_ALLGEMEINE_POSITIONEN',
 'Allgemeine Positionen',
 'General items'),

('NK_KEINE_POSITIONEN',
 'Keine Positionen erfasst',
 'No items recorded'),

('NK_POSITION_HINZUFUEGEN',
 'Position hinzufuegen',
 'Add item'),

('NK_VERSCHIEBEN',
 'Zeile verschieben',
 'Move row'),

('NK_ART',
 'Art',
 'Type'),

('NK_WERTE',
 'Werte',
 'Values'),

('NK_ART_UMLAGE',
 'Umlage',
 'Allocation'),

('NK_ART_VERBRAUCH',
 'Verbrauch',
 'Consumption'),

('NK_ART_ZUSCHLAG',
 'Zuschlag',
 'Surcharge'),

('NK_TOTALBETRAG',
 'Totalbetrag',
 'Total amount'),

('NK_GESAMTMENGE',
 'Gesamtmenge',
 'Total quantity'),

('NK_BETRAG_PRO_EINHEIT',
 'Betrag pro Einheit',
 'Amount per unit'),

('NK_PROZENTSATZ',
 'Prozentsatz',
 'Percentage'),

('NK_BEMESSUNGSGRUNDLAGE',
 'Bemessungsgrundlage',
 'Calculation basis'),

('NK_KEINE_ZEILEN_DAVOR',
 'keine Zeilen davor',
 'no rows above'),

-- ===== Maske: Kontrollzahlen =====
('NK_UMLAGE',
 'Umlage',
 'Allocation'),

('NK_SUMME_VERTEILT',
 'Summe verteilt',
 'Sum distributed'),

('NK_NICHT_VERTEILT',
 'Nicht verteilt',
 'Not distributed'),

('NK_RUNDUNGSDIFFERENZ',
 'Rundungsdifferenz',
 'Rounding difference'),

-- ===== Maske: Mieterbloecke =====
('NK_TAGE',
 'Tage',
 'days'),

('NK_BETRAG',
 'Betrag',
 'Amount'),

('NK_ZUSATZPOSITION',
 'Zusatzposition',
 'Additional item'),

('NK_KOSTENTOTAL',
 'Kostentotal',
 'Total costs'),

('NK_ANZAHL_MONATE',
 'Anzahl Monate',
 'Number of months'),

('NK_BETRAG_PRO_MONAT',
 'Betrag pro Monat',
 'Amount per month'),

('NK_KORREKTUR',
 'Korrekturbetrag',
 'Correction amount'),

('NK_AKONTO_TOTAL',
 'Akonto total',
 'Prepayments total'),

('NK_NACHZAHLUNG',
 'Nachzahlung',
 'Amount due'),

('NK_GUTHABEN',
 'Guthaben',
 'Credit'),

-- ===== Hinweise =====
('NK_HINWEIS_ABGERECHNET',
 'Diese Abrechnung ist abgeschlossen und schreibgeschuetzt. Zum Bearbeiten in der Liste den Haken bei "Abgerechnet" entfernen.',
 'This billing is closed and read-only. To edit it, clear the "Settled" checkbox in the list.'),

('NK_HINWEIS_OHNE_WOHNUNG',
 'Diesem Mieter ist keine Wohnung zugeordnet. Er traegt deshalb keinen Anteil an den Umlagen.',
 'No apartment is assigned to this tenant, so they bear no share of the allocations.'),

('NK_HINWEIS_ERST_SPEICHERN',
 'Die Mieterbloecke erscheinen, sobald die Abrechnung gespeichert ist - die Miettage kommen vom Server.',
 'The tenant blocks appear once the billing is saved; the rental days come from the server.'),

-- ===== Fehlermeldungen =====
('NK_FEHLER_LADEN',
 'Die Abrechnungen konnten nicht geladen werden',
 'Could not load the billings'),

('NK_FEHLER_SPEICHERN',
 'Die Abrechnung konnte nicht gespeichert werden',
 'Could not save the billing'),

('NK_FEHLER_LOESCHEN',
 'Die Abrechnung konnte nicht geloescht werden',
 'Could not delete the billing'),

('NK_FEHLER_ZEITRAUM',
 'Datum von muss vor oder gleich Datum bis sein',
 'Date from must be before or equal to date to'),

('NK_FEHLER_ZEITRAUM_PFLICHT',
 'Datum von und Datum bis sind Pflichtfelder',
 'Date from and date to are required'),

('NK_FEHLER_ANZAHL_WOHNUNGEN',
 'Die Anzahl Wohnungen muss mindestens 1 sein',
 'The number of apartments must be at least 1'),

('NK_FEHLER_ABGERECHNET',
 'Die Abrechnung ist abgeschlossen und kann nicht mehr geaendert werden',
 'The billing is closed and can no longer be changed'),

('NK_FEHLER_ABGERECHNET_FEHLT',
 'Es wurde kein Wert fuer "Abgerechnet" uebergeben',
 'No value was provided for "Settled"'),

('NK_FEHLER_POSITION_ART',
 'Jede Position braucht eine Art',
 'Every item needs a type'),

('NK_FEHLER_POSITION_BEZEICHNUNG',
 'Jede Position braucht eine Bezeichnung',
 'Every item needs a description'),

('NK_FEHLER_POSITION_UMLAGE',
 'Eine Umlage braucht einen Totalbetrag und eine Mengeneinheit',
 'An allocation needs a total amount and a unit'),

('NK_FEHLER_POSITION_VERBRAUCH',
 'Ein Verbrauch braucht einen Betrag pro Einheit und eine Mengeneinheit',
 'A consumption item needs an amount per unit and a unit'),

('NK_FEHLER_POSITION_ZUSCHLAG',
 'Ein Zuschlag braucht einen Prozentsatz',
 'A surcharge needs a percentage'),

('NK_FEHLER_PROZENTSATZ',
 'Der Prozentsatz muss zwischen 0 und 100 liegen',
 'The percentage must be between 0 and 100')

ON CONFLICT (key) DO NOTHING;
