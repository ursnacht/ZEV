INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('DATUM_VON',  'Datum von',  'Date from'),
('DATUM_BIS',  'Datum bis',  'Date to'),
('ZAHLDATUM',  'Zahldatum',  'Payment Date'),
('STATUS',     'Status',     'Status')
ON CONFLICT (key) DO NOTHING;
