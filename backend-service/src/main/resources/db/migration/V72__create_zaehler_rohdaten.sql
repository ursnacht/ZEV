-- Rohdaten-Tabelle für die MQTT-Integration: absolute Zählerstände je Messung.
-- Die Delta-/Intervall-Bildung erfolgt später im Aggregations-Job (siehe MQTT-Integration.md).
CREATE SEQUENCE zev.zaehler_rohdaten_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE zev.zaehler_rohdaten (
    id                        BIGINT PRIMARY KEY DEFAULT nextval('zev.zaehler_rohdaten_seq'),
    org_id                    BIGINT NOT NULL,
    einheit_id                BIGINT NOT NULL REFERENCES zev.einheit(id),
    zeit                      TIMESTAMP NOT NULL,
    zaehlerstand_bezug        DECIMAL(14,4) NOT NULL,
    zaehlerstand_einspeisung  DECIMAL(14,4) NOT NULL,
    empfangen_am              TIMESTAMP DEFAULT NOW(),
    verarbeitet               BOOLEAN NOT NULL DEFAULT FALSE,
    verarbeitet_am            TIMESTAMP,
    CONSTRAINT uk_zaehler_rohdaten UNIQUE (einheit_id, zeit)
);

CREATE INDEX idx_zaehler_rohdaten_unverarbeitet
    ON zev.zaehler_rohdaten(verarbeitet, zeit) WHERE verarbeitet = FALSE;

COMMENT ON COLUMN zev.zaehler_rohdaten.org_id IS 'Mandant (internes org_id, BIGINT) - aus dem MQTT-Topic abgeleitet';
COMMENT ON COLUMN zev.zaehler_rohdaten.einheit_id IS 'Zugeordnete Einheit (aufgelöst über org_id + messpunkt)';
COMMENT ON COLUMN zev.zaehler_rohdaten.zeit IS 'Messzeitpunkt (UTC) aus der MQTT-Nachricht';
COMMENT ON COLUMN zev.zaehler_rohdaten.zaehlerstand_bezug IS 'Absoluter Zählerstand Bezug (Wirkenergie kWh, OBIS 1.8.0), kumulativ';
COMMENT ON COLUMN zev.zaehler_rohdaten.zaehlerstand_einspeisung IS 'Absoluter Zählerstand Einspeisung (Wirkenergie kWh, OBIS 2.8.0), kumulativ';
COMMENT ON COLUMN zev.zaehler_rohdaten.empfangen_am IS 'Empfangszeitpunkt im Backend';
COMMENT ON COLUMN zev.zaehler_rohdaten.verarbeitet IS 'true, wenn durch den Aggregations-Job verarbeitet';
COMMENT ON COLUMN zev.zaehler_rohdaten.verarbeitet_am IS 'Zeitpunkt der Aggregation';
