-- Einspeisesteuerung: Mindest-Preisabstand fuer WARTEN_AUF_TAL
-- (Specs/Einspeisesteuerung.md, FR-2)
--
-- WARUM: Die Regel prueft bisher nur, OB das erwartete Tal tiefer liegt - nicht, WIE VIEL tiefer.
-- An vier ausgewerteten Tagen (17.-20.09.2026) sperrte sie deshalb an dreien bis in den fruehen
-- Nachmittag hinein. Am 19.09. lag der aktuelle Preis um 09:45 bereits bei rund 0.010 und das Tal
-- bei 0.005: Vier weitere Stunden Sperre - mitten in der besten Sonne - fuer einen halben Rappen
-- je kWh. Das Risiko, die Batterie am Abend nicht gefuellt zu haben, stand in keinem Verhaeltnis.
--
-- MITGEFUEHRT wie schwellwert, speicherwert und soc_minimum: Ein Entscheid soll sich im
-- Nachhinein erklaeren lassen. "Nicht gesperrt, obwohl ein Tal kam" ist nur dann eine Aussage,
-- wenn dabeisteht, welcher Abstand verlangt war.
--
-- NULLABLE: Entscheide vor dieser Migration haben den Wert nicht.

ALTER TABLE zev.steuerentscheid ADD COLUMN mindest_abstand NUMERIC(10, 5);

COMMENT ON COLUMN zev.steuerentscheid.mindest_abstand IS
    'Mindestabstand in CHF/kWh, um den das erwartete Tal unter dem aktuellen Preis liegen muss, damit WARTEN_AUF_TAL greift. Leer bei Entscheiden vor V164';
