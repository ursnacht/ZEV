-- Neuer Tariftyp ZUSATZ mit frei waehlbarer Mengeneinheit (Specs/Tarifpositionen.md).
-- Der CHECK-Constraint zaehlt die erlaubten Werte explizit auf (zuletzt V102) und muss bei
-- jedem neuen TarifTyp mitgezogen werden - sonst scheitert das Anlegen mit
-- DataIntegrityViolationException. VARCHAR(20) reicht fuer 'ZUSATZ'.
ALTER TABLE zev.tarif DROP CONSTRAINT IF EXISTS tarif_tariftyp_check;
ALTER TABLE zev.tarif ADD CONSTRAINT tarif_tariftyp_check
    CHECK (tariftyp IN ('ZEV', 'VNB', 'GRUNDGEBUEHR', 'LADESTROM', 'ZUSATZ'));

COMMENT ON COLUMN zev.tarif.tariftyp IS 'ZEV = Eigenverbrauch, VNB = Netzbezug, GRUNDGEBUEHR = Festpreis pro Zaehler/Monat, LADESTROM = manuell erfasste Ladestrom-Positionen, ZUSATZ = frei konfigurierbare Zusatzleistung mit eigener Mengeneinheit';

-- Mengeneinheit: nur fuer ZUSATZ gefuellt. Bei allen anderen Typen ergibt sie sich aus dem Typ
-- (TarifTyp.mengeneinheit()), deshalb nullable und bewusst OHNE NOT NULL-Constraint. Die Pflicht
-- fuer ZUSATZ prueft der TarifService - so steht die Regel an einer Stelle statt an zweien.
ALTER TABLE zev.tarif ADD COLUMN mengeneinheit VARCHAR(10);

ALTER TABLE zev.tarif ADD CONSTRAINT ck_tarif_mengeneinheit
    CHECK (mengeneinheit IS NULL OR mengeneinheit IN ('KWH', 'MONAT', 'STUECK'));

COMMENT ON COLUMN zev.tarif.mengeneinheit IS 'Mengeneinheit eines ZUSATZ-Tarifs (KWH, MONAT, STUECK); bei allen anderen Tariftypen NULL, dort folgt die Einheit aus dem Typ';

-- Uebersetzungen
INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('TYP_ZUSATZ', 'Zusatzleistung', 'Additional service'),
('MENGENEINHEIT', 'Mengeneinheit', 'Unit of measure'),
('MENGENEINHEIT_HINT',
 'Bestimmt, worin die Menge erfasst und auf der Rechnung ausgewiesen wird.',
 'Determines how the quantity is recorded and shown on the invoice.'),
('STUECK', 'Stück', 'Pcs'),
('TARIFPOSITION_MENGE_HINT_STUECK',
 'Anzahl; auf der Rechnung wird auf ganze Stück gerundet.',
 'Quantity; rounded to whole pieces on the invoice.'),
('TARIFPOSITION_NUR_LADESTATIONEN', 'Nur Ladestationen', 'Charging stations only'),
('TARIFPOSITION_NUR_LADESTATIONEN_HINT',
 'Abwählen, um Positionen auch für Wohnungen zu erfassen. Dort sind ausschliesslich Tarife vom Typ Zusatzleistung zulässig - der Normalfall bleibt die Ladestation.',
 'Uncheck to record positions for apartments as well. Only tariffs of type additional service are allowed there - the normal case remains the charging station.'),
('FEHLER_MENGENEINHEIT_ERFORDERLICH',
 'Für diesen Tariftyp ist die Mengeneinheit erforderlich',
 'The unit of measure is required for this tariff type')
ON CONFLICT (key) DO NOTHING;
