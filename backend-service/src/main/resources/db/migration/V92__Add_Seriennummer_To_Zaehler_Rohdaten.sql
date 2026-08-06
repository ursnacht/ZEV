-- Zählertausch-Erkennung (Specs/Zaehlertausch-Erkennung.md): Seriennummer des Zählers,
-- der den Rohdatensatz geliefert hat. Der Aggregations-Job erkennt einen Zählertausch am
-- Wechsel dieser Nummer zwischen Referenz- und End-Stand eines Intervalls und setzt dann eine
-- neue Baseline (kein Delta über die Tausch-Grenze) - richtungsunabhängig, also auch wenn der
-- neue Zähler HÖHER startet als der alte endete.
-- Nullable: Bestandsdaten und Payloads ohne Seriennummer laufen unverändert über den Fallback.
ALTER TABLE zev.zaehler_rohdaten
    ADD COLUMN seriennummer VARCHAR(64);

COMMENT ON COLUMN zev.zaehler_rohdaten.seriennummer IS
    'Seriennummer des liefernden Zählers (aus der Pi-Config je messpunkt); NULL = nicht gemeldet -> Tausch-Erkennung inaktiv, Fallback auf Reset-Guard';
