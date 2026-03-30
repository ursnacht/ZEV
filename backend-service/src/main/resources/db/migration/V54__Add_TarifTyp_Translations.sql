INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('TARIFTYP_ZEV',  'ZEV (Solarstrom)', 'ZEV (Solar power)'),
('TARIFTYP_VNB',  'VNB (Netzstrom)',  'VNB (Grid power)')
ON CONFLICT (key) DO NOTHING;

UPDATE zev.translation
SET deutsch  = 'ZEV = Solarstrom, VNB = Netzstrom, Grundgebühr = Monatlicher Festpreis',
    englisch = 'ZEV = Solar power, VNB = Grid power, Basic Fee = Monthly fixed price'
WHERE key = 'TARIFTYP_HINT';
