-- Untermenü "Admin" im Hamburgermenü (Specs/Hamburgermenü.md, FR-2)
INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('ADMIN', 'Admin', 'Admin')
ON CONFLICT (key) DO NOTHING;
