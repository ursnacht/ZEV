-- PostgreSQL doesn't support column reordering, so we recreate the table
CREATE TABLE zev.einheit_new (
    id BIGINT PRIMARY KEY DEFAULT nextval('zev.einheit_seq'),
    typ zev.einheit_typ NOT NULL DEFAULT 'CONSUMER',
    name VARCHAR(30) NOT NULL
);

INSERT INTO zev.einheit_new (id, typ, name)
SELECT id, typ, name FROM zev.einheit;

DROP TABLE zev.einheit;

ALTER TABLE zev.einheit_new RENAME TO einheit;
