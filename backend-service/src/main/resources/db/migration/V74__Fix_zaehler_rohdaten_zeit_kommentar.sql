-- Korrigiert den Spalten-Kommentar: 'zeit' ist die lokale Wanduhrzeit der Messung.
-- Der Pi sendet die Zeit als lokale Zeit mit Offset (ISO 8601); die lokale Wanduhrzeit
-- wird verbatim gespeichert (konsistent mit messwerte/CSV). V72 ist bereits ausgeführt
-- -> separate Migration.
COMMENT ON COLUMN zev.zaehler_rohdaten.zeit IS
    'Messzeitpunkt als lokale Wanduhrzeit (aus der lokalen Zeit mit Offset der MQTT-Nachricht)';
