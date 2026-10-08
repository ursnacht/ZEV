<#
.SYNOPSIS
    Spielt ein Backup der ZEV-Datenbank zurueck - oder prueft es gefahrlos.

.DESCRIPTION
    Anleitung: docs/Datenbank-Backup.md

    ABLAUF (ohne -NurPruefen):
      1. Backup in eine NEUE Datenbank zev_neu spielen. Schlaegt das fehl, ist nichts veraendert.
      2. Nachfrage. Dann backend-service und keycloak stoppen.
      3. Umbenennen: zev -> zev_alt, zev_neu -> zev.
      4. keycloak und backend-service starten.
    Der bisherige Stand bleibt als zev_alt erhalten (Rueckweg: siehe Anleitung).

    WARUM NICHT pg_restore --clean IN DIE LAUFENDE DATENBANK: --clean loescht nur, was im Backup
    vorkommt. Bei einem AELTEREN Backup blieben Tabellen neuerer Migrationen stehen, waehrend die
    Migrationsliste auf den alten Stand zurueckginge - Flyway versuchte sie beim Start neu anzulegen
    und braeche ab. Eine frische Datenbank kennt dieses Problem nicht.

    Mit -NurPruefen wird nur Schritt 1 ausgefuehrt (in zev_test), einige Zeilen werden gezaehlt,
    und zev_test wird wieder geloescht. Die laufende Datenbank bleibt unberuehrt.

    Alle Befehle an den Container kommen OHNE doppelte Anfuehrungszeichen aus: Windows PowerShell 5.1
    gibt sie in Argumenten an native Programme fehlerhaft weiter. SQL geht ueber die Standardeingabe.

.PARAMETER Datei
    Die Backup-Datei (aus db-backup.ps1).

.PARAMETER NurPruefen
    Nicht zurueckspielen, sondern nur pruefen, ob das Backup lesbar und vollstaendig ist.

.EXAMPLE
    .\scripts\db-restore.ps1 -Datei .\zev-2026-10-08_0930.dump -NurPruefen
    .\scripts\db-restore.ps1 -Datei .\zev-2026-10-08_0930.dump
#>
param(
    [Parameter(Mandatory = $true)][string]$Datei,
    [switch]$NurPruefen,
    [string]$Container = "postgres"
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path $Datei)) { throw "Datei nicht gefunden: $Datei" }

function Pruefe([string]$schritt) {
    if ($LASTEXITCODE -ne 0) { throw "$schritt ist fehlgeschlagen." }
}

# $POSTGRES_USER in EINFACHEN Anfuehrungszeichen: wird im Container aufgeloest, nicht hier.
function ImContainer([string]$befehl) {
    docker exec $Container sh -c $befehl
}
function SqlImContainer([string]$datenbank, [string]$sql) {
    $sql | docker exec -i $Container sh -c ('psql -v ON_ERROR_STOP=1 -U $POSTGRES_USER -d ' + $datenbank)
}

$ziel = if ($NurPruefen) { 'zev_test' } else { 'zev_neu' }

docker cp $Datei "${Container}:/tmp/zev-restore.dump"; Pruefe "docker cp"

try {
    # ---------- 1. In eine frische Datenbank spielen ----------
    Write-Host "Spiele Backup in die separate Datenbank $ziel ..."
    ImContainer ('dropdb -U $POSTGRES_USER --if-exists ' + $ziel + ' && createdb -U $POSTGRES_USER ' + $ziel)
    Pruefe "createdb $ziel"
    ImContainer ('pg_restore -U $POSTGRES_USER -d ' + $ziel + ' --no-owner --single-transaction /tmp/zev-restore.dump')
    if ($LASTEXITCODE -ne 0) {
        ImContainer ('dropdb -U $POSTGRES_USER --if-exists ' + $ziel) | Out-Null
        throw "pg_restore ist fehlgeschlagen - die laufende Datenbank ist unveraendert."
    }

    Write-Host ""
    Write-Host "Inhalt des Backups (zum Vergleich mit der laufenden Datenbank):"
    SqlImContainer $ziel @'
SELECT 'messwerte' AS tabelle, count(*)::text AS zeilen FROM zev.messwerte
UNION ALL SELECT 'steuerentscheid', count(*)::text FROM zev.steuerentscheid
UNION ALL SELECT 'einheit', count(*)::text FROM zev.einheit
UNION ALL SELECT 'keycloak.user_entity', count(*)::text FROM keycloak.user_entity
UNION ALL SELECT 'flyway: letzte Version', (SELECT version FROM zev.flyway_schema_history
                                           WHERE success ORDER BY installed_rank DESC LIMIT 1);
'@
    Pruefe "Zaehlung"

    if ($NurPruefen) {
        ImContainer ('dropdb -U $POSTGRES_USER --if-exists ' + $ziel) | Out-Null
        Write-Host "Backup ist lesbar. $ziel wurde wieder entfernt; die laufende Datenbank ist unveraendert."
        return
    }

    # ---------- 2. Nachfrage, dann stoppen ----------
    Write-Host ""
    Write-Host "ACHTUNG: Die Datenbank zev wird durch den Stand aus '$Datei' ersetzt -" -ForegroundColor Yellow
    Write-Host "Anwendungsdaten UND Keycloak-Benutzer. Der bisherige Stand bleibt als zev_alt erhalten;" -ForegroundColor Yellow
    Write-Host "ein frueheres zev_alt wird dabei geloescht." -ForegroundColor Yellow
    $antwort = Read-Host "Zum Fortfahren RESTORE eingeben"
    if ($antwort -cne "RESTORE") {
        ImContainer ('dropdb -U $POSTGRES_USER --if-exists ' + $ziel) | Out-Null
        Write-Host "Abgebrochen. $ziel wurde entfernt; die laufende Datenbank ist unveraendert."
        return
    }

    Write-Host "Stoppe backend-service und keycloak ..."
    docker stop backend-service keycloak | Out-Null; Pruefe "docker stop"

    try {
        # ---------- 3. Umbenennen ----------
        # Verbindungen zu zev beenden - ein Umbenennen ist sonst nicht moeglich (z. B. offene
        # Werkzeuge). Schlaegt das erste Umbenennen fehl, ist nichts veraendert.
        Write-Host "Tausche die Datenbanken: zev -> zev_alt, $ziel -> zev ..."
        SqlImContainer 'postgres' @"
SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = 'zev' AND pid <> pg_backend_pid();
DROP DATABASE IF EXISTS zev_alt;
ALTER DATABASE zev RENAME TO zev_alt;
ALTER DATABASE $ziel RENAME TO zev;
"@ | Out-Null
        Pruefe "Umbenennen (Stand pruefen: Datenbanken zev, zev_alt, $ziel)"
        Write-Host "Restore abgeschlossen. Der bisherige Stand liegt als zev_alt vor."
    } finally {
        # ---------- 4. Starten - auch nach einem Fehler ----------
        Write-Host "Starte keycloak und backend-service ..."
        docker start keycloak backend-service | Out-Null
    }
} finally {
    ImContainer 'rm -f /tmp/zev-restore.dump' | Out-Null
}
