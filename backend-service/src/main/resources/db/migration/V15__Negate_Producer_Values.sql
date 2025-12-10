-- Negate all Producer values (total and zev)
-- Producer values should be stored as negative (production) or positive (control unit consumption)
-- Previously they were stored as absolute values

UPDATE zev.messwerte m
SET total = -m.total,
    zev = -m.zev
FROM zev.einheit e
WHERE m.einheit_id = e.id
  AND e.typ = 'PRODUCER';
