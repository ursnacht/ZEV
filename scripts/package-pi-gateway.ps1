#!/usr/bin/env pwsh
# Packt die Pi-Gateway-Software (Python-Artefakt unter pi-gateway/) in EIN ZIP zur
# Uebertragung auf den Raspberry Pi. Auf dem Pi wird das ZIP entpackt, ein venv
# angelegt und der Dienst per systemd gestartet (siehe pi-gateway/README.md,
# Abschnitt "Deployment auf dem Pi" bzw. Specs/Pi-Gateway-Software.md Anhang A).
#
# (Die ZEV-App-Images fuer das NAS baut hingegen scripts/build-nas-images.ps1.)
#
# Ausgeschlossen werden lokale/generierte Dateien und Secrets: .venv, __pycache__,
# *.egg-info, build/, dist/, .env sowie lokale Configs (config.yaml, config.sim.yaml,
# *.local.yaml). Nur die *.example-Vorlagen werden mitgeliefert.
#
# Aufruf:
#   ./scripts/package-pi-gateway.ps1                       # -> /data/ZEV/zev-pi-gateway.zip
#   ./scripts/package-pi-gateway.ps1 -OutFile C:\tmp\pi.zip
#
# Naechster Schritt: scp <OutFile> <user>@<pi-host>:/home/pi/

[CmdletBinding()]
param(
    [string]$OutFile
)

$ErrorActionPreference = 'Stop'

# Projekt-Root = uebergeordneter Ordner dieses scripts/-Verzeichnisses
$root = Split-Path -Parent $PSScriptRoot
$source = Join-Path $root 'pi-gateway'
if (-not (Test-Path $source)) {
    throw "pi-gateway/ nicht gefunden unter $source"
}

# Standard-Ausgabepfad; bei Bedarf Verzeichnis anlegen.
$outDir = '/data/ZEV'
if (-not $OutFile) {
    if (-not (Test-Path $outDir)) {
        New-Item -ItemType Directory -Path $outDir -Force | Out-Null
    }
    $OutFile = Join-Path $outDir 'zev-pi-gateway.zip'
}

# Auszuschliessende Verzeichnisse (rekursiv) und Dateien (Secrets/lokale Configs).
$excludeDirs = @('.venv', 'venv', '__pycache__', 'build', 'dist',
                 '.ruff_cache', '.pytest_cache', '.mypy_cache')
$excludeFiles = @('.env', 'config.yaml', 'config.sim.yaml')
$excludeFilePatterns = @('*.pyc', '*.local.yaml')
# Ganze Pfade, die NICHT aufs Pi gehoeren (relativ zu pi-gateway/):
#   deploy/mosquitto = Broker (laeuft auf dem NAS, Auth via MOSQUITTO_USERS) - nicht Teil des Pi-Pakets.
$excludePaths = @('deploy/mosquitto')

# In ein temporaeres Staging-Verzeichnis kopieren, dort bereinigen, dann zippen.
$staging = Join-Path ([System.IO.Path]::GetTempPath()) ("zev-pi-gateway-" + [System.IO.Path]::GetRandomFileName())
$stageGw = Join-Path $staging 'pi-gateway'
try {
    Write-Host "> Kopiere pi-gateway/ nach Staging ..." -ForegroundColor Cyan
    Copy-Item -Path $source -Destination $stageGw -Recurse -Force

    # Ausgeschlossene Verzeichnisse rekursiv entfernen (inkl. verschachtelter __pycache__).
    foreach ($d in $excludeDirs) {
        Get-ChildItem -Path $stageGw -Directory -Recurse -Force -Filter $d -ErrorAction SilentlyContinue |
            Remove-Item -Recurse -Force -ErrorAction SilentlyContinue
    }
    # *.egg-info separat (Filter unterstuetzt kein Suffix-Verzeichnismuster sauber).
    Get-ChildItem -Path $stageGw -Directory -Recurse -Force -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -like '*.egg-info' } |
        Remove-Item -Recurse -Force -ErrorAction SilentlyContinue

    # Ausgeschlossene Dateien (exakte Namen) entfernen.
    foreach ($f in $excludeFiles) {
        Get-ChildItem -Path $stageGw -File -Recurse -Force -Filter $f -ErrorAction SilentlyContinue |
            Remove-Item -Force -ErrorAction SilentlyContinue
    }
    # Ausgeschlossene Datei-Muster entfernen.
    foreach ($p in $excludeFilePatterns) {
        Get-ChildItem -Path $stageGw -File -Recurse -Force -Filter $p -ErrorAction SilentlyContinue |
            Remove-Item -Force -ErrorAction SilentlyContinue
    }
    # Ausgeschlossene Pfade (nicht aufs Pi gehoerend) entfernen.
    foreach ($rel in $excludePaths) {
        $full = Join-Path $stageGw ($rel -replace '/', [System.IO.Path]::DirectorySeparatorChar)
        if (Test-Path $full) { Remove-Item $full -Recurse -Force -ErrorAction SilentlyContinue }
    }

    if (Test-Path $OutFile) { Remove-Item $OutFile -Force }
    Write-Host "> Erstelle ZIP $OutFile ..." -ForegroundColor Cyan
    # pi-gateway/ als oberstes Verzeichnis im ZIP behalten.
    Compress-Archive -Path $stageGw -DestinationPath $OutFile -CompressionLevel Optimal

    $sizeKb = [math]::Round((Get-Item $OutFile).Length / 1KB, 1)
    Write-Host ""
    Write-Host "Fertig: $OutFile ($sizeKb KB)" -ForegroundColor Green
    Write-Host "Naechster Schritt (siehe pi-gateway/README.md):" -ForegroundColor Green
    Write-Host "  scp `"$OutFile`" <user>@<pi-host>:/home/pi/"
    Write-Host "  # auf dem Pi:  unzip zev-pi-gateway.zip  ->  venv + systemd (README/Anhang A)"
}
finally {
    if (Test-Path $staging) { Remove-Item $staging -Recurse -Force -ErrorAction SilentlyContinue }
}
