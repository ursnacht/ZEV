-- Die Tarifposition haengt neu an der Einheit statt am Mieter (Specs/Ladestationen.md FR-2).
--
-- Grund: Die Menge gehoert zur Ladestation, nicht zur Person. Weil beim Mieterwechsel die RFID
-- invalidiert und eine neue Ladestations-Einheit angelegt wird, gehoert jede Einheit ueber ihre
-- ganze Lebensdauer genau einem Nutzer - die Zuordnung einer Quartalsposition bleibt eindeutig.
--
-- Keine Datenuebernahme: Die Tabelle ist leer (Stand 17.08.2026, vom Benutzer bereinigt).
ALTER TABLE zev.tarifposition DROP CONSTRAINT IF EXISTS uk_tarifposition;
DROP INDEX IF EXISTS zev.idx_tarifposition_mieter_quartal;

ALTER TABLE zev.tarifposition DROP COLUMN mieter_id;
ALTER TABLE zev.tarifposition ADD COLUMN einheit_id BIGINT NOT NULL
    REFERENCES zev.einheit (id) ON DELETE CASCADE;

COMMENT ON COLUMN zev.tarifposition.einheit_id IS 'Einheit, zu der die Menge gehoert (Typ LADESTATION); mit der Einheit verschwinden auch ihre Positionen';

-- Netz gegen exakte Duplikate. Die schaerfere Regel "hoechstens eine Position je Einheit,
-- Quartal und TARIFTYP" prueft der Service, weil der Typ am Tarif haengt.
ALTER TABLE zev.tarifposition ADD CONSTRAINT uk_tarifposition
    UNIQUE (org_id, einheit_id, tarif_id, jahr, quartal);

CREATE INDEX idx_tarifposition_einheit_quartal
    ON zev.tarifposition (org_id, einheit_id, jahr, quartal);
