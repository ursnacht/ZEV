-- Tabelle für persistierte Metriken
CREATE TABLE zev.metriken (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    value JSONB NOT NULL,
    zeitstempel TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Index für schnellen Zugriff nach Name
CREATE INDEX idx_metriken_name ON zev.metriken(name);
