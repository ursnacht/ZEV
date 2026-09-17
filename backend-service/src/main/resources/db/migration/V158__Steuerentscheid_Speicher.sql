-- Einspeisesteuerung: gemessene Lade- und Entlademenge des Speichers im Entscheid
-- (Specs/Einspeisesteuerung.md, FR-3 und FR-5b)
--
-- WARUM ZUSAETZLICH ZUR BILANZDIFFERENZ (V149): Dort wurde der Netto-Batteriefluss aus den vier
-- Bilanzkomponenten ERRECHNET, weil es keine Einheit vom Typ SPEICHER gab. Dieses Residuum enthaelt
-- alles, was sonst nicht gemessen ist - nicht erfasste Verbraucher eingeschlossen. Inzwischen gibt
-- es den Typ SPEICHER mit eigenem Zaehler: Hier stehen die GEMESSENEN Mengen. Beide Angaben
-- nebeneinander machen sichtbar, wie weit Rechnung und Messung auseinanderliegen.
--
-- GETRENNT und nicht als Saldo: total ist beim Speicher ΔLadung − ΔEntladung. Ein Saldo von 0 kann
-- "nichts passiert" heissen oder "2 kWh rein, 2 kWh raus" - fuer den Wirkungsgrad und fuer die
-- Verrechnung gegen die Produktion ist das ein Unterschied.
--
-- PRAEFIX speicher_: Die Tabelle hat bereits eine Spalte "batterieladung" - das ist der ZUSTAND
-- (FREI/GESPERRT), nicht eine Menge. Eine Spalte "ladung" daneben waere beim Lesen einer Abfrage
-- kaum auseinanderzuhalten.
--
-- NULLABLE wie bezug/ruecklieferung: Entscheide vor dieser Migration haben die Werte nicht, und ein
-- Mandant ohne Speicher-Einheit hat sie nie. Eine 0 hiesse "Speicher stand still" statt "kein
-- Speicher gemessen" - genau die Verwechslung, die bei der Bilanzdifferenz Zeit gekostet hat.

ALTER TABLE zev.steuerentscheid ADD COLUMN speicher_ladung    NUMERIC(12, 3);
ALTER TABLE zev.steuerentscheid ADD COLUMN speicher_entladung NUMERIC(12, 3);

COMMENT ON COLUMN zev.steuerentscheid.speicher_ladung IS
    'Gemessene Ladung der SPEICHER-Einheit im Intervall in kWh (positiver Anteil von total). Leer, wenn kein Speicher erfasst ist';
COMMENT ON COLUMN zev.steuerentscheid.speicher_entladung IS
    'Gemessene Entladung der SPEICHER-Einheit im Intervall in kWh, als Betrag (negativer Anteil von total). Leer, wenn kein Speicher erfasst ist';
