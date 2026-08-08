# Check JRE
$jreExe = 'F:\views\g\Naukri\electron\resources\jre\bin\javaw.exe'
if (-not (Test-Path $jreExe)) {
    Write-Host "MISSING: JRE javaw.exe -- will fetch"
    & 'F:\views\g\Naukri\build\fetch-jre.ps1'
} else {
    Write-Host "JRE OK: $jreExe"
}

# Check Playwright Chromium
$chromiumSearch = Get-ChildItem 'F:\views\g\Naukri\electron\resources\playwright\chromium-*\chrome-win\chrome.exe' -EA 0 | Select-Object -First 1
if (-not $chromiumSearch) {
    Write-Host "MISSING: Playwright Chromium -- will fetch"
    & 'F:\views\g\Naukri\build\install-playwright.ps1'
} else {
    Write-Host "Playwright Chromium OK: $($chromiumSearch.FullName)"
}

Write-Host "Resource check complete"
