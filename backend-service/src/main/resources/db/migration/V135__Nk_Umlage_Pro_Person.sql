-- Umlage pro Person (Specs/Nebenkosten/Abrechnung.md, FR-2)
--
-- Neben der bestehenden Umlage pro Wohnung (Positionsart UMLAGE, unveraendert) gibt es Kosten, die
-- nach Koepfen und nicht nach Wohnungen anfallen - die Gruenabfuhr ist der typische Fall. Dafuer
-- die neue Positionsart UMLAGE_PERSON mit demselben Feldbedarf wie UMLAGE, einem eigenen Nenner
-- (Anzahl Personen x Tage) und einer Personenzahl je Mieter.

-- ===================== Kopf: Anzahl Personen =====================

ALTER TABLE zev.nk_abrechnung ADD COLUMN anzahl_personen INTEGER;

-- Bestehende Abrechnungen: Anzahl Personen = Anzahl Wohnungen. Das ist derselbe Vorschlag, den die
-- Maske bei einer neuen Abrechnung macht, und laesst zusammen mit der Voreinstellung "1 Person je
-- Mieter" eine Umlage pro Person genau wie eine Umlage pro Wohnung rechnen - ein bestehender
-- Datenbestand aendert seine Zahlen dadurch also nicht.
UPDATE zev.nk_abrechnung SET anzahl_personen = anzahl_wohnungen WHERE anzahl_personen IS NULL;

ALTER TABLE zev.nk_abrechnung ALTER COLUMN anzahl_personen SET NOT NULL;
ALTER TABLE zev.nk_abrechnung
    ADD CONSTRAINT ck_nk_abrechnung_personen CHECK (anzahl_personen > 0);

COMMENT ON COLUMN zev.nk_abrechnung.anzahl_personen IS 'Nenner der Umlage pro Person: Anzahl Personen x Tage im Zeitraum. Erfasst, nicht abgeleitet; Vorschlag = Anzahl Wohnungen';

-- ===================== Positionsart UMLAGE_PERSON =====================

-- CHECK-Constraints zaehlen die erlaubten Werte auf - eine neue Enum-Konstante braucht deshalb DDL,
-- sonst scheitert erst das Speichern zur Laufzeit.
ALTER TABLE zev.nk_position DROP CONSTRAINT ck_nk_position_art;
ALTER TABLE zev.nk_position ADD CONSTRAINT ck_nk_position_art
    CHECK (art IN ('UMLAGE', 'UMLAGE_PERSON', 'VERBRAUCH', 'ZUSCHLAG', 'ANTEIL'));

-- UMLAGE_PERSON braucht dieselben Felder wie UMLAGE (Totalbetrag, Einheit, optional Gesamtmenge);
-- nur der Verteilschluessel unterscheidet sich.
ALTER TABLE zev.nk_position DROP CONSTRAINT ck_nk_position_felder;
ALTER TABLE zev.nk_position ADD CONSTRAINT ck_nk_position_felder CHECK (
       (art IN ('UMLAGE', 'UMLAGE_PERSON')
            AND totalbetrag IS NOT NULL AND einheit IS NOT NULL
            AND betrag_pro_einheit IS NULL AND prozentsatz IS NULL)
    OR (art = 'VERBRAUCH'
            AND betrag_pro_einheit IS NOT NULL AND einheit IS NOT NULL
            AND totalbetrag IS NULL AND gesamtmenge IS NULL AND prozentsatz IS NULL)
    OR (art = 'ZUSCHLAG'
            AND prozentsatz IS NOT NULL
            AND totalbetrag IS NULL AND gesamtmenge IS NULL
            AND betrag_pro_einheit IS NULL AND einheit IS NULL)
    OR (art = 'ANTEIL'
            AND totalbetrag IS NOT NULL AND einheit IS NULL AND gesamtmenge IS NULL
            AND betrag_pro_einheit IS NULL AND prozentsatz IS NULL)
);

-- ===================== Anzahl Personen je Mieter =====================

CREATE SEQUENCE zev.nk_person_seq START WITH 1 INCREMENT BY 1;

-- Eigene Tabelle und keine Spalte in nk_akonto: Diese Zahl hat mit dem Akonto nichts zu tun, und
-- eine Tabelle, deren Name nur die Haelfte ihres Inhalts nennt, faellt beim naechsten Leser auf.
CREATE TABLE zev.nk_person (
    id              BIGINT  PRIMARY KEY DEFAULT nextval('zev.nk_person_seq'),
    org_id          BIGINT  NOT NULL,
    abrechnung_id   BIGINT  NOT NULL,
    mieter_id       BIGINT  NOT NULL,
    anzahl_personen INTEGER NOT NULL DEFAULT 1,
    CONSTRAINT fk_nk_person_org        FOREIGN KEY (org_id)        REFERENCES zev.organisation(id),
    CONSTRAINT fk_nk_person_abrechnung FOREIGN KEY (abrechnung_id) REFERENCES zev.nk_abrechnung(id) ON DELETE CASCADE,
    CONSTRAINT fk_nk_person_mieter     FOREIGN KEY (mieter_id)     REFERENCES zev.mieter(id) ON DELETE RESTRICT,
    CONSTRAINT ck_nk_person_anzahl CHECK (anzahl_personen > 0),
    CONSTRAINT uq_nk_person UNIQUE (abrechnung_id, mieter_id, org_id)
);

COMMENT ON TABLE  zev.nk_person                 IS 'Anzahl Personen je Wohnung eines Mieters, bezogen auf eine Abrechnung';
COMMENT ON COLUMN zev.nk_person.org_id          IS 'Mandant (internes org_id, BIGINT)';
COMMENT ON COLUMN zev.nk_person.anzahl_personen IS 'Personen je Wohnung des Mieters; Vorgabe 1. Zaehler der Umlage pro Person: Miettage x Wohnungen x Personen';

CREATE INDEX idx_nk_person_abrechnung ON zev.nk_person (abrechnung_id, mieter_id);
