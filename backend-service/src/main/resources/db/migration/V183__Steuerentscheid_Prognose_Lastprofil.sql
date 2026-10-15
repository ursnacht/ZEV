-- Lastprofil im Steuerentscheid (Specs/Ladeplanung.md, FR-5): Mit gti, prognose_faktor und
-- prognose_lastprofil laesst sich der gespeicherte prognose_ueberschuss nachrechnen,
-- max(0, gti * prognose_faktor - prognose_lastprofil). Vor allem macht die Spalte den Fehler der
-- Verbrauchsprognose fuer JEDES Intervall messbar - bisher nur dort, wo ein Ueberschuss erwartet war.
-- Nullable: beim Rueckfall und fuer Entscheide vor V183 leer.

ALTER TABLE zev.steuerentscheid ADD COLUMN prognose_lastprofil NUMERIC(12,3);

COMMENT ON COLUMN zev.steuerentscheid.prognose_lastprofil IS
    'Erwarteter Verbrauch (Lastprofil, Median der gleichen Wochentage) dieses Intervalls in kWh zum Zeitpunkt des Entscheids; NULL beim Rueckfall und vor V183';
