-- Uebersetzungen der Einspeisesteuerung (Specs/Einspeisesteuerung.md, FR-9).
--
-- Die Steuerung schaltet nichts - der Hinweistext sagt das ausdruecklich, damit niemand das
-- Protokoll fuer eine Aufzeichnung tatsaechlicher Schaltvorgaenge haelt.
--
-- NACHTRAEGLICH GEAENDERT (13.09.2026, ausnahmsweise auf Anweisung des Users): PRODUKTION und
-- VERBRAUCH fehlten. Ich hatte beim Schreiben der Ansicht angenommen, dass es die beiden
-- allgemeinen Keys laengst gibt - tatsaechlich existierten nur PRODUKTION_TOTAL ("Produktion
-- (Total)", eine Monatssumme) und NK_ART_VERBRAUCH (eine Nebenkosten-Positionsart). Beide passen
-- nicht: Der erste traegt einen Zusatz, den die Diagrammlegende nicht will, der zweite koppelte
-- die Steuerungsansicht an ein fachfremdes Feature.
--
-- Der Eintrag in flyway_schema_history wurde dafuer entfernt; die Migration laeuft komplett neu.
-- Dank ON CONFLICT (key) DO NOTHING ist das folgenlos - bereits eingefuegte Keys bleiben, wie sie
-- sind, auch wenn sie inzwischen ueber die Uebersetzungsverwaltung angepasst wurden.

INSERT INTO zev.translation (key, deutsch, englisch) VALUES

('EINSPEISESTEUERUNG',
 'Einspeisesteuerung',
 'Feed-in control'),

('FEATURE_FLAG_EINSPEISESTEUERUNG',
 'Einspeisesteuerung (Trockenlauf)',
 'Feed-in control (dry run)'),

('STEUERUNG_TROCKENLAUF_HINWEIS',
 'Diese Ansicht zeigt, was die Steuerung tun würde. Es wird nichts geschaltet.',
 'This view shows what the control system would do. Nothing is actually switched.'),

('STEUERUNG_SCHWELLWERT',
 'Schwellwert',
 'Threshold'),

('STEUERUNG_SCHWELLWERT_HINWEIS',
 'Fällt der tiefste erwartete Preis des Resttages darunter, wird die Batterieladung für das Preistal freigehalten. In CHF/kWh; negative Werte sind erlaubt.',
 'If the lowest expected price for the rest of the day falls below this, battery charging is held back for the price trough. In CHF/kWh; negative values are allowed.'),

('STEUERUNG_SPEICHERWERT',
 'Wert einer gespeicherten kWh',
 'Value of a stored kWh'),

('STEUERUNG_SPEICHERWERT_HINWEIS',
 'Liegt die Einspeisevergütung darüber, lohnt sich Einspeisen mehr als Speichern. Vorgabe 0.31 = vermiedener Netzbezug abzüglich Lade- und Entladeverlusten.',
 'If the feed-in tariff exceeds this, feeding in beats storing. Default 0.31 = avoided grid supply less charging and discharging losses.'),

('STEUERUNG_BATTERIEKAPAZITAET',
 'Batteriekapazität',
 'Battery capacity'),

('STEUERUNG_BATTERIEKAPAZITAET_HINWEIS',
 'Nutzbare Kapazität in kWh. Wird derzeit von keiner Regel ausgewertet und dient der Dokumentation.',
 'Usable capacity in kWh. Currently not evaluated by any rule; kept for documentation.'),

('STEUERUNG_NACHRECHNEN',
 'Nachrechnen',
 'Recalculate'),

('STEUERUNG_BATTERIELADUNG',
 'Batterieladung',
 'Battery charging'),

('STEUERUNG_EINSPEISUNG',
 'Einspeisung',
 'Feed-in'),

('STEUERUNG_FREI',
 'Frei',
 'Enabled'),

('STEUERUNG_GESPERRT',
 'Gesperrt',
 'Blocked'),

('STEUERUNG_UEBERSCHUSS',
 'Überschuss',
 'Surplus'),

('STEUERUNG_TIEFSTPREIS_REST',
 'Tiefstpreis heute noch',
 'Lowest price remaining today'),

('STEUERUNG_REGEL',
 'Regel',
 'Rule'),

('STEUERUNG_REGEL_PREIS_NEGATIV',
 'Preis negativ — nicht einspeisen',
 'Negative price — no feed-in'),

('STEUERUNG_REGEL_KEIN_UEBERSCHUSS',
 'Kein Überschuss',
 'No surplus'),

('STEUERUNG_REGEL_EINSPEISEN_LOHNT',
 'Einspeisen lohnt mehr als speichern',
 'Feed-in beats storage'),

('STEUERUNG_REGEL_WARTEN_AUF_TAL',
 'Auf günstigeres Intervall warten',
 'Waiting for a cheaper interval'),

('STEUERUNG_REGEL_LADEN',
 'Laden',
 'Charge'),

('STEUERUNG_KEINE_ENTSCHEIDE',
 'Für diesen Tag liegen keine Entscheide vor',
 'No decisions for this day'),

('STEUERUNG_ENERGIE_VERSCHOBEN',
 'Verschobene Energie (kWh)',
 'Energy redirected (kWh)'),

('STEUERUNG_SIMULATION_ERGEBNIS',
 'Ergebnis der Rückrechnung',
 'Recalculation result'),

('STEUERUNG_TAGE',
 'Ausgewertete Tage',
 'Days evaluated'),

('STEUERUNG_INTERVALLE',
 'Intervalle mit Überschuss',
 'Intervals with surplus'),

('STEUERUNG_STUNDEN_LADUNG_GESPERRT',
 'Stunden mit gesperrter Ladung',
 'Hours with charging blocked'),

('STEUERUNG_STUNDEN_EINSPEISUNG_GESPERRT',
 'Stunden mit gesperrter Einspeisung',
 'Hours with feed-in blocked'),

('STEUERUNG_FEHLER_LADEN',
 'Die Entscheide konnten nicht geladen werden',
 'The decisions could not be loaded'),

('STEUERUNG_FEHLER_SIMULATION',
 'Die Rückrechnung ist fehlgeschlagen',
 'The recalculation failed'),

-- Allgemeine Mengenbezeichnungen fuer Diagrammlegende und Tabellenkopf. Bewusst ohne Praefix:
-- Sie beschreiben die Groesse selbst und sind damit auch ausserhalb der Einspeisesteuerung
-- brauchbar.
('PRODUKTION',
 'Produktion',
 'Production'),

('VERBRAUCH',
 'Verbrauch',
 'Consumption')

ON CONFLICT (key) DO NOTHING;
