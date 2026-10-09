-- Praezisiert den Spaltenkommentar aus V173: "ANDERS als preiszeitreihe.zeit_von (UTC)" liess sich
-- so lesen, als sei auch der Intervallbezug ein anderer. preiszeitreihe.zeit_von ist ebenfalls der
-- BEGINN - nur die Zeitzone unterscheidet sich. V173 ist bereits ausgefuehrt und bleibt unveraendert.
--
-- Bewusst NICHT hier: der Kommentar auf zev.messwerte.zeit (siehe V173).

COMMENT ON COLUMN zev.einstrahlungsprognose.zeit IS
    'BEGINN des 15-Minuten-Intervalls in ORTSZEIT (Europe/Zurich) - derselbe Bezug wie steuerentscheid.zeit_von und messwerte.zeit. Auch preiszeitreihe.zeit_von ist der BEGINN, aber in UTC';
