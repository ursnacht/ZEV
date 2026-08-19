-- Korrektur zu V111: Der Schlüssel lag im falschen Namensraum.
--   TARIFTYP_*  = Tariftypen  (TARIFTYP_ZEV, TARIFTYP_VNB, TARIFTYP_LADESTROM, ...)
--   TYP_*       = Einheitentypen (TYP_BEZUG, TYP_LADESTATION, TYP_RUECKLIEFERUNG)
-- Die Tarifliste bildet den Schlüssel als 'TARIFTYP_' + tariftyp; mit TYP_ZUSATZ erschien in
-- der Liste deshalb der rohe Schlüssel statt des Texts.
-- V111 ist bereits ausgeführt und wird nicht nachträglich geändert (Specs/generell.md).
INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('TARIFTYP_ZUSATZ', 'Zusatzleistung', 'Additional service')
ON CONFLICT (key) DO NOTHING;

-- Der falsch platzierte Schlüssel wird nirgends verwendet und stünde sonst als irreführender
-- Rest im Einheitentyp-Namensraum.
DELETE FROM zev.translation WHERE key = 'TYP_ZUSATZ';

-- Der Hinweis unter dem Tariftyp-Dropdown nannte nur drei der inzwischen fünf Typen.
UPDATE zev.translation
   SET deutsch  = 'ZEV = Solarstrom, VNB = Netzstrom, Grundgebühr = monatlicher Festpreis, Ladestrom = manuell erfasste kWh, Zusatzleistung = frei wählbare Einheit',
       englisch = 'ZEV = solar power, VNB = grid power, basic fee = monthly flat rate, charging current = manually recorded kWh, additional service = freely selectable unit'
 WHERE key = 'TARIFTYP_HINT';
