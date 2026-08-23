-- Nebenkostenabrechnung (Specs/Nebenkosten/Abrechnung.md).
--
-- Fuenf Tabellen: die Abrechnung selbst, ihre allgemeinen Positionen, die je Mieter erfassten
-- Verbrauchsmengen, die freien Zusatzpositionen und die Akonto-Angaben.
--
-- Berechnete Werte (Umlage, Zuschlag, Summen) werden bewusst NICHT gespeichert - sie ergeben
-- sich jederzeit aus den erfassten Daten. Damit die Zahlen einer abgeschlossenen Abrechnung
-- trotzdem stabil bleiben, stehen die Mieter-Fremdschluessel auf ON DELETE RESTRICT.

CREATE SEQUENCE zev.nk_abrechnung_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE zev.nk_position_seq   START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE zev.nk_verbrauch_seq  START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE zev.nk_zusatz_seq     START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE zev.nk_akonto_seq     START WITH 1 INCREMENT BY 1;

-- ===================== Abrechnung =====================

CREATE TABLE zev.nk_abrechnung (
    id               BIGINT       PRIMARY KEY DEFAULT nextval('zev.nk_abrechnung_seq'),
    org_id           BIGINT       NOT NULL,
    bezeichnung      VARCHAR(150) NOT NULL,
    datum_von        DATE         NOT NULL,
    datum_bis        DATE         NOT NULL,
    anzahl_wohnungen INTEGER      NOT NULL,
    abgerechnet      BOOLEAN      NOT NULL DEFAULT FALSE,
    erstellt_am      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_nk_abrechnung_org FOREIGN KEY (org_id) REFERENCES zev.organisation(id),
    CONSTRAINT ck_nk_abrechnung_datum    CHECK (datum_von <= datum_bis),
    CONSTRAINT ck_nk_abrechnung_wohnungen CHECK (anzahl_wohnungen > 0)
);

COMMENT ON TABLE  zev.nk_abrechnung                  IS 'Nebenkostenabrechnung je Zeitraum';
COMMENT ON COLUMN zev.nk_abrechnung.org_id           IS 'Mandant (internes org_id, BIGINT)';
COMMENT ON COLUMN zev.nk_abrechnung.bezeichnung      IS 'Freie Bezeichnung, z.B. "Nebenkostenabrechnung 2026"';
COMMENT ON COLUMN zev.nk_abrechnung.anzahl_wohnungen IS 'Nenner der Umlage: Anzahl Wohnungen x Tage im Zeitraum. Erfasst, nicht abgeleitet';
COMMENT ON COLUMN zev.nk_abrechnung.abgerechnet      IS 'Gesetzt = abgeschlossen und schreibgeschuetzt';

CREATE INDEX idx_nk_abrechnung_org_datum ON zev.nk_abrechnung (org_id, datum_von DESC);

-- ===================== Allgemeine Positionen =====================

