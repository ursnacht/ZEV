-- Add translation for quarter selector
INSERT INTO translation (key, deutsch, englisch) VALUES
('QUARTAL_WAEHLEN', 'Quartal wählen', 'Select quarter')
ON CONFLICT (key) DO NOTHING;
