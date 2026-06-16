INSERT INTO zev.translation (key, deutsch, englisch) VALUES
    ('HEUTE', 'Heute', 'Today'),
    ('GESTERN', 'Gestern', 'Yesterday'),
    ('ZAHLDATUM_LOESCHEN', 'Zahldatum löschen', 'Clear payment date')
ON CONFLICT (key) DO NOTHING;
