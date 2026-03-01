CREATE SEQUENCE IF NOT EXISTS zev.organisation_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE zev.organisation (
    id              BIGINT       PRIMARY KEY DEFAULT nextval('zev.organisation_seq'),
    keycloak_org_id UUID         NOT NULL,
    name            VARCHAR(255) NOT NULL,
    erstellt_am     TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_organisation_keycloak_org_id UNIQUE (keycloak_org_id)
);

CREATE INDEX idx_organisation_keycloak_org_id ON zev.organisation (keycloak_org_id);
