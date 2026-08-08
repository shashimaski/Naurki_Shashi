Write-Host "=== dist/ listing ==="
$distPath = 'F:\views\g\Naukri\dist'
if (Test-Path $distPath) {
    Get-ChildItem $distPath -Filter '*.exe' | Select-Object Name,
        @{N='SizeMB';E={[math]::Round($_.Length/1MB, 2)}},
        LastWriteTime | Format-Table -AutoSize
} else {
    Write-Host "dist/ directory not found!"
}

Write-Host "=== Full dist listing ==="
Get-ChildItem $distPath | Select-Object Name,
    @{N='SizeMB';E={[math]::Round($_.Length/1MB, 2)}},
    LastWriteTime | Format-Table -AutoSize
