# Re-read build.tests.ps1 content and rewrite without BOM, then run it
$srcPath = 'F:\views\g\Naukri\build\build.tests.ps1'
$content = [System.IO.File]::ReadAllText($srcPath)

# Check first bytes
$bytes = [System.IO.File]::ReadAllBytes($srcPath)
$hasBom = ($bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF)
Write-Host "File has BOM: $hasBom"
Write-Host "First 3 bytes: $($bytes[0]) $($bytes[1]) $($bytes[2])"

# Write a temp copy without BOM
$tempScript = 'F:\views\g\Naukri\build\build.tests.nobom.ps1'
[System.IO.File]::WriteAllText($tempScript, $content, (New-Object System.Text.UTF8Encoding($false)))
Write-Host "Wrote no-BOM copy to: $tempScript"

# Now execute it
Write-Host "=== Running smoke test ==="
& powershell.exe -File $tempScript
$exitCode = $LASTEXITCODE
Write-Host "Smoke test exit code: $exitCode"
exit $exitCode
