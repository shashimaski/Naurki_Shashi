#Requires -Version 5.1
<#
.SYNOPSIS
    Phase: build-electron - copies JARs into resources, runs electron-builder.
    Created by Adikarthik Gupta C B
.PARAMETER Variant
    Ship (default) - NSIS + portable installers only.
    E2E  - also stages mock-naukri.jar into electron/resources/mock/.
#>
param(
    [ValidateSet('Ship', 'E2E')]
    [string]$Variant = 'Ship'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

Write-Host "==== build-electron phase (Variant=$Variant) ===="

$root        = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$electronDir = Join-Path $root 'electron'

# ---- locate backend JAR ----
$backendTarget = Join-Path $root 'backend\target'
$beJar = Get-ChildItem -Path $backendTarget -Filter 'naukri-be*.jar' -ErrorAction SilentlyContinue |
         Where-Object { $_.Name -notmatch 'sources|javadoc|plain|original' } |
         Select-Object -First 1
if (-not $beJar) { throw "build-electron: backend JAR not found under $backendTarget - run build-backend first" }
Write-Host "Backend JAR : $($beJar.FullName)"

# Copy backend JAR -> electron/resources/backend/naukri-be.jar
$beResDir = Join-Path $electronDir 'resources\backend'
if (-not (Test-Path $beResDir)) { New-Item -ItemType Directory -Force -Path $beResDir | Out-Null }
$beJarDest = Join-Path $beResDir 'naukri-be.jar'
Write-Host "Copying backend JAR -> $beJarDest"
Copy-Item -Force $beJar.FullName $beJarDest

# ---- mock JAR handling ----
$mockResDir = Join-Path $electronDir 'resources\mock'
if ($Variant -eq 'E2E') {
    $mockTarget = Join-Path $root 'mock-naukri\target'
    $mockJar = Get-ChildItem -Path $mockTarget -Filter 'mock-naukri*.jar' -ErrorAction SilentlyContinue |
               Where-Object { $_.Name -notmatch 'sources|javadoc|plain|original' } |
               Select-Object -First 1
    if (-not $mockJar) { throw "build-electron(E2E): mock JAR not found under $mockTarget - run build-mock first" }
    Write-Host "Mock JAR    : $($mockJar.FullName)"
    if (-not (Test-Path $mockResDir)) { New-Item -ItemType Directory -Force -Path $mockResDir | Out-Null }
    $mockJarDest = Join-Path $mockResDir 'mock-naukri.jar'
    Write-Host "Copying mock JAR -> $mockJarDest"
    Copy-Item -Force $mockJar.FullName $mockJarDest
} else {
    # Ship: remove mock directory if it exists
    if (Test-Path $mockResDir) {
        Write-Host "Ship variant - removing $mockResDir"
        Remove-Item -Recurse -Force $mockResDir
    }
}

# ---- electron-builder ----
# npm install / npx (without an explicit target arg) resolve package.json from the
# current working directory, not from --prefix. Push-Location ensures CWD is the
# electron project root regardless of where the build was launched from.
Push-Location $electronDir
try {
    Write-Host 'Running   : npm install (electron)'
    & npm.cmd install
    if ($LASTEXITCODE -ne 0) { throw "build-electron: npm install exited with code $LASTEXITCODE" }

    Write-Host 'Running   : npx electron-builder --win nsis portable --config electron-builder.yml'
    & npx.cmd electron-builder --win nsis portable --config (Join-Path $electronDir 'electron-builder.yml')
    if ($LASTEXITCODE -ne 0) { throw "build-electron: electron-builder exited with code $LASTEXITCODE" }
} finally {
    Pop-Location
}

Write-Host "==== build-electron DONE (Variant=$Variant) ===="
