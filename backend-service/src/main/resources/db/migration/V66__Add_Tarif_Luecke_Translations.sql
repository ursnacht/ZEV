INSERT INTO zev.translation (key, deutsch, englisch) VALUES
    ('FEHLER_TARIF_LUECKEN', 'Für den Zeitraum fehlen gültige Tarife', 'Valid tariffs are missing for the period'),
    ('TARIF_LUECKE_ZEV',     'ZEV-Tarif fehlt für',                    'ZEV tariff missing for'),
    ('TARIF_LUECKE_VNB',     'VNB-Tarif fehlt für',                    'VNB tariff missing for'),
    ('TARIF_LUECKE_WEITERE', '(und weitere)',                          '(and more)')
ON CONFLICT (key) DO NOTHING;
