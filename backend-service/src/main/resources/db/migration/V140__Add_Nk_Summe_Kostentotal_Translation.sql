-- Summe der Kostentotale aller Mieter (Specs/Nebenkosten/Abrechnung.md, FR-7).
-- Die Beschriftung spiegelt bewusst NK_KOSTENTOTAL der Mieterzeile - dieselbe Groesse, nur ueber
-- alle Mieter summiert.
INSERT INTO zev.translation (key, deutsch, englisch) VALUES

('NK_SUMME_KOSTENTOTAL',
 'Kostentotal aller Mieter',
 'Total costs of all tenants')

ON CONFLICT (key) DO NOTHING;
