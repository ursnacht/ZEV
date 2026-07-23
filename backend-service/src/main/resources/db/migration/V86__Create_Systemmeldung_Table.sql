-- Systemmeldungen: persistente, mandantenfähige Betriebsmeldungen (Start: Bilanzmodell-Fehler)
CREATE SEQUENCE IF NOT EXISTS zev.systemmeldung_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE zev.systemmeldung (
    id                    BIGINT PRIMARY KEY DEFAULT nextval('zev.systemmeldung_seq'),
    org_id                BIGINT       NOT NULL,
    level                 VARCHAR(10)  NOT NULL,
    kategorie             VARCHAR(50)  NOT NULL,
    meldung_key           VARCHAR(100) NOT NULL,
    parameter             VARCHAR(500),
    erstmals_aufgetreten  TIMESTAMP    NOT NULL,
    zuletzt_aufgetreten   TIMESTAMP    NOT NULL,
    erledigt              BOOLEAN      NOT NULL DEFAULT FALSE,
    erledigt_am           TIMESTAMP,
    erledigt_automatisch  BOOLEAN      NOT NULL DEFAULT FALSE,
    zaehler               INTEGER      NOT NULL DEFAULT 1
);

-- Dedup-Invariante: max. ein OFFENER Eintrag je (org_id, meldung_key)
CREATE UNIQUE INDEX uk_systemmeldung_offen
    ON zev.systemmeldung (org_id, meldung_key) WHERE erledigt = FALSE;
-- Listenzugriff (mandantengefiltert, Default-Sortierung)
CREATE INDEX idx_systemmeldung_org_zuletzt ON zev.systemmeldung (org_id, zuletzt_aufgetreten DESC);
-- Retention-Cleanup
CREATE INDEX idx_systemmeldung_retention ON zev.systemmeldung (erledigt, erledigt_am);

COMMENT ON COLUMN zev.systemmeldung.org_id IS 'Mandant (interne org_id, serverseitig gesetzt)';
COMMENT ON COLUMN zev.systemmeldung.level IS 'Schweregrad INFO/WARN/ERROR';
COMMENT ON COLUMN zev.systemmeldung.kategorie IS 'Fehlerquelle/-gruppe als Uebersetzungs-Key';
COMMENT ON COLUMN zev.systemmeldung.meldung_key IS 'Uebersetzungs-Key des Fehlers';
COMMENT ON COLUMN zev.systemmeldung.parameter IS 'Dynamische Teile der Meldung (letztes Vorkommen), fuer Rendering';
COMMENT ON COLUMN zev.systemmeldung.erstmals_aufgetreten IS 'Zeitpunkt des ersten Auftretens (bei Dedup unveraendert)';
COMMENT ON COLUMN zev.systemmeldung.zuletzt_aufgetreten IS 'Zeitpunkt des letzten Auftretens (bei Dedup aktualisiert)';
COMMENT ON COLUMN zev.systemmeldung.erledigt IS 'true = bearbeitet/erledigt/nicht mehr relevant';
COMMENT ON COLUMN zev.systemmeldung.erledigt_am IS 'Zeitpunkt der Erledigung (manuell oder Auto-Resolve); Basis fuer Retention';
COMMENT ON COLUMN zev.systemmeldung.erledigt_automatisch IS 'true = per Auto-Resolve erledigt';
COMMENT ON COLUMN zev.systemmeldung.zaehler IS 'Anzahl Vorkommen (Dedup)';
