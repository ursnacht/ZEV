INSERT INTO zev.translation (key, deutsch, englisch) VALUES
    ('GESAMTBETRAG', 'Gesamtbetrag', 'Total amount')
ON CONFLICT (key) DO NOTHING;
