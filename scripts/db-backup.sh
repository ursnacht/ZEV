#!/bin/sh
# Sichert die ZEV-Datenbank (Schemas zev UND keycloak) - fuer das NAS (Hene).
# Anleitung: docs/Datenbank-Backup.md
#
# Aufruf:   ./scripts/db-backup.sh [ZIELVERZEICHNIS]
# Mit sudo: DOCKER="sudo docker" ./scripts/db-backup.sh /volume1/backup/zev
#
# Danach die Datei vom NAS herunterkopieren: Ein Backup auf derselben Platte schuetzt nicht gegen
# deren Ausfall.

set -eu

DOCKER="${DOCKER:-docker}"
CONTAINER="${CONTAINER:-postgres}"
ZIEL="${1:-.}"
DATEI="$ZIEL/zev-$(date +%Y-%m-%d_%H%M).dump"

echo "Sichere Datenbank zev aus Container '$CONTAINER' ..."
# Einfache Anfuehrungszeichen: $POSTGRES_USER wird im Container aufgeloest, nicht hier.
$DOCKER exec "$CONTAINER" sh -c 'pg_dump -U "$POSTGRES_USER" -d zev -Fc -f /tmp/zev-backup.dump'

trap '$DOCKER exec "$CONTAINER" rm -f /tmp/zev-backup.dump >/dev/null 2>&1 || true' EXIT
$DOCKER cp "$CONTAINER:/tmp/zev-backup.dump" "$DATEI"

echo "Backup geschrieben: $DATEI ($(du -h "$DATEI" | cut -f1))"
echo "Pruefen ohne Risiko:  ./scripts/db-restore.sh --nur-pruefen '$DATEI'"