CREATE TABLE zev.nk_position (
    id                 BIGINT        PRIMARY KEY DEFAULT nextval('zev.nk_position_seq'),
    org_id             BIGINT        NOT NULL,
    abrechnung_id      BIGINT        NOT NULL,
    art                VARCHAR(20)   NOT NULL,
    bezeichnung        VARCHAR(150)  NOT NULL,
    reihenfolge        INTEGER       NOT NULL,
    einheit            VARCHAR(20),
    totalbetrag        NUMERIC(12,2),
    gesamtmenge        NUMERIC(12,3),
    betrag_pro_einheit NUMERIC(12,4),
    prozentsatz        NUMERIC(5,2),
    CONSTRAINT fk_nk_position_org        FOREIGN KEY (org_id)        REFERENCES zev.organisation(id),
    CONSTRAINT fk_nk_position_abrechnung FOREIGN KEY (abrechnung_id) REFERENCES zev.nk_abrechnung(id) ON DELETE CASCADE,
    CONSTRAINT ck_nk_position_art     CHECK (art IN ('UMLAGE', 'VERBRAUCH', 'ZUSCHLAG')),
    CONSTRAINT ck_nk_position_einheit CHECK (einheit IS NULL OR einheit IN ('KWH', 'MONAT', 'STUECK', 'M3')),
    CONSTRAINT ck_nk_position_prozent CHECK (prozentsatz IS NULL OR (prozentsatz >= 0 AND prozentsatz <= 100)),
    CONSTRAINT ck_nk_position_mengen  CHECK (
        (totalbetrag IS NULL OR totalbetrag >= 0)
        AND (gesamtmenge IS NULL OR gesamtmenge >= 0)
        AND (betrag_pro_einheit IS NULL OR betrag_pro_einheit >= 0)
    ),
    -- Art-abhaengige Pflichtfelder: Wird das nur im Service geprueft, entstehen bei einem
    -- Fehler im Code Zeilen, die sich gar nicht mehr berechnen lassen.
    CONSTRAINT ck_nk_position_felder CHECK (
        (art = 'UMLAGE'
            AND totalbetrag IS NOT NULL AND einheit IS NOT NULL
            AND betrag_pro_einheit IS NULL AND prozentsatz IS NULL)
        OR (art = 'VERBRAUCH'
            AND betrag_pro_einheit IS NOT NULL AND einheit IS NOT NULL
            AND totalbetrag IS NULL AND gesamtmenge IS NULL AND prozentsatz IS NULL)
        OR (art = 'ZUSCHLAG'
            AND prozentsatz IS NOT NULL
            AND totalbetrag IS NULL AND gesamtmenge IS NULL
            AND betrag_pro_einheit IS NULL AND einheit IS NULL)
    ),
    -- Die Reihenfolge bestimmt das Ergebnis der Zuschlagskaskade; zwei Positionen mit
    -- derselben Nummer machten sie nicht-deterministisch.
    CONSTRAINT uq_nk_position_reihenfolge UNIQUE (abrechnung_id, reihenfolge, org_id)
);

COMMENT ON TABLE  zev.nk_position                    IS 'Allgemeine Positionen einer Nebenkostenabrechnung';
COMMENT ON COLUMN zev.nk_position.art                IS 'UMLAGE (verteilt), VERBRAUCH (Menge je Mieter) oder ZUSCHLAG (Prozent)';
COMMENT ON COLUMN zev.nk_position.reihenfolge        IS 'Fachlich tragend: bestimmt die Bemessungsgrundlage der Zuschlagskaskade';
COMMENT ON COLUMN zev.nk_position.totalbetrag        IS 'Nur UMLAGE: zu verteilender Gesamtbetrag';
COMMENT ON COLUMN zev.nk_position.gesamtmenge        IS 'Nur UMLAGE, optional: zu verteilende Gesamtmenge';
COMMENT ON COLUMN zev.nk_position.betrag_pro_einheit IS 'Nur VERBRAUCH: Preis je Mengeneinheit';
COMMENT ON COLUMN zev.nk_position.prozentsatz        IS 'Nur ZUSCHLAG: Prozent auf die Summe der Positionen davor';

CREATE INDEX idx_nk_position_abrechnung ON zev.nk_position (abrechnung_id, reihenfolge);

-- ===================== Verbrauchsmenge je Mieter =====================

CREATE TABLE zev.nk_verbrauch (
    id          BIGINT        PRIMARY KEY DEFAULT nextval('zev.nk_verbrauch_seq'),
    org_id      BIGINT        NOT NULL,
    position_id BIGINT        NOT NULL,
    mieter_id   BIGINT        NOT NULL,
    menge       NUMERIC(12,3) NOT NULL,
    CONSTRAINT fk_nk_verbrauch_org      FOREIGN KEY (org_id)      REFERENCES zev.organisation(id),
    CONSTRAINT fk_nk_verbrauch_position FOREIGN KEY (position_id) REFERENCES zev.nk_position(id) ON DELETE CASCADE,
    CONSTRAINT fk_nk_verbrauch_mieter   FOREIGN KEY (mieter_id)   REFERENCES zev.mieter(id) ON DELETE RESTRICT,
    CONSTRAINT ck_nk_verbrauch_menge CHECK (menge >= 0),
    CONSTRAINT uq_nk_verbrauch UNIQUE (position_id, mieter_id, org_id)
);

