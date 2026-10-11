-- Reihenfolge der Tarife (Specs/Tarifverwaltung.md, FR-4): bestimmt die Reihenfolge der Zeilen
-- auf der ZEV-Rechnung, ueber alle Tariftypen hinweg. Je Mandant fortlaufend.
--
-- Startwert = die bisherige Reihenfolge auf der Rechnung: ZEV, VNB, Tarifpositionen (LADESTROM,
-- ZUSATZ), Grundgebuehr; innerhalb eines Typs nach Beginn und Bezeichnung. Bestehende Rechnungen
-- sehen nach der Migration also gleich aus.

ALTER TABLE zev.tarif ADD COLUMN reihenfolge INTEGER;

UPDATE zev.tarif t
   SET reihenfolge = r.nr
  FROM (SELECT id,
               ROW_NUMBER() OVER (
                   PARTITION BY org_id
                   ORDER BY CASE tariftyp
                                WHEN 'ZEV' THEN 1
                                WHEN 'VNB' THEN 2
                                WHEN 'LADESTROM' THEN 3
                                WHEN 'ZUSATZ' THEN 4
                                WHEN 'GRUNDGEBUEHR' THEN 5
                                ELSE 6
                            END,
                            gueltig_von, bezeichnung, id) AS nr
          FROM zev.tarif) r
 WHERE t.id = r.id;

ALTER TABLE zev.tarif ALTER COLUMN reihenfolge SET NOT NULL;
ALTER TABLE zev.tarif ALTER COLUMN reihenfolge SET DEFAULT 0;

COMMENT ON COLUMN zev.tarif.reihenfolge IS
    'Reihenfolge der Zeilen auf der ZEV-Rechnung (aufsteigend, ueber alle Tariftypen, je Mandant); per Drag & Drop in der Tarifverwaltung';

INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('TARIFE_ZUKUENFTIG', 'Zukünftige Tarife', 'Future tariffs'),
('TARIFE_AKTUELLES_JAHR', 'Tarife', 'Tariffs'),
('TARIFE_ALT', 'Frühere Tarife', 'Past tariffs'),
('KEINE_TARIFE_IN_GRUPPE', 'Keine Tarife', 'No tariffs'),
('TARIF_VERSCHIEBEN', 'Tarif verschieben', 'Move tariff'),
('TARIF_REIHENFOLGE_HINWEIS', 'Die Reihenfolge bestimmt die Reihenfolge der Positionen auf der Rechnung. Zum Ändern einen Tarif am Griff ziehen.', 'The order determines the order of the lines on the invoice. Drag a tariff by its handle to change it.'),
('TARIF_REIHENFOLGE_GESPEICHERT', 'Reihenfolge gespeichert', 'Order saved'),
('FEHLER_REIHENFOLGE_TARIF', 'Die Reihenfolge konnte nicht gespeichert werden', 'The order could not be saved')
ON CONFLICT (key) DO NOTHING;
