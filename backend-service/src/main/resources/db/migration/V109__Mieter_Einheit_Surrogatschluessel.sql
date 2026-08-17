-- Die Zuordnungstabelle bekommt einen Surrogatschluessel wie alle anderen Entities.
--
-- Grund: Ein zusammengesetzter Schluessel verlangt in JPA eine eigene Schluesselklasse
-- (@IdClass/@Embeddable). Die liegt zwangslaeufig im Paket `entity`, ist aber selbst keine
-- Entity - und verletzt damit die ArchUnit-Regel "Entities sollten mit @Entity annotiert sein".
-- Statt die Regel aufzuweichen bekommt `mieter_einheit` denselben Aufbau wie jede andere
-- Tabelle: `id` aus einer Sequenz, dazu ein Unique-Constraint auf das fachliche Schluesselpaar.
CREATE SEQUENCE zev.mieter_einheit_seq START WITH 1 INCREMENT BY 1;

ALTER TABLE zev.mieter_einheit DROP CONSTRAINT pk_mieter_einheit;
ALTER TABLE zev.mieter_einheit ADD CONSTRAINT uk_mieter_einheit UNIQUE (mieter_id, einheit_id);

ALTER TABLE zev.mieter_einheit ADD COLUMN id BIGINT;
UPDATE zev.mieter_einheit SET id = nextval('zev.mieter_einheit_seq');
ALTER TABLE zev.mieter_einheit ALTER COLUMN id SET NOT NULL;
ALTER TABLE zev.mieter_einheit ADD CONSTRAINT pk_mieter_einheit PRIMARY KEY (id);

COMMENT ON COLUMN zev.mieter_einheit.id IS 'Technischer Schluessel; fachlich eindeutig ist (mieter_id, einheit_id)';
