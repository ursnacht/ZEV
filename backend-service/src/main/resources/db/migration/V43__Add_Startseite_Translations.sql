-- Startseite Translations
INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('STARTSEITE', 'Startseite', 'Home'),
('WILLKOMMEN_ZEV', 'Willkommen bei ZEV Management', 'Welcome to ZEV Management'),
('STARTSEITE_BESCHREIBUNG', 'Verwalten Sie Ihre Solarstrom-Gemeinschaft effizient und fair.', 'Manage your solar energy community efficiently and fairly.')
ON CONFLICT (key) DO NOTHING;
