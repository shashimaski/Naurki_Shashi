# Clean dist
Remove-Item 'F:\views\g\Naukri\dist\*' -Recurse -Force -EA 0
Write-Host "Dist cleaned"

Set-Location 'F:\views\g\Naukri\electron'
Write-Host "Starting electron-builder..."
$ebCmd = 'F:\views\g\Naukri\electron\node_modules\.bin\electron-builder.cmd'
& $ebCmd --win nsis zip --config electron-builder.yml
if ($LASTEXITCODE -ne 0) { throw "electron-builder failed with exit code $LASTEXITCODE" }
Write-Host "electron-builder succeeded"

# List artifacts
Write-Host "`nDist artifacts:"
Get-ChildItem 'F:\views\g\Naukri\dist' -File | ForEach-Object {
    $sizeMB = [math]::Round($_.Length / 1MB, 1)
    Write-Host "  $($_.Name) | $sizeMB MB | $($_.LastWriteTime)"
}
