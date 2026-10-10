-- Grundgebuehr pro Tag (Specs/Tarifverwaltung.md, FR-3): Mengeneinheit TAG und waehlbare Einheit
-- auch bei der Grundgebuehr (MONAT = volle Kalendermonate wie bisher, TAG = taggenau).
--
-- Nur der Tarif-Constraint wird erweitert. TAG gehoert nicht zur Nebenkostenabrechnung; die
-- Constraints von nk_position und nk_zusatz (V144) bleiben bewusst unveraendert.

ALTER TABLE zev.tarif DROP CONSTRAINT IF EXISTS ck_tarif_mengeneinheit;

ALTER TABLE zev.tarif ADD CONSTRAINT ck_tarif_mengeneinheit
    CHECK (mengeneinheit IS NULL
           OR mengeneinheit IN ('KWH', 'MONAT', 'TAG', 'STUECK', 'M3', 'M2', 'CHF'));

-- Bestehende Grundgebuehren ausdruecklich auf MONAT: unveraendertes Verhalten, und die Maske zeigt
-- die gewaehlte Einheit an.
UPDATE zev.tarif SET mengeneinheit = 'MONAT'
 WHERE tariftyp = 'GRUNDGEBUEHR' AND mengeneinheit IS NULL;

COMMENT ON COLUMN zev.tarif.mengeneinheit IS
    'Mengeneinheit: bei ZUSATZ KWH, MONAT, STUECK, M3, M2 oder CHF; bei GRUNDGEBUEHR MONAT (volle Kalendermonate) oder TAG (taggenau); bei anderen Typen NULL';

INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('TAG', 'Tag', 'Day'),
('TAGE', 'Tage', 'Days'),
('GRUNDGEBUEHR_EINHEIT_HINT', 'Monat: nur volle Kalendermonate im Rechnungszeitraum. Tag: jeder Tag im Rechnungszeitraum (taggenau).', 'Month: only full calendar months within the billing period. Day: every day within the billing period (pro rata).')
ON CONFLICT (key) DO NOTHING;
