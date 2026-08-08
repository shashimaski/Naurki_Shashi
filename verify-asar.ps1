$probe = 'C:\Users\e182114\AppData\Local\Temp\naukri-build-probe-170922'
$asarFile = "$probe\resources\app.asar"
$asarExtract = "$probe\app-extracted"

Write-Host "Extracting asar: $asarFile"
$asarCmd = 'F:\views\g\Naukri\electron\node_modules\.bin\asar.cmd'
& $asarCmd extract $asarFile $asarExtract
if ($LASTEXITCODE -ne 0) { throw "asar extraction failed" }
Write-Host "asar extracted to $asarExtract"

# Find renderer index.html
$rendererHtml = Get-ChildItem $asarExtract -Filter 'index.html' -Recurse | Where-Object { $_.FullName -match 'renderer' } | Select-Object -First 1
if (-not $rendererHtml) {
    Write-Host "Warning: No renderer/index.html -- checking all index.html:"
    Get-ChildItem $asarExtract -Filter 'index.html' -Recurse | Select-Object FullName
    $rendererHtml = Get-ChildItem $asarExtract -Filter 'index.html' -Recurse | Select-Object -First 1
}
Write-Host "Checking HTML: $($rendererHtml.FullName)"
$absPath = Select-String -Path $rendererHtml.FullName -Pattern 'src="/|href="/' -Quiet
if ($absPath) { throw "FAIL: packaged HTML has absolute paths -- vite base regression" }
Write-Host "HTML PASS: no absolute asset paths"

# Find renderer CSS
$rendererCss = Get-ChildItem $asarExtract -Filter '*.css' -Recurse | Where-Object { $_.FullName -match 'renderer' } | Select-Object -First 1
if (-not $rendererCss) {
    $rendererCss = Get-ChildItem $asarExtract -Filter '*.css' -Recurse | Select-Object -First 1
}
Write-Host "Checking CSS: $($rendererCss.FullName)"
foreach ($p in '\.btn-primary', '-webkit-autofill', '--accent') {
    if (-not (Select-String -Path $rendererCss.FullName -Pattern $p -Quiet)) {
        throw "FAIL: packaged CSS missing selector: $p"
    }
    Write-Host "  CSS PASS: $p found"
}

# Find preload.js
$preload = Get-ChildItem $asarExtract -Filter 'preload.js' -Recurse | Select-Object -First 1
if (-not $preload) { throw "preload.js not found in asar" }
Write-Host "Checking preload: $($preload.FullName)"
$cbCheck = Select-String -Path $preload.FullName -Pattern "exposeInMainWorld" -Quiet
if (-not $cbCheck) { throw "FAIL: preload not using contextBridge (exposeInMainWorld not found)" }
Write-Host "Preload PASS: exposeInMainWorld found"

# Show preload content snippet
Write-Host "`nPreload.js content preview:"
Get-Content $preload.FullName | Select-Object -First 20

Write-Host "`nAll asar verification checks PASSED"
