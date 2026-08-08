param()
$ErrorActionPreference = 'Stop'

Write-Host '==== build.tests -- smoke test ===='

$root    = 'F:\views\g\Naukri'
$distDir = Join-Path $root 'dist'

if (-not (Test-Path $distDir)) {
    Write-Host "SMOKE FAIL -- dist\ not found at $distDir"
    exit 1
}

$portableExe = Get-ChildItem -Path $distDir -Filter '*ortable*.exe' -Recurse -ErrorAction SilentlyContinue |
               Select-Object -First 1

if (-not $portableExe) {
    Write-Host "SMOKE FAIL -- No portable EXE found under $distDir"
    exit 1
}

Write-Host "Portable EXE : $($portableExe.FullName)"
Write-Host "Size (MB)    : $([math]::Round($portableExe.Length/1MB,2))"
Write-Host "Timestamp    : $($portableExe.LastWriteTime)"
Write-Host 'Launching ...'

$proc = Start-Process -FilePath $portableExe.FullName -PassThru

Write-Host "PID $($proc.Id) started -- waiting 15 seconds ..."
Start-Sleep -Seconds 15

if ($proc.HasExited) {
    Write-Host "SMOKE FAIL -- process exited early with code $($proc.ExitCode)"
    exit 1
}

Write-Host 'SMOKE OK'
Write-Host "Killing PID $($proc.Id) ..."
Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue

Write-Host '==== build.tests DONE ===='
exit 0
