-- Spaltenkommentar von einstrahlungsprognose.zeit an die neue Messwerte-Zeitkonvention anpassen
-- (Specs/Messwerte-Zeitkonvention.md): messwerte.zeit traegt jetzt ebenfalls den Intervall-BEGINN.
--
-- Bewusst NICHT hier: der Kommentar auf zev.messwerte.zeit. Ihn setzt das einmalige
-- Umstellungsskript scripts/messwerte-zeit-intervallbeginn.sql und erkennt daran, dass es schon
-- gelaufen ist. Setzte ihn diese Migration, verweigerte das Skript auf Hene die Umstellung.

COMMENT ON COLUMN zev.einstrahlungsprognose.zeit IS
    'BEGINN des 15-Minuten-Intervalls in ORTSZEIT (Europe/Zurich) - derselbe Bezug wie steuerentscheid.zeit_von und messwerte.zeit, ANDERS als preiszeitreihe.zeit_von (UTC)';
