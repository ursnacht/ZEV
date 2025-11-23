CREATE SEQUENCE zev.messwerte_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE zev.messwerte (
    id BIGINT PRIMARY KEY DEFAULT nextval('zev.messwerte_seq'),
    zeit TIMESTAMP NOT NULL,
    total DOUBLE PRECISION NOT NULL,
    zev DOUBLE PRECISION NOT NULL
);
