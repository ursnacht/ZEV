CREATE TYPE zev.einheit_typ AS ENUM ('PRODUCER', 'CONSUMER');

ALTER TABLE zev.einheit ADD COLUMN typ zev.einheit_typ NOT NULL DEFAULT 'CONSUMER';
