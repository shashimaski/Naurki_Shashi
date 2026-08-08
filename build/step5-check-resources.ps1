$needsFetch = $false
if (-not (Test-Path 'F:\views\g\Naukri\electron\resources\jre\bin\javaw.exe')) {
    Write-Host "WARNING: JRE not found"
    $needsFetch = $true
}
$chromiumExe = Get-ChildItem 'F:\views\g\Naukri\electron\resources\playwright\chromium-*\chrome-win\chrome.exe' -EA 0
if (-not $chromiumExe) {
    Write-Host "WARNING: Playwright chromium not found"
    $needsFetch = $true
}
if ($needsFetch) {
    Write-Host "Fetching JRE and Playwright..."
    if (Test-Path 'F:\views\g\Naukri\build\fetch-jre.ps1') {
        & 'F:\views\g\Naukri\build\fetch-jre.ps1'
    } else {
        Write-Warning "fetch-jre.ps1 not found"
    }
    if (Test-Path 'F:\views\g\Naukri\build\install-playwright.ps1') {
        & 'F:\views\g\Naukri\build\install-playwright.ps1'
    } else {
        Write-Warning "install-playwright.ps1 not found"
    }
} else {
    $jreVer = (& 'F:\views\g\Naukri\electron\resources\jre\bin\javaw.exe' -version 2>&1) | Select-Object -First 1
    Write-Host "JRE OK: $jreVer"
    Write-Host "Chromium OK: $($chromiumExe[0].FullName)"
}
Write-Host "Step 5: Resource check done"
