-- Translations for GRUNDGEBUEHR tariff type and updated invoice form
INSERT INTO zev.translation (key, deutsch, englisch) VALUES
-- New tariff type
('TARIFTYP_GRUNDGEBUEHR',      'Grundgebühr (CHF/Monat/Zähler)',        'Basic Fee (CHF/month/meter)'),
('TARIFTYP_HINT_GRUNDGEBUEHR', 'Monatlicher Festpreis pro Stromzähler', 'Monthly fixed price per electricity meter'),

-- Invoice form (all units instead of consumers only)
('EINHEITEN_WAEHLEN',          'Einheiten auswählen',                   'Select Units'),
('KEINE_EINHEITEN_VORHANDEN',  'Keine Einheiten vorhanden',             'No units available')

ON CONFLICT (key) DO NOTHING;
