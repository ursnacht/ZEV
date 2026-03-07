INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('DARK_MODE', 'Dark Mode', 'Dark Mode'),
('LIGHT_MODE', 'Light Mode', 'Light Mode')
ON CONFLICT (key) DO NOTHING;
