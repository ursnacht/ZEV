-- Vierte Positionsart ANTEIL: Totalbetrag an der Position, Prozentsatz je Mieter
-- (Specs/Nebenkosten/Abrechnung.md, FR-2). Anwendungsfall Heizkosten, deren Verteilschluessel
-- aus einer externen Abrechnung stammt.
--
-- Der Prozentsatz je Mieter steht in zev.nk_verbrauch.menge - dieselbe Zeile wie eine erfasste
-- Verbrauchsmenge. Bewusst keine eigene Spalte: Es ist genau ein Wert je Position und Mieter, und
-- die Bedeutung ergibt sich aus der Art der Position (siehe Spaltenkommentar unten).
--
-- V118 ist bereits ausgefuehrt; ihre Constraints werden hier ersetzt statt geaendert.

ALTER TABLE zev.nk_position DROP CONSTRAINT IF EXISTS ck_nk_position_art;

ALTER TABLE zev.nk_position ADD CONSTRAINT ck_nk_position_art
    CHECK (art IN ('UMLAGE', 'VERBRAUCH', 'ZUSCHLAG', 'ANTEIL'));

ALTER TABLE zev.nk_position DROP CONSTRAINT IF EXISTS ck_nk_position_felder;

ALTER TABLE zev.nk_position ADD CONSTRAINT ck_nk_position_felder CHECK (
    (art = 'UMLAGE'
        AND totalbetrag IS NOT NULL AND einheit IS NOT NULL
        AND betrag_pro_einheit IS NULL AND prozentsatz IS NULL)
    OR (art = 'VERBRAUCH'
        AND betrag_pro_einheit IS NOT NULL AND einheit IS NOT NULL
        AND totalbetrag IS NULL AND gesamtmenge IS NULL AND prozentsatz IS NULL)
    OR (art = 'ZUSCHLAG'
        AND prozentsatz IS NOT NULL
        AND totalbetrag IS NULL AND gesamtmenge IS NULL
        AND betrag_pro_einheit IS NULL AND einheit IS NULL)
    -- ANTEIL traegt NUR den Totalbetrag: Die Einheit ist immer Prozent und muesste nicht erfasst
    -- werden, und der Prozentsatz steht je Mieter, nicht an der Position.
    OR (art = 'ANTEIL'
        AND totalbetrag IS NOT NULL
        AND einheit IS NULL AND gesamtmenge IS NULL
        AND betrag_pro_einheit IS NULL AND prozentsatz IS NULL)
);

COMMENT ON COLUMN zev.nk_position.art IS
    'UMLAGE (zeitanteilig verteilt), VERBRAUCH (Menge je Mieter), ZUSCHLAG (Prozent auf die Zeilen davor) oder ANTEIL (Prozentsatz je Mieter auf den Totalbetrag)';

COMMENT ON COLUMN zev.nk_verbrauch.menge IS
    'Je Mieter erfasster Wert: bei VERBRAUCH die Menge, bei ANTEIL der Prozentsatz';

INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('NK_ART_ANTEIL',
 'Anteil (%)',
 'Share (%)'),

('NK_SUMME_PROZENT',
 'Summe der Anteile',
 'Sum of shares'),

('NK_ANTEIL_HINT',
 'Der Totalbetrag wird nach den je Mieter erfassten Prozentsätzen verteilt. Zur Kontrolle sollte ihre Summe 100% ergeben.',
 'The total amount is distributed by the percentages recorded per tenant. As a check, they should add up to 100%.'),

('NK_SUMME_PROZENT_ABWEICHUNG',
 'Die Summe der Anteile ergibt nicht 100%.',
 'The shares do not add up to 100%.')
ON CONFLICT (key) DO NOTHING;
