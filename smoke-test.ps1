$probe = 'C:\Users\e182114\AppData\Local\Temp\naukri-build-probe-170922'

# JAR size check
$stagedJar = Get-ChildItem $probe -Filter 'naukri-be.jar' -Recurse | Select-Object -First 1
$srcJar = Get-Item 'F:\views\g\Naukri\backend\target\naukri-be.jar'
Write-Host "Packaged JAR: $($stagedJar.Length) bytes"
Write-Host "Source JAR:   $($srcJar.Length) bytes"
if ($stagedJar.Length -ne $srcJar.Length) { throw "FAIL: packaged BE jar size $($stagedJar.Length) != source $($srcJar.Length) -- staging bug" }
Write-Host "JAR size PASS: sizes match"

# Find the portable EXE
$exe = Get-ChildItem $probe -Filter 'NaukriAutomator.exe' -Recurse | Select-Object -First 1
if (-not $exe) { throw "NaukriAutomator.exe not found in probe dir" }
Write-Host "`nStarting smoke test: $($exe.FullName)"

$t0 = Get-Date
$proc = Start-Process $exe.FullName -PassThru
Write-Host "Process started PID=$($proc.Id)"

$jvm = $null
$win = $null
$deadline = (Get-Date).AddSeconds(90)

while ((Get-Date) -lt $deadline) {
    Start-Sleep -Milliseconds 500
    if (-not $jvm) {
        $javawProcs = Get-Process javaw -EA 0
        if ($javawProcs) { $jvm = Get-Date; Write-Host "javaw appeared at +$([int]($jvm - $t0).TotalSeconds)s" }
    }
    $procState = Get-Process -Id $proc.Id -EA 0
    if ($procState) {
        $mw = $procState.MainWindowHandle
        if ($mw -and $mw -ne [IntPtr]::Zero) {
            $win = Get-Date
            Write-Host "Window appeared at +$([int]($win - $t0).TotalSeconds)s"
            break
        }
    }
}

if (-not $jvm) { Write-Host "WARNING: javaw never appeared within 90s" }
if (-not $win) { Write-Host "WARNING: main window never appeared within 90s" }

Write-Host "Waiting 10 more seconds..."
Start-Sleep -Seconds 10
$alive = -not $proc.HasExited
$javawSec = if ($jvm) { [int]($jvm - $t0).TotalSeconds } else { -1 }
$winSec   = if ($win) { [int]($win - $t0).TotalSeconds } else { -1 }
Write-Host "javaw at +${javawSec}s  window at +${winSec}s  alive+10s: $alive"

# Cleanup
Stop-Process -Id $proc.Id -Force -EA 0
Get-Process javaw -EA 0 | Stop-Process -Force -EA 0
Get-Process chrome, chromium -EA 0 | Where-Object { $_.Path -match 'ms-playwright' } | Stop-Process -Force -EA 0
Remove-Item $probe -Recurse -Force -EA 0
Write-Host "Probe cleaned up"

# Store results for report
Write-Output "SMOKE_JAVAW_SEC=$javawSec"
Write-Output "SMOKE_WIN_SEC=$winSec"
Write-Output "SMOKE_ALIVE=$alive"
