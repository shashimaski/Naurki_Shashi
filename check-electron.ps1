Set-Location 'F:\views\g\Naukri\electron'
Write-Host "=== Checking electron dir ==="
Get-ChildItem . | Select-Object Name, LastWriteTime | Format-Table

Write-Host "=== package.json scripts ==="
if (Test-Path 'package.json') {
    $pkg = Get-Content 'package.json' -Raw | ConvertFrom-Json
    $pkg.scripts | Format-List
    Write-Host "devDependencies:"
    $pkg.devDependencies | Format-List
}

Write-Host "=== node_modules/.bin check ==="
if (Test-Path 'node_modules\.bin\electron-builder') {
    Write-Host "electron-builder found in node_modules/.bin"
} elseif (Test-Path 'node_modules\.bin\electron-builder.cmd') {
    Write-Host "electron-builder.cmd found in node_modules/.bin"
} else {
    Write-Host "electron-builder NOT in node_modules/.bin"
    if (Test-Path 'node_modules') {
        Get-ChildItem 'node_modules\.bin' -Filter '*electron*' | Select-Object Name
    } else {
        Write-Host "node_modules does NOT exist"
    }
}

Write-Host "=== global electron-builder ==="
& electron-builder --version 2>&1
