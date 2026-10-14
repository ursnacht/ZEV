-- Erstellungsdatum einer Forderung (Specs/Debitorkontrolle.md, FR „Erstellt am"): Datum der
-- letzten Rechnungsgenerierung, die den Eintrag geschrieben hat. Bestand: heutiges Datum.

ALTER TABLE zev.debitor ADD COLUMN erstellt_am DATE;

UPDATE zev.debitor SET erstellt_am = CURRENT_DATE WHERE erstellt_am IS NULL;

ALTER TABLE zev.debitor ALTER COLUMN erstellt_am SET NOT NULL;
ALTER TABLE zev.debitor ALTER COLUMN erstellt_am SET DEFAULT CURRENT_DATE;

COMMENT ON COLUMN zev.debitor.erstellt_am IS
    'Datum der letzten Rechnungsgenerierung, die den Eintrag geschrieben hat (bei bezahlten Forderungen unveraendert); manuell erfasst: Erfassungsdatum. Bestand vor V182: Datum der Migration';

INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('DEBITOR_ERSTELLT_AM', 'Erstellt am', 'Created on')
ON CONFLICT (key) DO NOTHING;
