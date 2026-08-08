Remove-Item 'F:\views\g\Naukri\dist\*' -Recurse -Force -EA 0
Set-Location 'F:\views\g\Naukri\electron'
Write-Host "Running electron-builder..."
$eb = 'F:\views\g\Naukri\electron\node_modules\.bin\electron-builder.cmd'
& $eb --win nsis zip --config electron-builder.yml
if ($LASTEXITCODE -ne 0) { throw "electron-builder failed with exit code $LASTEXITCODE" }
Write-Host "Step 6: Packaging done"
