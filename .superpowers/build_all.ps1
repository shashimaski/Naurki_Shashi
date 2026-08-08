$env:JAVA_HOME = 'C:\Users\e182114\.jdks\azul-17.0.10'
$env:PATH = $env:JAVA_HOME + '\bin;' + $env:PATH
Set-Location 'F:\views\g\Naukri\backend'
Write-Host "=== Full build + all tests ==="
mvn clean package '-DfailIfNoTests=false' 2>&1
Write-Host "MVN_EXIT=$LASTEXITCODE"
exit $LASTEXITCODE
