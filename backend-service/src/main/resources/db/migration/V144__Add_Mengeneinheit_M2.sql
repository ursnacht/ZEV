-- Mengeneinheit um M2 (Quadratmeter) erweitern - fuer Umlagen, die nach FLAECHE verteilt werden.
-- Anlass ist die Kehrichtgrundgebuehr, die pro Quadratmeter Wohnflaeche verrechnet wird.
-- Siehe Specs/Nebenkosten/Abrechnung.md, FR-5.
--
-- DREI Constraints zaehlen die erlaubten Werte auf; alle drei sind anzupassen, sonst schlaegt
-- das Speichern an genau der Stelle fehl, an der die neue Einheit gebraucht wird. V117, V118 und
-- V121 sind bereits ausgefuehrt und werden deshalb nicht geaendert, sondern hier ersetzt.

ALTER TABLE zev.tarif DROP CONSTRAINT IF EXISTS ck_tarif_mengeneinheit;

ALTER TABLE zev.tarif ADD CONSTRAINT ck_tarif_mengeneinheit
    CHECK (mengeneinheit IS NULL
           OR mengeneinheit IN ('KWH', 'MONAT', 'STUECK', 'M3', 'M2', 'CHF'));

COMMENT ON COLUMN zev.tarif.mengeneinheit IS
    'Mengeneinheit eines ZUSATZ-Tarifs: KWH, MONAT, STUECK, M3, M2 oder CHF; bei anderen Typen NULL';

ALTER TABLE zev.nk_position DROP CONSTRAINT IF EXISTS ck_nk_position_einheit;

ALTER TABLE zev.nk_position ADD CONSTRAINT ck_nk_position_einheit
    CHECK (einheit IS NULL
           OR einheit IN ('KWH', 'MONAT', 'STUECK', 'M3', 'M2', 'CHF'));

ALTER TABLE zev.nk_zusatz DROP CONSTRAINT IF EXISTS ck_nk_zusatz_einheit;

ALTER TABLE zev.nk_zusatz ADD CONSTRAINT ck_nk_zusatz_einheit
    CHECK (einheit IN ('KWH', 'MONAT', 'STUECK', 'M3', 'M2', 'CHF'));

-- Uebersetzung der neuen Einheit. Der Enum-Name ist zugleich der Schluessel; angezeigt wird das
-- Einheitenzeichen mit hochgestellter Zwei - wie bei M3 ("m³") und nicht als "m2".
INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('M2', 'm²', 'm²')
ON CONFLICT (key) DO NOTHING;
