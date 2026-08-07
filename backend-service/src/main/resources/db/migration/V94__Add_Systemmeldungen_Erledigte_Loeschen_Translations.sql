-- Button "Erledigte Meldungen löschen" auf der Systemmeldungen-Seite (Specs/Systemmeldungen.md)
INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('SYSTEMMELDUNGEN_ERLEDIGTE_LOESCHEN', 'Erledigte Meldungen löschen', 'Delete resolved messages'),
('SYSTEMMELDUNGEN_ERLEDIGTE_LOESCHEN_BESTAETIGUNG', 'Alle erledigten Meldungen endgültig aus der Datenbank löschen? Offene Meldungen bleiben erhalten. Diese Aktion kann nicht rückgängig gemacht werden.', 'Permanently delete all resolved messages from the database? Open messages are kept. This action cannot be undone.'),
('SYSTEMMELDUNGEN_ERLEDIGTE_GELOESCHT', 'erledigte Meldung(en) gelöscht', 'resolved message(s) deleted')
ON CONFLICT (key) DO NOTHING;
