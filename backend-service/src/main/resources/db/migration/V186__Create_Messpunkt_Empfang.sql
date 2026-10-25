-- Messpunkt-Empfang: letzter MQTT-Empfang je Mandant und Messpunkt (Specs/Messpunkt-Empfang.md)
--
-- Eine Zeile je (org_id, messpunkt), per Upsert aktualisiert. Sie entsteht auch fuer Messpunkte
-- OHNE Einheit - genau die sucht, wer einen neuen Zaehler in Betrieb nimmt.
--
-- KEIN FK auf zev.einheit: Der Empfang besteht gerade auch ohne Einheit (FR-1.7).
-- KEIN FK auf zev.organisation: org_id stammt aus dem MQTT-Topic; eine Zeile mit einer org_id ohne
-- Mandanten ist vorgesehen und faellt nach 30 Tagen weg (FR-3.2).
--
-- Empfangszeiten als TIMESTAMPTZ, anders als die Messwerte: Das Alter ist eine Differenz zweier
-- Zeitpunkte und waere ueber zonenlose Ortszeit in der Nacht der Winterzeit-Umstellung falsch
-- (FR-1.8).

CREATE SEQUENCE zev.messpunkt_empfang_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE zev.messpunkt_empfang (
    id                       BIGINT       PRIMARY KEY DEFAULT nextval('zev.messpunkt_empfang_seq'),
    org_id                   BIGINT       NOT NULL,
    messpunkt                VARCHAR(255) NOT NULL,
    quelle                   VARCHAR(20)  NOT NULL,
    erster_empfang           TIMESTAMPTZ  NOT NULL,
    letzter_empfang          TIMESTAMPTZ  NOT NULL,
    anzahl_meldungen         BIGINT       NOT NULL,
    zaehlerstand_bezug       NUMERIC,
    zaehlerstand_einspeisung NUMERIC,
    ergebnis                 VARCHAR(30)  NOT NULL,
    grund                    VARCHAR(255),
    CONSTRAINT uq_messpunkt_empfang_org_messpunkt UNIQUE (org_id, messpunkt),
    CONSTRAINT ck_messpunkt_empfang_quelle CHECK (quelle IN ('PI_GATEWAY', 'GPLUG')),
    CONSTRAINT ck_messpunkt_empfang_ergebnis
        CHECK (ergebnis IN ('GESPEICHERT', 'UNBEKANNTER_MESSPUNKT', 'UNGUELTIG')),
    CONSTRAINT ck_messpunkt_empfang_anzahl CHECK (anzahl_meldungen >= 1),
    -- Ein Grund genau dann, wenn die Meldung verworfen wurde
    CONSTRAINT ck_messpunkt_empfang_grund CHECK ((ergebnis = 'UNGUELTIG') = (grund IS NOT NULL))
);

COMMENT ON TABLE zev.messpunkt_empfang IS
    'Letzter MQTT-Empfang je Mandant und Messpunkt, auch ohne Einheit (Specs/Messpunkt-Empfang.md)';
COMMENT ON COLUMN zev.messpunkt_empfang.id IS 'Technischer Schlüssel';
COMMENT ON COLUMN zev.messpunkt_empfang.org_id IS
    'Mandant aus dem MQTT-Topic; bewusst ohne FK auf zev.organisation';
COMMENT ON COLUMN zev.messpunkt_empfang.messpunkt IS
    'Messpunkt aus dem MQTT-Topic, auch wenn ihn keine Einheit trägt';
COMMENT ON COLUMN zev.messpunkt_empfang.quelle IS 'PI_GATEWAY (Topic zev/...) oder GPLUG (Topic gPlug/...)';
COMMENT ON COLUMN zev.messpunkt_empfang.erster_empfang IS 'Erste Meldung seit Anlage der Zeile, Backend-Uhr';
COMMENT ON COLUMN zev.messpunkt_empfang.letzter_empfang IS 'Letzte Meldung, Backend-Uhr (nicht die Gerätezeit)';
COMMENT ON COLUMN zev.messpunkt_empfang.anzahl_meldungen IS 'Anzahl Meldungen seit erster_empfang';
COMMENT ON COLUMN zev.messpunkt_empfang.zaehlerstand_bezug IS
    'Zählerstand Bezug der letzten Meldung in kWh; NULL, wenn nicht lesbar';
COMMENT ON COLUMN zev.messpunkt_empfang.zaehlerstand_einspeisung IS
    'Zählerstand Einspeisung der letzten Meldung in kWh; NULL, wenn nicht lesbar oder nicht gemeldet';
COMMENT ON COLUMN zev.messpunkt_empfang.ergebnis IS
    'Ergebnis der letzten Meldung: GESPEICHERT, UNBEKANNTER_MESSPUNKT oder UNGUELTIG';
COMMENT ON COLUMN zev.messpunkt_empfang.grund IS 'Grund des Verwerfens, nur bei UNGUELTIG';
