Get-NetTCPConnection -LocalPort 8080 -State Listen -EA 0 | Select-Object -ExpandProperty OwningProcess | ForEach-Object { Stop-Process -Id $_ -Force -EA 0 }
Get-NetTCPConnection -LocalPort 5173 -State Listen -EA 0 | Select-Object -ExpandProperty OwningProcess | ForEach-Object { Stop-Process -Id $_ -Force -EA 0 }
Get-Process chrome, chromium -EA 0 | Where-Object { $_.Path -match 'ms-playwright' } | Stop-Process -Force -EA 0
Start-Sleep -Seconds 2
Write-Host "Step 1: Cleanup done"
