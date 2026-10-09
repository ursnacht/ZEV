-- Einmalige Umstellung der MQTT-Messwerte auf den Intervall-BEGINN.
-- Spec: Specs/Messwerte-Zeitkonvention.md (FR-5, NFR-3)
--
-- Bis zu dieser Umstellung stempelte die MQTT-Live-Erfassung messwerte.zeit mit dem ENDE des
-- 15-Minuten-Intervalls, der CSV-Import mit dem BEGINN. Das Skript verschiebt alle Zeilen mit
-- quelle = 'MQTT' um 15 Minuten zurueck. Es ist KEINE Flyway-Migration: Betroffen sind nur die
-- Bestandsdaten von Hene; neue Installationen erfassen von Anfang an mit dem Beginn.
--
-- NUR AUSFUEHREN, SOLANGE DIE ALTE VERSION DIE MESSWERTE GESCHRIEBEN HAT.
-- Laeuft das Skript nach dem Start der neuen Version, verschiebt es auch deren bereits richtige
-- Stempel - ohne Meldung. Das Skript erkennt das nicht; der Schutz ist die Reihenfolge:
--
--   0. Vorher, bei laufendem Betrieb: Images der neuen Version bauen, uebertragen und laden
--      (docs/Deploy-Hene.md, Schritte 1 und 2 "Images laden").
--   1. Alte Version anhalten:
--        sudo docker stop backend-service
--   2. Backup ziehen und pruefen (docs/Datenbank-Backup.md):
--        DOCKER="sudo docker" ./scripts/db-backup.sh /volume1/backup/zev
--        DOCKER="sudo docker" ./scripts/db-restore.sh --nur-pruefen /volume1/backup/zev/zev-<JJJJ-MM-TT_HHMM>.dump
--   3. Dieses Skript ausfuehren (im Verzeichnis /volume1/docker/zev):
--        sudo docker exec -i postgres sh -c 'psql -v ON_ERROR_STOP=1 -U $POSTGRES_USER -d zev' \
--          < scripts/messwerte-zeit-intervallbeginn.sql
--      Erwartet: NOTICE "<n> MQTT-Messwerte auf den Intervallbeginn umgestellt", n gleich
--        SELECT count(*) FROM zev.messwerte WHERE quelle = 'MQTT';
--   4. Neue Version starten:
--        sudo docker compose up -d
--
-- Das Skript ist EIN DO-Block und damit atomar: Bricht es ab, ist nichts veraendert. Es bricht ab,
--   - wenn es schon einmal gelaufen ist (Spaltenkommentar gesetzt), oder
--   - wenn eine MQTT-Zeile auf eine Zeile anderer Quelle derselben Einheit fiele.
--
-- Keine psql-Metabefehle und kein BEGIN/COMMIT: Der Integrationstest fuehrt die Datei unveraendert
-- per JDBC aus (MesswerteZeitUmstellungSkriptIT).

DO $$
DECLARE
    kollisionen bigint;
    beispiel    text;
    verschoben  bigint;
BEGIN
    -- 1. Schon ausgeführt? Das Skript setzt am Ende den Spaltenkommentar.
    IF col_description('zev.messwerte'::regclass,
           (SELECT attnum FROM pg_attribute
             WHERE attrelid = 'zev.messwerte'::regclass AND attname = 'zeit')) LIKE 'Beginn%' THEN
        RAISE EXCEPTION 'Bereits umgestellt: zev.messwerte.zeit trägt schon den Intervallbeginn';
    END IF;

    -- 2. Kollision: Eine MQTT-Zeile landet auf einer Zeile, die NICHT mitwandert.
    --    (MQTT gegen MQTT kann nicht kollidieren — alle wandern gemeinsam.)
    SELECT count(*),
           min(format('Einheit %s, %s', b.einheit_id, to_char(b.zeit, 'YYYY-MM-DD HH24:MI')))
      INTO kollisionen, beispiel
      FROM zev.messwerte a
      JOIN zev.messwerte b
        ON b.einheit_id = a.einheit_id
       AND b.zeit = a.zeit - INTERVAL '15 minutes'
       AND b.quelle <> 'MQTT'
     WHERE a.quelle = 'MQTT';
    IF kollisionen > 0 THEN
        RAISE EXCEPTION '% MQTT-Messwerte fielen auf bestehende Nicht-MQTT-Werte, z. B. %',
            kollisionen, beispiel;
    END IF;

    -- 3. Umstellen
    UPDATE zev.messwerte SET zeit = zeit - INTERVAL '15 minutes' WHERE quelle = 'MQTT';
    GET DIAGNOSTICS verschoben = ROW_COUNT;

    COMMENT ON COLUMN zev.messwerte.zeit IS
        'Beginn des 15-Minuten-Intervalls, Ortszeit Europe/Zurich ohne Zone';

    RAISE NOTICE '% MQTT-Messwerte auf den Intervallbeginn umgestellt', verschoben;
END $$;
