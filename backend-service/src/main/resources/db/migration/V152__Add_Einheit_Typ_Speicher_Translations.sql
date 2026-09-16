-- Einheiten-Typ SPEICHER (Specs/Batteriespeicher.md, FR-1)
--
-- KEINE Schema-Migration noetig: zev.einheit.typ ist VARCHAR(20) ohne CHECK-Constraint, "SPEICHER"
-- passt. Vor dem Schreiben dieser Migration geprueft - bei anderen Enums im Projekt zaehlt ein
-- CHECK die erlaubten Werte auf, und eine Erweiterung ohne DDL waere dort beim ersten Insert
-- gescheitert.
--
-- Der Speicher traegt seine beiden Register anders als ein Zaehler:
--   zaehlerstandBezug       = Ladung      (kumulativ, kWh)
--   zaehlerstandEinspeisung = Entladung   (kumulativ, kWh)
-- Damit rechnet die Aggregation unveraendert: total = ΔLadung − ΔEntladung
-- (positiv = laedt, negativ = entlaedt).

INSERT INTO zev.translation (key, deutsch, englisch) VALUES

('TYP_SPEICHER',
 'Speicher',
 'Storage'),

-- Eigene Meldung statt EINHEIT_BILANZ_TYP_EXISTIERT: Der Hinweis auf einen "Bilanz-Typ" hilft
-- beim Anlegen einer zweiten Batterie nicht weiter.
('EINHEIT_SPEICHER_EXISTIERT',
 'Es ist bereits eine Einheit vom Typ Speicher erfasst. Pro Mandant ist nur eine zulässig.',
 'A storage unit already exists. Only one is allowed per tenant.')

ON CONFLICT (key) DO NOTHING;
