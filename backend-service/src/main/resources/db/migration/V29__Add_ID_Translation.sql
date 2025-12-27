-- Add missing ID translation
INSERT INTO translation (key, deutsch, englisch) VALUES
    ('ID', 'ID', 'ID')
ON CONFLICT (key) DO NOTHING;
