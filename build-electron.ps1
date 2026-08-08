Set-Location 'F:\views\g\Naukri\electron'
Write-Host "Starting electron-builder packaging..."
Write-Host "Working dir: $(Get-Location)"
Write-Host "Timestamp: $(Get-Date)"

$output = & npx electron-builder --win nsis portable --config electron-builder.yml 2>&1
$exitCode = $LASTEXITCODE
Write-Host "--- electron-builder output (last 30 lines) ---"
$lines = $output -split "`n"
$last30 = $lines | Select-Object -Last 30
$last30 | ForEach-Object { Write-Host $_ }
Write-Host "--- exit code: $exitCode ---"
exit $exitCode
