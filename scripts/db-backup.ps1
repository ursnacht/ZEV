<#
.SYNOPSIS
    Sichert die ZEV-Datenbank (Schemas zev UND keycloak) in eine Datei.

.DESCRIPTION
    Anleitung: docs/Datenbank-Backup.md

    Der Dump wird IM Container geschrieben und mit "docker cp" herauskopiert - nicht ueber eine
    Umleitung ">". Windows PowerShell 5.1 wandelt umgeleitete Ausgabe in Text um; die Binaerdatei
    waere unbrauchbar, ohne dass ein Fehler erscheint.

.PARAMETER Ziel
    Verzeichnis fuer die Backup-Datei. Vorgabe: aktuelles Verzeichnis.

.PARAMETER Container
    Name des Postgres-Containers. Vorgabe: postgres

.EXAMPLE
    .\scripts\db-backup.ps1 -Ziel D:\Backups\zev
#>
param(
    [string]$Ziel = ".",
    [string]$Container = "postgres"
)

$ErrorActionPreference = 'Stop'

$datei = Join-Path $Ziel ("zev-" + (Get-Date -Format "yyyy-MM-dd_HHmm") + ".dump")

Write-Host "Sichere Datenbank zev aus Container '$Container' ..."
# Einfache Anfuehrungszeichen: $POSTGRES_USER wird im Container aufgeloest, nicht hier.
docker exec $Container sh -c 'pg_dump -U $POSTGRES_USER -d zev -Fc -f /tmp/zev-backup.dump'
if ($LASTEXITCODE -ne 0) { throw "pg_dump ist fehlgeschlagen." }

try {
    docker cp "${Container}:/tmp/zev-backup.dump" $datei
    if ($LASTEXITCODE -ne 0) { throw "docker cp ist fehlgeschlagen." }
} finally {
    docker exec $Container rm -f /tmp/zev-backup.dump | Out-Null
}

$groesse = [math]::Round((Get-Item $datei).Length / 1MB, 1)
Write-Host "Backup geschrieben: $datei ($groesse MB)"
Write-Host "Pruefen ohne Risiko:  .\scripts\db-restore.ps1 -Datei '$datei' -NurPruefen"
