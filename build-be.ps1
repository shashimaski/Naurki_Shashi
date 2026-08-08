$env:JAVA_HOME = 'C:\Users\e182114\.jdks\azul-17.0.10'
$env:PATH = $env:JAVA_HOME + '\bin;' + $env:PATH
Set-Location 'F:\views\g\Naukri\backend'
Write-Host "JAVA_HOME = $env:JAVA_HOME"
& java -version
Write-Host "Starting Maven build..."
& mvn -q clean package -DskipTests 2>&1
Write-Host "Maven exit code: $LASTEXITCODE"
