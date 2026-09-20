-- Einspeisesteuerung: Hysterese der Regel SOC_TIEF (Specs/Einspeisesteuerung.md, FR-2a/FR-7)

INSERT INTO zev.translation (key, deutsch, englisch) VALUES

('STEUERUNG_SOC_HYSTERESE',
 'Hysterese Ladezustand',
 'State of charge hysteresis'),

('STEUERUNG_SOC_HYSTERESE_HINWEIS',
 'Prozentpunkte über dem Mindest-Ladezustand, bis zu denen weiter geladen wird. Beispiel: Mindestwert 20 %, Hysterese 5 % — freigegeben wird unter 20 %, wieder gesperrt erst ab 25 %. Ohne diesen Abstand wechselte der Entscheid im Viertelstundentakt, sobald der Ladezustand um den Mindestwert pendelt. Vorgabe 5 %; 0 schaltet die Hysterese ab.',
 'Percentage points above the minimum state of charge up to which charging continues. Example: minimum 20 %, hysteresis 5 % — released below 20 %, blocked again only above 25 %. Without this gap the decision would flip every quarter hour whenever the state of charge hovers around the minimum. Default 5 %; 0 disables it.')

ON CONFLICT (key) DO NOTHING;
