$env:JAVA_HOME = 'C:\Users\e182114\.jdks\azul-17.0.10'
$env:PATH = $env:JAVA_HOME + '\bin;' + $env:PATH
Set-Location 'F:\views\g\Naukri\backend'
Write-Output "=== TDD GREEN RUN (expect PASS) ==="
& mvn 'test' '-Dtest=HealthControllerTest' 2>&1
Write-Output "Exit code: $LASTEXITCODE"
