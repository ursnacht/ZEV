-- Eindeutigkeit der RFID je Mandant fuer Einheiten vom Typ LADESTATION
-- (Specs/Ladestationen.md FR-2). Der `messpunkt` einer Ladestation ist die RFID, mit der der
-- Ladevorgang gestartet wird; sie muss eindeutig sein, damit der spaetere Import aus dem
-- Lademanagement eine Menge genau einer Einheit zuordnen kann.
--
-- Bewusst PARTIELL: Ein globaler Unique-Index auf (org_id, messpunkt) ist nicht moeglich, weil
-- sich BEZUG und RUECKLIEFERUNG denselben Bilanzmesspunkt teilen duerfen (Register-Projektion
-- beim MQTT-Ingest, siehe EinheitRepository.findAllByOrgIdAndMesspunkt).
CREATE UNIQUE INDEX uk_einheit_messpunkt_ladestation
    ON zev.einheit (org_id, messpunkt)
    WHERE typ = 'LADESTATION' AND messpunkt IS NOT NULL;
