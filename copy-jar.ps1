$src = 'F:\views\g\Naukri\backend\target'
$dest = 'F:\views\g\Naukri\electron\resources\backend\naukri-be.jar'

# Find the fat jar
$jars = Get-ChildItem $src -Filter '*.jar' | Where-Object { $_.Name -notlike '*-sources.jar' -and $_.Name -notlike '*-javadoc.jar' -and $_.Name -notlike 'original-*' }
Write-Host "Found jars in target:"
$jars | ForEach-Object { Write-Host "  $($_.FullName) - $($_.Length) bytes - $($_.LastWriteTime)" }

# Find the main fat jar (largest or matches pattern)
$fatJar = $jars | Sort-Object Length -Descending | Select-Object -First 1
Write-Host "Selected fat jar: $($fatJar.FullName)"
Write-Host "Size: $($fatJar.Length) bytes"
Write-Host "Modified: $($fatJar.LastWriteTime)"

# Ensure destination directory exists
$destDir = Split-Path $dest
if (-not (Test-Path $destDir)) {
    New-Item -ItemType Directory -Path $destDir -Force | Out-Null
    Write-Host "Created directory: $destDir"
}

# Copy
Copy-Item $fatJar.FullName $dest -Force
Write-Host "Copied to: $dest"
$destInfo = Get-Item $dest
Write-Host "Destination size: $($destInfo.Length) bytes - $($destInfo.LastWriteTime)"
Write-Host "Copy exit: $LASTEXITCODE"
