#Requires -Version 5.1
<#
.SYNOPSIS
    Phase: build-frontend - installs deps, runs Vite build, copies dist to electron/renderer.
    Created by Adikarthik Gupta C B
#>
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

Write-Host '==== build-frontend phase ===='

$root       = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$frontendDir = Join-Path $root 'frontend'
$distDir     = Join-Path $frontendDir 'dist'
$rendererDir = Join-Path $root 'electron\renderer'

Write-Host "Frontend  : $frontendDir"
Write-Host "Dist      : $distDir"
Write-Host "Renderer  : $rendererDir"

# Choose npm ci if lockfile exists, else npm install.
# Push-Location into the frontend dir so npm resolves package.json from there
# regardless of the caller's CWD (npm --prefix no longer overrides the search).
$lockfile = Join-Path $frontendDir 'package-lock.json'
Push-Location $frontendDir
try {
    if (Test-Path $lockfile) {
        Write-Host 'Running   : npm ci (lockfile found)'
        & npm.cmd ci
    } else {
        Write-Host 'Running   : npm install (no lockfile)'
        & npm.cmd install
    }
    if ($LASTEXITCODE -ne 0) { throw "build-frontend: npm install/ci exited with code $LASTEXITCODE" }

    Write-Host 'Running   : npm run build'
    & npm.cmd run build
    if ($LASTEXITCODE -ne 0) { throw "build-frontend: npm run build exited with code $LASTEXITCODE" }
} finally {
    Pop-Location
}

# Copy dist -> electron/renderer (delete-then-copy for clean state)
if (Test-Path $rendererDir) {
    Write-Host "Removing existing renderer dir: $rendererDir"
    Remove-Item -Recurse -Force $rendererDir
}
Write-Host "Copying dist -> renderer"
Copy-Item -Recurse $distDir $rendererDir

Write-Host '==== build-frontend DONE ===='
