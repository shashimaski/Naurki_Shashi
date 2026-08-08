$zip = Get-ChildItem 'F:\views\g\Naukri\dist' -Filter '*.zip' | Select-Object -First 1
$nsis = Get-ChildItem 'F:\views\g\Naukri\dist' -Filter '*Setup*.exe' | Select-Object -First 1

if (-not $zip)  { throw "no zip artifact" }
if (-not $nsis) { throw "no NSIS installer artifact" }

Write-Host "ZIP:  $($zip.Name) | $([math]::Round($zip.Length/1MB,1)) MB | $($zip.LastWriteTime)"
Write-Host "NSIS: $($nsis.Name) | $([math]::Round($nsis.Length/1MB,1)) MB | $($nsis.LastWriteTime)"

# Extract zip to scratch folder
$timestamp = Get-Date -Format 'HHmmss'
$probe = "$env:TEMP\naukri-build-probe-$timestamp"
Write-Host "`nExtracting ZIP to $probe ..."
Expand-Archive -Path $zip.FullName -DestinationPath $probe -Force
Write-Host "Extraction complete"

# Check renderer HTML for absolute paths
$rendererHtml = Get-ChildItem $probe -Filter 'index.html' -Recurse | Where-Object { $_.FullName -match 'renderer' } | Select-Object -First 1
if (-not $rendererHtml) { throw "renderer index.html not found in extracted zip" }
Write-Host "Checking renderer HTML: $($rendererHtml.FullName)"
$absPath = Select-String -Path $rendererHtml.FullName -Pattern 'src="/|href="/' -Quiet
if ($absPath) { throw "FAIL: packaged HTML has absolute paths -- vite base regression" }
Write-Host "HTML PASS: no absolute asset paths"

# Check renderer CSS for selectors
$rendererCss = Get-ChildItem $probe -Filter '*.css' -Recurse | Where-Object { $_.FullName -match 'renderer' } | Select-Object -First 1
if (-not $rendererCss) { throw "renderer CSS not found in extracted zip" }
Write-Host "Checking renderer CSS: $($rendererCss.FullName)"
foreach ($p in '\.btn-primary', '-webkit-autofill', '--accent') {
    if (-not (Select-String -Path $rendererCss.FullName -Pattern $p -Quiet)) {
        throw "FAIL: packaged CSS missing selector: $p"
    }
    Write-Host "  CSS PASS: $p found"
}

# Check preload uses contextBridge
$preload = Get-ChildItem $probe -Filter 'preload.js' -Recurse | Select-Object -First 1
if (-not $preload) { throw "preload.js not found in extracted zip" }
Write-Host "Checking preload: $($preload.FullName)"
$cbCheck = Select-String -Path $preload.FullName -Pattern "exposeInMainWorld" -Quiet
if (-not $cbCheck) { throw "FAIL: preload not using contextBridge for port" }
Write-Host "Preload PASS: exposeInMainWorld found"

# Check backend jar size matches source
$stagedJar = Get-ChildItem $probe -Filter 'naukri-be.jar' -Recurse | Select-Object -First 1
if (-not $stagedJar) { throw "naukri-be.jar not found in extracted zip" }
$srcJar = Get-Item 'F:\views\g\Naukri\backend\target\naukri-be.jar'
Write-Host "Packaged JAR: $($stagedJar.Length) bytes"
Write-Host "Source JAR:   $($srcJar.Length) bytes"
if ($stagedJar.Length -ne $srcJar.Length) { throw "FAIL: packaged BE jar size $($stagedJar.Length) != source $($srcJar.Length) -- staging bug" }
Write-Host "JAR size PASS: sizes match ($($stagedJar.Length) bytes)"

Write-Host "`nAll bundle verification checks PASSED"
Write-Host "Probe dir: $probe"

# Return probe dir for smoke test
Write-Output "PROBE_DIR=$probe"
