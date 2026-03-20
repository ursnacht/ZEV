-- Translations for GRUNDGEBUEHR tariff type and updated invoice form
INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('KWH',   'kWh',   'kWh'),
('MONAT', 'Monat', 'Month')

ON CONFLICT (key) DO NOTHING;
