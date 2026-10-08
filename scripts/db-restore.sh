#!/bin/sh
# Spielt ein Backup der ZEV-Datenbank zurueck - oder prueft es gefahrlos. Fuer das NAS (Hene).
# Anleitung: docs/Datenbank-Backup.md
#
# Aufruf:   ./scripts/db-restore.sh --nur-pruefen DATEI    (pruefen, nichts veraendern)
#           ./scripts/db-restore.sh DATEI                  (zurueckspielen, mit Nachfrage)
# Mit sudo: DOCKER="sudo docker" ./scripts/db-restore.sh DATEI
#
# Ablauf und Begruendung wie in db-restore.ps1: Backup in eine FRISCHE Datenbank spielen, dann
# zev -> zev_alt und zev_neu -> zev umbenennen. pg_restore --clean in die laufende Datenbank liesse
# bei einem aelteren Backup Tabellen neuerer Migrationen stehen, und Flyway braeche beim Start ab.

set -eu

DOCKER="${DOCKER:-docker}"
CONTAINER="${CONTAINER:-postgres}"

NUR_PRUEFEN=0
if [ "${1:-}" = "--nur-pruefen" ]; then
    NUR_PRUEFEN=1
    shift
fi
DATEI="${1:?Backup-Datei angeben}"
[ -f "$DATEI" ] || { echo "Datei nicht gefunden: $DATEI" >&2; exit 1; }

if [ "$NUR_PRUEFEN" = 1 ]; then ZIEL=zev_test; else ZIEL=zev_neu; fi

im_container() { $DOCKER exec "$CONTAINER" sh -c "$1"; }
sql_im_container() { $DOCKER exec -i "$CONTAINER" sh -c "psql -v ON_ERROR_STOP=1 -U \"\$POSTGRES_USER\" -d $1"; }

$DOCKER cp "$DATEI" "$CONTAINER:/tmp/zev-restore.dump"
trap 'im_container "rm -f /tmp/zev-restore.dump" >/dev/null 2>&1 || true' EXIT

# ---------- 1. In eine frische Datenbank spielen ----------
echo "Spiele Backup in die separate Datenbank $ZIEL ..."
im_container "dropdb -U \"\$POSTGRES_USER\" --if-exists $ZIEL && createdb -U \"\$POSTGRES_USER\" $ZIEL"
if ! im_container "pg_restore -U \"\$POSTGRES_USER\" -d $ZIEL --no-owner --single-transaction /tmp/zev-restore.dump"; then
    im_container "dropdb -U \"\$POSTGRES_USER\" --if-exists $ZIEL" || true
    echo "pg_restore ist fehlgeschlagen - die laufende Datenbank ist unveraendert." >&2
    exit 1
fi

echo
echo "Inhalt des Backups (zum Vergleich mit der laufenden Datenbank):"
sql_im_container "$ZIEL" <<'SQL'
SELECT 'messwerte' AS tabelle, count(*)::text AS zeilen FROM zev.messwerte
UNION ALL SELECT 'steuerentscheid', count(*)::text FROM zev.steuerentscheid
UNION ALL SELECT 'einheit', count(*)::text FROM zev.einheit
UNION ALL SELECT 'keycloak.user_entity', count(*)::text FROM keycloak.user_entity
UNION ALL SELECT 'flyway: letzte Version', (SELECT version FROM zev.flyway_schema_history
                                           WHERE success ORDER BY installed_rank DESC LIMIT 1);
SQL

if [ "$NUR_PRUEFEN" = 1 ]; then
    im_container "dropdb -U \"\$POSTGRES_USER\" --if-exists $ZIEL"
    echo "Backup ist lesbar. $ZIEL wurde wieder entfernt; die laufende Datenbank ist unveraendert."
    exit 0
fi

# ---------- 2. Nachfrage, dann stoppen ----------
echo
echo "ACHTUNG: Die Datenbank zev wird durch den Stand aus '$DATEI' ersetzt -"
echo "Anwendungsdaten UND Keycloak-Benutzer. Der bisherige Stand bleibt als zev_alt erhalten;"
echo "ein frueheres zev_alt wird dabei geloescht."
printf "Zum Fortfahren RESTORE eingeben: "
read -r ANTWORT
if [ "$ANTWORT" != "RESTORE" ]; then
    im_container "dropdb -U \"\$POSTGRES_USER\" --if-exists $ZIEL"
    echo "Abgebrochen. $ZIEL wurde entfernt; die laufende Datenbank ist unveraendert."
    exit 0
fi

echo "Stoppe backend-service und keycloak ..."
$DOCKER stop backend-service keycloak >/dev/null

# ---------- 3. Umbenennen - 4. starten, auch nach einem Fehler ----------
STATUS=0
echo "Tausche die Datenbanken: zev -> zev_alt, $ZIEL -> zev ..."
sql_im_container postgres >/dev/null <<SQL || STATUS=$?
SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = 'zev' AND pid <> pg_backend_pid();
DROP DATABASE IF EXISTS zev_alt;
ALTER DATABASE zev RENAME TO zev_alt;
ALTER DATABASE $ZIEL RENAME TO zev;
SQL

echo "Starte keycloak und backend-service ..."
$DOCKER start keycloak backend-service >/dev/null

if [ "$STATUS" != 0 ]; then
    echo "Umbenennen fehlgeschlagen - Stand pruefen: Datenbanken zev, zev_alt, $ZIEL." >&2
    exit "$STATUS"
fi
echo "Restore abgeschlossen. Der bisherige Stand liegt als zev_alt vor."
