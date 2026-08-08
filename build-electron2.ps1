Set-Location 'F:\views\g\Naukri\electron'
Write-Host "Starting electron-builder packaging..."
Write-Host "Working dir: $(Get-Location)"
Write-Host "Timestamp: $(Get-Date)"

# Use node_modules\.bin\electron-builder directly
$ebCmd = 'F:\views\g\Naukri\electron\node_modules\.bin\electron-builder.cmd'

if (-not (Test-Path $ebCmd)) {
    Write-Host "electron-builder.cmd not found at: $ebCmd"
    # Try alternate
    $ebCmd = 'F:\views\g\Naukri\electron\node_modules\electron-builder\bin\electron-builder.js'
    Write-Host "Trying: $ebCmd"
}

Write-Host "Using: $ebCmd"

$output = & $ebCmd --win nsis portable --config electron-builder.yml 2>&1
$exitCode = $LASTEXITCODE
Write-Host "--- electron-builder output (last 30 lines) ---"
$lines = $output -split "`n"
$last30 = $lines | Select-Object -Last 30
$last30 | ForEach-Object { Write-Host $_ }
Write-Host "--- exit code: $exitCode ---"
exit $exitCode
