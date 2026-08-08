$env:JAVA_HOME = 'C:\Users\e182114\.jdks\azul-17.0.10'
Set-Location 'F:\views\g\Naukri\backend'
Write-Host 'Starting BE build...'
& mvn -q clean package -DskipTests
if ($LASTEXITCODE -ne 0) { throw "BE build failed with exit code $LASTEXITCODE" }
Write-Host 'BE build succeeded'
$jar = Get-Item 'F:\views\g\Naukri\backend\target\naukri-be.jar'
$sizeMB = [math]::Round($jar.Length / 1MB, 1)
Write-Host "JAR: $($jar.Name) | $sizeMB MB | $($jar.LastWriteTime)"
