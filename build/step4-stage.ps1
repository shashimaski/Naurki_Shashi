# Stage renderer
$dest = 'F:\views\g\Naukri\electron\renderer'
Remove-Item $dest -Recurse -Force -EA 0
New-Item -ItemType Directory -Force $dest | Out-Null
Copy-Item -Recurse -Force 'F:\views\g\Naukri\frontend\dist\*' $dest
Write-Host "Renderer staged to: $dest"

# Stage backend jar
$jarSrc = 'F:\views\g\Naukri\backend\target\naukri-be.jar'
$jarDest = 'F:\views\g\Naukri\electron\resources\backend\naukri-be.jar'
$jarDestDir = Split-Path $jarDest
if (-not (Test-Path $jarDestDir)) { New-Item -ItemType Directory -Force $jarDestDir | Out-Null }
Copy-Item -Force $jarSrc $jarDest
Write-Host "Backend jar staged to: $jarDest"
Write-Host "Step 4: Staging done"
