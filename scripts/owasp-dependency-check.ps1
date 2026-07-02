#!/usr/bin/env pwsh
# OWASP Dependency-Check Runner fuer die Maven-Module (CVE-/Vulnerability-Scan).
#
# Liest den NVD-API-Key AUSSCHLIESSLICH aus der Umgebungsvariable NVD_API_KEY.
# Der Key wird NIE ins Repo geschrieben oder geloggt.
# READ-ONLY gegenueber Sourcen: aendert keine pom.xml; schreibt nur Reports nach <modul>/target/
# (bereits via .gitignore ausgeschlossen).
#
# ---------------------------------------------------------------------------
# Einmalige Einrichtung -- eine der beiden Varianten:
#   A) .env-Datei (empfohlen, lokal, gitignored):
#        NVD_API_KEY=<dein-key>   in die .env eintragen (Vorlage: .env.example)
#   B) Persistente Env-Var:
#        setx NVD_API_KEY "<dein-key>"  -> danach NEUES Terminal oeffnen
#        (oder fuer die aktuelle Session:  $env:NVD_API_KEY = "<dein-key>")
#   Key kostenlos anfordern: https://nvd.nist.gov/developers/request-an-api-key
#   Hinweis: Eine gesetzte Env-Var hat Vorrang vor dem Wert in der .env.
# ---------------------------------------------------------------------------
#
# Aufruf:
#   ./scripts/owasp-dependency-check.ps1                     # alle Maven-Module (aggregate)
#   ./scripts/owasp-dependency-check.ps1 -Module backend-service
#
# Hinweise:
#   * Erster Lauf laedt die NVD-CVE-Datenbank (mehrere hundert MB) in den lokalen
#     Maven-Cache (~/.m2/repository/org/owasp/dependency-check-data/); Folgelaeufe
#     nutzen diesen Cache und sind deutlich schneller.
#   * Ohne gueltigen Key drosselt der NVD-Feed (HTTP 429) und der Lauf bricht ab.

[CmdletBinding()]
param(
    [string]$Module
)

$ErrorActionPreference = 'Stop'

# Projekt-Root = uebergeordneter Ordner dieses scripts/-Verzeichnisses
$root = Split-Path -Parent $PSScriptRoot

# .env laden (nur Variablen, die nicht bereits in der Umgebung gesetzt sind --
# eine echte Env-Var, z.B. via setx, hat damit Vorrang vor der .env).
$envFile = Join-Path $root '.env'
if (Test-Path $envFile) {
    foreach ($line in Get-Content $envFile) {
        $trimmed = $line.Trim()
        if ($trimmed -eq '' -or $trimmed.StartsWith('#')) { continue }
        $kv = $trimmed -split '=', 2
        if ($kv.Count -ne 2) { continue }
        $key = $kv[0].Trim()
        $val = $kv[1].Trim().Trim('"').Trim("'")
        if ($key -and [string]::IsNullOrEmpty([Environment]::GetEnvironmentVariable($key))) {
            Set-Item -Path "env:$key" -Value $val
        }
    }
}

if ([string]::IsNullOrWhiteSpace($env:NVD_API_KEY)) {
    Write-Host ""
    Write-Host "NVD_API_KEY ist nicht gesetzt -- OWASP-Scan wird uebersprungen." -ForegroundColor Yellow
    Write-Host "  1. Key anfordern (kostenlos): https://nvd.nist.gov/developers/request-an-api-key"
    Write-Host "  2. Persistent setzen:         setx NVD_API_KEY ""<dein-key>"""
    Write-Host "  3. Neues Terminal oeffnen  -- oder:  `$env:NVD_API_KEY = ""<dein-key>"""
    Write-Host "  Alternativ: NVD_API_KEY in der lokalen .env-Datei eintragen (siehe .env.example)."
    Write-Host ""
    exit 1
}

Push-Location $root
try {
    if ($Module) {
        $mvnArgs = @('org.owasp:dependency-check-maven:check', '-pl', $Module)
    }
    else {
        $mvnArgs = @('org.owasp:dependency-check-maven:aggregate')
    }
    $mvnArgs += @("-DnvdApiKey=$env:NVD_API_KEY", '-Dformat=ALL')

    # Dokumentierte False-Positives ausblenden (Begruendung je Eintrag in der Datei)
    $suppression = Join-Path $root 'dependency-check-suppressions.xml'
    if (Test-Path $suppression) {
        $mvnArgs += "-DsuppressionFiles=$suppression"
    }

    # Key fuer die Konsolenausgabe maskieren
    $display = ($mvnArgs -replace '(-DnvdApiKey=).*', '$1***') -join ' '
    Write-Host "> mvn $display" -ForegroundColor Cyan

    & mvn.cmd @mvnArgs
    $code = $LASTEXITCODE

    Write-Host ""
    if ($Module) {
        Write-Host "Report: $Module/target/dependency-check-report.html (+ .json)" -ForegroundColor Green
    }
    else {
        Write-Host "Reports: <modul>/target/dependency-check-report.html (+ .json) je Modul" -ForegroundColor Green
    }
    exit $code
}
finally {
    Pop-Location
}
