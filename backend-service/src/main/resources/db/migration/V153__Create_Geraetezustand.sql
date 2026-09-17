-- Gerätezustand: Zeitreihe für Momentanwerte von Geräten (Specs/Gerätezustand.md, FR-2)
--
-- ZUSTAND, NICHT VERBRAUCH. Die Werte hier werden NIE aggregiert und NIE verrechnet. Ein Ladestand
-- von 87 % ist keine Energiemenge, und die Differenz zweier Ladestaende ist keine Kilowattstunde.
-- Wer sie durch die Delta-Aggregation schickt, erhaelt "Delta SOC" - eine Groesse, die bei jedem
-- Ladezyklus das Vorzeichen wechselt. Ein Aggregations-Job fuer diese Tabelle existiert nicht und
-- soll nicht entstehen; deshalb gibt es auch kein verarbeitet-Feld wie in zaehler_rohdaten.
--
-- SCHMAL (groesse, wert) statt breit (je eine Spalte pro Groesse): Nicht jedes Geraet liefert jede
-- Groesse - eine Ladestation hat keinen Ladezustand, ein Zaehler keine Zelltemperatur. Eine breite
-- Tabelle haette Zeilen, in denen nur ein Wert gefuellt ist. Die Pruefungen, die eine typisierte
-- Spalte mitbraechte, holt die Registry im Code zurueck (entity/Zustandsgroesse.java).

CREATE SEQUENCE zev.geraetezustand_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE zev.geraetezustand (
    id           BIGINT         PRIMARY KEY DEFAULT nextval('zev.geraetezustand_seq'),
    org_id       BIGINT         NOT NULL,
    einheit_id   BIGINT         NOT NULL REFERENCES zev.einheit(id) ON DELETE CASCADE,
    zeit         TIMESTAMP      NOT NULL,
    groesse      VARCHAR(20)    NOT NULL,
    wert         NUMERIC(12, 3) NOT NULL,
    empfangen_am TIMESTAMP      DEFAULT now(),
    CONSTRAINT uk_geraetezustand UNIQUE (einheit_id, zeit, groesse),
    -- Wertebereich der Prozent-Groessen, als zweite Sicherung gegen einen Weg an der Anwendung
    -- vorbei (SQL-Import, kuenftiger zweiter Schreibpfad). Nennt bewusst nur die Prozent-Groessen
    -- und ist bei einer neuen Grad-Celsius-Groesse NICHT anzufassen.
    CONSTRAINT ck_geraetezustand_prozent CHECK (
        groesse NOT IN ('SOC', 'SOH') OR (wert >= 0 AND wert <= 100))
);

-- KEIN CHECK ueber 'groesse' selbst: Er zaehlte die erlaubten Werte auf und verlangte fuer jede
-- neue Groesse eine Migration - genau der Nachteil, den die schmale Tabelle vermeiden soll. Die
-- Pruefung liegt im Enum Zustandsgroesse; nur Bekanntes erreicht ueberhaupt ein INSERT.

-- "Letzter Wert vor Zeitpunkt" ist DIE Abfrage der Steuerung (FR-5.1). Bei Minutentakt entstehen
-- rund 525'000 Zeilen pro Jahr und Geraet; ohne diesen Index waere sie ein Durchlauf durch die
-- Zeitreihe statt eines Scans ueber wenige Zeilen.
CREATE INDEX idx_geraetezustand_letzter
    ON zev.geraetezustand (einheit_id, groesse, zeit DESC);

COMMENT ON TABLE zev.geraetezustand IS
    'Momentanwerte von Geräten als Zeitreihe (Ladezustand u.a.). Wird nie aggregiert und nie verrechnet (Specs/Gerätezustand.md)';
COMMENT ON COLUMN zev.geraetezustand.org_id IS
    'Mandant (internes org_id, BIGINT) - aus dem MQTT-Topic abgeleitet, nie aus dem Payload';
COMMENT ON COLUMN zev.geraetezustand.einheit_id IS
    'Zugeordnete Einheit (aufgelöst über org_id + messpunkt). ON DELETE CASCADE: Ein Zustandswert ist kein Beleg und ohne seine Einheit bedeutungslos';
COMMENT ON COLUMN zev.geraetezustand.zeit IS
    'Messzeitpunkt als lokale Wanduhrzeit (Europe/Zurich), wie zev.zaehler_rohdaten.zeit und zev.messwerte.zeit';
COMMENT ON COLUMN zev.geraetezustand.groesse IS
    'Name der Zustandsgrösse, Enum-Name aus entity/Zustandsgroesse.java (derzeit nur SOC)';
COMMENT ON COLUMN zev.geraetezustand.wert IS
    'Zahlenwert; die Einheit (%, °C) folgt aus groesse und steht bewusst nicht als eigene Spalte daneben. Rundung auf 3 Nachkommastellen ist in Kauf genommen';
COMMENT ON COLUMN zev.geraetezustand.empfangen_am IS
    'Empfangszeitpunkt im Backend; gesetzt von der Anwendung, der DEFAULT ist nur das Netz für einen Weg an ihr vorbei';
