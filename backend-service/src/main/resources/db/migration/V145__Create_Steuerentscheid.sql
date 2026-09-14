-- Einspeisesteuerung: Protokoll der Steuerentscheide (Specs/Einspeisesteuerung.md, FR-3)
--
-- Je 15-Minuten-Intervall EIN Entscheid: was die Steuerung tun WUERDE. Sie schaltet nichts -
-- diese Ausbaustufe ist ein Trockenlauf. Der Zweck der Tabelle ist Nachvollziehbarkeit: Ohne die
-- Eingangsgroessen neben dem Ergebnis liesse sich spaeter nicht sagen, warum um 09:15 gesperrt
-- wurde.
--
-- MIT org_id, anders als zev.preiszeitreihe: Entscheide sind anlagenspezifisch (sie haengen an
-- Produktion und Verbrauch einer bestimmten Liegenschaft), Preise dagegen mandantenuebergreifend.

CREATE SEQUENCE zev.steuerentscheid_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE zev.steuerentscheid (
    id              BIGINT         PRIMARY KEY DEFAULT nextval('zev.steuerentscheid_seq'),
    org_id          BIGINT         NOT NULL,
    zeit_von        TIMESTAMP      NOT NULL,
    preis           NUMERIC(10, 5),
    preis_tief_rest NUMERIC(10, 5),
    produktion      NUMERIC(12, 3) NOT NULL,
    verbrauch       NUMERIC(12, 3) NOT NULL,
    ueberschuss     NUMERIC(12, 3) NOT NULL,
    regel           VARCHAR(30)    NOT NULL,
    batterieladung  VARCHAR(10)    NOT NULL,
    einspeisung     VARCHAR(10)    NOT NULL,
    schwellwert     NUMERIC(10, 5) NOT NULL,
    speicherwert    NUMERIC(10, 5) NOT NULL,
    erstellt_am     TIMESTAMP      NOT NULL DEFAULT now(),
    CONSTRAINT fk_steuerentscheid_org FOREIGN KEY (org_id) REFERENCES zev.organisation(id),
    CONSTRAINT uq_steuerentscheid_org_zeit UNIQUE (org_id, zeit_von),
    CONSTRAINT ck_steuerentscheid_regel CHECK (regel IN
        ('PREIS_NEGATIV', 'KEIN_UEBERSCHUSS', 'EINSPEISEN_LOHNT', 'WARTEN_AUF_TAL', 'LADEN')),
    CONSTRAINT ck_steuerentscheid_batterieladung CHECK (batterieladung IN ('FREI', 'GESPERRT')),
    CONSTRAINT ck_steuerentscheid_einspeisung CHECK (einspeisung IN ('FREI', 'GESPERRT')),
    CONSTRAINT ck_steuerentscheid_ueberschuss CHECK (ueberschuss >= 0)
);

-- Die Tagesansicht liest je Mandant eine Zeitspanne; der Job schreibt per Upsert auf denselben
-- Schluessel. Der Unique-Constraint deckt beides ab, ein zusaetzlicher Index waere redundant.

COMMENT ON TABLE zev.steuerentscheid IS
    'Protokoll der Einspeisesteuerung, je 15-Min-Intervall ein Entscheid. Trockenlauf - es wird nichts geschaltet (Specs/Einspeisesteuerung.md)';
COMMENT ON COLUMN zev.steuerentscheid.org_id IS
    'Mandant (internes org_id, BIGINT); serverseitig gesetzt, nie aus dem Request';
COMMENT ON COLUMN zev.steuerentscheid.zeit_von IS
    'Beginn des ausgewerteten Intervalls in UTC (lokale Zeit waere an der Zeitumstellung nicht eindeutig)';
COMMENT ON COLUMN zev.steuerentscheid.preis IS
    'Einspeisepreis des Intervalls in CHF/kWh; leer, wenn kein Preis vorlag. Darf 0 und negativ sein';
COMMENT ON COLUMN zev.steuerentscheid.preis_tief_rest IS
    'Tiefster erwarteter Preis im Rest des Ortstages; leer, wenn keine Preise mehr vorliegen. Grundlage von Regel WARTEN_AUF_TAL';
COMMENT ON COLUMN zev.steuerentscheid.produktion IS
    'Summe der PRODUCER im Intervall in kWh, als Betrag (total steht dort negativ)';
COMMENT ON COLUMN zev.steuerentscheid.verbrauch IS
    'Summe der CONSUMER im Intervall in kWh';
COMMENT ON COLUMN zev.steuerentscheid.ueberschuss IS
    'max(0, produktion - verbrauch) in kWh';
COMMENT ON COLUMN zev.steuerentscheid.regel IS
    'Die erste zutreffende Regel, die den Entscheid bestimmt hat (FR-2)';
COMMENT ON COLUMN zev.steuerentscheid.batterieladung IS
    'Sollzustand der Batterieladung: FREI oder GESPERRT';
COMMENT ON COLUMN zev.steuerentscheid.einspeisung IS
    'Sollzustand der Einspeisung: FREI oder GESPERRT';
COMMENT ON COLUMN zev.steuerentscheid.schwellwert IS
    'Der beim Entscheid geltende Schwellwert - ohne ihn waere ein alter Entscheid nach einer Aenderung der Konfiguration nicht mehr erklaerbar';
COMMENT ON COLUMN zev.steuerentscheid.speicherwert IS
    'Der beim Entscheid geltende Wert einer gespeicherten kWh; gleiche Begruendung wie schwellwert';
COMMENT ON COLUMN zev.steuerentscheid.erstellt_am IS
    'Zeitpunkt des letzten Schreibens (Upsert)';
