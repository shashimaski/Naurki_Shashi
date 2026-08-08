#Requires -Version 5.1
<#
.SYNOPSIS
    Smoke test - launches the produced portable EXE, waits 15 s, asserts still running.
    Prints "SMOKE OK" and exits 0 on pass; exits 1 on failure.
    Created by Adikarthik Gupta C B
.NOTES
    Expects build\build.ps1 -Variant Ship to have run first.
    The portable EXE is produced by electron-builder under <root>\dist\.
#>
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

Write-Host '==== build.tests - smoke test ===='

$root    = Split-Path -Parent $PSScriptRoot
$distDir = Join-Path $root 'dist'

if (-not (Test-Path $distDir)) {
    Write-Error "dist\ not found at $distDir - run build.ps1 -Variant Ship first."
    exit 1
}

# Find the portable exe (electron-builder names it *-portable.exe or *Portable.exe)
$portableExe = Get-ChildItem -Path $distDir -Filter '*ortable*.exe' -Recurse -ErrorAction SilentlyContinue |
               Select-Object -First 1

if (-not $portableExe) {
    Write-Error "No portable EXE found under $distDir."
    exit 1
}

Write-Host "Portable EXE : $($portableExe.FullName)"
Write-Host 'Launching ...'

$proc = Start-Process -FilePath $portableExe.FullName -PassThru

Write-Host "PID $($proc.Id) started - waiting 15 seconds ..."
Start-Sleep -Seconds 15

if ($proc.HasExited) {
    Write-Error "SMOKE FAIL - process exited early with code $($proc.ExitCode)."
    exit 1
}

Write-Host 'SMOKE OK'
Write-Host "Killing PID $($proc.Id) ..."
Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue

Write-Host '==== build.tests DONE ===='
exit 0
