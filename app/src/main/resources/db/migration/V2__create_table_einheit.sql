CREATE SEQUENCE zev.einheit_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE zev.einheit (
    id BIGINT PRIMARY KEY DEFAULT nextval('zev.einheit_seq'),
    name VARCHAR(30) NOT NULL
);
