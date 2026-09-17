-- Systemmeldungen des Gerätezustands (Specs/Gerätezustand.md, FR-3)
--
-- ZWEI KEYS, nicht einer: SystemmeldungService.erfasse dedupliziert nach (orgId, meldungKey) und
-- ueberschreibt dabei den Parameter des offenen Eintrags. Unter einem gemeinsamen Key zeigte die
-- Meldung abwechselnd den Bereichs- und den Typfehler - zwei Ursachen, die verschiedene
-- Gegenmassnahmen verlangen: einmal die Skalierung im Pi, einmal die Zuordnung der Einheit.
--
-- KEIN Auto-Resolve fuer beide: Sie dokumentieren ein vergangenes Ereignis und werden vom
-- Betreiber als erledigt markiert - dieselbe Regel wie bei MQTT_ZAEHLER_LUECKE
-- (Specs/MQTT-Integration.md, FR-8). Ein einzelner Ausreisser schloesse die Meldung sonst wieder,
-- bevor sie jemand gesehen hat.

INSERT INTO zev.translation (key, deutsch, englisch) VALUES

('GERAETEZUSTAND_WERT_UNGUELTIG',
 'Zustandswert ausserhalb des erlaubten Bereichs oder nicht als Zahl lesbar – der Wert wurde verworfen, die Zählerstände derselben Meldung aber gespeichert. Häufigste Ursache ist eine falsche Skalierung am Messpunkt (Einheit, Grösse, Wert, erlaubter Bereich):',
 'State value outside the permitted range or not readable as a number – the value was discarded, but the meter readings of the same message were stored. The most common cause is an incorrect scaling factor at the measuring point (unit, quantity, value, permitted range):'),

('GERAETEZUSTAND_TYP_UNGUELTIG',
 'Zustandswert an einer Einheit gemeldet, die diese Grösse nicht führt – der Wert wurde verworfen. Ein Ladezustand gehört an einen Speicher; die Zuordnung des Messpunkts ist zu prüfen (Einheit, Typ, Grösse):',
 'State value reported for a unit that does not carry this quantity – the value was discarded. A state of charge belongs to a storage unit; check the assignment of the measuring point (unit, type, quantity):')

ON CONFLICT (key) DO NOTHING;
