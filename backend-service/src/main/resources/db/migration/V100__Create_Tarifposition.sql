-- Manuell erfasste Mengen zu einem Tarif, je Mieter und Quartal (siehe Specs/Ladestromtarif.md).
-- Bewusst GENERISCH gehalten: Die fachliche Bedeutung steckt ausschliesslich im referenzierten
-- Tarif. Erster Anwendungsfall ist Ladestrom (TarifTyp LADESTROM); weitere (Sauna, Waschkueche, ...)
-- kommen ohne Schema-Aenderung dazu.
CREATE SEQUENCE zev.tarifposition_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE zev.tarifposition (
    id              BIGINT PRIMARY KEY DEFAULT nextval('zev.tarifposition_seq'),
    org_id          BIGINT NOT NULL,
    mieter_id       BIGINT NOT NULL REFERENCES zev.mieter(id) ON DELETE CASCADE,
    tarif_id        BIGINT NOT NULL REFERENCES zev.tarif(id) ON DELETE RESTRICT,
    jahr            INT NOT NULL,
    quartal         INT NOT NULL,
    menge           NUMERIC(12,3) NOT NULL,
    erfassungsart   VARCHAR(20) NOT NULL DEFAULT 'MANUELL',
    quell_referenz  VARCHAR(64),
    bemerkung       VARCHAR(200),
    CONSTRAINT ck_tarifposition_quartal CHECK (quartal BETWEEN 1 AND 4),
    CONSTRAINT ck_tarifposition_jahr    CHECK (jahr BETWEEN 2000 AND 2100),
    CONSTRAINT ck_tarifposition_menge   CHECK (menge >= 0),
    -- Netz gegen exakte Duplikate. Die fachliche Regel ist strenger (hoechstens eine Position
    -- je Mieter, Quartal und TARIFTYP) und wird im Service geprueft, weil der Typ am Tarif haengt.
    CONSTRAINT uk_tarifposition UNIQUE (org_id, mieter_id, tarif_id, jahr, quartal)
);

-- Deckt die Abfrage bei der Rechnungserzeugung und die Eindeutigkeitspruefung ab.
CREATE INDEX idx_tarifposition_mieter_quartal
    ON zev.tarifposition(org_id, mieter_id, jahr, quartal);

COMMENT ON COLUMN zev.tarifposition.org_id IS 'Mandant (internes org_id, BIGINT)';
COMMENT ON COLUMN zev.tarifposition.mieter_id IS 'Anker der Position: der Mieter, dem die Menge verrechnet wird';
COMMENT ON COLUMN zev.tarifposition.tarif_id IS 'Referenzierter Tarif; sein Typ bestimmt die fachliche Bedeutung der Position';
COMMENT ON COLUMN zev.tarifposition.jahr IS 'Kalenderjahr des Quartals';
COMMENT ON COLUMN zev.tarifposition.quartal IS 'Quartal 1-4';
COMMENT ON COLUMN zev.tarifposition.menge IS 'Erfasste Menge; die Mengeneinheit ergibt sich aus dem Tarif (aktuell durchgehend kWh)';
COMMENT ON COLUMN zev.tarifposition.erfassungsart IS 'MANUELL oder IMPORT - Herkunft der Menge (Enum Erfassungsart; NICHT Quelle, das ist mit CSV/MQTT/API belegt)';
COMMENT ON COLUMN zev.tarifposition.quell_referenz IS 'Kennung, aus der die Menge stammt (bei IMPORT die Ladepunkt-Kennung); Nachweis der Herkunft';
COMMENT ON COLUMN zev.tarifposition.bemerkung IS 'Freier Text, rein informativ';

-- Zuordnungsgrundlage fuer den spaeteren automatischen Import aus dem Lademanagement.
ALTER TABLE zev.mieter ADD COLUMN ladepunkt VARCHAR(64);

COMMENT ON COLUMN zev.mieter.ladepunkt IS 'Ladepunkt-Kennung des Mieters; Zuordnungsgrundlage fuer den spaeteren Import, mandantenweit eindeutig';

-- Partiell: Mieter ohne Ladepunkt sind beliebig viele, gesetzte Kennungen aber eindeutig -
-- zwei Mieter mit derselben Kennung wuerden den Import mehrdeutig machen.
CREATE UNIQUE INDEX uk_mieter_ladepunkt
    ON zev.mieter(org_id, ladepunkt) WHERE ladepunkt IS NOT NULL;
