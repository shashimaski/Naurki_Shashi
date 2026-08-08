param([string]$probe)

Write-Host "=== Verifying dist artifacts ==="
Get-ChildItem 'F:\views\g\Naukri\dist' -File | Select-Object Name, @{n='SizeMB';e={[math]::Round($_.Length/1MB,1)}}, LastWriteTime | Format-Table -AutoSize

$zip  = Get-ChildItem 'F:\views\g\Naukri\dist' -Filter '*.zip'  | Select-Object -First 1
$nsis = Get-ChildItem 'F:\views\g\Naukri\dist' -Filter '*Setup*.exe' | Select-Object -First 1
if (-not $zip)  { throw "no zip artifact in dist" }
if (-not $nsis) { throw "no NSIS installer artifact in dist" }
Write-Host "ZIP : $($zip.Name)  $([math]::Round($zip.Length/1MB,1)) MB"
Write-Host "NSIS: $($nsis.Name)  $([math]::Round($nsis.Length/1MB,1)) MB"

# Extract zip for inspection
if (-not $probe) { $probe = "$env:TEMP\naukri-build-probe-$(Get-Date -Format 'HHmmss')" }
Write-Host "Extracting zip to: $probe"
Expand-Archive -Path $zip.FullName -DestinationPath $probe -Force

# ---------------------------------------------------
# 1. Check renderer HTML (from staged renderer dir, which was packed into asar)
# ---------------------------------------------------
$stagedHtml = 'F:\views\g\Naukri\electron\renderer\index.html'
if (-not (Test-Path $stagedHtml)) { throw "Staged renderer index.html not found at $stagedHtml" }
if (Select-String -Path $stagedHtml -Pattern 'src="/|href="/' -Quiet) { throw "Staged HTML has absolute paths -- vite base regression" }
Write-Host "HTML paths (staged renderer): OK (relative)"

# Also check inside zip - the renderer HTML is in the app.asar
# We verify the jar (NOT in asar) and preload separately
# For the zip's HTML we verify via win-unpacked (non-asar path check)
$winUnpacked = 'F:\views\g\Naukri\dist\win-unpacked'
if (Test-Path $winUnpacked) {
    $unpHtml = Get-ChildItem $winUnpacked -Filter 'index.html' -Recurse | Where-Object { $_.FullName -match 'renderer' } | Select-Object -First 1
    if ($unpHtml) {
        if (Select-String -Path $unpHtml.FullName -Pattern 'src="/|href="/' -Quiet) { throw "win-unpacked HTML has absolute paths" }
        Write-Host "HTML paths (win-unpacked renderer): OK (relative)"
    } else {
        Write-Host "Note: win-unpacked renderer HTML not found (packed into asar) -- using staged check only"
    }
}

# ---------------------------------------------------
# 2. Check CSS selectors (from staged renderer dir)
# ---------------------------------------------------
$rendererCss = Get-ChildItem 'F:\views\g\Naukri\electron\renderer\assets' -Filter '*.css' | Select-Object -First 1
if (-not $rendererCss) { throw "No CSS file found in staged renderer/assets" }
Write-Host "Renderer CSS: $($rendererCss.FullName)"
foreach ($p in '\.btn-primary', '-webkit-autofill', '--accent') {
    if (-not (Select-String -Path $rendererCss.FullName -Pattern $p -Quiet)) { throw "staged renderer CSS missing $p" }
    Write-Host "CSS selector $p : OK"
}

# ---------------------------------------------------
# 3. Check preload.js in electron source (packaged into asar)
# ---------------------------------------------------
$preloadSrc = 'F:\views\g\Naukri\electron\preload.js'
if (-not (Test-Path $preloadSrc)) { throw "preload.js not found at $preloadSrc" }
if (-not (Select-String -Path $preloadSrc -Pattern 'exposeInMainWorld' -Quiet)) { throw "preload missing exposeInMainWorld" }
if (-not (Select-String -Path $preloadSrc -Pattern 'NAUKRI_BE_PORT' -Quiet)) { throw "preload missing NAUKRI_BE_PORT export" }
Write-Host "Preload contextBridge (source): OK"

# Also check in win-unpacked if available
$winPreload = Get-ChildItem $winUnpacked -Filter 'preload.js' -Recurse -EA 0 | Select-Object -First 1
if ($winPreload) {
    if (-not (Select-String -Path $winPreload.FullName -Pattern 'exposeInMainWorld' -Quiet)) { throw "win-unpacked preload missing exposeInMainWorld" }
    if (-not (Select-String -Path $winPreload.FullName -Pattern 'NAUKRI_BE_PORT' -Quiet)) { throw "win-unpacked preload missing NAUKRI_BE_PORT" }
    Write-Host "Preload contextBridge (win-unpacked): OK"
}

# ---------------------------------------------------
# 4. Check jar in the zip probe dir (NOT inside asar)
# ---------------------------------------------------
$stagedJarInZip = Get-ChildItem $probe -Filter 'naukri-be.jar' -Recurse | Select-Object -First 1
if (-not $stagedJarInZip) { throw "naukri-be.jar not found in extracted zip at $probe" }
$srcJar = Get-Item 'F:\views\g\Naukri\backend\target\naukri-be.jar'
if ($stagedJarInZip.Length -ne $srcJar.Length) { throw "packaged BE jar size $($stagedJarInZip.Length) differs from source $($srcJar.Length)" }
Write-Host "Backend jar size in zip: OK ($([math]::Round($stagedJarInZip.Length/1MB,2)) MB)"

# Also verify preload.js in zip
$zipPreload = Get-ChildItem $probe -Filter 'preload.js' -Recurse | Select-Object -First 1
if ($zipPreload) {
    if (-not (Select-String -Path $zipPreload.FullName -Pattern 'exposeInMainWorld' -Quiet)) { throw "zip preload missing exposeInMainWorld" }
    if (-not (Select-String -Path $zipPreload.FullName -Pattern 'NAUKRI_BE_PORT' -Quiet)) { throw "zip preload missing NAUKRI_BE_PORT" }
    Write-Host "Preload contextBridge (in zip): OK"
} else {
    Write-Host "Note: preload.js inside zip is packed in app.asar -- verified from source"
}

Write-Host "Step 7: All verifications PASSED"
Write-Output "PROBE_DIR=$probe"
Write-Output "ZIP_NAME=$($zip.Name)"
Write-Output "ZIP_SIZE=$([math]::Round($zip.Length/1MB,1))"
Write-Output "NSIS_NAME=$($nsis.Name)"
Write-Output "NSIS_SIZE=$([math]::Round($nsis.Length/1MB,1))"
