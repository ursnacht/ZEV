-- Einstrahlungsprognose fuer die Ladeplanung (Specs/Ladeplanung.md, FR-2 und FR-5)
--
-- WARUM EINE EIGENE TABELLE und nicht Spalten am Steuerentscheid: Die Prognose beschreibt die
-- ZUKUNFT. Entscheide gibt es nur fuer abgeschlossene Intervalle - haengte die Prognose daran,
-- waere der Resttag, um den es geht, nie sichtbar.
--
-- WARUM GESPEICHERT und nicht bei Bedarf geholt: Der Steuerungs-Job darf nicht an einer fremden
-- Schnittstelle haengen. Ein eigener Job holt die Prognose und legt sie hier ab; der Steuerungs-
-- Job liest nur aus der Datenbank. Dasselbe Muster wie bei zev.preiszeitreihe.
--
-- MIT org_id, anders als die Preiszeitreihe: Preise gelten fuer den ganzen Markt, die Einstrahlung
-- haengt an Standort und Ausrichtung der Anlage.

CREATE SEQUENCE IF NOT EXISTS zev.einstrahlungsprognose_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE zev.einstrahlungsprognose (
    id            BIGINT       NOT NULL DEFAULT nextval('zev.einstrahlungsprognose_seq'),
    org_id        BIGINT       NOT NULL,
    zeit          TIMESTAMP    NOT NULL,
    gti           NUMERIC(8,2) NOT NULL,
    abgerufen_am  TIMESTAMP    NOT NULL,

    CONSTRAINT pk_einstrahlungsprognose PRIMARY KEY (id),
    CONSTRAINT uq_einstrahlungsprognose_org_zeit UNIQUE (org_id, zeit),
    CONSTRAINT ck_einstrahlungsprognose_gti CHECK (gti >= 0)
);

CREATE INDEX idx_einstrahlungsprognose_org_zeit ON zev.einstrahlungsprognose (org_id, zeit);

COMMENT ON TABLE zev.einstrahlungsprognose IS
    'Vorhergesagte Sonneneinstrahlung auf die Modulflaeche je 15-Minuten-Intervall, von Open-Meteo (Modell MeteoSchweiz ICON-CH1)';
COMMENT ON COLUMN zev.einstrahlungsprognose.zeit IS
    'BEGINN des 15-Minuten-Intervalls in ORTSZEIT (Europe/Zurich) - derselbe Bezug wie steuerentscheid.zeit_von, ANDERS als messwerte.zeit (Intervallende) und preiszeitreihe.zeit_von (UTC)';
COMMENT ON COLUMN zev.einstrahlungsprognose.gti IS
    'Global Tilted Irradiance in W/m2 - Einstrahlung auf die geneigte Modulflaeche, nicht horizontal';
COMMENT ON COLUMN zev.einstrahlungsprognose.abgerufen_am IS
    'Zeitpunkt des Abrufs. Ohne ihn waere nicht unterscheidbar, ob ein Wert von heute frueh oder von vorgestern stammt';
