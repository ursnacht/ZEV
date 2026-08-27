-- Herkunft einer Forderung: Stromabrechnung (ZEV) oder Nebenkosten (NK)
-- (Specs/Nebenkosten/RechnungenGenerieren.md, FR-5)
--
-- Die Herkunft gehoert in den UNIQUE-Key und nicht bloss in eine Anzeigespalte: Das Buchen laeuft
-- als Upsert (DebitorRepository.upsert, ON CONFLICT), und eine NK-Jahresabrechnung 01.01.-31.12.
-- hat denselben datum_von wie die ZEV-Quartalsrechnung Q1. Ohne die Herkunft im Schluessel wuerde
-- die NK-Buchung die ZEV-Forderung desselben Mieters stillschweigend ueberschreiben.
--
-- Der Bestand erhaelt 'ZEV'. Das ist eine korrekte Rueckschreibung und keine Annahme: Bis heute
-- konnte eine Forderung nur aus der Stromabrechnung entstehen.

ALTER TABLE zev.debitor ADD COLUMN herkunft VARCHAR(10) NOT NULL DEFAULT 'ZEV';

COMMENT ON COLUMN zev.debitor.herkunft IS
    'Herkunft der Forderung: ZEV (Stromabrechnung) oder NK (Nebenkosten)';

ALTER TABLE zev.debitor ADD CONSTRAINT debitor_herkunft_check
    CHECK (herkunft IN ('ZEV', 'NK'));

-- Schluesseltausch. Er kann auf dem Bestand nicht scheitern: Alle Zeilen tragen denselben Wert
-- 'ZEV', und der alte Schluessel war bereits eindeutig - der neue ist eine Erweiterung um eine
-- konstante Spalte.
ALTER TABLE zev.debitor DROP CONSTRAINT uq_debitor_mieter_von_org;

ALTER TABLE zev.debitor ADD CONSTRAINT uq_debitor_mieter_von_herkunft_org
    UNIQUE (mieter_id, datum_von, herkunft, org_id);

INSERT INTO zev.translation (key, deutsch, englisch) VALUES
-- ===== Aktion in der Liste der Abrechnungen =====
('NK_RECHNUNGEN_ERSTELLEN',
 'Rechnungen erstellen',
 'Create invoices'),

('NK_CONFIRM_RECHNUNGEN_ERSTELLEN',
 'Für alle Mieter dieser Abrechnung Rechnungen erstellen? Für jede Nachzahlung wird eine Forderung gebucht.',
 'Create invoices for all tenants of this billing? A receivable is booked for every amount due.'),

-- ===== Ergebnis des Laufs =====
('NK_RECHNUNGEN_ERGEBNIS',
 'Erstellte Rechnungen',
 'Created invoices'),

('NK_ANZAHL_RECHNUNGEN',
 'Rechnungen',
 'Invoices'),

('NK_ANZAHL_FORDERUNGEN',
 'Forderungen',
 'Receivables'),

('NK_SUMME_FORDERUNGEN',
 'Summe der Forderungen',
 'Total receivables'),

('NK_FORDERUNG',
 'Forderung',
 'Receivable'),

('NK_KEINE_FORDERUNG',
 'keine Forderung',
 'no receivable'),

-- ===== Fehler und Hinweise =====
('NK_FEHLER_NICHT_ABGERECHNET',
 'Diese Abrechnung ist nicht abgeschlossen. Rechnungen entstehen nur aus einer abgeschlossenen Abrechnung.',
 'This billing is not closed. Invoices are only created from a closed billing.'),

('NK_FEHLER_RECHNUNGEN_ERSTELLEN',
 'Die Rechnungen konnten nicht erstellt werden.',
 'The invoices could not be created.'),

('NK_FEHLER_RECHNUNG_MIETER',
 'Für diesen Mieter konnte keine Rechnung erstellt werden.',
 'No invoice could be created for this tenant.'),

('NK_RECHNUNG_ABGELAUFEN',
 'Diese Rechnung steht nicht mehr zum Herunterladen bereit. Bitte die Rechnungen erneut erstellen.',
 'This invoice is no longer available for download. Please create the invoices again.'),

('NK_HINWEIS_AUF_RECHNUNGEN_SEITE',
 'Nebenkostenrechnungen werden im Bereich Nebenkosten aus einer abgeschlossenen Abrechnung erstellt.',
 'Ancillary cost invoices are created in the ancillary costs area from a closed billing.'),

-- ===== Titel auf dem PDF =====
('NK_RECHNUNG_TITEL',
 'Nebenkostenabrechnung',
 'Ancillary cost statement'),

-- ===== Herkunft in der Debitorenkontrolle =====
('DEBITOR_HERKUNFT',
 'Herkunft',
 'Origin'),

('DEBITOR_HERKUNFT_ZEV',
 'ZEV',
 'ZEV'),

('DEBITOR_HERKUNFT_NK',
 'Nebenkosten',
 'Ancillary costs'),

('DEBITOR_HERKUNFT_ALLE',
 'Alle',
 'All')
ON CONFLICT (key) DO NOTHING;
