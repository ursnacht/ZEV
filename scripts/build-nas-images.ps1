#!/usr/bin/env pwsh
# Baut die drei ZEV-App-Images (backend-, admin-, frontend-service) fuer das NAS
# (Default linux/amd64) und exportiert sie als eine gzip-komprimierte tar-Datei.
#
# Aufruf:
#   ./scripts/build-nas-images.ps1                                  # linux/amd64, Tag "amd64"
#   ./scripts/build-nas-images.ps1 -Platform linux/arm64 -Tag arm64 # ARM-NAS
#   ./scripts/build-nas-images.ps1 -OutFile C:\tmp\zev.tar.gz
#
# Voraussetzungen, Uebertragung aufs NAS, Laden/Starten und die Registry-Alternative:
# siehe docs/NAS-Images.md (massgebliche Anleitung). Der Raspberry Pi faehrt nur den
# pi-gateway (separat: scripts/package-pi-gateway.ps1), nicht den App-Stack.

[CmdletBinding()]
param(
    [string]$Platform = 'linux/amd64',
    [string]$Tag = 'amd64',
    [string]$OutFile
)

$ErrorActionPreference = 'Stop'

# Projekt-Root = uebergeordneter Ordner dieses scripts/-Verzeichnisses
$root = Split-Path -Parent $PSScriptRoot

# Standard-Ausgabeverzeichnis; bei Bedarf anlegen.
$outDir = '/data/ZEV'
if (-not $OutFile) {
    if (-not (Test-Path $outDir)) {
        New-Item -ItemType Directory -Path $outDir -Force | Out-Null
    }
    $OutFile = Join-Path $outDir "zev-images-$Tag.tar.gz"
}

# Service -> Build-Kontext-Verzeichnis. Image-Name = zev-<service>:<Tag>.
$services = @('backend-service', 'admin-service', 'frontend-service')

Push-Location $root
try {
    $images = @()
    foreach ($svc in $services) {
        $image = "zev-$svc`:$Tag"
        $images += $image
        Write-Host ""
        Write-Host "> Baue $image fuer $Platform ..." -ForegroundColor Cyan
        & docker buildx build --platform $Platform -t $image --load "./$svc"
        if ($LASTEXITCODE -ne 0) {
            throw "Build von $image fehlgeschlagen (Exit-Code $LASTEXITCODE)."
        }
    }

    # Export: docker save schreibt ein tar; anschliessend per .NET GzipStream
    # komprimieren (kein externes gzip noetig). Das Ergebnis laesst sich auf dem Pi
    # direkt via `docker load -i <datei>` bzw. `gunzip -c <datei> | docker load` laden.
    $tmpTar = "$OutFile.tmp.tar"
    Write-Host ""
    Write-Host "> Exportiere Images nach $tmpTar ..." -ForegroundColor Cyan
    & docker save -o $tmpTar @images
    if ($LASTEXITCODE -ne 0) {
        throw "docker save fehlgeschlagen (Exit-Code $LASTEXITCODE)."
    }

    Write-Host "> Komprimiere nach $OutFile ..." -ForegroundColor Cyan
    $in = [System.IO.File]::OpenRead($tmpTar)
    try {
        $out = [System.IO.File]::Create($OutFile)
        try {
            $gzip = New-Object System.IO.Compression.GzipStream($out, [System.IO.Compression.CompressionMode]::Compress)
            try { $in.CopyTo($gzip) } finally { $gzip.Dispose() }
        }
        finally { $out.Dispose() }
    }
    finally { $in.Dispose() }
    Remove-Item $tmpTar -Force

    $sizeMb = [math]::Round((Get-Item $OutFile).Length / 1MB, 1)
    Write-Host ""
    Write-Host "Fertig: $OutFile ($sizeMb MB)" -ForegroundColor Green
    Write-Host "Naechster Schritt (siehe docs/NAS-Images.md):" -ForegroundColor Green
    Write-Host "  scp `"$OutFile`" <user>@<nas-host>:/volume1/docker/zev/"
}
finally {
    Pop-Location
}
