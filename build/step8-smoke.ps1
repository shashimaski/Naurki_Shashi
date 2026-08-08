param([string]$probe)

if (-not $probe) {
    # Try to find a recent probe dir
    $probe = Get-ChildItem "$env:TEMP" -Directory -Filter 'naukri-build-probe-*' | Sort-Object LastWriteTime -Descending | Select-Object -First 1 | Select-Object -ExpandProperty FullName
    if (-not $probe) { throw "No probe directory provided or found" }
}
Write-Host "Using probe dir: $probe"

$exe = Get-ChildItem $probe -Filter 'NaukriAutomator.exe' -Recurse | Select-Object -First 1
if (-not $exe) { throw "NaukriAutomator.exe not found in $probe" }
Write-Host "Launching: $($exe.FullName)"

$t0 = Get-Date
$proc = Start-Process $exe.FullName -PassThru
$jvm = $null
$win = $null
$deadline = (Get-Date).AddSeconds(90)

while ((Get-Date) -lt $deadline) {
    Start-Sleep -Milliseconds 500
    if (-not $jvm -and (Get-Process javaw -EA 0)) { $jvm = Get-Date }
    try {
        $mwHandle = (Get-Process -Id $proc.Id -EA 0).MainWindowHandle
        if ($mwHandle -and $mwHandle -ne 0) { $win = Get-Date; break }
    } catch {}
}

Start-Sleep -Seconds 10
$alive = -not $proc.HasExited

$jvmSec = if ($jvm) { [int]($jvm - $t0).TotalSeconds } else { -1 }
$winSec = if ($win) { [int]($win - $t0).TotalSeconds } else { -1 }

Write-Host ("javaw at +{0}s  window at +{1}s  alive+10s: {2}" -f $jvmSec, $winSec, $alive)

Stop-Process -Id $proc.Id -Force -EA 0
Get-Process javaw -EA 0 | Stop-Process -Force -EA 0
Get-Process chrome, chromium -EA 0 | Where-Object { $_.Path -match 'ms-playwright' } | Stop-Process -Force -EA 0

Remove-Item $probe -Recurse -Force -EA 0
Write-Host "Cleanup done"

Write-Output "SMOKE_JVM_SEC=$jvmSec"
Write-Output "SMOKE_WIN_SEC=$winSec"
Write-Output "SMOKE_ALIVE=$alive"