COMMENT ON TABLE  zev.nk_verbrauch           IS 'Je Mieter erfasste Menge zu einer VERBRAUCH-Position';
COMMENT ON COLUMN zev.nk_verbrauch.mieter_id IS 'FK auf Mieter; ON DELETE RESTRICT schuetzt abgeschlossene Abrechnungen';

-- ===================== Zusatzposition je Mieter =====================

CREATE TABLE zev.nk_zusatz (
    id                 BIGINT        PRIMARY KEY DEFAULT nextval('zev.nk_zusatz_seq'),
    org_id             BIGINT        NOT NULL,
    abrechnung_id      BIGINT        NOT NULL,
    mieter_id          BIGINT        NOT NULL,
    reihenfolge        INTEGER       NOT NULL,
    bezeichnung        VARCHAR(150)  NOT NULL,
    einheit            VARCHAR(20)   NOT NULL,
    menge              NUMERIC(12,3) NOT NULL,
    betrag_pro_einheit NUMERIC(12,4) NOT NULL,
    CONSTRAINT fk_nk_zusatz_org        FOREIGN KEY (org_id)        REFERENCES zev.organisation(id),
    CONSTRAINT fk_nk_zusatz_abrechnung FOREIGN KEY (abrechnung_id) REFERENCES zev.nk_abrechnung(id) ON DELETE CASCADE,
    CONSTRAINT fk_nk_zusatz_mieter     FOREIGN KEY (mieter_id)     REFERENCES zev.mieter(id) ON DELETE RESTRICT,
    CONSTRAINT ck_nk_zusatz_einheit CHECK (einheit IN ('KWH', 'MONAT', 'STUECK', 'M3')),
    CONSTRAINT ck_nk_zusatz_werte   CHECK (menge >= 0 AND betrag_pro_einheit >= 0),
    CONSTRAINT uq_nk_zusatz_reihenfolge UNIQUE (abrechnung_id, mieter_id, reihenfolge, org_id)
);

COMMENT ON TABLE  zev.nk_zusatz             IS 'Frei erfasste Position eines einzelnen Mieters';
COMMENT ON COLUMN zev.nk_zusatz.reihenfolge IS 'Gleicher Nummernraum wie nk_position; bei Gleichstand zaehlt die allgemeine Position zuerst';

CREATE INDEX idx_nk_zusatz_abrechnung ON zev.nk_zusatz (abrechnung_id, mieter_id);

-- ===================== Akonto je Mieter =====================

CREATE TABLE zev.nk_akonto (
    id               BIGINT        PRIMARY KEY DEFAULT nextval('zev.nk_akonto_seq'),
    org_id           BIGINT        NOT NULL,
    abrechnung_id    BIGINT        NOT NULL,
    mieter_id        BIGINT        NOT NULL,
    anzahl_monate    NUMERIC(5,2)  NOT NULL,
    betrag_pro_monat NUMERIC(10,2) NOT NULL,
    korrektur        NUMERIC(10,2) NOT NULL DEFAULT 0,
    CONSTRAINT fk_nk_akonto_org        FOREIGN KEY (org_id)        REFERENCES zev.organisation(id),
    CONSTRAINT fk_nk_akonto_abrechnung FOREIGN KEY (abrechnung_id) REFERENCES zev.nk_abrechnung(id) ON DELETE CASCADE,
    CONSTRAINT fk_nk_akonto_mieter     FOREIGN KEY (mieter_id)     REFERENCES zev.mieter(id) ON DELETE RESTRICT,
    CONSTRAINT ck_nk_akonto_werte CHECK (anzahl_monate >= 0 AND betrag_pro_monat >= 0),
    CONSTRAINT uq_nk_akonto UNIQUE (abrechnung_id, mieter_id, org_id)
);

COMMENT ON TABLE  zev.nk_akonto               IS 'Akonto-Zahlungen eines Mieters zu einer Abrechnung';
COMMENT ON COLUMN zev.nk_akonto.anzahl_monate IS 'Anteilig gerechnet: ein angebrochener Monat zaehlt mit seinem Anteil';
COMMENT ON COLUMN zev.nk_akonto.korrektur     IS 'Freier Korrekturbetrag, darf negativ sein';
