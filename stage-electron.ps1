# Stage renderer
$dest = 'F:\views\g\Naukri\electron\renderer'
Remove-Item $dest -Recurse -Force -EA 0
New-Item -ItemType Directory -Force $dest | Out-Null
Copy-Item -Recurse -Force 'F:\views\g\Naukri\frontend\dist\*' $dest
Write-Host "Renderer staged to $dest"

# Stage backend jar
$jarDest = 'F:\views\g\Naukri\electron\resources\backend\naukri-be.jar'
$jarDestDir = Split-Path $jarDest
if (-not (Test-Path $jarDestDir)) { New-Item -ItemType Directory -Force $jarDestDir | Out-Null }
Copy-Item -Force 'F:\views\g\Naukri\backend\target\naukri-be.jar' $jarDest
Write-Host "Backend JAR staged to $jarDest"

# Verify
$rendererHtml = 'F:\views\g\Naukri\electron\renderer\index.html'
if (-not (Test-Path $rendererHtml)) { throw "renderer index.html not found after staging" }
if (-not (Test-Path $jarDest)) { throw "naukri-be.jar not found after staging" }
$jarInfo = Get-Item $jarDest
Write-Host "Staged JAR: $($jarInfo.Length) bytes | $($jarInfo.LastWriteTime)"
Write-Host "Staging complete"
