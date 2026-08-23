-- Kennzeichen an der Einheit: nimmt sie an der Nebenkostenabrechnung teil?
-- (Specs/Nebenkosten/Abrechnung.md, FR-2)
--
-- Unter den CONSUMER-Einheiten stehen auch Messpunkte, die keine Wohnung sind - Allgemeinstrom,
-- Eigenverbrauch der PV-Anlage. Bisher wurde das ueber die Mieterzuordnung geraten; das traegt
-- nicht, weil solche Messpunkte durchaus einem Mieter (dem Eigentuemer) zugeordnet sein koennen.
-- Ein eigenes Kennzeichen sagt es ausdruecklich.

ALTER TABLE zev.einheit ADD COLUMN nebenkosten_relevant BOOLEAN NOT NULL DEFAULT TRUE;

COMMENT ON COLUMN zev.einheit.nebenkosten_relevant IS
    'Zaehlt diese Einheit als Wohnung in der Nebenkostenabrechnung? Nur bei CONSUMER ausgewertet';

-- Vorbelegung aus dem, was heute bekannt ist: Als Wohnung gilt vorerst, was ein CONSUMER ist UND
-- je einem Mieter zugeordnet war. Alles andere wird abgewaehlt - Bilanzmesspunkte, Produktion und
-- Ladestationen sind keine Wohnungen, ebenso wenig ein nie vermieteter Verbraucher.
--
-- Das ist eine Startbelegung, keine Regel: Ab hier ist allein das Kennzeichen massgebend. Ein
-- Verbraucher, der einem Mieter zugeordnet ist und trotzdem keine Wohnung ist (z.B. "Allgemein"
-- auf den Eigentuemer), muss einmalig in der Einheiten-Maske abgewaehlt werden.
UPDATE zev.einheit e
SET nebenkosten_relevant = FALSE
WHERE e.typ <> 'CONSUMER'
   OR NOT EXISTS (SELECT 1 FROM zev.mieter_einheit me WHERE me.einheit_id = e.id);

INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('EINHEIT_NEBENKOSTEN_RELEVANT',
 'Zählt als Wohnung in der Nebenkostenabrechnung',
 'Counts as an apartment in the ancillary cost billing'),

('EINHEIT_NEBENKOSTEN_RELEVANT_HINT',
 'Bestimmt den Nenner der Umlage. Abwählen bei Verbrauchern, die keine Wohnung sind - etwa Allgemeinstrom oder Eigenverbrauch der PV-Anlage.',
 'Determines the denominator of the allocation. Clear it for consumers that are not an apartment, such as common-area power or the PV system''s own consumption.')
ON CONFLICT (key) DO NOTHING;
