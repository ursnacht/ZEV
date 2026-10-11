-- Jahrespreis (Specs/Tarifverwaltung.md, FR-6): Der Preis eines Grundgebuehr-Tarifs oder eines
-- Zusatz-Tarifs pro Monat kann fuer das ganze Jahr erfasst werden. Die Rechnung verteilt ihn
-- anteilig: nach Monaten (Monate / 12) oder nach Tagen (Tage / Tage des Kalenderjahres).

ALTER TABLE zev.tarif ADD COLUMN preis_pro_jahr BOOLEAN NOT NULL DEFAULT false;

COMMENT ON COLUMN zev.tarif.preis_pro_jahr IS
    'true: preis gilt fuer das ganze Jahr und wird anteilig verrechnet (Monate/12 bzw. Tage/Tage des Jahres); nur GRUNDGEBUEHR und ZUSATZ mit Mengeneinheit MONAT';

INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('TAG', 'Tag', 'Day'),
('MONAT', 'Monat', 'Month'),
('TARIF_PREIS_PRO_JAHR', 'Preis gilt für das ganze Jahr', 'Price applies to the whole year'),
('TARIF_PREIS_PRO_JAHR_HINT', 'Die Rechnung verteilt den Jahrespreis anteilig auf die verrechneten Monate bzw. Tage.', 'The invoice distributes the annual price pro rata over the billed months or days.')
ON CONFLICT (key) DO NOTHING;
