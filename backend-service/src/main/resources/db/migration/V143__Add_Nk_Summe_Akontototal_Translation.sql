-- Summe der Akonto-Totale aller Mieter (Specs/Nebenkosten/Abrechnung.md, FR-7).
-- Die Beschriftung spiegelt NK_SUMME_KOSTENTOTAL darüber - dieselbe Bauart, andere Grösse.
INSERT INTO zev.translation (key, deutsch, englisch) VALUES

('NK_SUMME_AKONTOTOTAL',
 'Akonto total aller Mieter',
 'Total prepayments of all tenants')

ON CONFLICT (key) DO NOTHING;
