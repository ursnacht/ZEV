-- Einspeisesteuerung: Regel SOC_TIEF und ihre Schwelle (Specs/Einspeisesteuerung.md, FR-2/FR-7)

INSERT INTO zev.translation (key, deutsch, englisch) VALUES

('STEUERUNG_REGEL_SOC_TIEF',
 'Ladezustand zu tief — laden',
 'State of charge too low — charge'),

('STEUERUNG_SOC_MINIMUM',
 'Mindest-Ladezustand',
 'Minimum state of charge'),

('STEUERUNG_SOC_MINIMUM_HINWEIS',
 'Fällt der Ladezustand unter diesen Wert, wird eine Ladesperre aufgehoben — die Batterie lädt, auch wenn später ein günstigeres Intervall käme. Verhindert, dass der Speicher leerläuft, während die Steuerung auf ein Preistal wartet. Vorgabe 20 %. Ohne erfassten Speicher bleibt die Regel wirkungslos.',
 'If the state of charge drops below this value, a charging block is lifted — the battery charges even when a cheaper interval would follow. Prevents the storage from running empty while the control waits for a price trough. Default 20 %. Without a storage unit the rule has no effect.')

ON CONFLICT (key) DO NOTHING;
