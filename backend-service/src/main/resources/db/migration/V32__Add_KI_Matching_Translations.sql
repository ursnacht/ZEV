-- Add translations for AI-based unit matching
INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('EINHEIT_ERKANNT', 'Einheit automatisch erkannt', 'Unit automatically detected'),
('EINHEIT_NICHT_ERKANNT', 'Einheit konnte nicht automatisch erkannt werden', 'Unit could not be automatically detected'),
('EINHEIT_WIRD_ERKANNT', 'Einheit wird erkannt', 'Detecting unit'),
('EINHEIT_BITTE_PRUEFEN', 'Bitte Einheit prüfen', 'Please verify unit'),
('KI_NICHT_VERFUEGBAR', 'KI-Service nicht verfügbar', 'AI service not available'),
('AUTOMATISCH_ERKANNT', 'Automatisch erkannt', 'Auto-detected'),
('BITTE_PRUEFEN', 'Bitte prüfen', 'Please verify')
ON CONFLICT (key) DO NOTHING;
